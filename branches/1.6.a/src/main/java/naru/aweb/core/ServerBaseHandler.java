package naru.aweb.core;

import java.util.Iterator;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.ChannelHandler;
import naru.async.pool.PoolManager;
import naru.async.ssl.SslHandler;
import naru.aweb.auth.AuthSession;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.GzipContext;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.RequestContext;
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
	
	public static final String ATTRIBUTE_VELOCITY_TEMPLATE="velocityTemplate";
	public static final String ATTRIBUTE_VELOCITY_REPOSITORY="velocityRepository";
	public static final String ATTRIBUTE_VELOCITY_ENGINE="velocityEngine";
	public static final String ATTRIBUTE_KEEPALIVE_CONTEXT="keepAliveContext";
	public static final String ATTRIBUTE_SPDY_SESSION="spdySession";
	public static final String ATTRIBUTE_USER="loginUser";
	
	public enum SCOPE{
		HANDLER,
		REQUEST,
		KEEP_ALIVE,
		BROWSER,/* リクエストすべてにbidを振る必要がある、awebの機能としては提供しない */
		SESSION,
		AUTH_SESSION,
		APPLICATION,
		CONFIG
	}
	
	@Override
	public ChannelHandler forwardHandler(SslHandler handler) {
		SpdySession spdySession=getSpdySession();
		if(spdySession!=null){
			spdySession.setServerHandler((ServerBaseHandler)handler);
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
	
	private AuthSession getRootAuthSession(){
		AuthSession session=getAuthSession();
		if(session==null){
			return null;
		}
		return session.getSessionId().getPrimaryId().getAuthSession();
	}
	
	public void setAttr(SCOPE scope,String name,Object value){
		AuthSession session=null;
		switch(scope){
		case HANDLER:
			setAttribute(name, value);
		case REQUEST:
			getRequestContext().setAttribute(name, value);
		case SESSION:
			session=getAuthSession();
			if(session!=null){
				session.setAttribute(name, value);
			}
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				session.setAttribute(name, value);
			}
		default:
			break;
		}
	}
	
	public Object getAttr(SCOPE scope,String name){
		AuthSession session=null;
		switch(scope){
		case HANDLER:
			return getAttribute(name);
		case REQUEST:
			return getRequestContext().getAttribute(name);
		case SESSION:
			session=getAuthSession();
			if(session!=null){
				return session.getAttribute(name);
			}
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				return session.getAttribute(name);
			}
		default:
			break;
		}
		return null;
	}
	
	public Iterator<String> getAttrNames(SCOPE scope){
		switch(scope){
		case HANDLER:
			throw new UnsupportedOperationException("handler scope getAttrNames");
		case REQUEST:
			return getRequestContext().getAttributeNames();
		case SESSION:
			AuthSession session=getAuthSession();
			if(session!=null){
				return session.getAttributeNames();
			}
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				return session.getAttributeNames();
			}
		default:
			break;
		}
		return null;
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
	
	public AuthSession getAuthSession(){
		RequestContext requestContext=getRequestContext();
		if(requestContext==null){
			return null;
		}
		return requestContext.getAuthSession();
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
		RequestContext requestContext=getRequestContext();
		if(requestContext==null){
			return null;
		}
		return (SpdySession)requestContext.getAttribute(ATTRIBUTE_SPDY_SESSION);
	}

	@Override
	public boolean isSsl() {
		if(getSpdySession()!=null){
			return true;
		}
		return	super.isSsl();
	}
	
}
