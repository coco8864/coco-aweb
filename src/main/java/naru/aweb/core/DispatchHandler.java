package naru.aweb.core;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.ssl.SslAdapter;
import naru.async.store.Page;
import naru.async.store.Store;
import naru.aweb.auth.AuthHandler;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.Authorizer;
import naru.aweb.auth.MappingAuth;
import naru.aweb.auth.SessionId; //import naru.aweb.auth.Authenticator;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.Mapper;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

/**
 * DispatchHanderは、リクエストヘッダの解析を専門に行う
 * WebServerHandlerを継承すると、それが実施できないため、ServerBaseHandlerを継承している。
 * このことから、DispatchHanderから直接レスポンスを返却する事はできない。
 * 
 * @author naru
 * 
 */
public class DispatchHandler extends ServerBaseHandler {
	static Logger logger = Logger.getLogger(DispatchHandler.class);
	private static Config config = Config.getConfig();
	private static Mapper mapper = config.getMapper();
	private static Authorizer authorizer=config.getAuthorizer();
	private static byte[] ProxyOkResponse = "HTTP/1.0 200 Connection established\r\n\r\n"
			.getBytes();
	private static int limitRequestFieldSize = config.getInt("limitRequestFieldSize", 8192);

	private Date startTime;
	private boolean isFirstRead;
	private long connectHeaderLength;
	// Dispatch先が確定しバッファを取得するか否かが決定するまで通信データをheaderPageに蓄える
	private Page headerPage = new Page();

	public void recycle() {
		connectHeaderLength = 0;
		startTime = null;
		headerPage.recycle();
		super.recycle();
	}

	/*
	 * 初回connection開設時および、KeepAlive時に呼び出される
	 */
	public void onStartRequest() {
		logger.debug("#startRequest.cid:" + getChannelId());
		headerPage.recycle();
		asyncRead(null);
	}

	public void onAccepted(Object userContext) {
		setReadTimeout(config.getAcceptTimeout());// syn攻撃対応で比較的短く設定
		logger.debug("#accepted.cid:" + getChannelId());
		isFirstRead = true;
		getKeepAliveContext(true);// keepAliveContextを用意する
		startTime = new Date();
		onStartRequest();
	}

	public void onFinished() {
		logger.debug("#finished.cid:" + getChannelId());
		super.onFinished();
	}

	public void onTimeout(Object userContext) {
		// keep-alive timeoutもここに到着する
		logger.debug("#timeout.cid:" + getChannelId());
		asyncClose(null);
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.warn("Dispatcher failure.poolId:" + getPoolId(), t);
		asyncClose(null);
	}

	/**
	 * ssl確立後、次データを要求する。
	 */
	public boolean onHandshaked() {
		logger.debug("#handshaked.cid:" + getChannelId());
		return true;
	}

	public void onRead(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onRead.cid:" + getChannelId() + ":buffers.hashCode:"
				+ buffers.hashCode());
		if (isFirstRead) {
			// 最初の通信データでSSLか否かを判定する。
			isFirstRead = false;
			if (SslAdapter.isSsl(buffers[0])) {
				if (!sslOpenWithBuffer(false, buffers)) {
					asyncClose(null);// handshake失敗
				}
				return;
			}
			setReadTimeout(config.getReadTimeout());// adminのreadTimeoutを設定
		}
		super.onRead(userContext, buffers);
	}

	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onReadPlain.cid:" + getChannelId()
				+ ":buffers.hashCode:" + buffers.hashCode());
		if (startTime == null) {// keepAliveからのリクエストの場合ここがリクエストの基点となる
			startTime = new Date();
		}
		headerPage.putBuffer(PoolManager.duplicateBuffers(buffers), true);
		HeaderParser headerParser = getRequestHeader();
		for (int i = 0; i < buffers.length; i++) {
			headerParser.parse(buffers[i]);
		}
		PoolManager.poolArrayInstance(buffers);
		if (headerParser.isParseEnd()) {
			if (headerParser.isParseError()) {
				logger.warn("http header error");
				asyncClose(null);
			} else {
				mappingHandler();
			}
		} else {
			if (limitRequestFieldSize <= headerPage.getBufferLength()) {
				logger.warn("too long header size."
						+ headerPage.getBufferLength());
				asyncClose(null);
				return;
			}
			asyncRead(null);
		}
	}

	private AccessLog setupTraceLog(String realHostName,
			HeaderParser requestHeader, MappingResult mapping, User user) {
		// ブラウザから入力されたＵＲＬ,target
		// http(s)://xxxx:xx/xxx/xxx
		// mapping先のURL,resolve(proxyの場合)
		// http(s)://xxxx:xx/xxx/xxx
		AccessLog accessLog = getAccessLog();
		accessLog.setStartTime(startTime);
		accessLog.setTimeCheckPint(AccessLog.TimePoint.requestHeader);
		accessLog.setIp(getRemoteIp());
		if (user != null) {
			accessLog.setUserId(user.getLoginId());
		}
		if(Boolean.TRUE.equals(mapping.getOption("skipPhlog"))){
			accessLog.setSkipPhlog(true);
		}
		if(Boolean.TRUE.equals(mapping.getOption("shortFormatLog"))){
			accessLog.setShortFormat(true);
		}
		
		// ブラウザからの入力情報を設定
		Mapping.SecureType secureType = mapping.getTargetSecureType();
		if (secureType != null) {
			switch (mapping.getTargetSecureType()) {
			case PLAIN:
				if (mapping.isSourceTypeProxy()) {
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_PLAIN_PROXY);
				} else {
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_PLAIN_WEB);
				}
				break;
			case SSL:
				if (mapping.isSourceTypeProxy()) {
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_SSL_PROXY);
				} else {
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_SSL_WEB);
				}
				break;
			}
		}
//		accessLog.setRealHost(realHostName + ":" + getLocalPort());
		accessLog.setRealHost(realHostName);
		// リクエスト先情報を設定
		Mapping.DestinationType destinationType = mapping.getDestinationType();
		if (destinationType != null) {
			String origin = null;
			switch (mapping.getDestinationType()) {
			case HTTP:
				accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_HTTP);
				origin = mapping.getResolveServer().toString();
				break;
			case HTTPS:
				accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_HTTPS);
				origin = mapping.getResolveServer().toString();
				break;
			case FILE:
				accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_FILE);
				origin = mapping.getDestinationFile().getAbsolutePath();
				break;
			case HANDLER:
				accessLog
						.setDestinationType(AccessLog.DESTINATION_TYPE_HANDLER);
				origin = mapping.getHandlerClass().getName();
				break;
			}
			accessLog.setResolveOrigin(origin);// 本当の接続先が設定されている。
		}
		accessLog.setRequestLine(requestHeader.getRequestLine());
		accessLog.setRequestHeaderLength(connectHeaderLength
				+ requestHeader.getHeaderLength());
		accessLog.setChannelId(getChannelId());
		accessLog.setLocalIp(getLocalIp());
		logger.debug("cid:" + getChannelId() + ":requestLine:"
				+ accessLog.getRequestLine());
		// DBへのアクセスログ採取有無
		switch (mapping.getLogType()) {
		case NONE:
			headerPage.recycle();
			accessLog.setPersist(false);
			return accessLog;// AccessLogを採取しない場合はここから抜けていく
		case TRACE:
		case REQUEST_TRACE:
			// requestHeaderのpeek処理
			Store readPeekStore = Store.open(true);
			// headerPageには、読みすぎてしまったbody部分も入っているのでカット
			ByteBuffer[] buffers = headerPage.getBuffer();
			BuffersUtil.cut(buffers, connectHeaderLength
					+ requestHeader.getHeaderLength());
			readPeekStore.putBuffer(buffers);
			logger.debug("#setupTraceLog" + readPeekStore.getStoreId());
			accessLog.incTrace();
			readPeekStore.close(accessLog, readPeekStore);
			accessLog.setRequestHeaderDigest(readPeekStore.getDigest());
			// requestBodyのpeek処理,header parser時に読み込んだバッファをbody peekに
			readPeekStore = Store.open(true);
			ByteBuffer[] bufs = requestHeader.peekBodyBuffer();
			if (bufs != null) {
				readPeekStore.putBuffer(bufs);
			}
			pushReadPeekStore(readPeekStore);
			break;
		}
		accessLog.setPersist(true);
		return accessLog;
	}

	private void forwardMapping(String realHostName, HeaderParser requestHeader,
			MappingResult mapping, AuthSession auth) {
//		setRequestAttribute(ServerBaseHandler.ATTRIBUTE_AUTH_SESSION, auth);
		User user = null;
		if (auth != null) {
			user = auth.getUser();
		}
		setRequestAttribute(ServerBaseHandler.ATTRIBUTE_USER, user);
		// 処理の起点がaccessLogの中に採られる
		setupTraceLog(realHostName, requestHeader, mapping, user);
		setRequestMapping(mapping);
		Class<WebServerHandler> responseClass = mapping.getHandlerClass();
		WebServerHandler response = (WebServerHandler) forwardHandler(responseClass);
		if (response == null) {
			logger.warn("fail to forwardHandler:cid:" + getChannelId() + ":" + this);
			return;
		}
		logger.debug("responseObject:cid:" + getChannelId() + ":" + response + ":" + this);
		response.startResponse();
	}

	private static final String SSL_PROXY_OK_CONTEXT = "sslProxyOkContext";

	private MappingResult sslProxyMapping(Mapper mapper,
			KeepAliveContext keepAliveContext, String realHost,
			ServerParser server) {
		// 自分はproxyなので、localhostの場合、接続先は、リモートホストとなる
		ServerParser.resolveLocalhost(server, getRemoteIp());
		boolean isPeek = mapper.isPeekSslProxyServer(realHost, server);
		if (isPeek == false) {// 内容に興味がない場合
			MappingResult mappingResult = mapper.resolveSslProxy(realHost,
					server);
			if (mappingResult != null) {
				return mappingResult;// sslProxyとして動作する場合ここで解決
			}
			return DispatchResponseHandler.forbidden();
		}
		keepAliveContext.setProxyTargetServer(server);
		// データをpeekしようとしている。
		HeaderParser requestHeader = getRequestHeader();
		connectHeaderLength = requestHeader.getHeaderLength();
		requestHeader.recycle();
		asyncWrite(SSL_PROXY_OK_CONTEXT, BuffersUtil
				.toByteBufferArray(ByteBuffer.wrap(ProxyOkResponse)));
		return null;
	}

	private MappingResult proxyMapping(Mapper mapper, String realHost,
			ServerParser server, String path) {
		// 自分はproxyなので、localhostの場合、接続先は、リモートホストとなる
		ServerParser.resolveLocalhost(server, getRemoteIp());
		// proxyマッピング対象は、realHost,phntomHost,pathで決まる
		MappingResult mappingResult = mapper.resolveProxy(realHost, server,
				path);
		if (mappingResult != null) {
			return mappingResult;
		}
		return DispatchResponseHandler.forbidden();
		// return MappingResult.createProxyResult(server,path);
	}

	private MappingResult wsMapping(Mapper mapper,
			KeepAliveContext keepAliveContext, String realHost,
			ServerParser server, String path) {
		// webマッピング対象は、realHost,phntomHost,pathで決まる
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		// ssl websocket proxyのtraceは別途検討
		if (sslServer != null) {
			// ssl websocket proxy
			return null;
		}
		MappingResult mapping = mapper.resolveWs(realHost, isSsl(), server,
				path);
		return mapping;
	}

	private MappingResult webMapping(Mapper mapper,
			KeepAliveContext keepAliveContext, String realHost,
			ServerParser server, String path) {
		// webマッピング対象は、realHost,phntomHost,pathで決まる
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		if (config.getRealHost(sslServer) == null && sslServer != null) {// ssl
		// proxyでログ/traceを採取するルート
			MappingResult mappingResult = mapper.resolvePeekSslProxy(realHost,sslServer, path);
			if (mappingResult != null) {
				return mappingResult;
			}
			return DispatchResponseHandler.forbidden();
		}
		// 自サーバをssl proxyした場合は、Webマッピングから返却
		MappingResult mapping = mapper.resolveWeb(realHost, isSsl(), server,path);
		return mapping;
	}

	/*
	 * private AuthSession webAuth(MappingResult mapping,Authenticator
	 * authenticator,HeaderParser requestHeader){ AuthSession
	 * auth=authenticator.webAuthentication(requestHeader,true); if(auth!=null){
	 * return auth; } List<String> mappingRoles=mapping.getRolesList();
	 * if(mappingRoles.isEmpty()){ return authenticator.getAdminSession(); }
	 * return null; }
	 */

	private MappingResult sslProxyHandler(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext) {
		ServerParser server = requestHeader.getServer();
		keepAliveContext.setProxyTargetServer(server);
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		return sslProxyMapping(mapper, keepAliveContext, realHost, server);
	}

	private MappingResult proxyHandler(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext) {
		ServerParser server = requestHeader.getServer();
		keepAliveContext.setProxyTargetServer(server);
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		String path = requestHeader.getPath();
		MappingResult mapping = proxyMapping(mapper, realHost, server, path);
		mapping = checkPhAuth(requestHeader, keepAliveContext, requestContext,mapping);
		return mapping;
	}

	// private static final String PROXY_AUTHENTICATION_PATH="/authentication";
	private MappingResult webHandler(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext) {
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		String path = requestHeader.getPath();
		// if(PROXY_AUTHENTICATION_PATH.equals(path)){//認証リクエスト,?action=logon,?action=logoff
		// }
		ServerParser server = requestHeader.getServer();
		MappingResult mapping = webMapping(mapper, keepAliveContext, realHost,server, path);
		if (mapping == null) {
			mapping = DispatchResponseHandler.notfound("not found mapping");
			return mapping;
		}
		mapping = checkPhAuth(requestHeader, keepAliveContext,requestContext, mapping);
		return mapping;
	}

	private MappingResult wsHandler(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext) {
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		String path = requestHeader.getPath();
		ServerParser server = requestHeader.getServer();
		MappingResult mapping = wsMapping(mapper, keepAliveContext, realHost,server, path);
		if (mapping == null) {
			mapping = DispatchResponseHandler.notfound("not found mapping");
			return mapping;
		}
		mapping = checkPhAuth(requestHeader, keepAliveContext,requestContext,mapping);
		return mapping;
	}
	
	// 認証情報があれば取得してkeepAliveContextに設定する
	private MappingResult checkPhAuth(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext, 
			MappingResult mapping) {
		boolean isAuthId=false;
		if(!requestHeader.isProxy()&&!requestHeader.isWs()){
			String path=mapping.getResolvePath();
			isAuthId=AuthHandler.AJAX_AUTHID_PATH.equals(path);
		}
		
		String cookieId=requestHeader.getAndRemoveCookieHeader(SessionId.SESSION_ID);
		if(cookieId!=null){
			setRequestAttribute(SessionId.SESSION_ID, cookieId);
			AuthSession authSession=authorizer.getAuthSessionBySecondaryId(mapping.getMapping(),cookieId);
			if(authSession!=null){
				//権限チェック、権限がなければ403
				if(!authorizer.authorize(mapping.getMapping(),authSession)){
					authSession.unref();
					mapping.unref();
					mapping = DispatchResponseHandler.forbidden("fail to authrize.");
					return mapping;
				}
				requestContext.registerAuthSession(authSession);
				if(isAuthId){
					mapping.unref();
					mapping = DispatchResponseHandler.ajaxAleadyAuth();
				}
				return mapping;
			}
		}
		List<String> mappingRoles = mapping.getRolesList();
		if (mappingRoles.size() == 0) {// 認証を必要としない,/pub,/proxy.pac,/auth
//			keepAliveContext.setAuthSession(AuthSession.UNAUTH_SESSION);
			return mapping;
		}
		
		//認可処理
		setRequestAttribute(AuthHandler.AUTHORIZE_MARK, AuthHandler.AUTHORIZE_MARK);
		mapping.forwardAuth();
		return mapping;
	}

	private MappingResult checkMappingAuth(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext,
			MappingResult mapping){
		if(requestContext.getAuthSession()!=null){//ph認証済みの場合はそれを優先
			return mapping;
		}
		//TODO mappingベースの認証を実施
		MappingAuth mappingAuth=mapping.getMappingAuth();
		if(mappingAuth==null){
			return mapping;
		}
		AuthSession authSession=mappingAuth.authorize(requestHeader);
		if(authSession!=null){
			authSession.ref();
			requestContext.registerAuthSession(authSession);
			return mapping;
		}
		mapping.unref();
		mapping = DispatchResponseHandler.authenticate(mappingAuth.isProxy(),mappingAuth.createAuthenticateHeader());
		return mapping;
	}
	
	private void mappingHandler() {
		HeaderParser requestHeader = getRequestHeader();
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		RequestContext requestContext=getRequestContext();
		
		// 認証情報があれば取得してkeepAliveContextに設定する
		// doAuth(requestHeader,keepAliveContext);
		boolean isSslProxy = requestHeader.isSslProxy();
		boolean isProxy = requestHeader.isProxy();
		boolean isWs = requestHeader.isWs();
		/**
		 * proxy処理は、認証をしてからmappingを探す web,ws処理は、mappingを探してから、必要に応じて認証する
		 * web,ws処理は必ずしも認証が必要ない(/pub)ため
		 */
		
		//roleベースの認証は、mapping処理の中で実施
		MappingResult mapping = null;
		if (isSslProxy) {// sslproxyの場合,websocket ssl proxyも含まれるがこの段階では判別できない
			mapping = sslProxyHandler(requestHeader, keepAliveContext);
			if (mapping == null) {
				return;// 続けてweb requestも処理する場合
			}
			// TODO httpsをpeekせずにproxyする場合の認証...いまのところ無条件で利用できる,mappingAuthは効く
		} else if (isProxy) {// http proxyの場合
			mapping = proxyHandler(requestHeader, keepAliveContext,requestContext);
		} else if (isWs) {// WebSocketリクエストの場合
		//wsはhttp側でcookieを付加して認証する
			mapping = wsHandler(requestHeader, keepAliveContext,requestContext);
		} else {// httpリクエストの場合,sslproxyをpeekする時もここ
			if (keepAliveContext.isSslProxy()) {
				// ssl proxyからwebリクエストを取り出した
				// mapping=sslProxyHandler(requestHeader, keepAliveContext);
				mapping = webHandler(requestHeader, keepAliveContext,requestContext);
			} else {
				mapping = webHandler(requestHeader, keepAliveContext,requestContext);
			}
		}
		
		//mapping Authを実施
		//optionsにauth:{realm:"relm",type:"basic"|"digest",roles:"role1,role2"}
		if(!isWs){
			mapping=checkMappingAuth(requestHeader,keepAliveContext,requestContext,mapping);
		}
		if(requestContext.getAuthSession()==null){
			AuthSession.UNAUTH_SESSION.ref();
			requestContext.registerAuthSession(AuthSession.UNAUTH_SESSION);
		}
		
		// mapping=checkRole(mapping,auth);
		keepAliveContext.startRequest(requestHeader);
		/* mapping処理 */
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		AuthSession auth = requestContext.getAuthSession();
		
		/* dispatchHandlerを出航するときにAccessLogを付加 */
		keepAliveContext.getRequestContext().allocAccessLog();
		forwardMapping(realHost, requestHeader, mapping, auth);
	}

	public SSLEngine getSSLEngine() {
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		return config.getSslEngine(sslServer);
	}

	/**
	 * proxyへのOKリクエストを送信した場合、送信を確認してsslOpenを呼ぶ （SSLの層でcontextのcounterのため)
	 */
	@Override
	public void onWrittenPlain(Object userContext) {
		logger.debug("#WrittenPlain cid:" + getChannelId());
		if (userContext == SSL_PROXY_OK_CONTEXT) {
			isFirstRead = true;
			asyncRead(null);
		}
	}
}
