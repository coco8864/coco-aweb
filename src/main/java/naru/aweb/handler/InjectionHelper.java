package naru.aweb.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;

import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

public class InjectionHelper {
	private static Logger logger = Logger.getLogger(InjectionHelper.class);
	private Config config;
	private VelocityEngine velocityEngine=null;
	
	public InjectionHelper(Config config){
		this.config=config;
		initVelocity(config.getInjectionDir());
	}
	private void initVelocity(File injectionDir){
		velocityEngine = new VelocityEngine();
		velocityEngine.addProperty("file.resource.loader.path",injectionDir.getAbsolutePath());
		velocityEngine.setProperty("runtime.log.logsystem.class","org.apache.velocity.runtime.log.SimpleLog4JLogSystem");
		velocityEngine.setProperty("runtime.log.logsystem.log4j.category","velocity");
		velocityEngine.setProperty("resource.manager.logwhenfound","false");
		try {
			velocityEngine.init();
		} catch (Exception e) {
			throw new RuntimeException("fail to velocityEngine.ini()",e);
		}
	}
	
	private ByteBuffer merge(String injectionFileName){
		VelocityContext veloContext=new VelocityContext();
		veloContext.put("config", config);
		Writer out=null;
		try {
			File tmpFile=File.createTempFile("inj", ".tmp", config.getTmpDir());
			out=new OutputStreamWriter(new FileOutputStream(tmpFile),"utf-8");
			synchronized(velocityEngine){
				velocityEngine.mergeTemplate(injectionFileName, "utf-8", veloContext, out);
			}
			out.close();
			ByteBuffer buffer=readToBuffer(tmpFile);
			tmpFile.delete();
			return buffer;
		} catch (ResourceNotFoundException e) {
			logger.error("Velocity.mergeTemplate ResourceNotFoundException." + injectionFileName,e);
		} catch (ParseErrorException e) {
			logger.error("Velocity.mergeTemplate ParseErrorException." + injectionFileName,e);
		} catch (MethodInvocationException e) {
			logger.error("Velocity.mergeTemplate MethodInvocationException." + injectionFileName,e);
		} catch (Exception e) {
			logger.error("Velocity.mergeTemplate Exception." + injectionFileName,e);
		}finally{
			try {
				out.close();
			} catch (IOException ignore) {
			}
		}
		return null;
	}
	
	private ByteBuffer readToBuffer(File injectionFile){
		InputStream is=null;
		ByteBuffer buffer=null;
		try {
			is=new FileInputStream(injectionFile);
			int totalLength=(int)injectionFile.length();
			buffer=PoolManager.getBufferInstance(totalLength);
			int top=0;
			while(true){
				int len=is.read(buffer.array(),top,totalLength-top);
				if(len<0){
					break;
				}
				top+=len;
				if(top>=totalLength){
					break;
				}
			}
			is.close();
			buffer.limit(totalLength);
			return buffer;
		} catch (IOException e) {
			e.printStackTrace();
			if(buffer!=null){
				PoolManager.poolBufferInstance(buffer);
			}
		}finally{
			if(is!=null){
				try {
					is.close();
				} catch (IOException ignore) {
				}
			}
		}
		return null;
	}
	
	private static class InjectionContent{
		private File injectionFile;
		private ByteBuffer contents;
		private long lastModified=-1;
	}
	
	private Map<String,Class> injectorMap=new HashMap<String,Class>();
	private Map<String,InjectionContent> injectContentsMap=new HashMap<String,InjectionContent>();
	
	public ProxyInjector getInjector(String option){
		if(option==null){
			return null;
		}
		Class injectorClass;
		synchronized(injectorMap){
			injectorClass=injectorMap.get(option);
			if(injectorClass==null){
				try {
					injectorClass=getClass().getClassLoader().loadClass(option);
					injectorMap.put(option, injectorClass);
				} catch (ClassNotFoundException e) {
					//TODO この場合デフォルトのハンドラーを用意して,optionはファイル名として処理
					logger.error("not found injector class."+option,e);
					return null;
				}
			}
		}
		ProxyInjector injector=(ProxyInjector)PoolManager.getInstance(injectorClass);
		return injector;
	}
	
	private ByteBuffer readInjectContents(String injectionFileName){
		InjectionContent ic=injectContentsMap.get(injectionFileName);
		if(ic==null){
			ic=new InjectionContent();
			ic.injectionFile=new File(config.getInjectionDir(),injectionFileName);
			injectContentsMap.put(injectionFileName, ic);
		}
		long lastModified=ic.injectionFile.lastModified();
		if(ic.lastModified==lastModified){
			return PoolManager.duplicateBuffer(ic.contents);
		}
		ic.lastModified=lastModified;
		if(ic.contents!=null){
			PoolManager.poolBufferInstance(ic.contents);
		}
		ic.contents=merge(injectionFileName);
		injectContentsMap.put(injectionFileName, ic);
		return PoolManager.duplicateBuffer(ic.contents);
	}
	
	private static int PHASE_TAG=0;//"<head"一致中
	private static int PHASE_TAGEND=1;//" >"検索中
	private static int PHASE_END=2;//発見書き換え済み
	private class InjectionContext{
		private ByteBuffer contents;
		private int phase=PHASE_TAG;
		private byte[] markLowerBytes;
		private byte[] markUpperBytes;
		private byte endMark;
		private int markPos=0;
		
		InjectionContext(String mark,byte endMark){
			markLowerBytes=mark.toLowerCase().getBytes();
			markUpperBytes=mark.toUpperCase().getBytes();
			this.endMark=endMark;
		}
		
		private void doInject(ProxyHandler handler,ByteBuffer[] body){
			if(phase==PHASE_END){
				handler.responseBody(body);
				return;
			}
			if(body==null){//最終でまだ埋め込んでいなければ無条件に出力
				handler.responseBody(contents);
				contents=null;
				phase=PHASE_END;
				return;
			}
			long pos=searchPosition(body);
			if(phase!=PHASE_END){
				handler.responseBody(body);
				return;
			}
			long remainning=BuffersUtil.remaining(body);
			if(pos==remainning){//等しい可能性がある
				handler.responseBody(body);
				handler.responseBody(contents);
			}else{
				ByteBuffer[] body2=PoolManager.duplicateBuffers(body);
				BuffersUtil.cut(body, pos);
				BuffersUtil.skip(body2, pos);
				handler.responseBody(body);
				handler.responseBody(contents);
				handler.responseBody(body2);
			}
			contents=null;
		}
		private ByteBuffer[] inject(ByteBuffer[] body){
			if(phase==PHASE_END){
				return body;
			}
			if(body==null){//最終でまだ埋め込んでいなければ無条件に出力
				phase=PHASE_END;
				body=BuffersUtil.toByteBufferArray(contents);
				contents=null;
				return body;
			}
			long pos=searchPosition(body);
			if(phase!=PHASE_END){
				return body;
			}
			long remainning=BuffersUtil.remaining(body);
			if(pos==remainning){//等しい可能性がある
				body=BuffersUtil.concatenate(null, body, contents);
			}else{
				ByteBuffer[] body2=PoolManager.duplicateBuffers(body);
				BuffersUtil.cut(body, pos);
				BuffersUtil.skip(body2, pos);
				body=BuffersUtil.concatenate(body, contents, body2);
			}
			contents=null;
			return body;
		}
		
		/**
		 * @param body
		 * @param markLowerBytes
		 * @return markの開始位置を返却、mark.length分残りがない場合は、まだ見つかっていない
		 */
		private long searchPosition(ByteBuffer[] bodys){
			long searchPos=0;
			for(int i=0;i<bodys.length;i++){
				ByteBuffer body=bodys[i];
				byte[] array=body.array();
				for(int j=body.position();j<body.limit();j++){
					if(phase==PHASE_TAG){
						if(array[j]==markLowerBytes[markPos]||array[j]==markUpperBytes[markPos]){
							markPos++;
							if(markPos==markLowerBytes.length){
								phase=PHASE_TAGEND;
							}
						}else{
							markPos=0;
						}
					}else{//phase==PHASE_TAGEND
						if(array[j]==endMark){
							phase=PHASE_END;
							return searchPos+1;
						}
					}
					searchPos++;
				}
			}
			return searchPos;
		}
	}
	
	public long getInjectContentsLength(Object context){
		return (long)((InjectionContext)context).contents.remaining();
	}
	
	public void doInject(Object context,ProxyHandler handler,ByteBuffer[] body){
		InjectionContext injectionContext=(InjectionContext)context;
		injectionContext.doInject(handler, body);
	}
	
	
	private InjectionContext createInjectionContext(HeaderParser responseHeader,String mark,byte endMark){
		String contentType=responseHeader.getContentType();
		if(contentType==null || !contentType.startsWith("text/html")){
			return null;
		}
		return new InjectionContext(mark,endMark);
	}
	/**
	 */
	public Object getInjectContext(String injectionFile,HeaderParser responseHeader){
		String statusCode=responseHeader.getStatusCode();
		if( !"200".equals(statusCode)&& !"404".equals(statusCode)){
			return null;
		}
		InjectionContext context=createInjectionContext(responseHeader,"<head",(byte)'>');
		if(context!=null){
			synchronized(this){
				context.contents=readInjectContents(injectionFile);
			}
		}
		return context;
	}
	/**
	 */
	public Object getReplaceContext(String injectionFile){
		InjectionContext context=new InjectionContext("",(byte)0);
		synchronized(this){
			context.contents=readInjectContents(injectionFile);
		}
		return context;
	}

	/**
	 */
	public Object getInsertContext(String injectionFile){
		InjectionContext context=new InjectionContext("<head",(byte)'>');
		synchronized(this){
			context.contents=readInjectContents(injectionFile);
		}
		return context;
	}
	
	/**
	 */
	public Object getInjectContext(ByteBuffer contents,HeaderParser responseHeader){
		String statusCode=responseHeader.getStatusCode();
		if( !"200".equals(statusCode)&& !"404".equals(statusCode)){
			return null;
		}
		InjectionContext context=createInjectionContext(responseHeader,"<head",(byte)'>');
		if(context!=null){
			synchronized(this){
				context.contents=contents;
			}
		}
		return context;
	}
	
	public ByteBuffer[] inject(Object context,ByteBuffer[] body){
		if(context==null){
			return body;
		}
		InjectionContext injectionContext=(InjectionContext)context;
		return injectionContext.inject(body);
	}
}
