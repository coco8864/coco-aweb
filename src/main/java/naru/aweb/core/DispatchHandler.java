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
import naru.aweb.spdy.SpdyConfig;
import naru.aweb.spdy.SpdyHandler;
import naru.aweb.spdy.SpdySession;
import naru.aweb.util.ServerParser;
import net.sf.json.JSONObject;

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
	private static byte[] ProxyOkResponse = "HTTP/1.0 200 Connection established\r\n\r\n".getBytes();
	private static Config config = null;//Config.getConfig();
	private static Mapper mapper = null;//config.getMapper();
	private static Authorizer authorizer=null;//config.getAuthorizer();
	private static int limitRequestFieldSize = -1;//config.getInt("limitRequestFieldSize", 8192);

	private static Config getConfig(){
		if(config==null){
			config=Config.getConfig();
		}
		return config;
	}
	
	private static Mapper getMapper(){
		if(mapper==null){
			mapper=getConfig().getMapper();
		}
		return mapper;
	}
	
	private static Authorizer getAuthorizer(){
		if(authorizer==null){
			authorizer=getConfig().getAuthorizer();
		}
		return authorizer;
	}
	
	private static int getLimitRequestFieldSize(){
		if(limitRequestFieldSize==-1){
			limitRequestFieldSize=getConfig().getInt("limitRequestFieldSize", 8192);
		}
		return limitRequestFieldSize;
	}
	
	private Date startTime;
	private long handshakeTime=-1;
	private long connectTime=-1;
	private boolean isFirstRead;
	private long connectHeaderLength;
	// Dispatch�悪�m�肵�o�b�t�@���擾���邩�ۂ������肷��܂ŒʐM�f�[�^��headerPage�ɒ~����
	private Page headerPage = new Page();

	public void recycle() {
		connectHeaderLength = 0;
		startTime = null;
		handshakeTime=connectTime=-1;
		headerPage.recycle();
		//accept��sslProxy�̏ꍇ�A���̃^�C�~���O��true,
		//KeepAlive�Ōp�����鎞�̂��߂�����false�ŏ�����
		isFirstRead=false;
		isSpdyAvailable=false;
		super.recycle();
	}

//	private long startTotalReadLength;
//	private long startTotalWriteLength;
	
	/*
	 * ����connection�J�ݎ�����сAKeepAlive���ɌĂяo�����
	 */
	public void onStartRequest() {
		logger.debug("#startRequest.cid:" + getChannelId());
		headerPage.recycle();
//		startTotalReadLength=getTotalReadLength();
//		startTotalWriteLength=getTotalWriteLength();
		asyncRead(null);
	}

	public void onAccepted(Object userContext) {
		setReadTimeout(getConfig().getAcceptTimeout());// Connection Flood �U���Ή��Ŕ�r�I�Z���ݒ�
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
		//���ׂ������ꍇ�A���Ȃ�̕p�x�ł����ɗ���
		logger.debug("Dispatcher failure.poolId:" + getPoolId(), t);
		asyncClose(null);
	}
	
	private SSLEngine sslEngine;
	private boolean isSpdyAvailable;
	private String nextProtocol;
	/**
	 * ssl�m����A���f�[�^��v������B(return true)
	 */
	public boolean onHandshaked() {
		logger.debug("#handshaked.cid:" + getChannelId());
		handshakeTime=System.currentTimeMillis()-startTime.getTime();
		//SPDY�̂��߁A������foward����Ƃ��܂������Ȃ��A���������v
		this.nextProtocol=getConfig().getSpsyConfig().getNextProtocol(sslEngine);
		this.isSpdyAvailable=nextProtocol.startsWith("spdy/");
		return true;
	}

	public void onRead(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onRead.cid:" + getChannelId() + ":buffers.hashCode:"+ buffers.hashCode());
		if (isFirstRead) {
			if (startTime == null) {// keepAlive����̃��N�G�X�g�̏ꍇ���������N�G�X�g�̊�_�ƂȂ�
				startTime = new Date();
			}
			connectTime=System.currentTimeMillis()-startTime.getTime();
			// �ŏ��̒ʐM�f�[�^��SSL���ۂ��𔻒肷��B
			isFirstRead = false;
			setReadTimeout(getConfig().getReadTimeout());// admin��readTimeout��ݒ�
			if (SslAdapter.isSsl(buffers[0])) {
				if (!sslOpenWithBuffer(false, buffers)) {
					asyncClose(null);// handshake���s
				}
				return;
			}
		}
		super.onRead(userContext, buffers);
	}

	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onReadPlain.cid:" + getChannelId()+ ":buffers.hashCode:" + buffers.hashCode());
		if(isSpdyAvailable){
			SpdyHandler handler=(SpdyHandler)forwardHandler(SpdyHandler.class);
			if(handler!=null){
				logger.debug("#onReadPlain.cid:" + getChannelId()+ "fowardHandler nextProtocol:"+nextProtocol );
				handler.onHandshaked(nextProtocol);
				handler.onReadPlain(userContext, buffers);
				return;
			}
			//����close����Ă���
			logger.error("fail to forward SpdyHandler");
			asyncClose(null);
			return;
		}
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
				if(headerParser.isProxy()&&getConfig().getRealHost(headerParser.getServer())!=null ){
					//�������g��proxy���悤�Ƃ��Ă���B
					headerParser.forceWebRequest();
				}
				mappingHandler();
			}
		} else {
			if (getLimitRequestFieldSize() <= headerPage.getBufferLength()) {
				logger.warn("too long header size."
						+ headerPage.getBufferLength());
				asyncClose(null);
				return;
			}
			asyncRead(null);
		}
	}

	private AccessLog setupTraceLog(String realHostName,
			HeaderParser requestHeader, MappingResult mapping, User user,boolean isWs) {
		// �u���E�U������͂��ꂽ�t�q�k,target
		// http(s)://xxxx:xx/xxx/xxx
		// mapping���URL,resolve(proxy�̏ꍇ)
		// http(s)://xxxx:xx/xxx/xxx
		AccessLog accessLog = getAccessLog();
		accessLog.setStartTime(startTime);
		accessLog.setConnectTime(connectTime);
		accessLog.setHandshakeTime(handshakeTime);
//		accessLog.setRawRead(startTotalReadLength);
//		accessLog.setRawWrite(startTotalWriteLength);
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
				}else if(isWs){
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_WS);
				}else{
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_PLAIN_WEB);
				}
				break;
			case SSL:
				if (mapping.isSourceTypeProxy()) {
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_SSL_PROXY);
				}else if(isWs){
					accessLog.setSourceType(AccessLog.SOURCE_TYPE_WSS);
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
				accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_HANDLER);
				origin = mapping.getHandlerClass().getName();
				break;
			case WS:
				accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_WS);
				origin = mapping.getResolveServer().toString();
				break;
			case WSS:
				accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_WSS);
				origin = mapping.getResolveServer().toString();
				break;
			}
			accessLog.setResolveOrigin(origin);// �{���̐ڑ��悪�ݒ肳��Ă���B
		}
		accessLog.setRequestLine(requestHeader.getRequestLine());
		accessLog.setRequestHeaderLength(connectHeaderLength+requestHeader.getHeaderLength());
		accessLog.setChannelId(getChannelId());
		accessLog.setLocalIp(getLocalIp());
		logger.debug("cid:" + getChannelId() + ":requestLine:"+ accessLog.getRequestLine());
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
			SpdySession spdySession=getSpdySession();
			ByteBuffer[] buffers=null;
			if(spdySession==null){
				buffers = headerPage.getBuffer();
				BuffersUtil.cut(buffers, connectHeaderLength+requestHeader.getHeaderLength());
			}else{
				buffers=spdySession.getRequestHeader().getHeaderBuffer();
				accessLog.setRequestHeaderLength(BuffersUtil.remaining(buffers));
			}
			readPeekStore.putBuffer(buffers);
			logger.debug("#setupTraceLog" + readPeekStore.getStoreId());
			accessLog.incTrace();
			readPeekStore.close(accessLog, readPeekStore);
			accessLog.setRequestHeaderDigest(readPeekStore.getDigest());
			if(isWs){//WebSocket�̏ꍇbody trace�̍̎���@���Ⴄ
				break;
			}
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
			MappingResult mapping, AuthSession auth,boolean isWs) {
//		setRequestAttribute(ServerBaseHandler.ATTRIBUTE_AUTH_SESSION, auth);
		User user = null;
		if (auth != null) {
			user = auth.getUser();
		}
		setRequestAttribute(ServerBaseHandler.ATTRIBUTE_USER, user);
		// �����̋N�_��accessLog�̒��ɍ̂���
		setupTraceLog(realHostName, requestHeader, mapping, user,isWs);
		setRequestMapping(mapping);
		Class<WebServerHandler> responseClass = mapping.getHandlerClass();
		WebServerHandler responseHandler=(WebServerHandler)PoolManager.getInstance(responseClass);
		responseHandler = (WebServerHandler) forwardHandler(responseHandler);
		if (responseHandler == null) {
			logger.warn("fail to forwardHandler:cid:" + getChannelId() + ":" + this);
			return;
		}
		logger.debug("responseObject:cid:" + getChannelId() + ":" + responseHandler + ":" + this);
		responseHandler.startResponse();
	}

	private static final String SSL_PROXY_OK_CONTEXT = "sslProxyOkContext";

	private MappingResult sslProxyMapping(Mapper mapper,
			KeepAliveContext keepAliveContext, String realHost,
			ServerParser server) {
		// ������proxy�Ȃ̂ŁAlocalhost�̏ꍇ�A�ڑ���́A�����[�g�z�X�g�ƂȂ�
		ServerParser.resolveLocalhost(server, getRemoteIp());
		boolean isPeek = mapper.isPeekSslProxyServer(realHost, server);
		//HTTPS,WS proxy,WSS proxy�́A�T�[�o���ɓ����v���t�B�N�X�����ƒ����I�ɓ��삷��
		if (isPeek == false) {// ���e�ɋ������Ȃ��ꍇ
			MappingResult mappingResult = mapper.resolveSslProxy(realHost,server);
			if (mappingResult != null) {
				return mappingResult;// sslProxy�Ƃ��ē��삷��ꍇ�����ŉ���
			}
			return DispatchResponseHandler.forbidden();
		}
		// �f�[�^��peek���悤�Ƃ��Ă���B
		keepAliveContext.setProxyTargetServer(server);
		HeaderParser requestHeader = getRequestHeader();
		connectHeaderLength = requestHeader.getHeaderLength();
		requestHeader.recycle();
		
		//CONNECT���N�G�X�g�ɑ΂��Đ���(200)��ԋp����
		asyncWrite(SSL_PROXY_OK_CONTEXT, 
				BuffersUtil.toByteBufferArray(ByteBuffer.wrap(ProxyOkResponse)));
		return null;
	}

	private MappingResult proxyMapping(Mapper mapper, String realHost,
			ServerParser server, String path) {
		// ������proxy�Ȃ̂ŁAlocalhost�̏ꍇ�A�ڑ���́A�����[�g�z�X�g�ƂȂ�
		ServerParser.resolveLocalhost(server, getRemoteIp());
		// proxy�}�b�s���O�Ώۂ́ArealHost,phntomHost,path�Ō��܂�
		MappingResult mappingResult = mapper.resolveProxy(realHost, server,	path);
		if (mappingResult != null) {
			return mappingResult;
		}
		return DispatchResponseHandler.forbidden();
	}

	private MappingResult wsMapping(Mapper mapper,
			KeepAliveContext keepAliveContext, String realHost,
			ServerParser server, String path) {
		// web�}�b�s���O�Ώۂ́ArealHost,phntomHost,path�Ō��܂�
		ServerParser targetServer = keepAliveContext.getProxyTargetServer();
		//targetServer������Ƃ������Ƃ́AWS_PROXY
		boolean isProxy=(targetServer!=null);
		return mapper.resolveWs(realHost, isSsl(),isProxy, server,path);
	}

	private MappingResult webMapping(Mapper mapper,
			KeepAliveContext keepAliveContext, String realHost,
			ServerParser server, String path) {
		// web�}�b�s���O�Ώۂ́ArealHost,phntomHost,path�Ō��܂�
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		if (getConfig().getRealHost(sslServer) == null && sslServer != null) {// ssl
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

	//CONNECT���N�G�X�g����̐U�蕪��
	private MappingResult sslProxyHandler(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext) {
		ServerParser server = requestHeader.getServer();
		keepAliveContext.setProxyTargetServer(server);
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		return sslProxyMapping(getMapper(), keepAliveContext, realHost, server);
	}

	private MappingResult proxyHandler(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext) {
		ServerParser server = requestHeader.getServer();
		keepAliveContext.setProxyTargetServer(server);
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		String path = requestHeader.getPath();
		MappingResult mapping = proxyMapping(getMapper(), realHost, server, path);
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
		MappingResult mapping = webMapping(getMapper(), keepAliveContext, realHost,server, path);
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
		MappingResult mapping = wsMapping(getMapper(), keepAliveContext, realHost,server, path);
		if (mapping == null) {
			mapping = DispatchResponseHandler.notfound("not found mapping");
			return mapping;
		}
		mapping = checkPhAuth(requestHeader, keepAliveContext,requestContext,mapping);
		return mapping;
	}
	
	private enum AUTH_STAT{
		SUCCESS,/* �F�؍ς� */
		FAIL,/* �F�؍ς� */
		PUBLIC/* �F�؂̕K�v�Ȃ� */
	}
	
	private MappingResult authMarkResponse(String authMark,AUTH_STAT stat,AuthSession authSession){
		JSONObject response=new JSONObject();
		response.element("authUrl",config.getAuthUrl());
		if(AuthHandler.AUTH_CD_SET.equals(authMark)){
			/* AUTH_SET���́A�܂����F�؂̂͂� */
			response.element("result", false);
			response.element("reason", "seequence error");
		}else{
			switch(stat){
			case SUCCESS:
				response.element("result", true);
				response.element("appId", authSession.getAppId());
				break;
			case PUBLIC:
				response.element("result", true);
				response.element("appId", "public");
				break;
			case FAIL:
				response.element("result", false);
				response.element("reason", "lack of right");
				break;
			}
		}
		return DispatchResponseHandler.crossDomainFrame(response);
	}
	
	// �F�؏�񂪂���Ύ擾����keepAliveContext�ɐݒ肷��
	private MappingResult checkPhAuth(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext, 
			MappingResult mapping) {
		String authMark=(String)getRequestAttribute(AuthHandler.AUTH_MARK);
		
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		if(cookieId!=null){
			//TODO �����ƓK�؂ȏꏊ���Ȃ����H
			ServerParser domain=requestHeader.getServer();
			boolean isSsl=isSsl();
			domain.setupPortIfNeed(isSsl);
			Authorizer authorizer=getAuthorizer();
			AuthSession authSession=authorizer.getAuthSessionBySecondaryId(cookieId,mapping.getMapping(),isSsl,domain);
			if(authSession!=null){
				//�����`�F�b�N�A�������Ȃ����403
				if(!authorizer.authorize(mapping.getMapping(),authSession)){
					authSession.unref();
					mapping.unref();
					if(authMark!=null){
						return authMarkResponse(authMark,AUTH_STAT.FAIL,authSession);
					}
					mapping = DispatchResponseHandler.forbidden("lack of right");
					return mapping;
				}
				requestContext.registerAuthSession(authSession);
				if(authMark!=null){
					mapping.unref();
					return authMarkResponse(authMark,AUTH_STAT.SUCCESS,authSession);
				}
				return mapping;
			}
		}
		List<String> mappingRoles = mapping.getRolesList();
		if (mappingRoles.size() == 0) {// �F�؂�K�v�Ƃ��Ȃ�,/pub,/proxy.pac,/auth
			if(authMark!=null){
				return authMarkResponse(authMark,AUTH_STAT.PUBLIC,null);
			}
			return mapping;
		}
		
		if(authMark==null){
			authMark=AuthHandler.AUTHORIZE_MARK;
		}
		//�F����
		setRequestAttribute(AuthHandler.AUTHORIZE_MARK,authMark);
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
	
	public void mappingHandler() {
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
		
		/**
		 * 1.�F�؃`�F�b�N
		 * ws�̏ꍇ��javascript api�𓊂���O�ɂ��̃p�X���F�؂���Ă��邩�ۂ���₢���킹�郊�N�G�X�g�𑗐M����
		 * 1)����AuthUrl�ɗL����Secondary�Z�V����������ꍇ�ɂ́AOK��ԋp�A
		 * 2)�L����Secondary�Z�V�������Ȃ��ꍇ�ɂ́AAuthId(TempraryId)��ԋp�A
		 * 3)AuthUrl���s���ȏꍇ�ɂ́ANG��ԋp
		 * 
		 * ws�̃`�F�b�N�������)�w�b�_���Ŕ��肷��̂�GET
		 * GET /queue?PH_AUTH=check(Web or SSL Web)
		 * GET http://ph.host:port/?PH_AUTH=check(web proxy�̏ꍇ)
		 * GET /?PH_AUTH=check(ssl proxy�̏ꍇ)
		 * 
		 * 2.�F��
		 * 1.2)�̌�A/auth��pathOnceID���擾�A�Ăт����Ƀ��N�G�X�g����
		 * 1)TempolaryId,pathOnceId���L���ł���΁ASecondaryId���쐬����Set-Cookie
		 * 2)TempolaryId,pathOnceId���L���łȂ���΁ANG��ԋp
		 * 
		 * ws�̔F�������)�w�b�_���Ŕ��肷��̂�GET
		 * GET /queue?PH_AUTH=auth&pathOnceId=${id}(Web or SSL Web)
		 * GET http://ph.host:port/?PH_AUTH=auth&pathOnceId=${id}(web proxy�̏ꍇ)
		 * GET /?PH_AUTH=auth&pathOnceId=${id}(ssl proxy�̏ꍇ)
		 * 
		 * 3.�F��(����class�Ƃ͊֌W���Ȃ��j
		 * 1.2)�̌�/auth��pathOnceId�̎擾���ł��Ȃ������ꍇ(primaryId�����݂��Ȃ������j�A
		 * 1)/auth�ɉ�ʑJ�ڂ��ĔF�؂����߂�
		 * 2)�F�؂�����������A���_�C���N�g�Ō���URL�ɖ߂��Ă���(���̌㓯������������邩�ǂ����͕�����Ȃ������e����j
		 * 
		 */
		/* cookieId�͐؂�����requestAttribute�Ɉڂ��ς��� */
		String cookieId=requestHeader.getAndRemoveCookieHeader(SessionId.SESSION_ID);
		if(cookieId!=null){
			setRequestAttribute(SessionId.SESSION_ID, cookieId);
		}
		String query=requestHeader.getQuery();
		if(isWs==false && query!=null){
			if(query.startsWith(AuthHandler.QUERY_CD_CHECK)){
				setRequestAttribute(AuthHandler.AUTH_MARK, AuthHandler.AUTH_CD_CHECK);
			}else if(query.startsWith(AuthHandler.QUERY_CD_WS_CHECK)){
				setRequestAttribute(AuthHandler.AUTH_MARK, AuthHandler.AUTH_CD_WS_CHECK);
				isWs=true;
			}else if(query.startsWith(AuthHandler.QUERY_CD_SET)){
				setRequestAttribute(AuthHandler.AUTH_MARK, AuthHandler.AUTH_CD_SET);
			}else if(query.startsWith(AuthHandler.QUERY_CD_WS_SET)){
				setRequestAttribute(AuthHandler.AUTH_MARK, AuthHandler.AUTH_CD_WS_SET);
				isWs=true;
//			}else if(query.startsWith("__PH_AUTH_AUTHORIZE__")){
//				setRequestAttribute(AuthHandler.AUTH_MARK, AuthHandler.AUTH_AUTHORIZE);
			}
		}
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
		if(!isWs){//TODO ws�ł�401���ԋp�ł���悤�ɂȂ����̂ŏ����T�|�[�g��
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
		forwardMapping(realHost, requestHeader, mapping, auth,isWs);
	}

	public SSLEngine getSSLEngine() {
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		ServerParser sslServer = keepAliveContext.getProxyTargetServer();
		SSLEngine engine=getConfig().getSslEngine(sslServer);
		if(keepAliveContext.getRealHost().isSpdyAvailable()){
			SpdyConfig spdyConfig=getConfig().getSpsyConfig();
			spdyConfig.setNextProtocols(engine);
		}
		this.sslEngine=engine;
		return engine;
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
