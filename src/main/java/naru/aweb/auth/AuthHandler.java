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
 * 設定項目
 * 1)セションタイムアウト時間
 * 2)logout後のurl
 * 3)認証方式[basic|digest|basicForm|digestForm]
 * 
 * @author naru
 *
 */
public class AuthHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(AuthHandler.class);
	private static Config config = Config.getConfig();
	private static Authenticator authenticator = config.getAuthenticator();
	private static Authorizer authorizer=config.getAuthorizer();
	public static String APP_ID="appId";//javascript側でセションを識別するID,setAuthメソッド復帰情報、jsonのキーとして使用
	public static String AUTHORIZE_MARK="ahthorizeMark";//authorize目的で呼び出された場合に付加される。
	public static String AUTHENTICATE_PATH="/authenticate";//必要があれば認証処理も行うパス
	public static String USER_PATH="/user";//loginユーザおよび使えるWebURLを問い合わせるAPI
	public static String AJAX_PATHONCEID_PATH="/ajaxPathOnceId";//pathがこれで終わればajaxからの認可
	public static String AJAX_SETAUTH_PATH="/ajaxSetAuth";//
//	public static String AJAX_AUTHID_PATH="/ajaxAuthId";//認可済みで無い場合、authIdを返却
	private static String CLEANUP_AUTH_HEADER_PATH="/cleanupAuthHeader";//auth header削除用path
	private static String CHECK_SESSION_PATH="/checkSession";//webSocket,ajax api認証を行う場合呼び出す前にこのパスをiframeに表示
	private static String LOGOUT_PATH="/logout";//logout用
	private static String AUTH_PAGE_FILEPATH="/auth";
	private static String AUTH_ID="authId";//...temporaryIdの別名

	public static String QUERY_AUTH_MARK="queryAuthMark";//queryでauth呼び出しが指定された場合
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
	
	
	//admin/auth配下のコンテンツを返却する。
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
		//IEでiframe内のcookieを有効にするヘッダ,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
		setHeader("P3P", "CP=\"CAO PSA OUR\"");
		setHeader(HeaderParser.SET_COOKIE_HEADER, setCookieString);
	}
	
	private void redirect(String location,String setCookieString) {
		setCookie(setCookieString);
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
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
		if(temporaryId.isDirectUrl()){
			//directUrlの場合は即座にredirectする
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
		//元のurlに戻るところ
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
		
		//pathIdがあるか？cookieIdがあるか？有効か？
		if(pathId==null || cookieId==null || authorizer.isTemporaryId(cookieId,aplUrl)==false){//pathId,cookieIdいずれかが存在しない
			response.element("result", false);
			response.element("reason", "lack of set auth");
			crossDomainResponse(response,null);
			return;
		}
		//有効なPathOnceIdが付加されているか、権限はあるか？
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
		case Authorizer.CHECK_SECONDARY_OK://これはないはず
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
			//認証が完了した際に元のURLに戻るためtempraryIdにoriginUrlを保存する,この場合originUrlは関係のない別ドメインの可能性あり
			SessionId tempraryId=authorizer.createTemporaryId(originUrl,null,true);
			response.element("result", "redirectForAuthorizer");
			response.element("location", config.getAuthUrl()+"?authId="+tempraryId.getAuthId());
//			response.put("authId", tempraryId.getAuthId());
			break;
		}
		crossDomainResponse(response,null);
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
		String cookieDomain =requestHeader.getServer().toString();
		if (requestHeader.isProxy()) {
			cookiePath = "/";
		} else {
			cookiePath = mapping.getSourcePath();
		}
		//pathIdがあるか？cookieIdがあるか？有効か？
		if(pathId==null || cookieId==null || authorizer.isTemporaryId(cookieId,url)==false){//pathId,cookieIdいずれかが存在しない
			// redirect /auth
			if (url.endsWith(cookiePath) && !url.endsWith("/")) {
				url = url + "/";// 戻ってきた際には、/xxx;phId=xxxとなるが、chromeではcookieonceが付加されない
			}
			SessionId temporaryId = authorizer.createTemporaryId(url,cookiePath);
			String setCookieString = temporaryId.getSetCookieString();
			String authId=temporaryId.getAuthId();
			redirectAuth(authId, setCookieString);
			return;
		}
		
		//有効なPathOnceIdが付加されているか、権限はあるか？
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
	 * 復帰は３種類考えられる
	 * 1)認証必要なし
	 * 2)認証あり、matchする
	 * 3)認証あり、matchしない
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
		
		//authUrlの構築、wsの場合でも認証要求は、httpを使う(cookieを付加するのが目的)
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
		//TODO ドメインのチェックもすべき
		return mapper.checkCrossDomainWebWs(sourceType,isSsl, authPath,originUrl);
	}
	
	private String checkSessionProxyAuthUrl(String protocol,String authDomain,String originUrl,JSONObject res){
		//proxyとしてマッチするか？
		Mapper mapper=config.getMapper();
		StringBuffer authUrlSb=new StringBuffer();
		switch(checkCrossDomainProxy(mapper,protocol,authDomain,originUrl,authUrlSb)){
		case Mapper.CHECK_MATCH_NO_AUTH://認証の必要がない
			res.put("result", "secondary");
			res.put(APP_ID, "NEED_NOT_AUTH");
			return null;
		case Mapper.CHECK_NOT_MATCH:
			//許されないoriginからのアクセス
			logger.warn("not allow proxy url."+authDomain);
			res.put("result", "false");
			res.put("reason", "not allow proxy url");
			responseJson(res);
			return null;
		}
		return authUrlSb.toString();
	}
	
	private String checkSessionWebWsAuthUrl(String protocol,String authDomain,String authPath,String originUrl,JSONObject res){
		//TODO Web Wsの分離,
		//web wsとしてマッチするか？
		Mapper mapper=config.getMapper();
		switch(checkCrossDomainWebWs(mapper,protocol,authPath,originUrl)){
		case Mapper.CHECK_MATCH_NO_AUTH://認証の必要がない
			res.put("result", "secondary");
			res.put(APP_ID, "NEED_NOT_AUTH");
			return null;
		case Mapper.CHECK_NOT_MATCH:
			//許されないoriginからのアクセス
			logger.warn("not allow web url."+authPath);
			res.put("result", "false");
			res.put("reason", "not allow web url");
			return null;
		}
		//TODO ドメインのチェックをした場合は、ドメインを設定する
		//現状WSをmainHost以外で動作させると認証できない
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
	 * 認証の状態をチェックする
	 * CHECK_SECONDARY_OK: Primaryは認証済みだが当該URLには未認証
	 * CHECK_PRIMARY_ONLY:　当該URLに対して既に認証済み
	 * CHECK_NO_PRIMARY: Primary認証未実施
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
			//認証が完了した際に元のURLに戻るためtempraryIdにoriginUrlを保存する
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
		//TODO authUrlが,mapping対象か否か?
		JSONObject res=new JSONObject();
		res.put("authUrl",config.getAuthUrl());
		if("proxy".equals(sourceType)){
			//proxyとしてマッチするか？
			aplAuthUrl=checkSessionProxyAuthUrl(protocol, authDomain, originUrl, res);
		}else{
			//web wsとしてマッチするか？
			aplAuthUrl=checkSessionWebWsAuthUrl(protocol, authDomain, authPath, originUrl, res);
		}
		/* authUrl=null:認証をチェックする必要がない、urlが不当 or 認証を必要としないurl */
		if(aplAuthUrl!=null){
			//認証チェック
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
		//TODO 排他
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
			if(temporaryId.isDirectUrl()){//認証を必要としないページに戻る場合
				completeResponse("200", url);
			}else{
				SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, primaryId.getId());
				completeResponse("200", pathOnceId.encodeUrl());
			}
		}else{//単に認証だけした場合
			temporaryId.remove();
			completeResponse("200", config.getPublicWebUrl());
		}
	}
	
	private void queryAuthCheck(){
		JSONObject json=new JSONObject();
		json.put("authUrl",config.getAuthUrl());
		HeaderParser requestHeader = getRequestHeader();
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		//有効なsecondarysessionがあるか？
		//あれば、result:trueでレスポンス
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
			//内部矛盾
		}
		url=url.substring(0,pos);
		
		//AUTH_IDをレスポンス
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
			//内部矛盾
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
		}else{//primaryが有効じゃなくなった等
			json.put("result", false);
			json.put("reason", "lost session");
		}
		setRequestAttribute("response", json.toString());
		forwardAuthPage("/crossDomainFrame.vsp");
	}
	
	/* いらないはず */
	private void queryAuth(){
		ParameterParser parameter = getParameterParser();
		HeaderParser requestHeader = getRequestHeader();
		String url=requestHeader.getAddressBar(isSsl());
		int pos=url.indexOf("?");
		if(pos<0){
			//内部矛盾
		}
		url=url.substring(0,pos);
		JSONObject json=new JSONObject();
//		String callback=parameter.getParameter("callback");
		String cookieId=(String)getRequestAttribute(SessionId.SESSION_ID);
		String pathOnceId=parameter.getParameter("pathOnceId");
		if(pathOnceId==null || cookieId==null || authorizer.isTemporaryId(cookieId,url)==false){//pathId,cookieIdいずれかが存在しない
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
			json.put(APP_ID,appId.toString());//javascript側でセションを識別するID
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
		//直接使えるWebURLを列挙
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
		//keepAliveさせない,IEは、同一のポートでhttpとhttpsをやるとおかしな動きをするため
		
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
		
		//1)他のmappingを使おうとしたが認可が必要だったのでdispatchされた場合
		//2)認可が終了してsecondaryIdを作成する場合
		String authMark=(String)getRequestAttribute(AUTHORIZE_MARK);
		if(authMark!=null){
			//認可(authorize)処理
			MappingResult mapping=getRequestMapping();
			String path=mapping.getResolvePath();
			String url=requestHeader.getAddressBar(isSsl());
			String cookiePath=mapping.getSourcePath();
			String cookieDomain=requestHeader.getServer().toString();
			if(authMark.equals(AUTH_CD_CHECK)){
				//secondaryがないことは判明済み
				//AUTH_AUTHORIZEにリダイレクト
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
					json.put(APP_ID,appId.toString());//javascript側でセションを識別するID
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
		
		//AUTH_AUTHORIZEの場合
		//primaryを探してあればPathOnceIdを作成してAUTH_SETにredirect
		//primaryがなければ,primaryがない旨のレスポンス、このとき戻り先のURLをAuthIdに埋め込む
		//checkSessionAuth(String cookieId,String aplAuthUrl,String originUrl,JSONObject res)
		//の処理
		
		//直接呼び出された場合
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		//authFrame等のコンテンツ処理
		if(path.endsWith(".html")||path.endsWith(".vsp")||path.endsWith(".vsf")){
			forwardAuthPage(path);
			return;
		}
		
//		String callback=parameter.getParameter("callback");
		/**
		 * digest,basic認証の単純なメカニズムではlogoffを実現できない。
		 * 認証直後にauthenticationヘッダを破壊+cookie付加でlogoffを実現
		 * Phase1:Authenticationヘッダを使って認証、scriptを返却
		 * Phase2:scriptからのリクエストでAuthenticationヘッダをダミーユーザに設定
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
			if(pathOnceId==null){//cookieIdが無効な場合つまりprimarySessionがない場合
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
		if(authId==null){//authIdが付加されていない,単に認証を求めてきた場合
			SessionId temporaryId = authorizer.createTemporaryId(null,authorizer.getAuthPath());
			authId=temporaryId.getAuthId();
		}
		loginRedirect(cookieId,isAjaxAuth,authId);
	}
}
