package naru.aweb.auth;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.aweb.config.User;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.handler.ServerBaseHandler.SCOPE;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;

/*
 * 注意：認証が必要ない場合もPROXY_AUTHORIZATION_HEADERやCookieヘッダを削除する必要がある。
 * 1)認証不要設定とした場合、みんなadmin
 * 2)認証要設定で、認証の必要のないURIへのWebアクセスの場合、anonymous
 * 3)認証要設定で、anonymousを許す場合、nameが"anonymous"なら何でも認証通過、
 * 
 * 注意２：nonceをsessionIDとして利用するためDIGEST認証である必要がある。Basic認証の処理は残っているが使えない
 */
public class Authenticator {
	private static final String AUTHENTICATE_LOGOUT_URL = "logoutUrl";
	private static Logger logger = Logger.getLogger(Authenticator.class);
	public static final String DUMMY_USER_NAME="PhDummyUserNameForDigestAuth";
	private static final String AUTHENTICATE_REALM = "authenticateRealm";
	private static final String AUTHENTICATE_SCHEME = "authenticateScheme";
	private static final String ADMIN_ID = "adminId";
	private static final String DIGEST_AUTHENTICATE_RANDOM_ENTOROPY = "digestAuthenticateRandomEntoropy";
	private static final String TOKEN_RANDOM_ENTOROPY = "tokenRandomEntoropy";
	public static final String NONE="None";//認証なし
	public static final String BASIC="Basic";//Basic認証
	public static final String DIGEST="Digest";//Digest認証
	public static final String BASIC_FORM="BasicForm";//Form認証
	public static final String DIGEST_FORM="DigestForm";//Form認証
	public static final String INTERNET_AUTH="InternetAuth";//facebook,google,openId認証,
	//http://d.hatena.ne.jp/vividcode/20111025/1319547289 fasebook
	//https://developers.google.com/accounts/docs/OpenID?hl=ja#gsa_example google
	private static SecureRandom nonceRandom;
	private static SecureRandom tokenRandom;
	private static MessageDigest passDigest;
	private static MessageDigest digestAuthDigest;//digest認証は、MD5
	
	//loginidとUserのマップ、最新情報を保持,任意のタイミングでDBに格納すれば忘れてもよい
	private Map<String,User> loginIdUserMap=Collections.synchronizedMap(new HashMap<String,User>());
	
	static AuthSession internalCreateAuthSession(User user){
		AuthSession authSession=(AuthSession)PoolManager.getInstance(AuthSession.class);
		authSession.init(user,getNextRandom(tokenRandom));
		return authSession;
	}
	
	private static String getNextRandom(SecureRandom random){
		byte[] bytes = (byte[]) PoolManager.getArrayInstance(byte.class, 16);
		String nonce=null;
		random.nextBytes(bytes);
		nonce=DataUtil.byteToString(bytes);
		PoolManager.poolArrayInstance(bytes);
		return nonce;
	}
	
	private User admin;
//	private String logoutUrl;
//	private User dummyUser;//digest認証に使う
//	private AuthSession adminSession;
	private String scheme=null;//nullの場合、認証なし
	private String realm;
	private Object interval=null;
	
	public User getUserFromCache(String loginId){
		return loginIdUserMap.get(loginId);
	}
	public void putUserCache(User user){
		loginIdUserMap.put(user.getLoginId(),user);
	}
	public void removeUserCache(String loginId){
		loginIdUserMap.remove(loginId);
	}
	
	public User getUserByLoginId(String loginId){
		User user=getUserFromCache(loginId);
		if(user!=null){
			return user;
		}
		user=User.getByLoginId(loginId);
		if(user==null){
			return null;
		}
		return user;
	}
	
	public User createUser(String loginId,String nickname,String password,String roles){
		if(DUMMY_USER_NAME.equals(loginId)){
			//DUMMY_USER_NAMEでは登録できなくする
			return null;
		}
		User user=new User();
		user.setLoginId(loginId);
		user.setNickname(nickname);
		user.changePassword(this,password, realm);
		//本物のpassowrdが漏れないようにoffline passwordは一律"password"で初期化
		//user.setOfflinePassHash(calcPassHash(loginId,"password"));
		user.setRoles(roles);
		Date now=new Date();
		user.setCreateDate(now);
		synchronized(loginIdUserMap){
			User regUser=getUserByLoginId(loginId);
			if(regUser!=null){
				//既に登録されている！！
				return null;
			}
			user.save();
		}
		return user;
	}
	
	public void term(){
		try{
			TimerManager.clearInterval(interval);
			for(User user:loginIdUserMap.values()){
				user.update();
			}
		}catch(Throwable t){
			logger.error("fail to authenticator.term",t);
		}finally{
			loginIdUserMap.clear();
		}
	}

	public String getScheme(){
		return scheme;
	}
	
	public void setScheme(String scheme){
		if(BASIC.equalsIgnoreCase(scheme)){
			this.scheme=BASIC;
		}else if(DIGEST.equalsIgnoreCase(scheme)){
			this.scheme=DIGEST;
		}else if(BASIC_FORM.equalsIgnoreCase(scheme)){
			this.scheme=BASIC_FORM;
		}else if(DIGEST_FORM.equalsIgnoreCase(scheme)){
			this.scheme=DIGEST_FORM;
		}else if(INTERNET_AUTH.equalsIgnoreCase(scheme)){
			this.scheme=INTERNET_AUTH;
		}else{
			this.scheme=NONE;
		}
		configuration.setProperty(AUTHENTICATE_SCHEME, this.scheme);
	}
	
	private Config config;
	private Configuration configuration;
	private String passSalt;
	private String offlinePassSalt;

	public String calcOfflinePassHash(String username,String password){
		return calcPassHash(offlinePassSalt,username,password);
	}

	public String calcPassHash(String username,String password){
		return calcPassHash(passSalt,username,password);
	}

	public String calcPassHash(String salt,String username,String password){
		StringBuffer sb=new StringBuffer(salt);
		sb.append(':');
		sb.append(username);
		sb.append(':');
		sb.append(password);
		try {
			byte[] digestByte=passDigest.digest(sb.toString().getBytes("iso8859_1"));
			return DataUtil.byteToString(digestByte);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public String calcDigestAuthPassHash(String username,String password,String realm){
		StringBuilder sb=new StringBuilder();
		sb.append(username);
		sb.append(":");
		sb.append(realm);
		sb.append(":");
		sb.append(password);
		try {
			byte[] digestByte=digestAuthDigest.digest(sb.toString().getBytes("iso8859_1"));
			return DataUtil.byteToString(digestByte);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private String calcSalt(String key){
		String entropy=configuration.getString(key +"Entropy");
		if(entropy==null){
			entropy=key+System.currentTimeMillis();
		}
		byte[]digestByte=passDigest.digest(entropy.getBytes());
		String digest=DataUtil.encodeBase64(digestByte);
		configuration.setProperty(key, digest);
		return digest;
	}
	
	public Authenticator(Config config,boolean isCleanup){
		this.config=config;
		this.configuration=config.getConfiguration(null);
		setScheme(configuration.getString(AUTHENTICATE_SCHEME,null));
		realm=configuration.getString(AUTHENTICATE_REALM,"phantomProxyRealm");
		nonceRandom=config.getRandom(DIGEST_AUTHENTICATE_RANDOM_ENTOROPY);
		tokenRandom=config.getRandom(TOKEN_RANDOM_ENTOROPY);
		
		String algorithm=configuration.getString(Config.PASS_HASH_ALGORITHM);
		if(algorithm==null){
			algorithm="SHA-256";
		}
		try {
			passDigest=MessageDigest.getInstance(algorithm);
			digestAuthDigest=MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			logger.error("MessageDigest.getInstance error.algorithm:"+algorithm,e);
			throw new RuntimeException("MessageDigest.getInstance error.algorithm:"+algorithm,e);
		}
		
		//必要な場合、adminユーザを作成
		String adminId=configuration.getString(ADMIN_ID);
		if(isCleanup){
			passSalt=calcSalt(Config.PASS_SALT);
			offlinePassSalt=calcSalt(Config.OFFLINE_PASS_SALT);
			String initAdminPass=configuration.getString("initAdminPass");
			admin=createUser(adminId,adminId,initAdminPass,User.ROLE_ADMIN);
		}
		if(admin==null){
			try{
				admin=User.getByLoginId(adminId);
			}catch(Throwable e){//起動後adminを消したが、かまわない
				admin=new User();
				admin.setLoginId(adminId);
			}
		}
		passSalt=config.getString(Config.PASS_SALT);
		offlinePassSalt=config.getString(Config.OFFLINE_PASS_SALT);
		
		String openidDef=config.getString("authOpenidDef");
		if(openidDef==null){
			openidDef="googole(openid),https://www.google.com/accounts/o8/id\nyahoo japan,yahoo.co.jp\nyahoo.com,https://me.yahoo.com\n";
			config.setProperty("authOpenidDef",openidDef);
		}
		setupOpenidDef(openidDef);
	}
	
	private static Pattern usernamePattern=Pattern.compile("(?:\\s*username\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern realmPattern=Pattern.compile("(?:\\s*realm\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern noncePattern=Pattern.compile("(?:\\s*nonce\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern uriPattern=Pattern.compile("(?:\\s*uri\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern algorithmPattern=Pattern.compile("(?:\\s*algorithm\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern qopPattern=Pattern.compile("(?:\\s*qop\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern ncPattern=Pattern.compile("(?:\\s*nc\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern cnoncePattern=Pattern.compile("(?:\\s*cnonce\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	private static Pattern responsePattern=Pattern.compile("(?:\\s*response\\s*=[\\s\"]*([^,\\s\"]*))",Pattern.CASE_INSENSITIVE);
	
	public static final String KEY_BASIC_AUTHORIZATION="ph_basic_authorization";//TODO basic WWW_AUTHORIZATION_HEADERヘッダの全部
	public static final String KEY_USERNAME="ph_username";
	public static final String KEY_PASSWORD="ph_password";
	public static final String KEY_REALM="ph_realm";
	public static final String KEY_NONCE="ph_nonce";
	public static final String KEY_URI="ph_uri";
	public static final String KEY_ALGORITHM="ph_algorithm";
	public static final String KEY_QOP="ph_qop";
	public static final String KEY_NC="ph_nc";
	public static final String KEY_CNONCE="ph_cnonce";
	public static final String KEY_RESPONSE="ph_response";
	
	private static String getPatternString(String target,Pattern pattern){
		Matcher matcher=null;
		synchronized(pattern){
			matcher=pattern.matcher(target);
		}
		if(!matcher.find()){
			return null;
		}
		return matcher.group(1);
	}

	private User regUser(User user){
		String loginId=user.getLoginId();
		synchronized(loginIdUserMap){
			User regUser=loginIdUserMap.get(loginId);
			if(regUser==null){
				loginIdUserMap.put(loginId, user);
			}else{
				user=regUser;
			}
		}
		return user;
	}
	
	//mapping　authの場合は、loginしない
	public AuthSession createAuthSession(User user){
		user=regUser(user);
		return internalCreateAuthSession(user);
	}
	
	/**
	 * logoutは、Authorizerで行う
	 * @param user
	 * @return
	 */
	public AuthSession loginUser(User user){
		user=regUser(user);
		user.setLastLogin(new Date());
		user.login();
		AuthSession authSession=internalCreateAuthSession(user);
//		logger.info("login:"+user.getLoginId());
		return authSession;
	}
	
	//http://www.studyinghttp.net/cgi-bin/rfc.cgi?2617
	//logoffは実現できるのか？
	//nonceを拒否する方法は、stale=trueでよいが...これはリダイレクト相当
	//Authentication-Info ヘッダでは、nextnonceが指定できるが、これは成功した後のnonce
	//やりたいこと
	//1)認証にてlogon
	//2)logoffリンクにてlogoff　... nonceを無効にすることはできるがpasswordを知っている
	//3)領域を訪れた場合、認証画面
	//4)別ユーザでlogonできる
	//Digest username="hoge", realm="Secret Zone",
	//   nonce="RMH1usDrAwA=6dc290ea3304de42a7347e0a94089ff5912ce0de",
	//   uri="/~68user/net/sample/http-auth-digest/secret.html", algorithm=MD5,
	//   qop=auth, nc=00000001,  cnonce="e79e26e0d17c978d",
	//   response="0d73182c1602ce8749feeb4b89389019"
	
	/**
	 * 任意のproxyリクエストが通過
	 * @param requestHeader
	 * @return
	 */
	public User proxyAuthentication(HeaderParser requestHeader) {
		if(scheme==null){
			return admin;
		}
		List<String> authHeaders=requestHeader.getHeaders(HeaderParser.PROXY_AUTHORIZATION_HEADER);
		if(authHeaders==null){
			return null;
		}
//		String authControle=requestHeader.getHeader(AUTH_CONTROLE_HEADER);
		
		Iterator<String> itr=authHeaders.iterator();
		User user=null;
		while(itr.hasNext()){
			String authHeader=itr.next();
			if(scheme==BASIC){//isBasic
//				user=basicAuthentication(authHeader);
			}else if(scheme==DIGEST){//isDigest
//				user=digestAuthentication(realm,requestHeader.getMethod(),authHeader);
			}
			if(user!=null){
				itr.remove();
				if(authHeaders.size()==0){
					requestHeader.removeHeader(HeaderParser.PROXY_AUTHORIZATION_HEADER);
				}
				break;
			}
		}
		return user;
	}
	
	/**
	 * proxy認証できない場合のレスポンスを生成
	 * @param headerParser
	 * @return
	 */
	public void forwardProxyAuthenication(WebServerHandler response){
//		WebServerHandler response=(WebServerHandler)dispatcher.forwardHandler(WebServerHandler.class);
		if(scheme==BASIC){//isBasic
			response.setHeader("Proxy-Authenticate", "Basic Realm=\"" + realm + "\"");
			response.completeResponse("407","Authorization Required");
		}else if(scheme==DIGEST){//isDigest
			//IE7,8は、proxy認証でstale=FALSEとしても、もう一回新しいnonceで問い合わせが来る,
			//web認証はそんな事ない
			String nonce=getNonce();
			response.setHeader("Proxy-Authenticate", 
					"Digest Realm=\""+ realm +
					"\", nonce="+ nonce +
					", algorithm=MD5, qop=\"auth\", stale=FALSE"
				);
			response.completeResponse("407","Authorization Required");
		}else{//ありえない
			response.completeResponse("500","Authorization Error");
		}
	}
	
	public void forceDigestAuthenticate(AuthHandler authHandler,Authorizer authorizer,String authId){
		HeaderParser requestHeader=authHandler.getRequestHeader();
		Map<String,String> authParam=parseAuthHeaders(requestHeader,HeaderParser.WWW_AUTHORIZATION_HEADER,DIGEST,realm);
		User user=headerAuthenticate(authParam,requestHeader.getMethod(),DIGEST);
		if(user!=null){
			//SessionId temporaryId = authorizer.createTemporaryId(null,authorizer.getAuthPath());
			//String authId=temporaryId.getAuthId();
			AuthSession authSession=loginUser(user);
			authorizer.setAuthSessionToTemporaryId(authId, authSession);
			authHandler.setAttribute(SCOPE.REQUEST,AuthHandler.AUTH_ID, authId);
			authHandler.setAttribute(SCOPE.REQUEST,"dummyname", DUMMY_USER_NAME);
			authHandler.setAttribute(SCOPE.REQUEST,"username", user.getLoginId());
			authHandler.setAttribute(SCOPE.REQUEST,"dummyPassword", user.getDummyPassword());
			authHandler.setAttribute(SCOPE.REQUEST,"cleanupPath", "cleanupAuthForceDigest");
			authHandler.forwardPage("/creanupAuthHeader.vsp");
		}else{
			//TODO userのロールをadminに制限,ipアドレスを指定以外を制限等
			//authHandler.getRemoteIp()
			authHandler.setHeader(HeaderParser.WWW_AUTHENTICATE_HEADER,createDigestAuthenticateHeader(realm));
			authHandler.setAttribute(SCOPE.REQUEST,AuthHandler.ATTRIBUTE_RESPONSE_STATUS_CODE, "401");
			authHandler.forwardPage("/webAuthenticate.vsp");
		}
	}
	
	public boolean cleanupAuthForceDigest(AuthHandler authHandler){
		//普通の認証どは逆でcredentialに誤りがあれば200,正しければ401
		HeaderParser requestHeader=authHandler.getRequestHeader();
		Map<String,String> authParam=parseAuthHeaders(requestHeader,HeaderParser.WWW_AUTHORIZATION_HEADER,DIGEST,realm);
		if(authParam!=null){
			User user=headerAuthenticate(authParam,requestHeader.getMethod(),DIGEST);
			if(user==null){
				return true;
			}
		}
		authHandler.setHeader(HeaderParser.WWW_AUTHENTICATE_HEADER,createDigestAuthenticateHeader(realm));
		authHandler.setAttribute(SCOPE.REQUEST,AuthHandler.ATTRIBUTE_RESPONSE_STATUS_CODE, "401");
		authHandler.forwardPage("/webAuthenticate.vsp");
		return false;
	}
	
	/**
	 * 任意のwebリクエストが通過
	 * Cookieヘッダからuserを特定
	 * @param headerParser
	 * @return
	 */
	public User webAuthenticate(AuthHandler authHandler) {
		//認証が必要なpathかどうかをチェック
		if(scheme==null || NONE.equalsIgnoreCase(scheme)){
			return admin;//認証なしモード
		}
		User user=null;
		if(scheme==BASIC||scheme==DIGEST){
			HeaderParser requestHeader=authHandler.getRequestHeader();
			Map<String,String> authParam=parseAuthHeaders(requestHeader,HeaderParser.WWW_AUTHORIZATION_HEADER);
			user=headerAuthenticate(authParam,requestHeader.getMethod());
		}else if(scheme==BASIC_FORM){
			ParameterParser parameter=authHandler.getParameterParser();
			Map<String,String> authParam=new HashMap<String,String>();
			authParam.put(KEY_USERNAME,parameter.getParameter(KEY_USERNAME));
			authParam.put(KEY_PASSWORD, parameter.getParameter(KEY_PASSWORD));
			user=basicAuthentication(authParam);
		}else if(scheme==DIGEST_FORM){
			ParameterParser parameter=authHandler.getParameterParser();
			Map<String,String> authParam=new HashMap<String,String>();
			authParam.put(KEY_USERNAME,parameter.getParameter(KEY_USERNAME));
			authParam.put(KEY_RESPONSE, parameter.getParameter(KEY_RESPONSE));
			authParam.put(KEY_CNONCE, parameter.getParameter(KEY_CNONCE));
			authParam.put(KEY_NONCE, parameter.getParameter(KEY_NONCE));
			user=digestFormAuthentication(authParam);
		}else if(scheme==INTERNET_AUTH){
			
		}
		if(user==null){
			forwardWebAuthenication(authHandler);
		}
		return user;
	}
	
	private String getNonce(){
		return getNextRandom(nonceRandom);
	}
	
	//TODO 自分が意図して作ったnonceかどうかをチェックする。
	//リモートIP,ローカルIP,entoropy,起動時間...
	private boolean checkNonce(String nonce){
		logger.debug("checkNonce:"+nonce);
		return true;
	}
	
	public User getAdminUser() {
		return admin;
	}
	
	public boolean forwardCleanupAuthHeader(AuthHandler authHandler,User user){
		if(scheme!=BASIC && scheme!=DIGEST){
			return false;
		}
		authHandler.setAttribute(SCOPE.REQUEST,"dummyname", DUMMY_USER_NAME);
		authHandler.setAttribute(SCOPE.REQUEST,"username", user.getLoginId());
		authHandler.setAttribute(SCOPE.REQUEST,"dummyPassword", user.getDummyPassword());
		//basic digestの場合は、authヘッダをcleanupする必要がある
		//cookieOnceにauthSessionを詰め込んで以下の情報をcreanupAuthHeader.vspに渡す
		//1)cookieOnceのid
		//2)username:user.getLoginId()
		//3)password:間違ったpassword..乱数からシークアンドトライ
		authHandler.forwardPage("/creanupAuthHeader.vsp");
		return true;
	}
	
	public boolean cleanupAuthHeader(AuthHandler authHandler){
		if(scheme!=BASIC && scheme!=DIGEST){
			return true;
		}
		//普通の認証どは逆でcredentialに誤りがあれば200,正しければ401
		HeaderParser requestHeader=authHandler.getRequestHeader();
		Map<String,String> authParam=parseAuthHeaders(requestHeader,HeaderParser.WWW_AUTHORIZATION_HEADER);
		if(authParam==null){
			forwardWebAuthenication(authHandler);
			return false;
		}
		User user=headerAuthenticate(authParam,requestHeader.getMethod());
		if(user==null){
			return true;
		}else{
			forwardWebAuthenication(authHandler);
		}
		return false;
	}

	public String createBasicAuthenticateHeader(String realm){
		StringBuffer sb=new StringBuffer("Basic Realm=\"");
		sb.append(realm);
		sb.append("\"");
		return sb.toString();
	}
	
	public String createDigestAuthenticateHeader(String realm){
		String nonce=getNonce();
		StringBuffer sb=new StringBuffer("Digest Realm=\"");
		sb.append(realm);
		sb.append("\", nonce=");
		sb.append(nonce);
		sb.append(", algorithm=MD5, qop=\"auth\", stale=FALSE");
		return sb.toString();
	}
	
	private Map<String,String> openids=new HashMap<String,String>();
	public void setupOpenidDef(String openidDef){
		openids.clear();
		if(openidDef==null){
			return;
		}
		String[] lines=openidDef.split("\n");
		for(String line:lines){
			String[] parts=line.split(",",2);
			if(parts.length<2){
				continue;
			}
			openids.put(parts[0], parts[1]);
		}
	}
	
	private String internetAuthDirectLocation(String authId){
		if(config.getBoolean("useAuthFb")){//facebook
			return config.getAuthUrl()+"/internetAuth/fbReq?authId="+authId;
		}else if(config.getBoolean("useAuthTwitter")){//twitter
			return config.getAuthUrl()+"/internetAuth/twitterReq?authId="+authId;
		}else if(config.getBoolean("useAuthGoogle")){//google
			return config.getAuthUrl()+"/internetAuth/googleReq?authId="+authId;
		}else if(config.getBoolean("useAuthOpenid") && openids.size()>=1){
			Set<Entry<String,String>> set=openids.entrySet();
			Entry<String,String> entry=set.iterator().next();
			return config.getAuthUrl()+"/internetAuth/openIdReq?authId="+authId+"&identifier="+entry.getValue();
		}
		return null;
	}
	
	/**
	 * web認証できない場合のレスポンスを生成
	 * @param headerParser
	 * @return
	 */
	private void forwardWebAuthenication(AuthHandler response){
		if(scheme==BASIC){//isBasic
			response.setHeader(HeaderParser.WWW_AUTHENTICATE_HEADER, createBasicAuthenticateHeader(realm));
			response.setAttribute(SCOPE.REQUEST,AuthHandler.ATTRIBUTE_RESPONSE_STATUS_CODE, "401");
			response.forwardPage("/webAuthenticate.vsp");
		}else if(scheme==DIGEST){
			response.setHeader(HeaderParser.WWW_AUTHENTICATE_HEADER,createDigestAuthenticateHeader(realm));
			response.setAttribute(SCOPE.REQUEST,AuthHandler.ATTRIBUTE_RESPONSE_STATUS_CODE, "401");
			response.forwardPage("webAuthenticate.vsp");
		}else if(scheme==BASIC_FORM){
			response.forwardPage("basicForm.vsp");
		}else if(scheme==DIGEST_FORM){
			response.setAttribute(SCOPE.REQUEST,"nonce", getNonce());
			response.forwardPage("digestForm.vsp");
		}else if(scheme==INTERNET_AUTH){
			boolean isDirect=config.getBoolean("isAuthInternetDirect", false);
			if(isDirect){
				String authId=(String)response.getAttribute(SCOPE.REQUEST,AuthHandler.AUTH_ID);
				String location=internetAuthDirectLocation(authId);
				if(location!=null){
					response.redirect(location);
					return;
				}
				logger.warn("isAuthInternetDirect:true but no auth target.");
			}
			response.setAttribute(SCOPE.REQUEST,"openids", openids);
			response.forwardPage("internetAuth.vsp");
		}else{//ありえない
			response.completeResponse("500","Authorization Error");
		}
	}
	public User headerAuthenticate(Map<String,String> authParam,String method) {
		return headerAuthenticate(authParam, method,scheme);
	}
	
	public User headerAuthenticate(Map<String,String> authParam,String method,String scheme){
		User user=null;
		if(scheme==BASIC){//isBasic
			user=basicAuthentication(authParam);
		}else if(scheme==DIGEST){//isDigest
			user=digestAuthentication(authParam,method);
		}
		return user;
	}
	
	private User digestFormAuthentication(Map<String,String> authParam){
		if(authParam==null){
			return null;
		}
		String username=authParam.get(KEY_USERNAME);
		if(username==null){
			return null;
		}
		User user=getUserByLoginId(username);
		if(user==null){
			return null;
		}
		String nonce=authParam.get(KEY_NONCE);
		if(!checkNonce(nonce)){
			return null;//自分の作ったnonceじゃない！！
		}
		String a1=user.getPassHash();//ＤＢにpasswordを直接保存していない
		String cnonce=authParam.get(KEY_CNONCE);
		
		StringBuilder sb=new StringBuilder();
		sb.append(a1);
		sb.append(":");
		sb.append(nonce);
		sb.append(":");
		sb.append(cnonce);
		String response=authParam.get(KEY_RESPONSE);
		String calcRespnse=DataUtil.byteToString(passDigest.digest(sb.toString().getBytes()));
		if(calcRespnse.equals(response)){
			return user;
		}
		return null;
	}
	
	
	private User basicAuthentication(Map<String,String> authParam){
		if(authParam==null){
			return null;
		}
		String username=authParam.get(KEY_USERNAME);
		if(username==null){
			return null;
		}
		User user=getUserByLoginId(username);
		if(user==null){
			return null;
		}
		String password=authParam.get(KEY_PASSWORD);
		String calcCredential=calcPassHash(username, password);
		String credential=user.getPassHash();
		if(calcCredential.equals(credential)){
			return user;
		}
		return null;
	}
	
	private User digestAuthentication(Map<String,String> authParam,String method){
		if(authParam==null){
			return null;
		}
		String nonce=authParam.get(KEY_NONCE);
		String username=authParam.get(KEY_USERNAME);
		if(!checkNonce(nonce)){
			return null;//自分の作ったnonceじゃない！！
		}
		User user=getUserByLoginId(username);
		if(user==null){
			return null;//username間違い
		}
//		String algorithm=getPatternString(digestHeader,algorithmPattern);
		String response=authParam.get(KEY_RESPONSE);
		String uri=authParam.get(KEY_URI);
		String qop=authParam.get(KEY_QOP);
		String nc=authParam.get(KEY_NC);
		String cnonce=authParam.get(KEY_CNONCE);
		
		String a1=user.getDigestAuthPassHash();//ＤＢにpasswordを直接保存していない
		StringBuilder sb=new StringBuilder();
		sb.append(method);
		sb.append(":");
		sb.append(uri);
		String a2=DataUtil.digestHex(sb.toString().getBytes());
		
		sb.setLength(0);
		sb.append(a1);
		sb.append(":");
		sb.append(nonce);
		sb.append(":");
		sb.append(nc);
		sb.append(":");
		sb.append(cnonce);
		sb.append(":");
		sb.append(qop);
		sb.append(":");
		sb.append(a2);
		String calcResponse=DataUtil.digestHex(sb.toString().getBytes());
		if(calcResponse.equals(response)){
			return user;
		}
		return null;
	}
	
	public Map<String,String> parseAuthHeaders(HeaderParser requestHeader,String authHeaderName){
		return parseAuthHeaders(requestHeader, authHeaderName, scheme, realm);
	}
	
	public Map<String,String> parseAuthHeaders(HeaderParser requestHeader,String authHeaderName,String scheme,String realm){
		List<String> authHeaders=requestHeader.getHeaders(authHeaderName);
		if(authHeaders==null){
			return null;
		}
		String targetAuthHeader=null;
		Map<String,String>resultMap=null;
		if(scheme==BASIC){
			for(String authHeader:authHeaders){
				resultMap=parseAuthHeaderBasic(authHeader);
				if(resultMap!=null){
					resultMap.put(KEY_BASIC_AUTHORIZATION, authHeader);
					targetAuthHeader=authHeader;
					break;
				}
			}
		}else if(scheme==DIGEST){
			for(String authHeader:authHeaders){
				resultMap=parseAuthHeaderDigest(authHeader,realm);
				if(resultMap!=null){
					targetAuthHeader=authHeader;
					break;
				}
			}
		}
		if(resultMap==null){
			return null;
		}
		if(authHeaders.size()==1){
			requestHeader.removeHeader(authHeaderName);
		}else{
			authHeaders.remove(targetAuthHeader);
		}
		return resultMap;
	}
	
	private Map<String,String> parseAuthHeaderBasic(String authHeader){
		if(authHeader==null){
			return null;
		}
		String[] authParts=authHeader.split(" ");
		if(authParts.length<2 || !BASIC.equalsIgnoreCase(authParts[0])){
			return null;
		}
		String userPass=DataUtil.decodeBase64(authParts[1]);
		String[] userPassPars=userPass.split(":",2);
		if(userPassPars.length<2){
			logger.warn("basic authentication fail."+authParts[1]);
			return null;
		}
		Map<String,String>authParam=new HashMap<String,String>();
		authParam.put(KEY_USERNAME, userPassPars[0]);
		authParam.put(KEY_PASSWORD, userPassPars[1]);
		return authParam;
	}
	
	//Digest username="hoge", realm="Secret Zone",
	//   nonce="RMH1usDrAwA=6dc290ea3304de42a7347e0a94089ff5912ce0de",
	//   uri="/~68user/net/sample/http-auth-digest/secret.html", algorithm=MD5,
	//   qop=auth, nc=00000001,  cnonce="e79e26e0d17c978d",
	//   response="0d73182c1602ce8749feeb4b89389019"
	private Map<String,String> parseAuthHeaderDigest(String authHeader,String realm){
		if(authHeader==null){
			return null;
		}
		String[] authParts=authHeader.split(" ",2);
		if(authParts.length<2 || !DIGEST.equalsIgnoreCase(authParts[0])){
			return null;
		}
		Map<String,String>authParam=new HashMap<String,String>();
		String digestHeader=authParts[1];
		String paramRealm=getPatternString(digestHeader,realmPattern);
		if(realm!=null && !realm.equals(paramRealm)){
			return null;
		}
		authParam.put(KEY_REALM,paramRealm);
		authParam.put(KEY_NONCE,getPatternString(digestHeader,noncePattern));
		authParam.put(KEY_USERNAME,getPatternString(digestHeader,usernamePattern));
		authParam.put(KEY_ALGORITHM,getPatternString(digestHeader,algorithmPattern));
		authParam.put(KEY_RESPONSE,getPatternString(digestHeader,responsePattern));
		authParam.put(KEY_URI,getPatternString(digestHeader,uriPattern));
		authParam.put(KEY_QOP,getPatternString(digestHeader,qopPattern));
		authParam.put(KEY_NC,getPatternString(digestHeader,ncPattern));
		authParam.put(KEY_CNONCE,getPatternString(digestHeader,cnoncePattern));
		return authParam;
	}

	public String getRealm() {
		return realm;
	}

	public String getLogoutUrl() {
		String logoutUrl=config.getString(AUTHENTICATE_LOGOUT_URL, null);
		if(logoutUrl==null||"".equals(logoutUrl)){
			logoutUrl=config.getPublicWebUrl();
		}
		return logoutUrl;
	}

	public void setLogoutUrl(String logoutUrl) {
		configuration.setProperty(AUTHENTICATE_LOGOUT_URL,logoutUrl);
//		this.logoutUrl = logoutUrl;
	}
}
