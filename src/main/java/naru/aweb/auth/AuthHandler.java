package naru.aweb.auth;

import java.net.MalformedURLException;
import java.net.URL;

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
	public static String AJAX_USER_PATH="/ajaxUser";//ajax����login���[�U��₢���킹��API
	public static String AJAX_PATHONCEID_PATH="/ajaxPathOnceId";//path������ŏI����ajax����̔F��
	public static String AJAX_SETAUTH_PATH="/ajaxSetAuth";//
	public static String AJAX_AUTHID_PATH="/ajaxAuthId";//�F�ς݂Ŗ����ꍇ�AauthId��ԋp
	private static String CLEANUP_AUTH_HEADER_PATH="/cleanupAuthHeader";//auth header�폜�ppath
	private static String AJAX_CHECK_SESSION_PATH="/ajaxCheckSession";//webSocket,ajax api�F�؂��s���ꍇ�Ăяo���O�ɂ��̃p�X��iframe�ɕ\��
	private static String LOGOUT_PATH="/logout";//logout�p
	private static String AUTH_PAGE_FILEPATH="/auth/";
	private static String AUTH_ID="authId";//...temporaryId�̕ʖ�

	public static String QUERY_AUTH_MARK="queryAuthMark";//query��auth�Ăяo�����w�肳�ꂽ�ꍇ
	public static String QUERY_AUTH_CHECK="check";
	public static String QUERY_AUTH_AUTH="auth";
	public static String QUERY_SETAUTH_AUTH="setAuth";
	
	//admin/auth�z���̃R���e���c��ԋp����B
	public void forwardAuthPage(String fileName){
		MappingResult mapping=getRequestMapping();
		mapping.setResolvePath(AUTH_PAGE_FILEPATH + fileName);
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
	
	private void loginRedirect(String cookieId,boolean isAjaxAuth,String authId,String callback){
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
			responseJson(json,callback);
			return;
		}
		//����url�ɖ߂�Ƃ���
		setHeader(HeaderParser.LOCATION_HEADER, encodeUrl);
		completeResponse("302");
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
		Mapping m=mapping.getMapping();
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
		String setCookieString=authorizer.createSecondarySetCookieStringFromPathOnceId(pathId,url,m,isSsl(),cookieDomain,cookiePath,null);
		if(setCookieString==null){
			forbidden("fail to authorize.");
			return;
		}
		redirect(url, setCookieString);
	}
	
	private boolean isMatchProxy(Mapper mapper,String authUrl,String originUrl){
		boolean isSsl=false;
		String host=null;
		int port=-1;
		Mapping.SourceType sourceType=Mapping.SourceType.PROXY;
		try {
			URL url=new URL(authUrl);
			String prot=url.getProtocol();
			if("https".equals(prot)){
				sourceType=Mapping.SourceType.PROXY;
				isSsl=true;
			}else if("wss".equals(prot)){
				sourceType=Mapping.SourceType.WS_PROXY;
				isSsl=true;
			}else if("http".equals(prot)){
				isSsl=false;
				sourceType=Mapping.SourceType.PROXY;
			}else if("ws".equals(prot)){
				isSsl=false;
				sourceType=Mapping.SourceType.WS_PROXY;
			}
			host=url.getHost();
			port=url.getPort();
			if(port<0){
				if(isSsl){
					port=443;
				}else{
					port=80;
				}
			}
		} catch (MalformedURLException e) {
			logger.warn("authURL format error."+authUrl,e);
			return false;
		}
		if( mapper.isMappingAllowProxyDomain(sourceType,isSsl, host, port,originUrl) ){
			return true;
		}
		return false;
	}
	private boolean isMatchWebWs(Mapper mapper,String protocol,String authPath,String originUrl){
		boolean isSsl=false;
		if("https:".equals(protocol)){
			isSsl=true;
		}
		if(mapper.isMappingAllowWebPath(isSsl, authPath,originUrl) ){
			return true;
		}
		return false;
	}
	
	private void checkSession(String cookieId){
		JSONObject res=new JSONObject();
		ParameterParser parameter = getParameterParser();
		String protocol=parameter.getParameter("protocol");
		String authUrl=parameter.getParameter("authUrl");
		String originUrl=parameter.getParameter("originUrl");
		//TODO authUrl��,mapping�Ώۂ��ۂ�?
		Mapper mapper=config.getMapper();
		if("proxy".equals(protocol)){
			//proxy�Ƃ��ă}�b�`���邩�H
			if( !isMatchProxy(mapper,authUrl,originUrl) ){
				//������Ȃ�origin����̃A�N�Z�X
				logger.warn("not allow proxy url."+authUrl);
				res.put("result", "ng");
				responseJson(res);
				return;
			}
			if(!authUrl.endsWith("/")){
				authUrl=authUrl+"/";
			}
		}else{
			//web ws�Ƃ��ă}�b�`���邩�H
			if(!isMatchWebWs(mapper,protocol, authUrl,originUrl) ){
				//������Ȃ�origin����̃A�N�Z�X
				logger.warn("not allow web path."+authUrl);
				res.put("result", "ng");
				responseJson(res);
				return;
			}
			StringBuffer sb=new StringBuffer();
			sb.append(protocol);
			sb.append("//");
			sb.append(config.getSelfDomain());
			sb.append(":");
			sb.append(config.getInt(Config.SELF_PORT));
			sb.append(authUrl);
			authUrl=sb.toString();
		}
		
		StringBuffer appId=new StringBuffer();
		int rc=authorizer.checkSecondarySessionByPrimaryId(cookieId,authUrl,appId);
		switch(rc){
		case Authorizer.CHECK_SECONDARY_OK:
			res.put("result", "secondary");
			res.put(APP_ID, appId.toString());
			break;
		case Authorizer.CHECK_PRIMARY_ONLY:
			SessionId pathOnceSession=authorizer.createPathOnceIdByPrimary(authUrl, cookieId);
			if(pathOnceSession!=null){
				res.put("result", "primary");
				res.put("pathOnceId", pathOnceSession.getId());
			}else{
				res.put("result", "ng");
			}
			break;
		case Authorizer.CHECK_NO_PRIMARY:
			SessionId tempraryId=authorizer.createTemporaryId(originUrl,null,true);
			res.put("result", "redirectAuth");
			res.put("authId", tempraryId.getAuthId());
			break;
		}
		String callback=parameter.getParameter("callback");
		responseJson(res,callback);
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
		ParameterParser parameter = getParameterParser();
		String callback=parameter.getParameter("callback");
		
		JSONObject json=new JSONObject();
		HeaderParser requestHeader = getRequestHeader();
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		//�L����secondarysession�����邩�H
		//����΁Aresult:true�Ń��X�|���X
		if(cookieId!=null){
			if(authorizer.isSecondaryId(cookieId)){
				json.put("result", true);
				responseJson(json,callback);
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
		SessionId temporaryId = authorizer.createTemporaryId(url,requestHeader.getPath());
		String setCookieString = temporaryId.getSetCookieString();
		setCookie(setCookieString);
		String authId=temporaryId.getAuthId();
		json.put("result", false);
		json.put(AUTH_ID, authId);
		responseJson(json,callback);
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
		String pathOnceId=parameter.getParameter("pathOnceId");
		String cookiePath = requestHeader.getPath();
		String cookieDomain =requestHeader.getServer().toString();
		StringBuffer appId=new StringBuffer();
		String setCookieString = authorizer.createSecondarySetCookieStringFromPathOnceId(
				pathOnceId,url,null,isSsl(),cookieDomain,cookiePath,appId);
		if(setCookieString!=null){
			setCookie(setCookieString);
			json.put("result", true);
			json.put(APP_ID,appId.toString());
		}else{
			json.put("result", false);
		}
		setRequestAttribute("response", json.toString());
		forwardAuthPage("crossDomainFrame.vsp");
	}
	
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
		String callback=parameter.getParameter("callback");
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		String pathOnceId=parameter.getParameter("pathOnceId");
		if(pathOnceId==null || cookieId==null || authorizer.isTemporaryId(cookieId,url)==false){//pathId,cookieId�����ꂩ�����݂��Ȃ�
			json.put("result", false);
			responseJson(json,callback);
			return;
		}
		String cookiePath = requestHeader.getPath();
		String cookieDomain =requestHeader.getServer().toString();
		StringBuffer appId=new StringBuffer();
		String setCookieString = authorizer.createSecondarySetCookieStringFromPathOnceId(
				pathOnceId,url,null,
				isSsl(),cookieDomain,cookiePath,
				appId);
		if(setCookieString!=null){
			setCookie(setCookieString);
			json.put("result", true);
			json.put(APP_ID,appId.toString());//javascript���ŃZ�V���������ʂ���ID
		}else{
			json.put("result", false);
		}
		responseJson(json,callback);
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
		}else if(QUERY_AUTH_AUTH.equals(queryAuth)){
			queryAuth();
			return;
		}
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		HeaderParser requestHeader = getRequestHeader();
		ParameterParser parameter = getParameterParser();
		
		//1)����mapping���g�����Ƃ������F���K�v�������̂�dispatch���ꂽ�ꍇ
		//2)�F���I������secondaryId���쐬����ꍇ
		if(getRequestAttribute(AUTHORIZE_MARK)!=null){
			//�F��(authorize)����
			MappingResult mapping=getRequestMapping();
			String path=mapping.getResolvePath();
			String url=requestHeader.getAddressBar(isSsl());
			String cookiePath=mapping.getSourcePath();
			String cookieDomain=requestHeader.getServer().toString();
			if(AJAX_AUTHID_PATH.equals(path)){//WEB && POST
				url=url.substring(0, url.length()-AJAX_AUTHID_PATH.length());
				SessionId temporaryId = authorizer.createTemporaryId(url,cookiePath);
				String setCookieString = temporaryId.getSetCookieString();
				setCookie(setCookieString);
				String authId=temporaryId.getAuthId();
				JSONObject json=new JSONObject();
				json.put("result", false);
				json.put(AUTH_ID, authId);
				responseJson(json);
			}else if(AJAX_SETAUTH_PATH.equals(path)){//WEB && POST
				url=url.substring(0, url.length()-AJAX_SETAUTH_PATH.length());
				String pathId=parameter.getParameter("pathOnceId");
				StringBuffer appId=new StringBuffer();
				String setCookieString = authorizer.createSecondarySetCookieStringFromPathOnceId(
						pathId,url,mapping.getMapping(),
						isSsl(),cookieDomain,cookiePath,
						appId);
				JSONObject json=new JSONObject();
				if(setCookieString!=null){
//					String setCookieString = Cookie.formatSetCookieHeader(SessionId.SESSION_ID,secondaryId,null, cookiePath,-1);
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
		
		//���ڌĂяo���ꂽ�ꍇ
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		//authFrame���̃R���e���c����
		if(path.endsWith(".html")||path.endsWith(".vsp")||path.endsWith(".vsf")){
			forwardAuthPage(path);
			return;
		}
		
		String callback=parameter.getParameter("callback");
		/**
		 * digest,basic�F�؂̒P���ȃ��J�j�Y���ł�logoff�������ł��Ȃ��B
		 * �F�ؒ����authentication�w�b�_��j��+cookie�t����logoff������
		 * 
		 * Phase1:Authentication�w�b�_���g���ĔF�؁Ascript��ԋp
		 * Phase2:script����̃��N�G�X�g��Authentication�w�b�_���_�~�[���[�U�ɐݒ�
		 */
		if(CLEANUP_AUTH_HEADER_PATH.equals(path)){
			cleanupAuthHeader();
			return;
		}else if(AJAX_CHECK_SESSION_PATH.equals(path)){
			checkSession(cookieId);
			return;
		}else if(AJAX_USER_PATH.equals(path)){
			User user=authorizer.getUserByPrimaryId(cookieId);
			if(user!=null){
				responseJson(user.toJson(),callback);
			}else{
				responseJson(null,callback);
			}
			return;
		}else if(AJAX_PATHONCEID_PATH.equals(path)){
			String authId=parameter.getParameter(AUTH_ID);
			String url = authorizer.getUrlFromTemporaryId(authId);
			if(url==null){
				responseJson(false,callback);
				return;
			}
			SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url,cookieId);
			if(pathOnceId==null){//cookieId�������ȏꍇ�܂�primarySession���Ȃ��ꍇ
				responseJson(false,callback);
			}else if(callback==null){
				responseJson(pathOnceId.getId());
			}else{
				responseJson("'"+pathOnceId.getId()+"'",callback);//TODO �Ȃ񂩕�
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
		loginRedirect(cookieId,isAjaxAuth,authId,callback);
	}
}
