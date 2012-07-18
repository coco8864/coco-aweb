package naru.aweb.core;

import java.util.Iterator;

import org.apache.log4j.Logger;

import naru.async.pool.PoolManager;
import naru.async.ssl.SslHandler;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.http.GzipContext;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

public abstract class ServerBaseHandler extends SslHandler {
	private static Logger logger = Logger.getLogger(ServerBaseHandler.class);
	private static Config config=Config.getConfig();
	
	public static final String ATTRIBUTE_RESPONSE_STATUS_CODE="responseStatusCode";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_TYPE="responseContentType";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION = "reponseContentDisposition";
	public static final String ATTRIBUTE_STORE_DIGEST = "storeDigest";
	public static final String ATTRIBUTE_RESPONSE_FILE = "responseFile";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_LENGTH = "responseContentLength";
	public static final String ATTRIBUTE_STORE_OFFSET = "responseOffset";
	//cacheÇégÇÌÇ»Ç¢èÍçáÇ…ê›íË
	public static final String ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE = "responseFileNotUseCache";
	
	public static final String ATTRIBUTE_VELOCITY_PAGE="velocityPage";
	public static final String ATTRIBUTE_VELOCITY_REPOSITORY="velocityRepository";
	public static final String ATTRIBUTE_KEEPALIVE_CONTEXT="keepAliveContext";
	
	public static final String ATTRIBUTE_USER="loginUser";
//	public static final String ATTRIBUTE_AUTH_SESSION="authSession";
	
	public RequestContext getRequestContext(){
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		if(keepAliveContext==null){
			return null;
		}
		RequestContext requestContext=keepAliveContext.getRequestContext();
		return requestContext;
	}
	
	public AccessLog getAccessLog(){
		return getRequestContext().getAccessLog();
	}
	
	public HeaderParser getRequestHeader(){
		RequestContext requestContext=getRequestContext();
		if(requestContext==null){
			logger.warn("getRequestHeader requestContext is null.",new Throwable());
			return null;
		}
		return requestContext.getRequestHeader();
	}
	
	public void setRequestAttribute(String name,Object value){
		getRequestContext().setAttribute(name, value);
	}
	
	public Object getRequestAttribute(String name){
		return getRequestContext().getAttribute(name);
	}
	
	public Iterator<String> getRequestAttributeNames(){
		return getRequestContext().getAttributeNames();
	}
	
	public KeepAliveContext getKeepAliveContext(){
		return getKeepAliveContext(false);
	}

	public KeepAliveContext getKeepAliveContext(boolean isCreate){
		if(getChannelId()<0){//ChannelContextÇ™Ç¢Ç»Ç¢èÍçá
			return null;
		}
		KeepAliveContext keepAliveContext=(KeepAliveContext)getAttribute(WebServerHandler.ATTRIBUTE_KEEPALIVE_CONTEXT);
		if(isCreate && keepAliveContext==null){
			keepAliveContext=(KeepAliveContext)PoolManager.getInstance(KeepAliveContext.class);
			keepAliveContext.setAcceptServer(ServerParser.create(getLocalIp(), getLocalPort()));
			setKeepAliveContext(keepAliveContext);
		}
		return keepAliveContext;
	}
	public void setKeepAliveContext(KeepAliveContext keepAliveContext){
		setAttribute(ATTRIBUTE_KEEPALIVE_CONTEXT,keepAliveContext);
	}
	
	public ParameterParser getParameterParser() {
		return getRequestContext().getParameterParser();
	}
	
	public GzipContext getGzipContext() {
		return getRequestContext().getGzipContext();
	}
	public void setGzipContext(GzipContext gzipContext) {
		getRequestContext().setGzipContext(gzipContext);
	}
	
	public MappingResult getRequestMapping() {
		return getRequestContext().getMapping();
	}
	public void setRequestMapping(MappingResult mapping) {
		getRequestContext().setMapping(mapping);
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:"+getChannelId());
		super.onFinished();
	}
}
