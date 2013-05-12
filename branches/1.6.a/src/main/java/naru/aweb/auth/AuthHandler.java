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
import naru.aweb.mapping.MappingResult;
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
	public static String LOGIN_ID="loginId";
	public static String APP_SID="appSid";//javascript���ŃA�v���P�[�V�����Z�V���������ʂ���ID,setAuth���\�b�h���A���Ajson�̃L�[�Ƃ��Ďg�p
	public static String TOKEN="token";
	public static String AUTHORIZE_MARK="ahthorizeMark";//authorize�ړI�ŌĂяo���ꂽ�ꍇ�ɕt�������B
	public static String AUTHENTICATE_PATH="/authenticate";//�K�v������ΔF�؏������s���p�X
	public static String INFO_PATH="/info";//���[�U���,���p�\�T�[�r�X�̖₢���킹API
	public static String AUTH_FRAME_PATH="/authFrame";//���[�U���,���p�\�T�[�r�X�̖₢���킹API
	private static String CLEANUP_AUTH_HEADER_PATH="/cleanupAuthHeader";//auth header�폜�ppath
	private static String LOGOUT_PATH="/logout";//logout�p
	private static String AJAX_LOGOUT_PATH="/ajaxLogout";//ajax Logout�p
	private static String AUTH_ID="authId";//...temporaryId�̕ʖ�
	
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
			mapping.setResolvePath(fileName);
		}else{
			mapping.setResolvePath("/" +fileName);
		}
//		mapping.setDesitinationFile(config.getAdminDocumentRoot());
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
		if(setCookieString==null){
			return;
		}
		//IE��iframe����cookie��L���ɂ���w�b�_,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
		setHeader("P3P", "CP=\"CAO PSA OUR\"");
		setHeader(HeaderParser.SET_COOKIE_HEADER, setCookieString);
	}
	
	private void redirect(String location,String setCookieString) {
		setCookie(setCookieString);
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	
	private void redirect(String location) {
		redirect(location,null);
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
	
	private void loginRedirect(String cookieId,String authId){
		SessionId temporaryId = authorizer.getTemporaryId(authId);
		if(temporaryId==null){
			completeResponse("403", "authentication error");
			return;
		}
		String url=temporaryId.getUrl();
		SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, cookieId);
		if(pathOnceId==null){//cookieId�������ȏꍇ
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
		
		//role����v���Ă���Ƃ͌���Ȃ�
		AuthSession authSession=secondaryId.getAuthSession();
		if(!authorizer.authorize(m,authSession)){
			secondaryId.remove();
			response.element("result", false);
			response.element("reason", "lack of right");
			crossDomainResponse(response,null);
			return;
		}
		
		response.element("result", true);
		response.element(AUTH_ID, secondaryId.getAuthSession().getAppSid());
		response.element(TOKEN, secondaryId.getAuthSession().getToken());
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
		StringBuffer appSid=new StringBuffer();
		int rc=authorizer.checkSecondarySessionByPrimaryId(cookieId,url,appSid);
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
			//xhr�o�R�ŃZ�V�����؂ꂪ���������ꍇ�́A
			if("XMLHttpRequest".equalsIgnoreCase(requestHeader.getHeader("X-Requested-With"))){
				url=requestHeader.getHeader("Referer");
				if(url==null){
					url=requestHeader.getHeader("Origin");
				}
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
	
	private JSONObject infoObject(User user){
		JSONObject response=new JSONObject();
		response.element("result", true);
		List<String> userRoles=null;
		JSONObject userJson=null;
		if(user!=null){
			userRoles=user.getRolesList();
			userJson=(JSONObject)user.toJson();
		}else{
			userRoles=null;
			userJson=new JSONObject();
		}
		response.element("roles",userRoles);
		response.element("user",userJson);
		
		//���ڎg����WebURL���
		List<Mapping> allowMappings=config.getMapper().getRoleMappings(userRoles);
		JSONArray allowUrls=new JSONArray();
		for(Mapping allowMapping:allowMappings){
			String url=allowMapping.calcSourceUrl();
			if(url==null){
				continue;
			}
			String notes=allowMapping.getNotes();
			JSONObject urlJson=new JSONObject();
			urlJson.put("notes", notes);
			urlJson.put("url", url);
			allowUrls.add(urlJson);
		}
		response.element("allowUrls", allowUrls);
		return response;
	}
	
	//authFrame
	private void authFrame(String cookieId){
		User user=authorizer.getUserByPrimaryId(cookieId);
		JSONObject response=infoObject(user);
		setRequestAttribute("info", response.toString());
		setRequestAttribute("offlinePassHash", user.getOfflinePassHash());
		forwardAuthPage("/authFrame.vsp");
	}
	
	//info
	private void info(String cookieId){
		User user=authorizer.getUserByPrimaryId(cookieId);
		JSONObject response=infoObject(user);
		setRequestAttribute("response", response.toString());
		forwardAuthPage("/crossDomainFrame.vsp");
	}
	
	//ajaxLogout
	private void ajaxLogout(String cookieId){
		boolean result=authorizer.logout(cookieId);
		JSONObject response=new JSONObject();
		response.element("result", result);
		responseJson(response);
	}
	
	public void startResponseReqBody() {
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		keepAliveContext.setKeepAlive(false);
		//keepAlive�����Ȃ�,IE�́A����̃|�[�g��http��https�����Ƃ������ȓ��������邽��
		
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		HeaderParser requestHeader = getRequestHeader();
		ParameterParser parameter = getParameterParser();
		
		//1)����mapping���g�����Ƃ������F���K�v�������̂�dispatch���ꂽ�ꍇ
		//2)�F���I������secondaryId���쐬����ꍇ
		String authMark=(String)getRequestAttribute(AUTHORIZE_MARK);
		if(authMark!=null){
			//�F��(authorize)����
			MappingResult mapping=getRequestMapping();
			mapping.setDesitinationFile(config.getAuthDocumentRoot());
			/*
			String path=mapping.getResolvePath();
			String url=requestHeader.getAddressBar(isSsl());
			String cookiePath=mapping.getSourcePath();
			String cookieDomain=requestHeader.getServer().toString();
			*/
			if(authMark.equals(AUTH_CD_CHECK)){
				//secondary���Ȃ����Ƃ͔����ς�
				//AUTH_AUTHORIZE�Ƀ��_�C���N�g
				crossDomainCheck(false,requestHeader, mapping);
				return;
			}else if(authMark.equals(AUTH_CD_WS_CHECK)){
				crossDomainCheck(true,requestHeader, mapping);
				return;
			}else if(authMark.equals(AUTH_CD_SET)){
				String authId=parameter.getParameter("pathOnceId");
				crossDomainSet(false,requestHeader, mapping,authId,cookieId);
				return;
			}else if(authMark.equals(AUTH_CD_WS_SET)){
				String authId=parameter.getParameter("pathOnceId");
				crossDomainSet(true,requestHeader, mapping,authId,cookieId);
				return;
			}
			secondaryAuthorize(requestHeader,mapping,cookieId);
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
//		}else if(CHECK_SESSION_PATH.equals(path)){
//			checkSession(cookieId);
//			return;
		}else if(AUTH_FRAME_PATH.equals(path)){
			authFrame(cookieId);
			return;
		}else if(INFO_PATH.equals(path)){
			info(cookieId);
			return;
		}else if(AJAX_LOGOUT_PATH.equals(path)){
			ajaxLogout(cookieId);
			return;
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
//		boolean isAjaxAuth=(path.indexOf(AuthHandler.AJAX_PATHONCEID_PATH)>=0);//TODO,�s���S,path��񂪂���̂ŁA�I�[��v�ł͂���
		String authId=parameter.getParameter(AUTH_ID);
//		String url = authorizer.getUrlFromCookieOnceAuthId(authId);
		if(authId==null){//authId���t������Ă��Ȃ�,�P�ɔF�؂����߂Ă����ꍇ
			if(authorizer.getUserByPrimaryId(cookieId)!=null){//login�����`�F�b�N
				//����login���Ȃ̂ŔF�؏����͕K�v�Ȃ�
				redirect(config.getPublicWebUrl());
				return;
			}
			SessionId temporaryId = authorizer.createTemporaryId(null,authorizer.getAuthPath());
			authId=temporaryId.getAuthId();
		}
		loginRedirect(cookieId,authId);
	}
}
