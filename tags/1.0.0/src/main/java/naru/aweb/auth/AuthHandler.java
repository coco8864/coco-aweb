package naru.aweb.auth;

import java.util.Map;

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import net.sf.json.JSONObject;

/**
 * 設定項目
 * 1)セションタイムアウト時間
 * 2)logout後のurl
 * 3)認証方式[basic|digest|basicForm|digestForm]
 * 
 * @author naru
 *
 */

public class AuthHandler extends WebServerHandler {
	private static Config config = Config.getConfig();
	private static Authenticator authenticator = config.getAuthenticator();
	private static Authorizer authorizer=config.getAuthorizer();
	public static String AUTHORIZE_MARK="ahthorizeMark";//authorize目的で呼び出された場合に付加される。
	public static String AUTHENTICATE_PATH="/authenticate";//必要があれば認証処理も行うパス
	public static String AJAX_USER_PATH="/ajaxUser";//ajaxからloginユーザを問い合わせるAPI
	public static String AJAX_PATHONCEID_PATH="/ajaxPathOnceId";//pathがこれで終わればajaxからの認可
	public static String AJAX_SETAUTH_PATH="/ajaxSetAuth";//
	public static String AJAX_AUTHID_PATH="/ajaxAuthId";//認可済みで無い場合、authIdを返却
	private static String CLEANUP_AUTH_HEADER_PATH="/cleanupAuthHeader";//auth header削除用path
	private static String LOGOUT_PATH="/logout";//logout用
	private static String AUTH_PAGE_FILEPATH="/auth/";
	private static String AUTH_ID="authId";//...temporaryIdの別名
	
	//admin/auth配下のコンテンツを返却する。
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
		//IEでiframe内のcookieを有効にするヘッダ,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
		setHeader("P3P", "CP=\"CAO PSA OUR\"");
		setHeader(HeaderParser.SET_COOKIE_HEADER, setCookieString);
	}
	
	private void redirect(String location,String setCookieString) {
		setCookie(setCookieString);
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	
	private void ajaxRedirect(String authId,String setCookieString) {
		StringBuffer sb=new StringBuffer(authorizer.getAuthUrl());
		sb.append(AJAX_PATHONCEID_PATH);
		sb.append('?');
		sb.append(AUTH_ID);
		sb.append('=');
		sb.append(authId);
		String location = sb.toString();
		
		setCookie(setCookieString);
		
		JSONObject json=new JSONObject();
		json.put("isRedirect", true);
		json.put("location", location);
		ParameterParser parameter = getParameterParser();
		responseJson(json,parameter.getParameter("callback"));
	}
	
	/*
	 * 認証して
	 * 1)失敗したら、認証画面にforward
	 * 2)成功してcleanupが必要ならcleanup画面にforward
	 * 3)成功してcleanupが必要ないなら、AuthHandlerに処理を任せる
	 */
	private SessionId authenticate(String url,String authId){
		// authentication header or parameterで認証
		//form認証の場合、cleanupAuthPathの場合、authIdをformに埋め込む必要がある
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
		String setCookieString = primaryId.getSetCookieString(authorizer.getAuthPath(),authorizer.isAuthSsl());
		if(url==null){
			redirect(config.getPublicWebUrl(), setCookieString);//doneResponse("200","authentication success.");
			return null;
		}
		setCookie(setCookieString);
		SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, primaryId.getId());
		return pathOnceId;
	}
	
	private void loginOnly(String cookieId,boolean isAjaxAuth,String callback){
		User user=authorizer.getUserByPrimaryId(cookieId);
		if(user!=null){
			if(isAjaxAuth){
//				setHeader(HeaderParser.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
				JSONObject json=new JSONObject();
				json.put("isRedirect", false);
				json.put("result", user.toJson());
				responseJson(json,callback);
				return;
			}
			completeResponse("200", "authentication ok");
			return;
		}
		if(isAjaxAuth){
			setHeader(HeaderParser.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
			JSONObject json=new JSONObject();
			json.put("isRedirect", false);
			json.put("result", null);
			responseJson(json,callback);
			return;
		}
		authenticate(null,null);
	}
	
	private void redirectOrgUrl(SessionId pathOnceId,String authId){
		String url = authorizer.getUrlFromTemporaryId(authId);
		String encodeUrl=null;
		if(url==null){
			encodeUrl=config.getPublicWebUrl();
			pathOnceId.unref(true);//この場合pathOnceIdは必要ない
		}else{
			pathOnceId.setUrl(url);
			encodeUrl = pathOnceId.encodeUrl();
		}
		//元のurlに戻るところ
		setHeader(HeaderParser.LOCATION_HEADER, encodeUrl);
		completeResponse("302");
	}
	
	private boolean primaryAuthorize(HeaderParser requestHeader,String cookieId,String authId){
		SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(cookieId);
		if(pathOnceId==null){//cookieIdが無効な場合
			return false;
		}
		redirectOrgUrl(pathOnceId,authId);
		return true;
	}
	
	private void loginRedirect(String cookieId,boolean isAjaxAuth,String authId,String callback){
		String url = authorizer.getUrlFromTemporaryId(authId);
		SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, cookieId);
		if(pathOnceId==null){//cookieIdが無効な場合
			if(isAjaxAuth){
				completeResponse("403", "can't authentication");
				return;
			}
			pathOnceId=authenticate(url,authId);
			if( pathOnceId==null ){
				return;
			}
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
		//元のurlに戻るところ
		setHeader(HeaderParser.LOCATION_HEADER, encodeUrl);
		completeResponse("302");
	}
	
	/**
	 * 1)任意のmappingで認可が求められたが、未認可の場合
	 * 2)認可後authIdつきのurlに戻ってきた
	 * @param requestHeader
	 * @param mapping
	 * @param cookieId
	 * @param isAjaxAuth
	 */
	private void secondaryAuthorize(HeaderParser requestHeader,MappingResult mapping,String cookieId){
		// GETメソッドじゃないと新規の認証はできない
		String method=requestHeader.getMethod();
		if (!HeaderParser.GET_METHOD.equalsIgnoreCase(method) && 
			!HeaderParser.HEAD_METHOD.equalsIgnoreCase(method)) {
			forbidden("fail to authorize because of method");
			return;
		}
		// WebSocketは直接認証はできない
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
		if (requestHeader.isProxy()) {
			cookiePath = "/";
		} else {
			cookiePath = mapping.getSourcePath();
		}
		Mapping m=mapping.getMapping();
		//pathIdがあるか？cookieIdがあるか？有効か？
		if(pathId==null || cookieId==null || authorizer.isTemporaryId(cookieId,url)==false){//pathId,cookieIdいずれかが存在しない
			// redirect /auth
			if (url.endsWith(cookiePath) && !url.endsWith("/")) {
				url = url + "/";// 戻ってきた際には、/xxx;phId=xxxとなるが、chromeではcookieonceが付加されない
			}
			SessionId temporaryId = authorizer.createTemporaryId(url);
			String setCookieString = temporaryId.getSetCookieString(cookiePath, isSsl());
			String authId=temporaryId.getAuthId();
			redirectAuth(authId, setCookieString);
			return;
		}
		
		//有効なPathOnceIdが付加されているか、権限はあるか？
		String setCookieString = authorizer.createSecondaryCookieFromPathOnceId(pathId,url,m,cookiePath,isSsl());
		if(setCookieString==null){
			forbidden("fail to authorize.");
			return;
		}
		// redirect self
		// keepAliveContext.setAuthSession(secondaryId.getAuthSessionFromPrimary());
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
		//TODO 排他
		SessionId temporaryId=authorizer.getTemporaryId(authId);
		if(temporaryId==null){
			forbidden("faild to get authId");
			return;
		}
		AuthSession authSession=temporaryId.popAuthSession();
		SessionId primaryId = authorizer.createPrimaryId(authSession);
		String setCookieString = primaryId.getSetCookieString(authorizer.getAuthPath(),authorizer.isAuthSsl());
		setCookie(setCookieString);
		String url=temporaryId.getUrl();
		if(url!=null){
			SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, primaryId.getId());
			completeResponse("200", pathOnceId.encodeUrl());
		}else{//単に認証だけした場合
			temporaryId.remove();
			completeResponse("200", config.getPublicWebUrl());
		}
	}
	
	public void startResponseReqBody() {
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		keepAliveContext.setKeepAlive(false);
		//keepAliveさせない,IEは、同一のポートでhttpとhttpsをやるとおかしな動きをするため
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		HeaderParser requestHeader = getRequestHeader();
		ParameterParser parameter = getParameterParser();
		
		//1)他のmappingを使おうとしたが認可が必要だったのでdispatchされた場合
		//2)認可が終了してsecondaryIdを作成する場合
		if(getRequestAttribute(AUTHORIZE_MARK)!=null){
			//認可(authorize)処理
			MappingResult mapping=getRequestMapping();
			String path=mapping.getResolvePath();
			String url=requestHeader.getAddressBar(isSsl());
			String cookiePath=mapping.getSourcePath();
			if(AJAX_AUTHID_PATH.equals(path)){//WEB && POST
				url=url.substring(0, url.length()-AJAX_AUTHID_PATH.length());
				SessionId temporaryId = authorizer.createTemporaryId(url);
				String setCookieString = temporaryId.getSetCookieString(cookiePath, isSsl());
				setCookie(setCookieString);
				String authId=temporaryId.getAuthId();
				JSONObject json=new JSONObject();
				json.put("result", false);
				json.put(AUTH_ID, authId);
				responseJson(json);
			}else if(AJAX_SETAUTH_PATH.equals(path)){//WEB && POST
				url=url.substring(0, url.length()-AJAX_SETAUTH_PATH.length());
				String pathId=parameter.getParameter("pathOnceId");
				String setCookieString = authorizer.createSecondaryCookieFromPathOnceId(pathId,url,mapping.getMapping(),cookiePath,isSsl());
				JSONObject json=new JSONObject();
				if(setCookieString!=null){
					setCookie(setCookieString);
					json.put("result", true);
				}else{
					json.put("result", false);
				}
				responseJson(json);
			}else{
				secondaryAuthorize(requestHeader,mapping,cookieId);
			}
			return;
		}
		
		//直接呼び出された場合
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		//authFrame等のコンテンツ処理
		if(path.endsWith(".html")||path.endsWith(".vsp")||path.endsWith(".vsf")){
			forwardAuthPage(path);
			return;
		}
		
		/**
		 * digest,basic認証の単純なメカニズムではlogoffを実現できない。
		 * 認証直後にauthenticationヘッダを破壊+cookie付加でlogoffを実現
		 * 
		 * Phase1:Authenticationヘッダを使って認証、scriptを返却
		 * Phase2:scriptからのリクエストでAuthenticationヘッダをダミーユーザに設定
		 */
		if(CLEANUP_AUTH_HEADER_PATH.equals(path)){
			cleanupAuthHeader();
			return;
		}else if(AJAX_USER_PATH.equals(path)){
			User user=authorizer.getUserByPrimaryId(cookieId);
			if(user!=null){
				responseJson(user.toJson(),parameter.getParameter("callback"));
			}else{
				responseJson(null,parameter.getParameter("callback"));
			}
			return;
		}else if(AJAX_PATHONCEID_PATH.equals(path)){
			String authId=parameter.getParameter(AUTH_ID);
			String url = authorizer.getUrlFromTemporaryId(authId);
			if(url==null){
				responseJson(false,parameter.getParameter("callback"));
			}
			SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url,cookieId);
			if(pathOnceId==null){//cookieIdが無効な場合
				responseJson(false,parameter.getParameter("callback"));
			}else{
				responseJson(pathOnceId.getId(),parameter.getParameter("callback"));
			}
			return;
		}else if(AUTHENTICATE_PATH.equals(path)){
			
		}else if(LOGOUT_PATH.equals(path)){
			authorizer.logout(cookieId);
			redirectLogout();
			return;
		}
		
		/*
		 *  /ajaxAuthenticate ... ajaxから認証済みである事を確認
		 *  /authenticate ... 認証済みである事を確認
		 *  /authorize ... 必要があれば認証画面を出す
		 *  1)認証済みであれば、/authenticateと同じ
		 *  2)認証情報が正しいか?
		 *  3)no -> forwardWebAuthenication
		 *  4)yes , basic,digestの場合=> cleanupAuth.vspを返却
		 *  5)yes , form系の場合 =>元urlにリダイレクト
		 *  /logout ... logout
		 *  /cleanupAuthHeader ... 認証後、authヘッダをクリア
		 *  1)認証情報が正しいか?
		 *  2)yes -> forwardWebAuthenication
		 *  3)no -> cookieOnceIdからauthSessionを取り出し、PrimaryIdを作成
		 *  4)setCookie:primaryId
		 *  5)200 元urlをjson形式で返却
		 * 　　???.html, ???.vsp , ???.vsf
		*/
		
		//認証(authenticate)処理
		boolean isAjaxAuth=(path.indexOf(AuthHandler.AJAX_PATHONCEID_PATH)>=0);//TODO,不完全,path情報があるので、終端一致ではだめ
		String authId=parameter.getParameter(AUTH_ID);
//		String url = authorizer.getUrlFromCookieOnceAuthId(authId);
		String callback = parameter.getParameter("callback");
		if(authId==null){//authIdが付加されていない,単に認証を求めてきた場合
			SessionId temporaryId = authorizer.createTemporaryId(null);
			authId=temporaryId.getAuthId();
		}
		loginRedirect(cookieId,isAjaxAuth,authId,callback);
	}
}
