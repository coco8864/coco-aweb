package naru.aweb.handler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;

import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.tools.ToolContext;
import org.apache.velocity.tools.ToolManager;
import org.apache.velocity.tools.config.EasyFactoryConfiguration;
import org.apache.velocity.tools.generic.EscapeTool;

public class VelocityPageHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(VelocityPageHandler.class);
	private static String DEFAULT_CONTENT_TYPE="text/html; charset=utf-8";
	private static Map<File,VelocityEngine> engineMap=Collections.synchronizedMap(new HashMap<File,VelocityEngine>());
	private static Config config=Config.getConfig();
	private static ToolManager toolManager=null;
	
//	private ParameterParser paremeter;
	
	private static ToolManager getToolManager(){
		if(toolManager==null){
			ToolManager tm = new ToolManager();
			/*
			EasyFactoryConfiguration efConfig = new EasyFactoryConfiguration();		
			efConfig.tool("esc", EscapeTool.class);
			tm.configure(efConfig);
			*/
			File settingDir=config.getSettingDir();
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
	
	public void startResponseReqBody(){
		MappingResult mapping=getRequestMapping();
		File veloRep=mapping.getDestinationFile();
		String veloPage=mapping.getResolvePath();
		if(veloRep==null || veloPage==null){
			logger.debug("not found repository");
			completeResponse("404","file not found");
			return;
		}
//		String veloPage=(String)getRequestAttribute(ATTRIBUTE_VELOCITY_PAGE);
//		if(veloPage==null){
//			File file=(File)getRequestAttribute(ATTRIBUTE_RESPONSE_FILE);
//			veloPage=file.getAbsolutePath().substring(veloRep.getAbsolutePath().length());
//		}
		VelocityEngine velocityEngine=getEngine(veloRep);
		merge(velocityEngine,veloPage);
	}
	
	private Context createVeloContext(){
		ToolManager toolManager = getToolManager();
		ToolContext veloContext=toolManager.createContext();
		veloContext.put("handler", this);
		veloContext.put("parameter", getParameterParser());
//		KeepAliveContext keepAliveContext=getKeepAliveContext();
		RequestContext requestContext=getRequestContext();
		veloContext.put("session", requestContext.getAuthSession());
		veloContext.put("config", config);
		Iterator<String> itr=getRequestAttributeNames();
		while(itr.hasNext()){
			String key=itr.next();
			Object value=getRequestAttribute(key);
			veloContext.put(key, value);
		}
		return veloContext;
	}
	
	private void merge(VelocityEngine velocityEngine,String veloPage){
		setNoCacheResponseHeaders();//動的コンテンツなのでキャッシュさせない
		Context veloContext=createVeloContext();
		String contentDisposition=(String)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION);
		if( contentDisposition!=null){
			setHeader(HeaderParser.CONTENT_DISPOSITION_HEADER, contentDisposition);
		}
		String contentType=(String)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE);
		if(contentType==null){
			contentType=DEFAULT_CONTENT_TYPE;
		}
		setContentType(contentType);
		String statusCode=(String)getRequestAttribute(ATTRIBUTE_RESPONSE_STATUS_CODE);
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
			velocityEngine.mergeTemplate(veloPage, "utf-8", veloContext, out);
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
//			paremeter=null;
		}
	}
	
/*	
	//velocitymacroのhandler経由でparameterが取得できるようにする
	public String getParameter(String name){
		if(paremeter==null){
			return null;
		}
		return paremeter.getParameter(name);
	}
	
	public List getParameters(String name){
		if(paremeter==null){
			return null;
		}
		return paremeter.getParameters(name);
	}
	
	public Iterator getParameterNames(){
		if(paremeter==null){
			return null;
		}
		return paremeter.getParameterNames();
	}
*/
	
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
