package naru.aweb.auth;

import java.util.List;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.Mapper;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * �ݒ荀��
 * 1)�Z�V�����^�C���A�E�g����
 * 2)logout���url
 * 3)�F�ؕ���[basic|digest|basicForm|digestForm]
 * 
 * @author naru
 *
 */
public class AuthHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(AuthHandler.class);
	private static Config config = Config.getConfig();
	private static Authenticator authenticator = config.getAuthenticator();
	private static Authorizer authorizer=config.getAuthorizer();
	public static String APP_ID="appId";//javascript���ŃZ�V���������ʂ���ID,setAuth���\�b�h���A���Ajson�̃L�[�Ƃ��Ďg�p
	public static String AUTHORIZE_MARK="ahthorizeMark";//authorize�ړI�ŌĂяo���ꂽ�ꍇ�ɕt�������B
	public static String AUTHENTICATE_PATH="/authenticate";//�K�v������ΔF�؏������s���p�X
	public static String USER_PATH="/user";//login���[�U����юg����WebURL��₢���킹��API
	public static String AJAX_PATHONCEID_PATH="/ajaxPathOnceId";//path������ŏI����ajax����̔F��
	public static String AJAX_SETAUTH_PATH="/ajaxSetAuth";//
//	public static String AJAX_AUTHID_PATH="/ajaxAuthId";//�F�ς݂Ŗ����ꍇ�AauthId��ԋp
	private static String CLEANUP_AUTH_HEADER_PATH="/cleanupAuthHeader";//auth header�폜�ppath
	private static String CHECK_SESSION_PATH="/checkSession";//webSocket,ajax api�F�؂��s���ꍇ�Ăяo���O�ɂ��̃p�X��iframe�ɕ\��
	private static String LOGOUT_PATH="/logout";//logout�p
	private static String AUTH_PAGE_FILEPATH="/auth";
	private static String AUTH_ID="authId";//...temporaryId�̕ʖ�

	public static String QUERY_AUTH_MARK="queryAuthMark";//query��auth�Ăяo�����w�肳�ꂽ�ꍇ
	public static String QUERY_AUTH_CHECK="check";
	public static String QUERY_AUTH_AUTH="auth";
	public static String QUERY_SETAUTH_AUTH="setAuth";
	
	public static String AUTH_MARK="authMark";
	public static String AUTH_CD_CHECK="crossDomainAuthCheck";
	public static String AUTH_CD_WS_CHECK="crossDomainAuthWsCheck";
	public static String AUTH_CD_AUTHORIZE="crossDomainAuthorize";
	public static String AUTH_CD_SET="crossDomainAuthSet";
	public static String AUTH_CD_WS_SET="crossDomainAuthWsSet";
	
	public static String QUERY_CD_CHECK="__PH_AUTH__=__CD_CHECK__";
	public static String QUERY_CD_WS_CHECK="__PH_AUTH__=__CD_WS_CHECK__";
	public static String QUERY_CD_AUTHORIZE="__PH_AUTH__=__CD_AUTHORIZE__";
	public static String QUERY_CD_SET="__PH_AUTH__=__CD_SET__";
	public static String QUERY_CD_WS_SET="__PH_AUTH__=__CD_WS_SET__";
	
	
	//admin/auth�z���̃R���e���c��ԋp����B
	public void forwardAuthPage(String fileName){
		MappingResult mapping=getRequestMapping();
		if(fileName.startsWith("/")){
			mapping.setResolvePath(AUTH_PAGE_FILEPATH + fileName);
		}else{
			mapping.setResolvePath(AUTH_PAGE_FILEPATH + "/" +fileName);
		}
		mapping.setDesitinationFile(config.getAdminDocumentRoot());
		forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
	}
	
	private void forbidden(String message) {
		completeResponse("403", message);
	}
	
	private void notFound(String message) {
		completeResponse("404", message);
	}
	
	private void redirectLogout() {
		redirect(authenticator.getLogoutUrl(),authorizer.delCookieString());
	}
	
	private void redirectAuth(String authId, String setCookieString) {
		StringBuffer sb=new StringBuffer(authorizer.getAuthUrl());
		sb.append(AUTHENTICATE_PATH);
		sb.append('?');
		sb.append(AUTH_ID);
		sb.append('=');
		sb.append(authId);
		redirect(sb.toString(),setCookieString);
	}
	
	private void setCookie(String setCookieString){
		//IE��iframe����cookie��L���ɂ���w�b�_,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
		setHeader("P3P", "CP=\"CAO PSA OUR\"");
		setHeader(HeaderParser.SET_COOKIE_HEADER, setCookieString);
	}
	
	private void redirect(String location,String setCookieString) {
		setCookie(setCookieString);
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	
	
	/*
	 * �F�؂���
	 * 1)���s������A�F�؉�ʂ�forward
	 * 2)��������cleanup���K�v�Ȃ�cleanup��ʂ�forward
	 * 3)��������cleanup���K�v�Ȃ��Ȃ�AAuthHandler�ɏ�����C����
	 */
	private SessionId authenticate(String url,String authId){
		// authentication header or parameter�ŔF��
		//form�F�؂̏ꍇ�AcleanupAuthPath�̏ꍇ�AauthId��form�ɖ��ߍ��ޕK�v������
		setRequestAttribute(AUTH_ID, authId);
		User user = authenticator.webAuthenticate(this);
		if (user == null) {
			return null;
		}
		AuthSession authSession=authenticator.loginUser(user);
		authorizer.setAuthSessionToTemporaryId(authId, authSession);
		if( authenticator.forwardCleanupAuthHeader(this, authSession.getUser()) ){
			return null;
		}
		SessionId primaryId = authorizer.createPrimaryId(authSession);
		String setCookieString = primaryId.getSetCookieString();
		if(url==null){
			redirect(config.getPublicWebUrl(), setCookieString);//doneResponse("200","authentication success.");
			return null;
		}
		setCookie(setCookieString);
		SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, primaryId.getId());
		return pathOnceId;
	}
	
	private void loginRedirect(String cookieId,boolean isAjaxAuth,String authId){
		SessionId temporaryId = authorizer.getTemporaryId(authId);
		if(temporaryId==null){
			completeResponse("403", "authentication error");
			return;
		}
		String url=temporaryId.getUrl();
		SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, cookieId);
		if(pathOnceId==null){//cookieId�������ȏꍇ
			if(isAjaxAuth){
				completeResponse("403", "can't authentication");
				return;
			}
			pathOnceId=authenticate(url,authId);
			if( pathOnceId==null ){
				return;
			}
		}
		if(temporaryId.isDirectUrl()){
			//directUrl�̏ꍇ�͑�����redirect����
			pathOnceId.unref();
			setHeader(HeaderParser.LOCATION_HEADER, url);
			completeResponse("302");
			return;
		}
		String encodeUrl = pathOnceId.encodeUrl();
		if(isAjaxAuth){
//			setHeader(HeaderParser.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
			JSONObject json=new JSONObject();
			json.put("isRedirect", true);
			json.put("location", encodeUrl);
			responseJson(json);
			return;
		}
		//����url�ɖ߂�Ƃ���
		setHeader(HeaderParser.LOCATION_HEADER, encodeUrl);
		completeResponse("302");
	}
	
	//TODO on making
	private void crossDomainResponse(Object response,String setCookieString){
		if(setCookieString!=null){
			setCookie(setCookieString);
		}
		setRequestAttribute("response", response);
		forwardAuthPage("/crossDomainFrame.vsp");
	}
	
	private void crossDomainRedirect(String location,String setCookieString){
		JSONObject response=new JSONObject();
		response.element("result", "redirect");
		response.element("location", location);
		crossDomainResponse(response,setCookieString);
	}
	
	private String getCrossDomainAplUrl(boolean isWs,HeaderParser requestHeader,String cookiePath){
		StringBuffer url=new StringBuffer();
		if(isSsl()){
			if(isWs){
				url.append("wss://");
			}else{
				url.append("https://");
			}
		}else{
			if(isWs){
				url.append("ws://");
			}else{
				url.append("http://");
			}
		}
		url.append(requestHeader.getServer().toString());
		if (!requestHeader.isProxy()) {
			url.append(cookiePath);
		}
		url.append("/");
		return url.toString();
	}
	
	private String getCrossDomainCookiePath(HeaderParser requestHeader,MappingResult mapping){
		if (requestHeader.isProxy()) {
			return "/";
		} else {
			return mapping.getSourcePath();
		}
	}
	
	private void crossDomainCheck(boolean isWs,HeaderParser requestHeader,MappingResult mapping){
		String cookiePath=getCrossDomainCookiePath(requestHeader, mapping);
		String aplUrl=getCrossDomainAplUrl(isWs,requestHeader, cookiePath);
		
		SessionId temporaryId = authorizer.createTemporaryId(aplUrl,cookiePath);
		String setCookieString = temporaryId.getSetCookieString();
		String authId=temporaryId.getAuthId();
		
		StringBuffer sb=new StringBuffer(authorizer.getAuthUrl());
		sb.append(AUTHENTICATE_PATH);
		sb.append("?");
		sb.append(QUERY_CD_AUTHORIZE);
		sb.append("&");
		sb.append(AUTH_ID);
		sb.append('=');
		sb.append(authId);
		crossDomainRedirect(sb.toString(),setCookieString);
	}
	
	private void crossDomainSet(boolean isWs,HeaderParser requestHeader,MappingResult mapping,String pathId,String cookieId){
		JSONObject response=new JSONObject();
		response.element("authUrl", config.getAuthUrl());
		
		String cookiePath=getCrossDomainCookiePath(requestHeader, mapping);
		String aplUrl=getCrossDomainAplUrl(isWs,requestHeader, cookiePath);
		String cookieDomain =requestHeader.getServer().toString();
		
		//pathId�����邩�HcookieId�����邩�H�L�����H
		if(pathId==null || cookieId==null || authorizer.isTemporaryId(cookieId,aplUrl)==false){//pathId,cookieId�����ꂩ�����݂��Ȃ�
			response.element("result", false);
			response.element("reason", "lack of set auth");
			crossDomainResponse(response,null);
			return;
		}
		//�L����PathOnceId���t������Ă��邩�A�����͂��邩�H
		Mapping m=mapping.getMapping();
		SessionId secondaryId=authorizer.createSecondarySetCookieStringFromPathOnceId(pathId,aplUrl,m,isSsl(),cookieDomain,cookiePath,null);
		if(secondaryId==null){
			response.element("result", false);
			response.element("reason", "logoffed");
			crossDomainResponse(response,null);
			return;
		}
		response.element("result", true);
		response.element("appId", secondaryId.getAuthSession().getAppId());
		crossDomainResponse(response,secondaryId.getSetCookieString());
	}
	
	private void crossDomainAuthorize(String cookieId,String authId,String originUrl){
		JSONObject response=new JSONObject();
		SessionId temporaryId = authorizer.getTemporaryId(authId);
		if(temporaryId==null){
			response.element("result", false);
			response.element("reason", "authentication error");
			crossDomainResponse(response,null);
			return;
		}
		String url=temporaryId.getUrl();
		StringBuffer appId=new StringBuffer();
		int rc=authorizer.checkSecondarySessionByPrimaryId(cookieId,url,appId);
		switch(rc){
		case Authorizer.CHECK_SECONDARY_OK://����͂Ȃ��͂�
			response.element("result", false);
			response.element("reason", "aleady secondary session");
			break;
		case Authorizer.CHECK_PRIMARY_ONLY:
			SessionId pathOnceSession=authorizer.createPathOnceIdByPrimary(url, cookieId);
			if(pathOnceSession!=null){
				if(url.startsWith("ws")){
					url=url.replaceAll("^ws", "http");
					crossDomainRedirect(url +"?" + QUERY_CD_WS_SET + "&pathOnceId=" + pathOnceSession.getId(), null);
				}else{
					crossDomainRedirect(url +"?" + QUERY_CD_SET + "&pathOnceId=" + pathOnceSession.getId(), null);
				}
				return;
			}else{
				response.element("result", false);
				response.element("reason", "logoff");
			}
			break;
		case Authorizer.CHECK_NO_PRIMARY:
			//�F�؂����������ۂɌ���URL�ɖ߂邽��tempraryId��originUrl��ۑ�����,���̏ꍇoriginUrl�͊֌W�̂Ȃ��ʃh���C���̉\������
			SessionId tempraryId=authorizer.createTemporaryId(originUrl,null,true);
			response.element("result", "redirectForAuthorizer");
			response.element("location", config.getAuthUrl()+"?authId="+tempraryId.getAuthId());
//			response.put("authId", tempraryId.getAuthId());
			break;
		}
		crossDomainResponse(response,null);
	}
	
	
	/**
	 * 1)�C�ӂ�mapping�ŔF�����߂�ꂽ���A���F�̏ꍇ
	 * 2)�F��authId����url�ɖ߂��Ă���
	 * @param requestHeader
	 * @param mapping
	 * @param cookieId
	 * @param isAjaxAuth
	 */
	private void secondaryAuthorize(HeaderParser requestHeader,MappingResult mapping,String cookieId){
		// GET���\�b�h����Ȃ��ƐV�K�̔F�؂͂ł��Ȃ�
		String method=requestHeader.getMethod();
		if (!HeaderParser.GET_METHOD.equalsIgnoreCase(method) && 
			!HeaderParser.HEAD_METHOD.equalsIgnoreCase(method)) {
			forbidden("fail to authorize because of method");
			return;
		}
		// WebSocket�͒��ڔF�؂͂ł��Ȃ�
		if (requestHeader.isWs()) {
			forbidden("fail to authorize because of websocket");
			return;
		}
		String urlWithPathId = requestHeader.getAddressBar(isSsl());
		String url=urlWithPathId;
		StringBuffer urlSb=new StringBuffer();
		String pathId=SessionId.getPathId(urlWithPathId,urlSb);
		if(pathId!=null){
			url=urlSb.toString();
		}
		String cookiePath = null;
		String cookieDomain =requestHeader.getServer().toString();
		if (requestHeader.isProxy()) {
			cookiePath = "/";
		} else {
			cookiePath = mapping.getSourcePath();
		}
		//pathId�����邩�HcookieId�����邩�H�L�����H
		if(pathId==null || cookieId==null || authorizer.isTemporaryId(cookieId,url)==false){//pathId,cookieId�����ꂩ�����݂��Ȃ�
			// redirect /auth
			if (url.endsWith(cookiePath) && !url.endsWith("/")) {
				url = url + "/";// �߂��Ă����ۂɂ́A/xxx;phId=xxx�ƂȂ邪�Achrome�ł�cookieonce���t������Ȃ�
			}
			SessionId temporaryId = authorizer.createTemporaryId(url,cookiePath);
			String setCookieString = temporaryId.getSetCookieString();
			String authId=temporaryId.getAuthId();
			redirectAuth(authId, setCookieString);
			return;
		}
		
		//�L����PathOnceId���t������Ă��邩�A�����͂��邩�H
		Mapping m=mapping.getMapping();
		SessionId secondaryId=authorizer.createSecondarySetCookieStringFromPathOnceId(pathId,url,m,isSsl(),cookieDomain,cookiePath,null);
		if(secondaryId==null){
			forbidden("fail to authorize.");
			return;
		}
		String setCookieString=secondaryId.getSetCookieString();
		redirect(url, setCookieString);
	}
	
	/*
	 * ���A�͂R��ލl������
	 * 1)�F�ؕK�v�Ȃ�
	 * 2)�F�؂���Amatch����
	 * 3)�F�؂���Amatch���Ȃ�
	 */
	private int checkCrossDomainProxy(Mapper mapper,String protocol,String authDomain,String originUrl,StringBuffer authUrlSb){
		boolean isSsl=false;
		int defaultPort=80;
		Mapping.SourceType sourceType=Mapping.SourceType.PROXY;
		if("https:".equals(protocol)){
			sourceType=Mapping.SourceType.PROXY;
			isSsl=true;
			defaultPort=443;
		}else if("wss:".equals(protocol)){
			sourceType=Mapping.SourceType.WS_PROXY;
			isSsl=true;
			defaultPort=443;
		}else if("http:".equals(protocol)){
			sourceType=Mapping.SourceType.PROXY;
			isSsl=false;
			defaultPort=80;
		}else if("ws:".equals(protocol)){
			sourceType=Mapping.SourceType.WS_PROXY;
			isSsl=false;
			defaultPort=80;
		}else{
			logger.warn("protocol format error."+protocol);
			return Mapper.CHECK_NOT_MATCH;
		}
		ServerParser authDomainParser=ServerParser.parse(authDomain, defaultPort);
		String host=authDomainParser.getHost();
		int port=authDomainParser.getPort();
		authDomainParser.unref(true);
		
		//authUrl�̍\�z�Aws�̏ꍇ�ł��F�ؗv���́Ahttp���g��(cookie��t������̂��ړI)
		if(isSsl){
			authUrlSb.append("https://");
		}else{
			authUrlSb.append("http://");
		}
		authUrlSb.append(host);
		authUrlSb.append(":");
		authUrlSb.append(port);
		authUrlSb.append("/");
		
		return mapper.checkCrossDomainProxy(sourceType,isSsl, host, port,originUrl);
	}
	
	private int checkCrossDomainWebWs(Mapper mapper,String protocol,String authPath,String originUrl){
		Mapping.SourceType sourceType;
		boolean isSsl;
		if("https:".equals(protocol)){
			sourceType=Mapping.SourceType.WEB;
			isSsl=true;
		}else if("wss:".equals(protocol)){
			sourceType=Mapping.SourceType.WS;
			isSsl=true;
		}else if("http:".equals(protocol)){
			sourceType=Mapping.SourceType.WEB;
			isSsl=false;
		}else if("ws:".equals(protocol)){
			sourceType=Mapping.SourceType.WS;
			isSsl=false;
		}else{
			logger.warn("protocol format error."+protocol);
			return Mapper.CHECK_NOT_MATCH;
		}
		//TODO �h���C���̃`�F�b�N�����ׂ�
		return mapper.checkCrossDomainWebWs(sourceType,isSsl, authPath,originUrl);
	}
	
	private String checkSessionProxyAuthUrl(String protocol,String authDomain,String originUrl,JSONObject res){
		//proxy�Ƃ��ă}�b�`���邩�H
		Mapper mapper=config.getMapper();
		StringBuffer authUrlSb=new StringBuffer();
		switch(checkCrossDomainProxy(mapper,protocol,authDomain,originUrl,authUrlSb)){
		case Mapper.CHECK_MATCH_NO_AUTH://�F�؂̕K�v���Ȃ�
			res.put("result", "secondary");
			res.put(APP_ID, "NEED_NOT_AUTH");
			return null;
		case Mapper.CHECK_NOT_MATCH:
			//������Ȃ�origin����̃A�N�Z�X
			logger.warn("not allow proxy url."+authDomain);
			res.put("result", "false");
			res.put("reason", "not allow proxy url");
			responseJson(res);
			return null;
		}
		return authUrlSb.toString();
	}
	
	private String checkSessionWebWsAuthUrl(String protocol,String authDomain,String authPath,String originUrl,JSONObject res){
		//TODO Web Ws�̕���,
		//web ws�Ƃ��ă}�b�`���邩�H
		Mapper mapper=config.getMapper();
		switch(checkCrossDomainWebWs(mapper,protocol,authPath,originUrl)){
		case Mapper.CHECK_MATCH_NO_AUTH://�F�؂̕K�v���Ȃ�
			res.put("result", "secondary");
			res.put(APP_ID, "NEED_NOT_AUTH");
			return null;
		case Mapper.CHECK_NOT_MATCH:
			//������Ȃ�origin����̃A�N�Z�X
			logger.warn("not allow web url."+authPath);
			res.put("result", "false");
			res.put("reason", "not allow web url");
			return null;
		}
		//TODO �h���C���̃`�F�b�N�������ꍇ�́A�h���C����ݒ肷��
		//����WS��mainHost�ȊO�œ��삳����ƔF�؂ł��Ȃ�
		StringBuffer sb=new StringBuffer();
		if("https:".equals(protocol)||"wss:".equals(protocol)){
			sb.append("https://");
		}else{
			sb.append("http://");
		}
		sb.append(config.getSelfDomain());
		sb.append(":");
		sb.append(config.getInt(Config.SELF_PORT));
		sb.append(authPath);
		return sb.toString();
	}

	/**
	 * �F�؂̏�Ԃ��`�F�b�N����
	 * CHECK_SECONDARY_OK: Primary�͔F�؍ς݂������YURL�ɂ͖��F��
	 * CHECK_PRIMARY_ONLY:�@���YURL�ɑ΂��Ċ��ɔF�؍ς�
	 * CHECK_NO_PRIMARY: Primary�F�ؖ����{
	 * 
	 * @param cookieId
	 * @param aplAuthUrl
	 * @param originUrl
	 * @return
	 */
	private JSONObject checkSessionAuth(String cookieId,String aplAuthUrl,String originUrl,JSONObject res){
		StringBuffer appId=new StringBuffer();
		int rc=authorizer.checkSecondarySessionByPrimaryId(cookieId,aplAuthUrl,appId);
		switch(rc){
		case Authorizer.CHECK_SECONDARY_OK:
			res.put("result", "secondary");
			res.put(APP_ID, appId.toString());
			break;
		case Authorizer.CHECK_PRIMARY_ONLY:
			SessionId pathOnceSession=authorizer.createPathOnceIdByPrimary(aplAuthUrl, cookieId);
			if(pathOnceSession!=null){
				res.put("result", "primary");
				res.put("pathOnceId", pathOnceSession.getId());
				res.put("authEncUrl", aplAuthUrl +"?PH_AUTH=setAuth&pathOnceId=" + pathOnceSession.getId());
			}else{
				res.put("result", "ng");
			}
			break;
		case Authorizer.CHECK_NO_PRIMARY:
			//�F�؂����������ۂɌ���URL�ɖ߂邽��tempraryId��originUrl��ۑ�����
			SessionId tempraryId=authorizer.createTemporaryId(originUrl,null,true);
			res.put("result", "redirectAuth");
			res.put("authId", tempraryId.getAuthId());
			break;
		}
		return res;
	}

	/**
	 * @param cookieId
	 */
	private void checkSession(String cookieId){
		ParameterParser parameter = getParameterParser();
		String sourceType=parameter.getParameter("sourceType");
		String originUrl=parameter.getParameter("originUrl");
		String protocol=parameter.getParameter("protocol");
		String authDomain=parameter.getParameter("authDomain");
		String authPath=parameter.getParameter("authPath");
		String isAjax=parameter.getParameter("isAjax");
		String aplAuthUrl=null;
		//TODO authUrl��,mapping�Ώۂ��ۂ�?
		JSONObject res=new JSONObject();
		res.put("authUrl",config.getAuthUrl());
		if("proxy".equals(sourceType)){
			//proxy�Ƃ��ă}�b�`���邩�H
			aplAuthUrl=checkSessionProxyAuthUrl(protocol, authDomain, originUrl, res);
		}else{
			//web ws�Ƃ��ă}�b�`���邩�H
			aplAuthUrl=checkSessionWebWsAuthUrl(protocol, authDomain, authPath, originUrl, res);
		}
		/* authUrl=null:�F�؂��`�F�b�N����K�v���Ȃ��Aurl���s�� or �F�؂�K�v�Ƃ��Ȃ�url */
		if(aplAuthUrl!=null){
			//�F�؃`�F�b�N
			checkSessionAuth(cookieId, aplAuthUrl, originUrl,res);
		}
		if("true".equals(isAjax)){
			responseJson(res);
		}else{
			setRequestAttribute("response", res.toString());
			forwardAuthPage("/checkSessionCDFrame.vsp");
		}
	}
	
	private void cleanupAuthHeader(){
		if( !authenticator.cleanupAuthHeader(this) ){
			return;
		}
		ParameterParser parameter = getParameterParser();
		String authId=parameter.getParameter(AUTH_ID);
		if(authId==null){
			forbidden("faild to get authId");
			return;
		}
		//TODO �r��
		SessionId temporaryId=authorizer.getTemporaryId(authId);
		if(temporaryId==null){
			forbidden("faild to get authId");
			return;
		}
		AuthSession authSession=temporaryId.popAuthSession();
		SessionId primaryId = authorizer.createPrimaryId(authSession);
		String setCookieString = primaryId.getSetCookieString();
		setCookie(setCookieString);
		String url=temporaryId.getUrl();
		if(url!=null){
			if(temporaryId.isDirectUrl()){//�F�؂�K�v�Ƃ��Ȃ��y�[�W�ɖ߂�ꍇ
				completeResponse("200", url);
			}else{
				SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, primaryId.getId());
				completeResponse("200", pathOnceId.encodeUrl());
			}
		}else{//�P�ɔF�؂��������ꍇ
			temporaryId.remove();
			completeResponse("200", config.getPublicWebUrl());
		}
	}
	
	private void queryAuthCheck(){
		JSONObject json=new JSONObject();
		json.put("authUrl",config.getAuthUrl());
		HeaderParser requestHeader = getRequestHeader();
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		//�L����secondarysession�����邩�H
		//����΁Aresult:true�Ń��X�|���X
		if(cookieId!=null){
			StringBuffer appId=new StringBuffer();
			if(authorizer.isSecondaryId(cookieId,appId)){
				json.put("result", true);
				json.put(APP_ID, appId.toString());
				setRequestAttribute("response", json.toString());
				forwardAuthPage("/crossDomainFrame.vsp");
				return;
			}
		}
		String url=requestHeader.getAddressBar(isSsl());
		int pos=url.indexOf("?");
		if(pos<0){
			//��������
		}
		url=url.substring(0,pos);
		
		//AUTH_ID�����X�|���X
//		SessionId temporaryId = authorizer.createTemporaryId(url,requestHeader.getPath());
//		String setCookieString = temporaryId.getSetCookieString();
//		setCookie(setCookieString);
//		String authId=temporaryId.getAuthId();
		json.put("result", false);
//		json.put(AUTH_ID, authId);
		setRequestAttribute("response", json.toString());
		forwardAuthPage("/crossDomainFrame.vsp");
	}
	
	private void querySetAuth(){
		ParameterParser parameter = getParameterParser();
		HeaderParser requestHeader = getRequestHeader();
		String url=requestHeader.getAddressBar(isSsl());
		int pos=url.indexOf("?");
		if(pos<0){
			//��������
		}
		url=url.substring(0,pos);
		JSONObject json=new JSONObject();
		json.put("authUrl",config.getAuthUrl());
		String pathOnceId=parameter.getParameter("pathOnceId");
		String cookiePath = requestHeader.getPath();
		String cookieDomain =requestHeader.getServer().toString();
		StringBuffer appId=new StringBuffer();
		SessionId secondaryId=authorizer.createSecondarySetCookieStringFromPathOnceId(
				pathOnceId,url,null,isSsl(),cookieDomain,cookiePath,appId);
		if(secondaryId!=null){
			String setCookieString =secondaryId.getSetCookieString();
			setCookie(setCookieString);
			json.put("result", true);
			json.put(APP_ID,appId.toString());
		}else{//primary���L������Ȃ��Ȃ�����
			json.put("result", false);
			json.put("reason", "lost session");
		}
		setRequestAttribute("response", json.toString());
		forwardAuthPage("/crossDomainFrame.vsp");
	}
	
	/* ����Ȃ��͂� */
	private void queryAuth(){
		ParameterParser parameter = getParameterParser();
		HeaderParser requestHeader = getRequestHeader();
		String url=requestHeader.getAddressBar(isSsl());
		int pos=url.indexOf("?");
		if(pos<0){
			//��������
		}
		url=url.substring(0,pos);
		JSONObject json=new JSONObject();
//		String callback=parameter.getParameter("callback");
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		String pathOnceId=parameter.getParameter("pathOnceId");
		if(pathOnceId==null || cookieId==null || authorizer.isTemporaryId(cookieId,url)==false){//pathId,cookieId�����ꂩ�����݂��Ȃ�
			json.put("result", false);
			responseJson(json);
			return;
		}
		String cookiePath = requestHeader.getPath();
		String cookieDomain =requestHeader.getServer().toString();
		StringBuffer appId=new StringBuffer();
		SessionId secondaryId= authorizer.createSecondarySetCookieStringFromPathOnceId(
				pathOnceId,url,null,
				isSsl(),cookieDomain,cookiePath,
				appId);
		if(secondaryId!=null){
			String setCookieString =secondaryId.getSetCookieString();
			setCookie(setCookieString);
			json.put("result", true);
			json.put(APP_ID,appId.toString());//javascript���ŃZ�V���������ʂ���ID
		}else{
			json.put("result", false);
		}
		responseJson(json);
	}
	
	//TODO xxx
	private void user(String cookieId){
		ParameterParser parameter = getParameterParser();
		String isAjax=parameter.getParameter("isAjax");
		User user=authorizer.getUserByPrimaryId(cookieId);
		List<String> userRoles=null;
		JSONObject userJson=null;
		if(user!=null){
			userRoles=user.getRolesList();
			userJson=(JSONObject)user.toJson();
		}else{
			userRoles=null;
			userJson=new JSONObject();
		}
		//���ڎg����WebURL���
		List<Mapping> allowMappings=config.getMapper().getRoleWebMappings(userRoles);
		JSONArray allowUrls=new JSONArray();
		for(Mapping allowMapping:allowMappings){
			String notes=allowMapping.getNotes();
			String url=allowMapping.calcSourceUrl();
			JSONObject urlJson=new JSONObject();
			urlJson.put("notes", notes);
			urlJson.put("url", url);
			allowUrls.add(urlJson);
		}
		userJson.put("allowUrls", allowUrls);
		if("true".equals(isAjax)){
			responseJson(userJson);
		}else{
			setRequestAttribute("response", userJson.toString());
			forwardAuthPage("/crossDomainFrame.vsp");
		}
	}
	
	public void startResponseReqBody() {
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		keepAliveContext.setKeepAlive(false);
		//keepAlive�����Ȃ�,IE�́A����̃|�[�g��http��https�����Ƃ������ȓ��������邽��
		
		String queryAuth=(String)getRequestAttribute(QUERY_AUTH_MARK);
		if(QUERY_AUTH_CHECK.equals(queryAuth)){
			queryAuthCheck();
			return;
		}else if(QUERY_SETAUTH_AUTH.equals(queryAuth)){
			querySetAuth();
			return;
		}
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		HeaderParser requestHeader = getRequestHeader();
		ParameterParser parameter = getParameterParser();
		
		//1)����mapping���g�����Ƃ������F���K�v�������̂�dispatch���ꂽ�ꍇ
		//2)�F���I������secondaryId���쐬����ꍇ
		String authMark=(String)getRequestAttribute(AUTHORIZE_MARK);
		if(authMark!=null){
			//�F��(authorize)����
			MappingResult mapping=getRequestMapping();
			String path=mapping.getResolvePath();
			String url=requestHeader.getAddressBar(isSsl());
			String cookiePath=mapping.getSourcePath();
			String cookieDomain=requestHeader.getServer().toString();
			if(authMark.equals(AUTH_CD_CHECK)){
				//secondary���Ȃ����Ƃ͔����ς�
				//AUTH_AUTHORIZE�Ƀ��_�C���N�g
				crossDomainCheck(false,requestHeader, mapping);
				return;
			}else if(authMark.equals(AUTH_CD_WS_CHECK)){
				crossDomainCheck(true,requestHeader, mapping);
			}else if(authMark.equals(AUTH_CD_SET)){
				String authId=parameter.getParameter("pathOnceId");
				crossDomainSet(false,requestHeader, mapping,authId,cookieId);
				return;
			}else if(authMark.equals(AUTH_CD_WS_SET)){
				String authId=parameter.getParameter("pathOnceId");
				crossDomainSet(true,requestHeader, mapping,authId,cookieId);
				return;
			}
			if(AJAX_SETAUTH_PATH.equals(path)){//WEB && POST
				url=url.substring(0, url.length()-AJAX_SETAUTH_PATH.length());
				String pathId=parameter.getParameter("pathOnceId");
				StringBuffer appId=new StringBuffer();
				SessionId secondaryId= authorizer.createSecondarySetCookieStringFromPathOnceId(
						pathId,url,mapping.getMapping(),
						isSsl(),cookieDomain,cookiePath,
						appId);
				JSONObject json=new JSONObject();
				if(secondaryId!=null){
					String setCookieString = secondaryId.getSetCookieString();
					setCookie(setCookieString);
					json.put("result", true);
					json.put(APP_ID,appId.toString());//javascript���ŃZ�V���������ʂ���ID
				}else{
					json.put("result", false);
				}
				responseJson(json);
			}else{
				secondaryAuthorize(requestHeader,mapping,cookieId);
			}
			return;
		}
		
		String query=requestHeader.getQuery();
		if(query!=null && query.startsWith(QUERY_CD_AUTHORIZE)){
			String authId=parameter.getParameter(AUTH_ID);
			String originUrl=parameter.getParameter("originUrl");//TODO
			crossDomainAuthorize(cookieId,authId,originUrl);
			return;
		}
		
		//AUTH_AUTHORIZE�̏ꍇ
		//primary��T���Ă����PathOnceId���쐬����AUTH_SET��redirect
		//primary���Ȃ����,primary���Ȃ��|�̃��X�|���X�A���̂Ƃ��߂���URL��AuthId�ɖ��ߍ���
		//checkSessionAuth(String cookieId,String aplAuthUrl,String originUrl,JSONObject res)
		//�̏���
		
		//���ڌĂяo���ꂽ�ꍇ
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		//authFrame���̃R���e���c����
		if(path.endsWith(".html")||path.endsWith(".vsp")||path.endsWith(".vsf")){
			forwardAuthPage(path);
			return;
		}
		
//		String callback=parameter.getParameter("callback");
		/**
		 * digest,basic�F�؂̒P���ȃ��J�j�Y���ł�logoff�������ł��Ȃ��B
		 * �F�ؒ����authentication�w�b�_��j��+cookie�t����logoff������
		 * Phase1:Authentication�w�b�_���g���ĔF�؁Ascript��ԋp
		 * Phase2:script����̃��N�G�X�g��Authentication�w�b�_���_�~�[���[�U�ɐݒ�
		 */
		if(CLEANUP_AUTH_HEADER_PATH.equals(path)){
			cleanupAuthHeader();
			return;
		}else if(CHECK_SESSION_PATH.equals(path)){
			checkSession(cookieId);
			return;
		}else if(USER_PATH.equals(path)){
			user(cookieId);
			return;
		}else if(AJAX_PATHONCEID_PATH.equals(path)){
			String authId=parameter.getParameter(AUTH_ID);
			String url = authorizer.getUrlFromTemporaryId(authId);
			if(url==null){
				responseJson(false);
				return;
			}
			SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url,cookieId);
			if(pathOnceId==null){//cookieId�������ȏꍇ�܂�primarySession���Ȃ��ꍇ
				responseJson(false);
			}else{
				responseJson(pathOnceId.getId());
			}
			return;
		}else if(AUTHENTICATE_PATH.equals(path)){
			
		}else if(LOGOUT_PATH.equals(path)){
			authorizer.logout(cookieId);
			redirectLogout();
			return;
		}
		
		/*
		 *  /ajaxAuthenticate ... ajax����F�؍ς݂ł��鎖���m�F
		 *  /authenticate ... �F�؍ς݂ł��鎖���m�F
		 *  /authorize ... �K�v������ΔF�؉�ʂ��o��
		 *  1)�F�؍ς݂ł���΁A/authenticate�Ɠ���
		 *  2)�F�؏�񂪐�������?
		 *  3)no -> forwardWebAuthenication
		 *  4)yes , basic,digest�̏ꍇ=> cleanupAuth.vsp��ԋp
		 *  5)yes , form�n�̏ꍇ =>��url�Ƀ��_�C���N�g
		 *  /logout ... logout
		 *  /cleanupAuthHeader ... �F�،�Aauth�w�b�_���N���A
		 *  1)�F�؏�񂪐�������?
		 *  2)yes -> forwardWebAuthenication
		 *  3)no -> cookieOnceId����authSession�����o���APrimaryId���쐬
		 *  4)setCookie:primaryId
		 *  5)200 ��url��json�`���ŕԋp
		 * �@�@???.html, ???.vsp , ???.vsf
		*/
		
		//�F��(authenticate)����
		boolean isAjaxAuth=(path.indexOf(AuthHandler.AJAX_PATHONCEID_PATH)>=0);//TODO,�s���S,path��񂪂���̂ŁA�I�[��v�ł͂���
		String authId=parameter.getParameter(AUTH_ID);
//		String url = authorizer.getUrlFromCookieOnceAuthId(authId);
		if(authId==null){//authId���t������Ă��Ȃ�,�P�ɔF�؂����߂Ă����ꍇ
			SessionId temporaryId = authorizer.createTemporaryId(null,authorizer.getAuthPath());
			authId=temporaryId.getAuthId();
		}
		loginRedirect(cookieId,isAjaxAuth,authId);
	}
}
