package naru.aweb.handler;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;

import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.handler.ServerBaseHandler.SCOPE;
import naru.aweb.mapping.Mapping;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

public class ReplayHelper {
	private static Logger logger = Logger.getLogger(ReplayHelper.class);
	private static Config config=Config.getConfig();
	private File defaultRootDir=new File(config.getString("path.replayDocroot"));

	private String getResolveDigest(HeaderParser requestHeader,MappingResult mapping){
		ServerParser targetHostServer=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		String resolveDigest=AccessLog.calcResolveDigest(requestHeader.getMethod(),mapping.isResolvedHttps(),targetHostServer.toString(),path,requestHeader.getQuery());
		return resolveDigest;
	}
	
	private AccessLog searchAccessLog(AccessLog myAccessLog,HeaderParser requestHeader,MappingResult mapping){
		//String resolveDigest=getResolveDigest(requestHeader,mapping);
		//TODO resolveDigestとbodyDigestをキーに探す
//		Collection<AccessLog> accessLogs=AccessLog.query("WHERE destinationType!='R' && statusCode!=null && responseBodyDigest!=null && resolveDigest=='"+resolveDigest+"' ORDER BY id DESC");
		
		String path=mapping.getResolvePath();
		
		Collection<AccessLog> accessLogs=AccessLog.query("WHERE destinationType!='R' && statusCode!=null && requestLine!=null && requestLine.matches('.*"+path+".*') ORDER BY id DESC");
		Iterator<AccessLog> itr=accessLogs.iterator();
		AccessLog accessLog=null;
		while(itr.hasNext()){
			accessLog=itr.next();
			myAccessLog.setOriginalLogId(accessLog.getId());
			return accessLog; 
		}
		System.out.println("miss:" + myAccessLog.getRequestLine());
		return null; 
	}
	
	private File existsFile(File base,String path){
		File file=new File(base,path);
		if(file.exists()){
			return file;
		}
		return null;
	}
	
	private File searchFile(HeaderParser requestHeader,MappingResult mapping){
		String protocol=null;
		if(!"GET".equals(requestHeader.getMethod())){
			return null;
		}
		switch(mapping.getDestinationType()){
		case HTTP:
			protocol="http/";
			break;
		case HTTPS:
			protocol="https/";
			break;
		default:
			return null;
		}
		//a.com/path1/path2/file.jsの場合
		String replayDocRoot=(String)mapping.getOption(Mapping.OPTION_REPLAY_DOCROOT);
		File seachRoot=defaultRootDir;
		if(replayDocRoot!=null){
			seachRoot=new File(replayDocRoot);
		}
		if(!seachRoot.exists()){
			return null;
		}
		ServerParser resolveServer=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		File file;
		//{http}/{searchRoot}/a.com/path1/path2/file.js
		file=existsFile(seachRoot,protocol+resolveServer.getHost() + path);
		if(file!=null && file.isFile()){
			return file;
		}
		//{searchRoot}/path1/path2/file.js
		file=existsFile(seachRoot,path);
		if(file!=null&& file.isFile()){
			return file;
		}
		//{searchRoot}/file.js
		int pos=path.lastIndexOf('/');
		file=existsFile(seachRoot,path.substring(pos));
		if(file!=null&& file.isFile()){
			return file;
		}
		return null;
	}
	
	/**
	 */
	public boolean doReplay(WebServerHandler handler,ByteBuffer[] body){
		if(logger.isDebugEnabled())logger.debug("#doReplay cid:"+handler.getChannelId());
//		Set history=getUserSetting().getReplayHistory();
		AccessLog accessLog=handler.getAccessLog();
		if(accessLog==null){
			return true;//レスポンスが切れたから
		}
		HeaderParser requestHeader=handler.getRequestHeader();
		MappingResult mapping=handler.getRequestMapping();
		AccessLog recodeLog=searchAccessLog(accessLog,requestHeader,mapping);
		if(recodeLog!=null){
			//過去のaccessLogが見つかった場合、contentTypeだけはファイルの場合も利用する
//			bodyPage.recycle();
			accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_REPLAY);
			handler.setStatusCode(recodeLog.getStatusCode());
			String contentType=recodeLog.getContentType();
			if(contentType!=null){
				handler.setContentType(contentType);
			}
		}
		//ファイルから探す
		File file=searchFile(requestHeader,mapping);
		if(file!=null){
//			bodyPage.recycle();
			accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_REPLAY);
			if(logger.isDebugEnabled())logger.debug("response from file.file:"+file.getAbsolutePath());
			handler.setAttribute(SCOPE.REQUEST,ProxyHandler.ATTRIBUTE_RESPONSE_FILE,file);
			WebServerHandler response=(WebServerHandler) handler.forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
			PoolManager.poolBufferInstance(body);//TODO ちゃんと使おう
			return true;
		}else if(recodeLog!=null){
			String bodyDigest=recodeLog.getResponseBodyDigest();
			Store store=Store.open(bodyDigest);
			if(store!=null){
				if(logger.isDebugEnabled())logger.debug("response from trace.bodyDigest:"+bodyDigest);
				String contentEncoding=recodeLog.getContentEncoding();
				if(contentEncoding!=null){
					handler.setHeader(HeaderParser.CONTENT_ENCODING_HEADER,contentEncoding);
				}
				String transferEncoding=recodeLog.getTransferEncoding();
				if(transferEncoding!=null){
					handler.setHeader(HeaderParser.TRANSFER_ENCODING_HEADER,transferEncoding);
				}else{
					long contentLength=recodeLog.getResponseLength();
					handler.setContentLength(contentLength);
				}
				handler.setAttribute(SCOPE.REQUEST,"Store", store);
				handler.forwardHandler(Mapping.STORE_HANDLER);
				PoolManager.poolBufferInstance(body);//TODO ちゃんと使おう
				return true;
			}
		}
		return false;
	}
}
