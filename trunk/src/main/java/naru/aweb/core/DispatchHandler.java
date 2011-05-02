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
 * DispatchHander�́A���N�G�X�g�w�b�_�̉�͂���ɍs��
 * WebServerHandler���p������ƁA���ꂪ���{�ł��Ȃ����߁AServerBaseHandler���p�����Ă���B
 * ���̂��Ƃ���ADispatchHander���璼�ڃ��X�|���X��ԋp���鎖�͂ł��Ȃ��B
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
	// Dispatch�悪�m�肵�o�b�t�@���擾���邩�ۂ������肷��܂ŒʐM�f�[�^��headerPage�ɒ~����
	private Page headerPage = new Page();

	public void recycle() {
		connectHeaderLength = 0;
		startTime = null;
		headerPage.recycle();
		super.recycle();
	}

	/*
	 * ����connection�J�ݎ�����сAKeepAlive���ɌĂяo�����
	 */
	public void onStartRequest() {
		logger.debug("#startRequest.cid:" + getChannelId());
		headerPage.recycle();
		asyncRead(null);
	}

	public void onAccepted(Object userContext) {
		setReadTimeout(config.getAcceptTimeout());// syn�U���Ή��Ŕ�r�I�Z���ݒ�
		logger.debug("#accepted.cid:" + getChannelId());
		isFirstRead = true;
		getKeepAliveContext(true);// keepAliveContext��p�ӂ���
		startTime = new Date();
		onStartRequest();
	}

	public void onFinished() {
		logger.debug("#finished.cid:" + getChannelId());
		super.onFinished();
	}

	public void onTimeout(Object userContext) {
		// keep-alive timeout�������ɓ�������
		logger.debug("#timeout.cid:" + getChannelId());
		asyncClose(null);
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.warn("Dispatcher failure.poolId:" + getPoolId(), t);
		asyncClose(null);
	}

	/**
	 * ssl�m����A���f�[�^��v������B
	 */
	public boolean onHandshaked() {
		logger.debug("#handshaked.cid:" + getChannelId());
		return true;
	}

	public void onRead(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onRead.cid:" + getChannelId() + ":buffers.hashCode:"
				+ buffers.hashCode());
		if (isFirstRead) {
			// �ŏ��̒ʐM�f�[�^��SSL���ۂ��𔻒肷��B
			isFirstRead = false;
			if (SslAdapter.isSsl(buffers[0])) {
				if (!sslOpenWithBuffer(false, buffers)) {
					asyncClose(null);// handshake���s
				}
				return;
			}
			setReadTimeout(config.getReadTimeout());// admin��readTimeout��ݒ�
		}
		super.onRead(userContext, buffers);
	}

	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onReadPlain.cid:" + getChannelId()
				+ ":buffers.hashCode:" + buffers.hashCode());
		if (startTime == null) {// keepAlive����̃��N�G�X�g�̏ꍇ���������N�G�X�g�̊�_�ƂȂ�
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
		// �u���E�U������͂��ꂽ�t�q�k,target
		// http(s)://xxxx:xx/xxx/xxx
		// mapping���URL,resolve(proxy�̏ꍇ)
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
		
		// �u���E�U����̓��͏���ݒ�
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
		// ���N�G�X�g�����ݒ�
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
			accessLog.setResolveOrigin(origin);// �{���̐ڑ��悪�ݒ肳��Ă���B
		}
		accessLog.setRequestLine(requestHeader.getRequestLine());
		accessLog.setRequestHeaderLength(connectHeaderLength
				+ requestHeader.getHeaderLength());
		accessLog.setChannelId(getChannelId());
		accessLog.setLocalIp(getLocalIp());
		logger.debug("cid:" + getChannelId() + ":requestLine:"
				+ accessLog.getRequestLine());
		// DB�ւ̃A�N�Z�X���O�̎�L��
		switch (mapping.getLogType()) {
		case NONE:
			headerPage.recycle();
			accessLog.setPersist(false);
			return accessLog;// AccessLog���̎悵�Ȃ��ꍇ�͂������甲���Ă���
		case TRACE:
		case REQUEST_TRACE:
			// requestHeader��peek����
			Store readPeekStore = Store.open(true);
			// headerPage�ɂ́A�ǂ݂����Ă��܂���body�����������Ă���̂ŃJ�b�g
			ByteBuffer[] buffers = headerPage.getBuffer();
			BuffersUtil.cut(buffers, connectHeaderLength
					+ requestHeader.getHeaderLength());
			readPeekStore.putBuffer(buffers);
			logger.debug("#setupTraceLog" + readPeekStore.getStoreId());
			accessLog.incTrace();
			readPeekStore.close(accessLog, readPeekStore);
			accessLog.setRequestHeaderDigest(readPeekStore.getDigest());
			// requestBody��peek����,header parser���ɓǂݍ��񂾃o�b�t�@��body peek��
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
		// �����̋N�_��accessLog�̒��ɍ̂���
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
		// ������proxy�Ȃ̂ŁAlocalhost�̏ꍇ�A�ڑ���́A�����[�g�z�X�g�ƂȂ�
		ServerParser.resolveLocalhost(server, getRemoteIp());
		boolean isPeek = mapper.isPeekSslProxyServer(realHost, server);
		if (isPeek == false) {// ���e�ɋ������Ȃ��ꍇ
			MappingResult mappingResult = mapper.resolveSslProxy(realHost,
					server);
			if (mappingResult != null) {
				return mappingResult;// sslProxy�Ƃ��ē��삷��ꍇ�����ŉ���
			}
			return DispatchResponseHandler.forbidden();
		}
		keepAliveContext.setProxyTargetServer(server);
		// �f�[�^��peek���悤�Ƃ��Ă���B
		HeaderParser requestHeader = getRequestHeader();
		connectHeaderLength = requestHeader.getHeaderLength();
		requestHeader.recycle();
		asyncWrite(SSL_PROXY_OK_CONTEXT, BuffersUtil
				.toByteBufferArray(ByteBuffer.wrap(ProxyOkResponse)));
		return null;
	}

	private MappingResult proxyMapping(Mapper mapper, String realHost,
			ServerParser server, String path) {
		// ������proxy�Ȃ̂ŁAlocalhost�̏ꍇ�A�ڑ���́A�����[�g�z�X�g�ƂȂ�
		ServerParser.resolveLocalhost(server, getRemoteIp());
		// proxy�}�b�s���O�Ώۂ́ArealHost,phntomHost,path�Ō��܂�
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
		// web�}�b�s���O�Ώۂ́ArealHost,phntomHost,path�Ō��܂�
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		// ssl websocket proxy��trace�͕ʓr����
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
		// web�}�b�s���O�Ώۂ́ArealHost,phntomHost,path�Ō��܂�
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		if (config.getRealHost(sslServer) == null && sslServer != null) {// ssl
		// proxy�Ń��O/trace���̎悷�郋�[�g
			MappingResult mappingResult = mapper.resolvePeekSslProxy(realHost,sslServer, path);
			if (mappingResult != null) {
				return mappingResult;
			}
			return DispatchResponseHandler.forbidden();
		}
		// ���T�[�o��ssl proxy�����ꍇ�́AWeb�}�b�s���O����ԋp
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
		// if(PROXY_AUTHENTICATION_PATH.equals(path)){//�F�؃��N�G�X�g,?action=logon,?action=logoff
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
	
	// �F�؏�񂪂���Ύ擾����keepAliveContext�ɐݒ肷��
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
				//�����`�F�b�N�A�������Ȃ����403
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
		if (mappingRoles.size() == 0) {// �F�؂�K�v�Ƃ��Ȃ�,/pub,/proxy.pac,/auth
//			keepAliveContext.setAuthSession(AuthSession.UNAUTH_SESSION);
			return mapping;
		}
		
		//�F����
		setRequestAttribute(AuthHandler.AUTHORIZE_MARK, AuthHandler.AUTHORIZE_MARK);
		mapping.forwardAuth();
		return mapping;
	}

	private MappingResult checkMappingAuth(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext,
			MappingResult mapping){
		if(requestContext.getAuthSession()!=null){//ph�F�؍ς݂̏ꍇ�͂����D��
			return mapping;
		}
		//TODO mapping�x�[�X�̔F�؂����{
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
		
		// �F�؏�񂪂���Ύ擾����keepAliveContext�ɐݒ肷��
		// doAuth(requestHeader,keepAliveContext);
		boolean isSslProxy = requestHeader.isSslProxy();
		boolean isProxy = requestHeader.isProxy();
		boolean isWs = requestHeader.isWs();
		/**
		 * proxy�����́A�F�؂����Ă���mapping��T�� web,ws�����́Amapping��T���Ă���A�K�v�ɉ����ĔF�؂���
		 * web,ws�����͕K�������F�؂��K�v�Ȃ�(/pub)����
		 */
		
		//role�x�[�X�̔F�؂́Amapping�����̒��Ŏ��{
		MappingResult mapping = null;
		if (isSslProxy) {// sslproxy�̏ꍇ,websocket ssl proxy���܂܂�邪���̒i�K�ł͔��ʂł��Ȃ�
			mapping = sslProxyHandler(requestHeader, keepAliveContext);
			if (mapping == null) {
				return;// ������web request����������ꍇ
			}
			// TODO https��peek������proxy����ꍇ�̔F��...���܂̂Ƃ��떳�����ŗ��p�ł���,mappingAuth�͌���
		} else if (isProxy) {// http proxy�̏ꍇ
			mapping = proxyHandler(requestHeader, keepAliveContext,requestContext);
		} else if (isWs) {// WebSocket���N�G�X�g�̏ꍇ
		//ws��http����cookie��t�����ĔF�؂���
			mapping = wsHandler(requestHeader, keepAliveContext,requestContext);
		} else {// http���N�G�X�g�̏ꍇ,sslproxy��peek���鎞������
			if (keepAliveContext.isSslProxy()) {
				// ssl proxy����web���N�G�X�g�����o����
				// mapping=sslProxyHandler(requestHeader, keepAliveContext);
				mapping = webHandler(requestHeader, keepAliveContext,requestContext);
			} else {
				mapping = webHandler(requestHeader, keepAliveContext,requestContext);
			}
		}
		
		//mapping Auth�����{
		//options��auth:{realm:"relm",type:"basic"|"digest",roles:"role1,role2"}
		if(!isWs){
			mapping=checkMappingAuth(requestHeader,keepAliveContext,requestContext,mapping);
		}
		if(requestContext.getAuthSession()==null){
			AuthSession.UNAUTH_SESSION.ref();
			requestContext.registerAuthSession(AuthSession.UNAUTH_SESSION);
		}
		
		// mapping=checkRole(mapping,auth);
		keepAliveContext.startRequest(requestHeader);
		/* mapping���� */
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		AuthSession auth = requestContext.getAuthSession();
		
		/* dispatchHandler���o�q����Ƃ���AccessLog��t�� */
		keepAliveContext.getRequestContext().allocAccessLog();
		forwardMapping(realHost, requestHeader, mapping, auth);
	}

	public SSLEngine getSSLEngine() {
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		return config.getSslEngine(sslServer);
	}

	/**
	 * proxy�ւ�OK���N�G�X�g�𑗐M�����ꍇ�A���M���m�F����sslOpen���Ă� �iSSL�̑w��context��counter�̂���)
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
