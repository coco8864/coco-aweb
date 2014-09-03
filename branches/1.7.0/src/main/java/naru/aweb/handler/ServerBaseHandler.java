package naru.aweb.handler;

import java.util.Iterator;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.ChannelHandler;
import naru.async.pool.PoolManager;
import naru.async.ssl.SslHandler;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.User;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.http.GzipContext;
import naru.aweb.http.RequestContext;
import naru.aweb.mapping.Mapping;
import naru.aweb.mapping.MappingResult;
import naru.aweb.spdy.SpdySession;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;
import naru.aweb.util.ServerParser;

/**
 * リクエスト単位に作成されるhandlerの基底クラス<br/>
 * 各スコープ変数へのアクセス方法を提供する。<br/>
 * 直接継承して利用しない。<br/>
 * handlerオブジェクトは再利用される。再利用される際には、recycleメソッドで通知される。
 * @author naru
 *
 */
public abstract class ServerBaseHandler extends SslHandler {
	private static Logger logger = Logger.getLogger(ServerBaseHandler.class);
	private static Config config=Config.getConfig();
	
	public static final String ATTRIBUTE_RESPONSE_STATUS_CODE="responseStatusCode";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_TYPE="responseContentType";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION = "reponseContentDisposition";
	public static final String ATTRIBUTE_RESPONSE_FILE = "responseFile";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_LENGTH = "responseContentLength";
	public static final String ATTRIBUTE_STORE_OFFSET = "responseOffset";
	//cacheを使わない場合に設定
	public static final String ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE = "responseFileNotUseCache";
	
	public static final String ATTRIBUTE_VELOCITY_TEMPLATE="velocityTemplate";
	public static final String ATTRIBUTE_VELOCITY_REPOSITORY="velocityRepository";
	public static final String ATTRIBUTE_VELOCITY_ENGINE="velocityEngine";
	public static final String ATTRIBUTE_KEEPALIVE_CONTEXT=KeepAliveContext.class.getName();
	public static final String ATTRIBUTE_SPDY_SESSION=SpdySession.class.getName();
	public static final String ATTRIBUTE_USER=User.class.getName();
	
	public enum SCOPE{
		/**
		 * handlerスコープ
		 */
		HANDLER,
		/**
		 * リクエストスコープ
		 */
		REQUEST,
		/**
		 * keepAliveスコープ<br/>
		 * 同一Socketの範囲,spdy通信の場合はREQUESTと同義
		 */
		KEEP_ALIVE,
		/**
		 * SessionStorageスコープ<br/>
		 * 同一sessionでもブラウザが異なると別のスコープとなる。Linkの機能として提供。<br/>
		 */
		BROWSER,/* リクエストすべてにbidを振る必要がある、linkの機能として提供、awebの機能としては提供しない */
		/**
		 * sessionスコープ<br/>
		 * ログイン、単一アプリ単位の範囲<br/>
		 */
		SESSION,
		/**
		 * authSessionスコープ<br/>
		 * ログイン、複数アプリ単位の範囲<br/>
		 */
		AUTH_SESSION,
		/**
		 * mappingスコープ<br/>
		 * mapping定義の範囲<br/>
		 */
		MAPPING,/* mapping定義単位つまり、applicationスコープ */
		/**
		 * applicationスコープ<br/>
		 * アプリケーションとして意味のある複数のmapping定義の範囲、servletアプリケーションとしては、applicationスコープ相当<br/>
		 * 未実装<br/>
		 */
		APPLICATION,
		/**
		 * configスコープ<br/>
		 * phantom server範囲　設定情報等<br/>
		 */
		CONFIG
	}
	
	/**
	 * 他handlerに処理を引き継ぎます。<br/>
	 */
	@Override
	public ChannelHandler forwardHandler(SslHandler handler) {
		SpdySession spdySession=getSpdySession();
		if(spdySession!=null){
			spdySession.setServerHandler((ServerBaseHandler)handler);
		}
		return super.forwardHandler(handler);
	}
	
	/**
	 * 当該リクエストのrequestContextを取得します。<br/>
	 */
	public RequestContext getRequestContext(){
		return getRequestContext(false);
	}
	public RequestContext getRequestContext(boolean isNew){
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		if(keepAliveContext==null){
			return null;
		}
		RequestContext requestContext=keepAliveContext.getRequestContext(isNew);
		return requestContext;
	}
	
	/**
	 * 当該リクエストのaccessLogオブジェクトを取得します。<br/>
	 */
	public AccessLog getAccessLog(){
		return getRequestContext().getAccessLog();
	}
	
	/**
	 * 当該リクエストのrequestHeaderオブジェクトを取得します。<br/>
	 */
	public HeaderParser getRequestHeader(){
		RequestContext requestContext=getRequestContext();
		if(requestContext==null){
			logger.warn("getRequestHeader requestContext is null.",new Throwable());
			return null;
		}
		return requestContext.getRequestHeader();
	}
	
	/**
	 * 他アプリケーションとも共有するsessionオブジェクトを取得します。<br/>
	 */
	private AuthSession getRootAuthSession(){
		AuthSession session=getAuthSession();
		if(session==null){
			return null;
		}
		return session.getSessionId().getPrimaryId().getAuthSession();
	}
	
	/**
	 * 各スコープにkey&value形式で値を設定します。<br/>
	 * HANDLER,REQUEST,SESSION,AUTH_SESSION,MAPPING,CONFIGをサポート<br/>
	 * @param scope　スコープ
	 * @param name キー名
	 * @param value 値
	 */
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
		case CONFIG:
			config.setProperty(name, value);
			break;
		default:
			throw new IllegalArgumentException("not supoert scope:"+scope);
		}
	}
	
	/**
	 * 各スコープに設定された値を取得します。<br/>
	 * HANDLER,REQUEST,SESSION,AUTH_SESSION,MAPPING,CONFIGをサポート<br/>
	 * @param scope　スコープ
	 * @param name キー名
	 * @return 値
	 */
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
		case CONFIG:
			return config.getProperty(name);
		default:
			throw new IllegalArgumentException("not supoert scope:"+scope);
		}
	}
	
	/**
	 * 各スコープに設定されている情報のキー名の一覧を取得します。<br/>
	 * HANDLER,REQUEST,SESSION,AUTH_SESSION,MAPPING,CONFIGをサポート<br/>
	 * @param scope　スコープ
	 * @return　キー名一覧
	 */
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
		case CONFIG:
			return config.getConfiguration(null).getKeys();
		default:
			throw new IllegalArgumentException("not supoert scope:"+scope);
		}
	}
	
	/**
	 * 当該ハンドラーのkeepAliveContextを取得します。<br/>
	 * 同一socketのハンドラーは同一のcontextを返却します。ただし、spdy回線の場合はrequest単位となります。<br/>
	 * @return　keepAliveContext
	 */
	public KeepAliveContext getKeepAliveContext(){
		return getKeepAliveContext(false);
	}

	protected KeepAliveContext getKeepAliveContext(boolean isCreate){
		if(getChannelId()<0){//ChannelContextがいない場合
			return null;
		}
		KeepAliveContext keepAliveContext=(KeepAliveContext)getChannelAttribute(ATTRIBUTE_KEEPALIVE_CONTEXT);
		if(isCreate && keepAliveContext==null){
			keepAliveContext=(KeepAliveContext)PoolManager.getInstance(KeepAliveContext.class);
			keepAliveContext.setAcceptServer(ServerParser.create(getLocalIp(), getLocalPort()));
			setKeepAliveContext(keepAliveContext);
		}
		return keepAliveContext;
	}
	
	/**
	 * アプリケーションは使用しない
	 * @param keepAliveContext
	 */
	public void setKeepAliveContext(KeepAliveContext keepAliveContext){
		endowChannelAttribute(ATTRIBUTE_KEEPALIVE_CONTEXT,keepAliveContext);
	}
	
	/**
	 * 当該リクエストのパラメタを取得します。<br/>
	 * parameterは、key&value形式だけでなくアップロードされたファイルやJSONオブジェクト形式で取得できます。
	 * @return parameter
	 */
	public ParameterParser getParameterParser() {
		return getRequestContext().getParameterParser();
	}
	
	protected GzipContext getGzipContext() {
		return getRequestContext().getGzipContext();
	}
	protected void setGzipContext(GzipContext gzipContext) {
		getRequestContext().setGzipContext(gzipContext);
	}
	
	/**
	 * 当該requestのマッピング情報(どのmapping定義でリクエストされたか?)を取得します。
	 * @return mapping情報
	 */
	public MappingResult getRequestMapping() {
		return getRequestContext().getMapping();
	}
	protected void setRequestMapping(MappingResult mapping) {
		getRequestContext().setMapping(mapping);
	}
	
	/**
	 * 当該セション情報を取得します。<br/>
	 * @return　セション情報
	 */
	public AuthSession getAuthSession(){
		RequestContext requestContext=getRequestContext();
		if(requestContext==null){
			return null;
		}
		return requestContext.getAuthSession();
	}
	
	/**
	 * 当該handlerで処置中に回線が回収された場合に通知されます。<br/>
	 * overrideする場合は、元メソッドも呼び出してください。<br/>
	 */
	public void onFinished() {
		if(logger.isDebugEnabled())logger.debug("#finished.cid:"+getChannelId());
		super.onFinished();
	}
	
	/* DsipatchHandlerでoverrideする他ハンドラは必要なし */
	/**
	 * 利用不可
	 */
	@Override
	public SSLEngine getSSLEngine() {
		return null;
	}

	/**
	 * spdy通信している場合、spdyセション情報を取得します。<br/>
	 * @return　spdyセション情報
	 */
	public SpdySession getSpdySession() {
		return (SpdySession)getAttribute(SCOPE.REQUEST, ATTRIBUTE_SPDY_SESSION);
	}
	
	/**
	 * 当該リクエストがSSL通信を利用しているか否かを取得します。<br/>
	 */
	@Override
	public boolean isSsl() {
		if(getSpdySession()!=null){
			return true;
		}
		return	super.isSsl();
	}
	
}
