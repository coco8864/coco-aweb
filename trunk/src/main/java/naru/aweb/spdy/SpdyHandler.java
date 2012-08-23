package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.auth.AuthHandler;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.Authorizer;
import naru.aweb.auth.MappingAuth;
import naru.aweb.auth.SessionId;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.core.DispatchResponseHandler;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.Mapper;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;
import net.sf.json.JSONObject;

/**
 * @author Naru
 *
 */
public class SpdyHandler extends ServerBaseHandler {
	private static Logger logger=Logger.getLogger(SpdyHandler.class);
	private static Config config = null;//Config.getConfig();
	private static Mapper mapper = null;//config.getMapper();
	private static Authorizer authorizer=null;//config.getAuthorizer();
	
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
	
	private SpdyFrame frame=new SpdyFrame();
	private Map<Integer,SpdySession> sessions=new HashMap<Integer,SpdySession>();
	
	@Override
	public void recycle() {
		super.recycle();
	}

	public boolean onHandshaked(String protocol) {
		logger.debug("#handshaked.cid:" + getChannelId() +":"+protocol);
		frame.init(protocol);
		return false;//���͂�asyncRead��������
	}
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
//		BuffersUtil.hexDump("SPDY c->s row data",buffers);
		try {
			for(ByteBuffer buffer:buffers){
				if( frame.parse(buffer) ){
					doFrame();
					frame.prepareNext();
				}
			}
			asyncRead(null);
		} catch (RuntimeException e) {
			logger.error("SpdyHandler parse error.",e);
			asyncClose(null);
		}finally{
			PoolManager.poolArrayInstance(buffers);
		}
	}
	
	private void doFrame(){
		logger.debug("SpdyHandler#doFrame cid:"+getChannelId());
		int streamId=frame.getStreamId();
		ByteBuffer[] dataBuffer;
//		HeaderParser header;
		SpdySession session=null;
		int statusCode;
		short type=frame.getType();
		switch(type){
		case SpdyFrame.TYPE_DATA_FRAME:
			dataBuffer=frame.getDataBuffers();
			session=sessions.get(streamId);
			if(session!=null){
				session.responseBody(frame.isFin(),dataBuffer);
			}else{
				logger.error("illegal streamId:"+streamId);
				PoolManager.poolBufferInstance(dataBuffer);
			}
			break;
		case SpdyFrame.TYPE_SYN_STREAM:
			HeaderParser requestHeader=frame.getHeader();
			session=SpdySession.create(this, streamId, requestHeader);
			sessions.put(streamId, session);
			mappingHandler(session);
			/*
			header.unref();
			HeaderParser response=(HeaderParser)PoolManager.getInstance(HeaderParser.class);
			response.setStatusCode("200 OK");
			response.setResHttpVersion(HeaderParser.HTTP_VESION_11);
			response.setContentType("text/plain");
			ByteBuffer[] res=frame.buildSynReply(streamId, header);
			asyncWrite(null, res);
			ByteBuffer resBody=ByteBuffer.wrap("test OK".getBytes());
			res=frame.buildDataFrame(streamId, SpdyFrame.FLAG_FIN, BuffersUtil.toByteBufferArray(resBody));
			asyncWrite(null, res);
			*/
			break;
		case SpdyFrame.TYPE_RST_STREAM:
			session=sessions.get(streamId);
			statusCode=frame.getStatusCode();
			break;
		case SpdyFrame.TYPE_HEADERS:
		default:
		}
	}
	
	private static final String WRITE_CONTEXT_BODY = "writeContextBody";
	private static final String WRITE_CONTEXT_HEADER = "writeContextHeader";
	
	private static class SpdyCtx{
		SpdySession spdySession;
		Object ctx;
		SpdyCtx(SpdySession spdySession,String ctx){
			this.spdySession=spdySession;
			this.ctx=ctx;
		}
	}
	
	public void responseHeader(SpdySession spdySession,HeaderParser responseHeader){
		ByteBuffer[] synReplyFrame=frame.buildSynReply(spdySession.getStreamId(), responseHeader);
		asyncWrite(new SpdyCtx(spdySession,WRITE_CONTEXT_HEADER), synReplyFrame);
	}
	
	public void responseBody(SpdySession spdySession,boolean isFin,ByteBuffer[] body){
		char flags=0;
		if(isFin){
			flags=SpdyFrame.FLAG_FIN;
		}
		ByteBuffer[] dataFrame=frame.buildDataFrame(spdySession.getStreamId(), flags, body);
		asyncWrite(new SpdyCtx(spdySession,WRITE_CONTEXT_BODY), dataFrame);
	}
	
	public synchronized boolean asyncWrite(Object context,ByteBuffer[] buffers){
		return asyncWrite(context,buffers);
	}
	
	@Override
	public void onWrittenPlain(Object userContext) {
		SpdyCtx spdyCtx=(SpdyCtx)userContext;
		if(spdyCtx.ctx==WRITE_CONTEXT_HEADER){
//			spdyCtx.spdySession.onWrittenBody();
		}else if(spdyCtx.ctx==WRITE_CONTEXT_BODY){
			spdyCtx.spdySession.onWrittenBody();
		}
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:"+getChannelId());
		super.onFinished();
	}
	
	private void mappingHandler(SpdySession session) {
		/* cookieId�͐؂�����requestAttribute�Ɉڂ��ς��� */
		HeaderParser requestHeader=session.getRequestHeader();
		KeepAliveContext keepAliveContext=session.getKeepAliveContext();
		RequestContext requestContext=session.getRequestContext();
		
		String cookieId=requestHeader.getAndRemoveCookieHeader(SessionId.SESSION_ID);
		if(cookieId!=null){
			setRequestAttribute(SessionId.SESSION_ID, cookieId);
		}
		String query=requestHeader.getQuery();
		if(query!=null){
			if(query.startsWith(AuthHandler.QUERY_CD_CHECK)){
				setRequestAttribute(AuthHandler.AUTH_MARK, AuthHandler.AUTH_CD_CHECK);
			}else if(query.startsWith(AuthHandler.QUERY_CD_SET)){
				setRequestAttribute(AuthHandler.AUTH_MARK, AuthHandler.AUTH_CD_SET);
			}
		}
		//role�x�[�X�̔F�؂́Amapping�����̒��Ŏ��{
		MappingResult mapping = null;
		mapping = webHandler(requestHeader, keepAliveContext,requestContext);
		//mapping Auth�����{
		//options��auth:{realm:"relm",type:"basic"|"digest",roles:"role1,role2"}
		mapping=checkMappingAuth(requestHeader,keepAliveContext,requestContext,mapping);
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
		requestContext.allocAccessLog();
		forwardMapping(realHost, requestHeader, mapping, auth);
	}
	
	// private static final String PROXY_AUTHENTICATION_PATH="/authentication";
	private MappingResult webHandler(HeaderParser requestHeader,
			KeepAliveContext keepAliveContext,RequestContext requestContext) {
		String realHost = keepAliveContext.getRealHost().getName();// getRealHost(localIp,localPort)
		String path = requestHeader.getPath();
		// if(PROXY_AUTHENTICATION_PATH.equals(path)){//�F�؃��N�G�X�g,?action=logon,?action=logoff
		// }
		ServerParser server = requestHeader.getServer();
		MappingResult mapping = webMapping(getMapper(),realHost,server, path);
		if (mapping == null) {
			mapping = DispatchResponseHandler.notfound("not found mapping");
			return mapping;
		}
		mapping = checkPhAuth(requestHeader, keepAliveContext,requestContext, mapping);
		return mapping;
	}
	
	private MappingResult webMapping(Mapper mapper,String realHost,ServerParser server, String path) {
		MappingResult mapping = mapper.resolveWeb(realHost, true, server,path);
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
	
	private enum AUTH_STAT{
		SUCCESS,/* �F�؍ς� */
		FAIL,/* �F�؍ς� */
		PUBLIC/* �F�؂̕K�v�Ȃ� */
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
	
	private void forwardMapping(String realHostName, HeaderParser requestHeader,
			MappingResult mapping, AuthSession auth) {
		User user = null;
		if (auth != null) {
			user = auth.getUser();
		}
		setRequestAttribute(ServerBaseHandler.ATTRIBUTE_USER, user);
		// �����̋N�_��accessLog�̒��ɍ̂���
//		setupTraceLog(realHostName, requestHeader, mapping, user);
		setRequestMapping(mapping);
		Class<WebServerHandler> responseClass = mapping.getHandlerClass();
		WebServerHandler responseHandler = (WebServerHandler) forwardHandler(responseClass);
		if (responseHandler == null) {
			logger.warn("fail to forwardHandler:cid:" + getChannelId() + ":" + this);
			return;
		}
		logger.debug("responseObject:cid:" + getChannelId() + ":" + responseHandler + ":" + this);
		responseHandler.startResponse();
	}
	
	/*
	private AccessLog setupTraceLog(String realHostName,
			HeaderParser requestHeader, MappingResult mapping, User user) {
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
		accessLog.setSourceType(AccessLog.SOURCE_TYPE_SSL_WEB);
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
			}
			accessLog.setResolveOrigin(origin);// �{���̐ڑ��悪�ݒ肳��Ă���B
		}
		accessLog.setRequestLine(requestHeader.getRequestLine());
		accessLog.setRequestHeaderLength(0/*connectHeaderLength+requestHeader.getHeaderLength());
		accessLog.setChannelId(getChannelId());
		accessLog.setLocalIp(getLocalIp());
		logger.debug("cid:" + getChannelId() + ":requestLine:"+ accessLog.getRequestLine());
		// DB�ւ̃A�N�Z�X���O�̎�L��
		switch (mapping.getLogType()) {
		case NONE:
//			headerPage.recycle();
			accessLog.setPersist(false);
			return accessLog;// AccessLog���̎悵�Ȃ��ꍇ�͂������甲���Ă���
		case TRACE:
		case REQUEST_TRACE:
			// requestHeader��peek����
			Store readPeekStore = Store.open(true);
			// headerPage�ɂ́A�ǂ݂����Ă��܂���body�����������Ă���̂ŃJ�b�g
	//		ByteBuffer[] buffers = headerPage.getBuffer();
	//		BuffersUtil.cut(buffers, connectHeaderLength
	//				+ requestHeader.getHeaderLength());
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
*/
	
	
}
