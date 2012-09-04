package naru.aweb.core;

import java.util.Iterator;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.ChannelHandler;
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
import naru.aweb.spdy.SpdySession;
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
	//cacheを使わない場合に設定
	public static final String ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE = "responseFileNotUseCache";
	
	public static final String ATTRIBUTE_VELOCITY_PAGE="velocityPage";
	public static final String ATTRIBUTE_VELOCITY_REPOSITORY="velocityRepository";
	public static final String ATTRIBUTE_KEEPALIVE_CONTEXT="keepAliveContext";
	
	public static final String ATTRIBUTE_USER="loginUser";
	
	//spdy経由で呼び出されている場合設定される、この場合、ChannelHandler系のメソッドは使えない
	private SpdySession spdySession;
	
	public void recycle() {
		spdySession=null;
		super.recycle();
	}

	@Override
	public ChannelHandler forwardHandler(SslHandler handler) {
		if(spdySession!=null&&handler instanceof ServerBaseHandler){
			ServerBaseHandler serverHandler=(ServerBaseHandler)handler;
			serverHandler.spdySession=spdySession;
			spdySession.setServerHandler(serverHandler);
			spdySession=null;
		}
		return super.forwardHandler(handler);
	}
	
	
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
		if(getChannelId()<0){//ChannelContextがいない場合
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
	
	/* DsipatchHandlerでoverrideする他ハンドラは必要なし */
	@Override
	public SSLEngine getSSLEngine() {
		return null;
	}

	public SpdySession getSpdySession() {
		return spdySession;
	}

	public void setSpdySession(SpdySession spdySession) {
		this.spdySession = spdySession;
	}
	
	@Override
	public boolean isSsl() {
		if(spdySession!=null){
			return true;
		}
		return	super.isSsl();
	}
	
}
