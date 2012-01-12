package naru.aweb.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.config.FileCache2;
import naru.aweb.config.Mapping;
import naru.aweb.config.FileCache2.FileCacheInfo;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

public class FileSystemHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(FileSystemHandler.class);
	private static Config config=null;//Config.getConfig();
	private static String LISTING_PAGE="/fileSystem/listing.vsp";
	private static Configuration contentTypeConfig=null;//config.getConfiguration("ContentType");
	private static Map<String,String> contentTypeMap=new HashMap<String,String> ();
	private static FileCache2 fileCache=null;

	private static Config getConfig(){
		if(config==null){
			config=Config.getConfig();
		}
		return config;
	}

	private static Configuration getContentTypeConfig(){
		if(contentTypeConfig==null){
			contentTypeConfig=getConfig().getConfiguration("ContentType");
		}
		return contentTypeConfig;
	}
	
	private static String calcContentType(String ext){
		String contentType=contentTypeMap.get(ext);
		if(contentType!=null){
			return contentType;
		}
		contentType=getContentTypeConfig().getString(ext,"application/octet-stream");
		synchronized(contentTypeMap){
			contentTypeMap.put(ext, contentType);
		}
		return contentType;
	}
	
	private static FileCache2 getFileCache(){
		if(fileCache==null){
			fileCache=getConfig().getFileCache();
		}
		return fileCache;
	}
	
	//TODO adminSettingからデフォルト値を取得する
	private static boolean defaultListing=true;
	private static String[] defaultWelcomeFiles=new String[]{"index.html","index.htm","index.vsp"};
	//vsp ... "velocity server page"  vsf ... "velocity server flagment"
	private static String[] defaultVelocityExtentions=new String[]{".vsp","vsf"};
	
	private String[] getWelcomeFiles(MappingResult mapping){
		String welcomFiles=(String)mapping.getOption(MappingResult.PARAMETER_FILE_WELCOME_FILES);
		if(welcomFiles==null){
			return defaultWelcomeFiles;
		}
		return welcomFiles.split(",");
	}
	
	private boolean isListing(MappingResult mapping){
		String listing=(String)mapping.getOption(MappingResult.PARAMETER_FILE_LISTING);
		if(listing==null){
			return defaultListing;
		}
		return !"false".equalsIgnoreCase(listing);
	}
	
	/*
	 * MappingResultのOptionに従ってVelocityHandlerに処理を任せる
	 * 1)"velocityUse"がfalse以外
	 * 2)"velocityExtentions"と拡張子が一致する
	 * TODO mapping.setOption(key, value)を使って最適化
	 */
	private boolean isVelocityUse(MappingResult mapping,String path){
		if(path==null){
			return false;
		}
		String velocityUse=(String)mapping.getOption(MappingResult.PARAMETER_VELOCITY_USE);
		if("false".equalsIgnoreCase(velocityUse)){
			return false;
		}
		if(path.endsWith("ph-loader.js")){//特別扱い
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE,"application/javascript");
			return true;
		}
		String velocityExtentionsParam=(String)mapping.getOption(MappingResult.PARAMETER_VELOCITY_EXTENTIONS);
		String[] velocityExtentions=defaultVelocityExtentions; 
		if(velocityExtentionsParam!=null){
			velocityExtentions=velocityExtentionsParam.split(",");
		}
		for(int i=0;i<velocityExtentions.length;i++){
			if(path.endsWith(velocityExtentions[i])){
				return true;
			}
		}
		return false;
	}
	
	private String getContentType(File file){
		String contentType=(String)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE);
		if(contentType!=null){
			return contentType;
		}
		String name=file.getName();
		int pos=name.lastIndexOf(".");
		if( pos>0 ){
			String ext=name.substring(pos+1);
			contentType=calcContentType(ext);
			if( contentType!=null){
				return contentType;
			}
		}
		//疑わしきは、OctedStream
		return "application/octet-stream";
	}
	
	private long getContentLength(long fileLength){
		Long length=(Long)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_LENGTH);
		if(length!=null){
			return length.longValue();
		}
		return fileLength;
	}
	
	
	private void responseBodyFromFile(File file,FileCacheInfo fileCacheInfo) throws IOException{
		Long offset=(Long)getRequestAttribute(ATTRIBUTE_STORE_OFFSET);
		if(fileCacheInfo!=null){
			ByteBuffer[] contents=fileCacheInfo.getContents();
			if(contents!=null){
				if(offset!=null){
					BuffersUtil.skip(contents, offset.intValue());
				}
				responseBody(contents);
				responseEnd();//実close発行
				return;
			}
		}
		
		FileChannel readChannel=null;
		FileInputStream fis=new FileInputStream(file);
		readChannel=fis.getChannel();
		if(offset!=null){
			readChannel.position(offset.longValue());
		}
		responseBodyFromChannel(readChannel);
	}
	
	//存在確認済みのディレクトリを一覧レスポンスする。
	private boolean snedFileList(String uri,FileCacheInfo info,boolean isBase){
		if(!uri.endsWith("/")){
			uri=uri+"/";
		}
		setRequestAttribute("isBase",isBase);
		setRequestAttribute("base",uri);
		setRequestAttribute("source", info.getCanonicalFile());
		setRequestAttribute("fileList", info.listFiles());
		MappingResult mapping=getRequestMapping();
		
		mapping.setResolvePath(LISTING_PAGE);
		mapping.setDesitinationFile(getConfig().getAdminDocumentRoot());
		forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
		return false;//委譲
	}
	
	//存在確認済みのファイルをレスポンスする。
	private boolean sendFile(MappingResult mapping,File baseDirectory,String path,String ifModifiedSince,File file,FileCacheInfo fileCacheInfo){
		if(isVelocityUse(mapping,path)){
			//TODO ちゃんとする
			mapping.setResolvePath(path);//加工後のpathを設定
			mapping.setDesitinationFile(baseDirectory);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return false;//委譲
		}
		
//		String ifModifiedSince=requestParser.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
		Date ifModifiedSinceDate=HeaderParser.parseDateHeader(ifModifiedSince);
		long ifModifiedSinceTime=-1;
		if(ifModifiedSinceDate!=null){
			ifModifiedSinceTime=ifModifiedSinceDate.getTime();
		}
		long lastModifiedTime=fileCacheInfo!=null?fileCacheInfo.lastModified():file.lastModified();
		String lastModified=HeaderParser.fomatDateHeader(new Date(lastModifiedTime));
		//ファイル日付として表現できる値には、誤差があるため、表現できる時刻を取得
		lastModifiedTime=HeaderParser.parseDateHeader(lastModified).getTime();
		if( ifModifiedSinceTime>=lastModifiedTime ){
			completeResponse("304");
			return true;
		}
		setHeader(HeaderParser.LAST_MODIFIED_HEADER, lastModified);
		long contentLength=getContentLength(fileCacheInfo!=null?fileCacheInfo.length():file.length());
		setContentLength(contentLength);
		String contentDisposition=(String)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION);
		if( contentDisposition!=null){
			setHeader(HeaderParser.CONTENT_DISPOSITION_HEADER, contentDisposition);
		}
		String contentType=getContentType(fileCacheInfo!=null?fileCacheInfo.getCanonicalFile():file);
		setContentType(contentType);
		setStatusCode("200");
		try {
			responseBodyFromFile(file,fileCacheInfo);
			return false;
		} catch (IOException e) {
			logger.error("responseBodyFromFile error."+file,e);
			setStatusCode("500");//有効かどうかは不明だが
		}
		return true;
	}
	
	public void startResponseReqBody(){
		if(response()){
			responseEnd();//TODO必要ないと思う
			return;
		}
	}
	
	private boolean response(){
		HeaderParser requestHeader=getRequestHeader();
		String ifModifiedSince=requestHeader.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
		String selfPath=requestHeader.getRequestUri();
		
		MappingResult mapping=getRequestMapping();
		File file=(File)getRequestAttribute(ATTRIBUTE_RESPONSE_FILE);
		if(file!=null){//レスポンスするファイルが、直接指定された場合
			FileCacheInfo fileCacheInfo=null;
			if(getRequestAttribute(ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE)==null){
				fileCacheInfo=getFileCache().lockFileInfo(file);
			}
			try{
				if( (fileCacheInfo!=null && !fileCacheInfo.exists())||(fileCacheInfo==null && !file.exists())){
					logger.debug("Not found."+file.getAbsolutePath());
					completeResponse("404","file not exists");
					return true;
				}
				return sendFile(mapping,null,null,ifModifiedSince,file,fileCacheInfo);
			} finally{
				if(fileCacheInfo!=null){
					fileCacheInfo.unlock();
				}
			}
		}
		String path=mapping.getResolvePath();
		try {
			path = URLDecoder.decode(path,"utf-8");
		} catch (UnsupportedEncodingException e) {
			logger.error("URLDecoder.decode error",e);
			throw new IllegalArgumentException("URLDecoder.decode error",e);
		}
		//クエリの削除
		int pos=path.indexOf('?');
		if(pos>=0){
			path=path.substring(0,pos);
		}
		
		File baseDirectory=mapping.getDestinationFile();
		FileCacheInfo info=getFileCache().lockFileInfo(baseDirectory, path);
		try{
			if(info.isError()){
				logger.warn("fail to getCanonicalPath.");
				completeResponse("500","fail to getCanonicalPath.");
				return true;
			}else if(!info.isInBase()){
				//トラバーサルされたら、loggingして404
				logger.warn("traversal error.");
				completeResponse("404","traversal error");
				return true;
			}else if(!info.exists() || !info.canRead()){
				logger.debug("Not found."+info.getCanonicalFile());
				completeResponse("404","file not exists");
				return true;
			}
			//welcomefile処理
			String[] welcomeFiles=getWelcomeFiles(mapping);
			if( info.isDirectory() && welcomeFiles!=null){
				FileCacheInfo childInfo=info.lockWelcomefile(welcomeFiles);
				if(childInfo!=null){
					if(childInfo.exists()&&childInfo.canRead()&&!path.endsWith("/")){
						childInfo.unlock();
						//もし、URIが"/"で終わっていなかったら相対が解決できないので、リダイレクト
						ServerParser selfServer=requestHeader.getServer();
						StringBuilder sb=new StringBuilder();
						if(isSsl()){
							sb.append("https://");
						}else{
							sb.append("http://");
						}
						sb.append(selfServer.toString());
						sb.append(selfPath);
						sb.append("/");
						setHeader(HeaderParser.LOCATION_HEADER, sb.toString());
						completeResponse("302");
						return true;
					}
					info.unlock();
					info=childInfo;
				}
			}
			if(info.isFile()){//ファイルだったら
				return sendFile(mapping,baseDirectory,path,ifModifiedSince,file,info);
			}
			boolean listing=isListing(mapping);
			if(listing && info.isDirectory()){//ディレクトリだったら
				//velocityPageからリスト出力
				return snedFileList(selfPath,info,"/".equals(path));
			}
		}finally{
			info.unlock();
		}
		logger.debug("Not allow listing");
		completeResponse("404");
		return true;
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		asyncClose(userContext);
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:" +getChannelId());
		closeBodyChannel();
		super.onFinished();
	}
	
	//fileダウンロード用メソッド
	private ReadableByteChannel bodyChannel;
	private void responseBodyFromChannel(FileChannel readChannel) throws IOException {
		/*
		 * 一揆に読み込むためのコード、早いかもしれないが、swapが一杯になるからメモリ効率に難あり
		while(true){
			ByteBuffer buffer=allocBuffer();
			if( channel.read(buffer)<=0){
				return;
			}
			buffer.flip();
			responseBody(buffer);
		}
		*/
		bodyChannel=readChannel;
		responseBodyChannel();
	}
	
	private void responseBodyChannel() throws IOException{
		ByteBuffer buffer=PoolManager.getBufferInstance();
		if( bodyChannel.read(buffer)<=0){
			PoolManager.poolBufferInstance(buffer);
			closeBodyChannel();
			responseEnd();//実close発行
			return;
		}
		buffer.flip();
		logger.debug("responseBodyChannel cid:"+getChannelId() +":length:"+buffer.remaining());
		responseBody(buffer);
	}
	
	private void closeBodyChannel(){
		if(bodyChannel!=null){
			try {
				bodyChannel.close();
			} catch (IOException e) {
				logger.error("fail to close bodyChannel.",e);
			}
			bodyChannel=null;
		}
	}

	public void onWrittenBody() {
		logger.debug("#writtenBody.cid:" +getChannelId());
		if(bodyChannel!=null){
			try {
				responseBodyChannel();
			} catch (IOException e) {
				logger.error("fail to responseBodyChannel",e);
				closeBodyChannel();
			}
		}
		super.onWrittenBody();
	}
}
