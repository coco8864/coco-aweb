package naru.aweb.auth;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.aweb.config.User;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;

/*
 * ���ӁF�F�؂��K�v�Ȃ��ꍇ��PROXY_AUTHORIZATION_HEADER��Cookie�w�b�_���폜����K�v������B
 * 1)�F�ؕs�v�ݒ�Ƃ����ꍇ�A�݂��admin
 * 2)�F�ؗv�ݒ�ŁA�F�؂̕K�v�̂Ȃ�URI�ւ�Web�A�N�Z�X�̏ꍇ�Aanonymous
 * 3)�F�ؗv�ݒ�ŁAanonymous�������ꍇ�Aname��"anonymous"�Ȃ牽�ł��F�ؒʉ߁A
 * 
 * ���ӂQ�Fnonce��sessionID�Ƃ��ė��p���邽��DIGEST�F�؂ł���K�v������BBasic�F�؂̏����͎c���Ă��邪�g���Ȃ�
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
	public static final String NONE="None";//�F�؂Ȃ�
	public static final String BASIC="Basic";//Basic�F��
	public static final String DIGEST="Digest";//Digest�F��
	public static final String BASIC_FORM="BasicForm";//Form�F��
	public static final String DIGEST_FORM="DigestForm";//Form�F��
	private static SecureRandom nonceRandom;
	private static SecureRandom tokenRandom;
	
	//loginid��User�̃}�b�v�A�ŐV����ێ�,�C�ӂ̃^�C�~���O��DB�Ɋi�[����ΖY��Ă��悢
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
//	private User dummyUser;//digest�F�؂Ɏg��
//	private AuthSession adminSession;
	private String scheme=null;//null�̏ꍇ�A�F�؂Ȃ�
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
	
	private User createUser(String loginId,String password,String roles){
		if(DUMMY_USER_NAME.equals(loginId)){
			//DUMMY_USER_NAME�ł͓o�^�ł��Ȃ�����
			return null;
		}
		User user=new User();
		user.setLoginId(loginId);
		
		user.changePassword(password, realm);
		user.setRoles(roles);
		Date now=new Date();
		user.setCreateDate(now);
//		user.setChangePass(now);
		synchronized(loginIdUserMap){
			User regUser=getUserByLoginId(loginId);
			if(regUser!=null){
				//���ɓo�^����Ă���I�I
				return null;
			}
			user.save();
		}
		return user;
	}
	
	public void term(){
		TimerManager.clearInterval(interval);
		for(User user:loginIdUserMap.values()){
			user.update();
		}
		loginIdUserMap.clear();
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
		}else{
			this.scheme=NONE;
		}
		configuration.setProperty(AUTHENTICATE_SCHEME, this.scheme);
	}
	
	private Config config;
	private Configuration configuration;
	
	public Authenticator(Config config,boolean isCleanup){
		this.config=config;
		this.configuration=config.getConfiguration(null);
		setScheme(configuration.getString(AUTHENTICATE_SCHEME,null));
		realm=configuration.getString(AUTHENTICATE_REALM,"phantomProxyRealm");
//		logoutUrl=configuration.getString(AUTHENTICATE_LOGOUT_URL);
//		Config config = Config.getConfig();
		nonceRandom=config.getRandom(DIGEST_AUTHENTICATE_RANDOM_ENTOROPY);
		tokenRandom=config.getRandom(TOKEN_RANDOM_ENTOROPY);
		
		/*
		if(BASIC.equalsIgnoreCase(scheme)){
			scheme=BASIC;
		}else if(DIGEST.equalsIgnoreCase(scheme)){
			scheme=DIGEST;
		}else if(DIGEST.equalsIgnoreCase(scheme)){
			scheme=DIGEST;
		}else{
			scheme=null;
			admin=new User();
			admin.setLoginId(adminId);
			admin.setRoles("admin");
			adminSession=new AuthSession(admin,getNextRandom(tokenRandom));
			return;
		}
		*/
		//�K�v�ȏꍇ�Aadmin���[�U���쐬
		String adminId=configuration.getString(ADMIN_ID);
		if(isCleanup){
			String initAdminPass=configuration.getString("initAdminPass");
			admin=createUser(adminId,initAdminPass,User.ROLE_ADMIN);
		}
		if(admin==null){
			try{
				admin=User.getByLoginId(adminId);
			}catch(Throwable e){//�N����admin�����������A���܂�Ȃ�
				admin=new User();
				admin.setLoginId(adminId);
			}
		}
		
//		adminSession=(AuthSession)PoolManager.getInstance(AuthSession.class);
//		adminSession.init(admin,getNextRandom(tokenRandom));
//		adminSession.ref();
		//�K�v�ȏꍇ�Aadmin���[�U���쐬
		/*
		dummyUser=null;
		try{
			dummyUser=User.getByLoginId(DUMMY_USER_NAME);
		}catch(Throwable e){
		}
		if(dummyUser==null){
			dummyUser=createUser(DUMMY_USER_NAME,DUMMY_USER_NAME,null);
		}
		*/
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
	
	public static final String KEY_BASIC_AUTHORIZATION="ph_basic_authorization";//TODO basic WWW_AUTHORIZATION_HEADER�w�b�_�̑S��
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
	
	
	//mapping�@auth�̏ꍇ�́Alogin���Ȃ�
	public AuthSession createAuthSession(User user){
		user=regUser(user);
		return internalCreateAuthSession(user);
	}
	
	/**
	 * logout�́AAuthorizer�ōs��
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
	//logoff�͎����ł���̂��H
	//nonce�����ۂ�����@�́Astale=true�ł悢��...����̓��_�C���N�g����
	//Authentication-Info �w�b�_�ł́Anextnonce���w��ł��邪�A����͐����������nonce
	//��肽������
	//1)�F�؂ɂ�logon
	//2)logoff�����N�ɂ�logoff�@... nonce�𖳌��ɂ��邱�Ƃ͂ł��邪password��m���Ă���
	//3)�̈��K�ꂽ�ꍇ�A�F�؉��
	//4)�ʃ��[�U��logon�ł���
	//Digest username="hoge", realm="Secret Zone",
	//   nonce="RMH1usDrAwA=6dc290ea3304de42a7347e0a94089ff5912ce0de",
	//   uri="/~68user/net/sample/http-auth-digest/secret.html", algorithm=MD5,
	//   qop=auth, nc=00000001,  cnonce="e79e26e0d17c978d",
	//   response="0d73182c1602ce8749feeb4b89389019"
	
	/**
	 * �C�ӂ�proxy���N�G�X�g���ʉ�
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
	 * proxy�F�؂ł��Ȃ��ꍇ�̃��X�|���X�𐶐�
	 * @param headerParser
	 * @return
	 */
	public void forwardProxyAuthenication(WebServerHandler response){
//		WebServerHandler response=(WebServerHandler)dispatcher.forwardHandler(WebServerHandler.class);
		if(scheme==BASIC){//isBasic
			response.setHeader("Proxy-Authenticate", "Basic Realm=\"" + realm + "\"");
			response.completeResponse("407","Authorization Required");
		}else if(scheme==DIGEST){//isDigest
			//IE7,8�́Aproxy�F�؂�stale=FALSE�Ƃ��Ă��A�������V����nonce�Ŗ₢���킹������,
			//web�F�؂͂���Ȏ��Ȃ�
			String nonce=getNonce();
			response.setHeader("Proxy-Authenticate", 
					"Digest Realm=\""+ realm +
					"\", nonce="+ nonce +
					", algorithm=MD5, qop=\"auth\", stale=FALSE"
				);
			response.completeResponse("407","Authorization Required");
		}else{//���肦�Ȃ�
			response.completeResponse("500","Authorization Error");
		}
	}
	
	/**
	 * �C�ӂ�web���N�G�X�g���ʉ�
	 * Cookie�w�b�_����user�����
	 * @param headerParser
	 * @return
	 */
	public User webAuthenticate(AuthHandler authHandler) {
		//�F�؂��K�v��path���ǂ������`�F�b�N
		if(scheme==null || NONE.equalsIgnoreCase(scheme)){
			return admin;//�F�؂Ȃ����[�h
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
		}
		if(user==null){
			forwardWebAuthenication(authHandler);
		}
		return user;
	}
	
	private String getNonce(){
		return getNextRandom(nonceRandom);
	}
	
	//TODO �������Ӑ}���č����nonce���ǂ������`�F�b�N����B
	//�����[�gIP,���[�J��IP,entoropy,�N������...
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
		authHandler.setRequestAttribute("dummyname", DUMMY_USER_NAME);
		authHandler.setRequestAttribute("username", user.getLoginId());
		authHandler.setRequestAttribute("dummyPassword", user.getDummyPassword());
		//basic digest�̏ꍇ�́Aauth�w�b�_��cleanup����K�v������
		//cookieOnce��authSession���l�ߍ���ňȉ��̏���creanupAuthHeader.vsp�ɓn��
		//1)cookieOnce��id
		//2)username:user.getLoginId()
		//3)password:�Ԉ����password..��������V�[�N�A���h�g���C
		authHandler.forwardAuthPage("/creanupAuthHeader.vsp");
		return true;
	}
	
	public boolean cleanupAuthHeader(AuthHandler authHandler){
		if(scheme!=BASIC && scheme!=DIGEST){
			return true;
		}
		//���ʂ̔F�؂ǂ͋t��credential�Ɍ�肪�����200,���������401
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
	
	/**
	 * web�F�؂ł��Ȃ��ꍇ�̃��X�|���X�𐶐�
	 * @param headerParser
	 * @return
	 */
	private void forwardWebAuthenication(AuthHandler response){
		if(scheme==BASIC){//isBasic
			response.setHeader(HeaderParser.WWW_AUTHENTICATE_HEADER, createBasicAuthenticateHeader(realm));
			response.setRequestAttribute(AuthHandler.ATTRIBUTE_RESPONSE_STATUS_CODE, "401");
			response.forwardAuthPage("/webAuthenticate.vsp");
		}else if(scheme==DIGEST){
			response.setHeader(HeaderParser.WWW_AUTHENTICATE_HEADER,createDigestAuthenticateHeader(realm));
			response.setRequestAttribute(AuthHandler.ATTRIBUTE_RESPONSE_STATUS_CODE, "401");
			response.forwardAuthPage("webAuthenticate.vsp");
		}else if(scheme==BASIC_FORM){
			response.forwardAuthPage("basicForm.vsp");
		}else if(scheme==DIGEST_FORM){
			response.setRequestAttribute("nonce", getNonce());
			response.forwardAuthPage("digestForm.vsp");
		}else{//���肦�Ȃ�
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
			return null;//�����̍����nonce����Ȃ��I�I
		}
		String a1=user.getPassHash();//�c�a��password�𒼐ڕۑ����Ă��Ȃ�
		String cnonce=authParam.get(KEY_CNONCE);
		
		StringBuilder sb=new StringBuilder();
		sb.append(a1);
		sb.append(":");
		sb.append(nonce);
		sb.append(":");
		sb.append(cnonce);
		String response=authParam.get(KEY_RESPONSE);
		String calcRespnse=DataUtil.digestHexSha1(sb.toString().getBytes());
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
		String calcCredential=User.calcPassHashSha1(username, password);
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
			return null;//�����̍����nonce����Ȃ��I�I
		}
		User user=getUserByLoginId(username);
		if(user==null){
			return null;//username�ԈႢ
		}
//		String algorithm=getPatternString(digestHeader,algorithmPattern);
		String response=authParam.get(KEY_RESPONSE);
		String uri=authParam.get(KEY_URI);
		String qop=authParam.get(KEY_QOP);
		String nc=authParam.get(KEY_NC);
		String cnonce=authParam.get(KEY_CNONCE);
		
		String a1=user.getDigestAuthPassHash();//�c�a��password�𒼐ڕۑ����Ă��Ȃ�
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
