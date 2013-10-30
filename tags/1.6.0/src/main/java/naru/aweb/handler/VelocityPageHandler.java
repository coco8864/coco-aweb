package naru.aweb.handler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import naru.aweb.config.Config;
import naru.aweb.handler.ServerBaseHandler.SCOPE;
import naru.aweb.http.RequestContext;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.tools.ToolContext;
import org.apache.velocity.tools.ToolManager;

public class VelocityPageHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(VelocityPageHandler.class);
	private static String DEFAULT_CONTENT_TYPE="text/html; charset=utf-8";
	private static Map<File,VelocityEngine> engineMap=Collections.synchronizedMap(new HashMap<File,VelocityEngine>());
	private static Config config=null;
	private static ToolManager toolManager=null;
	private static Map<String, String> contentTypeMap = new HashMap<String, String>();

	private static Config getConfig() {
		if (config == null) {
			config = Config.getConfig();
		}
		return config;
	}

	private static String calcContentType(String fileName) {
		String contentType = contentTypeMap.get(fileName);
		if (contentType != null) {
			return contentType;
		}
		contentType = getConfig().getContentType(fileName);
		if(contentType==null){
			contentType=DEFAULT_CONTENT_TYPE;
		}
		synchronized (contentTypeMap) {
			contentTypeMap.put(fileName, contentType);
		}
		return contentType;
	}
	
	private static ToolManager getToolManager(){
		if(toolManager==null){
			ToolManager tm=null;
			synchronized(VelocityPageHandler.class){
				tm = new ToolManager();
			}
			/* 同時にToolManagerを作成すると以下になる模様
			 * 2013-09-23 16:05:26,802 [thread-dispatch:2] ERROR org.apache.commons.digester.Digester - Begin event threw exception
org.apache.commons.beanutils.ConversionException: Error converting from 'String' to 'Class' loader (instance of  naru/queuelet/loader/QueueletLoader): attempted  duplicate class definition for name: "org/apache/velocity/tools/generic/AlternatorTool"
	at org.apache.commons.beanutils.converters.AbstractConverter.handleError(AbstractConverter.java:267)
	at org.apache.commons.beanutils.converters.AbstractConverter.convert(AbstractConverter.java:164)
...
Caused by: java.lang.LinkageError: loader (instance of  naru/queuelet/loader/QueueletLoader): attempted  duplicate class definition for name: "org/apache/velocity/tools/generic/AlternatorTool"
	at java.lang.ClassLoader.defineClass1(Native Method)
			 * QueueletLoaderの一般的な問題かもしれないが
			 */
			File settingDir=getConfig().getSettingDir();
			File configFile=new File(settingDir,"velocityTool.xml");
			tm.configure(configFile.getAbsolutePath());
			toolManager=tm;
		}
		return toolManager;
	}
	
	private static VelocityEngine getEngine(File repository){
		VelocityEngine velocityEngine=engineMap.get(repository);
		if(velocityEngine!=null){
			return velocityEngine;
		}
		velocityEngine = new VelocityEngine();
//		velocityEngine.addProperty(RuntimeConstants.RESOURCE_LOADER,"file");
		velocityEngine.addProperty("file.resource.loader.path",repository.getAbsolutePath());
		velocityEngine.addProperty("file.resource.loader.cache","false");
		velocityEngine.addProperty("file.resource.loader.modificationCheckInterval ","60");
		velocityEngine.setProperty("runtime.log.logsystem.class","org.apache.velocity.runtime.log.SimpleLog4JLogSystem");
		velocityEngine.setProperty("runtime.log.logsystem.log4j.category","velocity");
		velocityEngine.setProperty("resource.manager.logwhenfound","false");
		try {
			velocityEngine.init();
		} catch (Exception e) {
			throw new RuntimeException("fail to velocityEngine.ini()",e);
		}
		logger.info("create VelocityEngine.repository:"+repository);
		engineMap.put(repository, velocityEngine);
		return velocityEngine;
	}
	
	private String getContentType(String name) {
		String contentType = (String) getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_CONTENT_TYPE);
		if (contentType != null) {
			return contentType;
		}
		return calcContentType(name);
	}
	
	public void onRequestBody(){
		MappingResult mapping=getRequestMapping();
		VelocityEngine velocityEngine=(VelocityEngine)getAttribute(SCOPE.REQUEST,ATTRIBUTE_VELOCITY_ENGINE);
		String veloPage=(String)getAttribute(SCOPE.REQUEST,ATTRIBUTE_VELOCITY_TEMPLATE);
		if(veloPage==null){
			veloPage=mapping.getResolvePath();
		}
		if(velocityEngine==null){
			File veloRep=(File)getAttribute(SCOPE.REQUEST,ATTRIBUTE_VELOCITY_REPOSITORY);
			if(veloRep==null){
				veloRep=mapping.getDestinationFile();
			}
			if(veloRep==null || veloPage==null){
				logger.debug("not found repository");
				completeResponse("404","file not found");
				return;
			}
			velocityEngine=getEngine(veloRep);
		}
		merge(velocityEngine,veloPage);
	}
	
	private Context createVeloContext(){
		ToolManager toolManager = getToolManager();
		ToolContext veloContext=toolManager.createContext();
		veloContext.put("handler", this);
		veloContext.put("parameter", getParameterParser());
		veloContext.put("session", getAuthSession());
		veloContext.put("config", getConfig());
		Iterator<String> itr=getAttributeNames(SCOPE.REQUEST);
		while(itr.hasNext()){
			String key=itr.next();
			Object value=getAttribute(SCOPE.REQUEST,key);
			veloContext.put(key, value);
		}
		return veloContext;
	}
	
	private void merge(VelocityEngine velocityEngine,String veloPage){
		setNoCacheResponseHeaders();//動的コンテンツなのでキャッシュさせない
		Context veloContext=createVeloContext();
		String contentDisposition=(String)getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION);
		if( contentDisposition!=null){
			setHeader(HeaderParser.CONTENT_DISPOSITION_HEADER, contentDisposition);
		}
		String contentType=getContentType(veloPage);
		setContentType(contentType);
		String statusCode=(String)getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_STATUS_CODE);
		if(statusCode==null){
			statusCode="200";
		}
		setStatusCode(statusCode);
		Writer out=null;
		try {
			out = getResponseBodyWriter("utf-8");
		} catch (UnsupportedEncodingException e) {
			logger.error("fail to getWriter.",e);
			responseEnd();
			return;
		}
		try {
			synchronized(velocityEngine){
				velocityEngine.mergeTemplate(veloPage, "utf-8", veloContext, out);
			}
		} catch (ResourceNotFoundException e) {
			logger.error("Velocity.mergeTemplate ResourceNotFoundException." + veloPage,e);
		} catch (ParseErrorException e) {
			logger.error("Velocity.mergeTemplate ParseErrorException." + veloPage,e);
		} catch (MethodInvocationException e) {
			logger.error("Velocity.mergeTemplate MethodInvocationException." + veloPage,e);
		} catch (Exception e) {
			logger.error("Velocity.mergeTemplate Exception." + veloPage,e);
		}finally{
			try {
				out.close();
			} catch (IOException ignore) {
			}
			responseEnd();
		}
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
}
