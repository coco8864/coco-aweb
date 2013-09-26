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
import naru.aweb.config.Mapping;
import naru.aweb.handler.KeepAliveContext;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.GzipContext;
import naru.aweb.http.HeaderParser;
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
	//cache���g��Ȃ��ꍇ�ɐݒ�
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
		BROWSER,/* ���N�G�X�g���ׂĂ�bid��U��K�v������Alink�̋@�\�Ƃ��Ē񋟁Aaweb�̋@�\�Ƃ��Ă͒񋟂��Ȃ� */
		SESSION,
		AUTH_SESSION,
		MAPPING,/* mapping��`�P�ʂ܂�Aapplication�X�R�[�v */
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
	/*
	public void setRequestAttribute(String name,Object value){
		setAttribute(SCOPE.REQUEST,name,value);
		//getRequestContext().setAttribute(name, value);
	}
	
	public Object getRequestAttribute(String name){
		return getAttribute(SCOPE.REQUEST,name);
//		return getRequestContext().getAttribute(name);
	}
	
	public Iterator<String> getRequestAttributeNames(){
		return getAttributeNames(SCOPE.REQUEST);
//		return getRequestContext().getAttributeNames();
	}
	*/
	
	private AuthSession getRootAuthSession(){
		AuthSession session=getAuthSession();
		if(session==null){
			return null;
		}
		return session.getSessionId().getPrimaryId().getAuthSession();
	}
	
	public void setAttribute(SCOPE scope,String name,Object value){
		AuthSession session=null;
		switch(scope){
		case HANDLER:
			setAttribute(name, value);
			break;
		case REQUEST:
			RequestContext requestContext=getRequestContext();
			if(requestContext!=null){
				requestContext.setAttribute(name, value);
			}
			break;
		case SESSION:
			session=getAuthSession();
			if(session!=null){
				session.setAttribute(name, value);
			}
			break;
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				session.setAttribute(name, value);
			}
			break;
		case MAPPING:
			MappingResult mappingResult=getRequestMapping();
			Mapping mapping=mappingResult.getMapping();
			mapping.setAttribute(name,value);
			break;
		default:
			break;
		}
	}
	
	public Object getAttribute(SCOPE scope,String name){
		AuthSession session=null;
		switch(scope){
		case HANDLER:
			return getAttribute(name);
		case REQUEST:
			RequestContext requestContext=getRequestContext();
			if(requestContext!=null){
				return requestContext.getAttribute(name);
			}
			return null;
		case SESSION:
			session=getAuthSession();
			if(session!=null){
				return session.getAttribute(name);
			}
			return null;
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				return session.getAttribute(name);
			}
			return null;
		case MAPPING:
			MappingResult mappingResult=getRequestMapping();
			Mapping mapping=mappingResult.getMapping();
			return mapping.getAttribute(name);
		default:
			break;
		}
		return null;
	}
	
	public Iterator<String> getAttributeNames(SCOPE scope){
		switch(scope){
		case HANDLER:
			throw new UnsupportedOperationException("handler scope getAttrNames");
		case REQUEST:
			RequestContext requestContext=getRequestContext();
			if(requestContext!=null){
				return requestContext.getAttributeNames();
			}
			return null;
		case SESSION:
			AuthSession session=getAuthSession();
			if(session!=null){
				return session.getAttributeNames();
			}
			return null;
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				return session.getAttributeNames();
			}
			return null;
		case MAPPING:
			MappingResult mappingResult=getRequestMapping();
			Mapping mapping=mappingResult.getMapping();
			return mapping.getAttributeNames();
		default:
			break;
		}
		return null;
	}
	
	public KeepAliveContext getKeepAliveContext(){
		return getKeepAliveContext(false);
	}

	public KeepAliveContext getKeepAliveContext(boolean isCreate){
		if(getChannelId()<0){//ChannelContext�����Ȃ��ꍇ
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
	
	/* DsipatchHandler��override���鑼�n���h���͕K�v�Ȃ� */
	@Override
	public SSLEngine getSSLEngine() {
		return null;
	}

	public SpdySession getSpdySession() {
		return (SpdySession)getAttribute(SCOPE.REQUEST, ATTRIBUTE_SPDY_SESSION);
	}

	@Override
	public boolean isSsl() {
		if(getSpdySession()!=null){
			return true;
		}
		return	super.isSsl();
	}
	
}
