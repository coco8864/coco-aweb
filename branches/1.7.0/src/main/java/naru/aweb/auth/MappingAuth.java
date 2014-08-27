package naru.aweb.auth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import naru.async.Timer;
import naru.async.timer.TimerManager;
import naru.aweb.util.HeaderParser;
import net.sf.json.JSONObject;

/**
 * マッピング単位の認証処理を行う
 * basic,digest logoffなし
 * @author Naru
 */
public class MappingAuth implements Timer{
	//2*INTERVAL以上使われないSessionは開放する
	private static final long INTERVAL=10*60*1000;
	private String realm;
	private String scheme;
	private Set<String> roles;
	private boolean isProxy;//falseの場合web
	private Authenticator authenticator;
	private String authHeaderName;
	private String authIdKey;
	private Object timerId=null;
	
	//認証済み情報のキャッシュ
	private Map<String,AuthSession> authIds1;
	private Map<String,AuthSession> authIds2;
	
	public MappingAuth(Authenticator authenticator){
		this.authenticator=authenticator;
	}
	
	public boolean init(JSONObject auth,boolean isProxy){
		this.isProxy=isProxy;
		this.realm=authenticator.getRealm();
		String scheme=auth.getString("scheme");
		if(Authenticator.BASIC.equalsIgnoreCase(scheme)){
			this.scheme=Authenticator.BASIC;
			this.authIdKey=Authenticator.KEY_BASIC_AUTHORIZATION;
			this.realm=auth.getString("realm");
			if(this.realm==null){
				return false;
			}
		}else if(Authenticator.DIGEST.equalsIgnoreCase(scheme)){
			this.scheme=Authenticator.DIGEST;
			this.authIdKey=Authenticator.KEY_NONCE;
		}else{
			return false;
		}
		String roles=auth.getString("roles");
		this.roles=new HashSet<String>();
		if(roles!=null){
			for(String role:roles.split(",")){
				this.roles.add(role);
			}
		}
		if(isProxy){
			this.authHeaderName=HeaderParser.PROXY_AUTHORIZATION_HEADER;
		}else{
			this.authHeaderName=HeaderParser.WWW_AUTHORIZATION_HEADER;
		}
		authIds1=new HashMap<String,AuthSession>();
		authIds2=new HashMap<String,AuthSession>();
		timerId=TimerManager.setInterval(INTERVAL, this, null);
		return true;
	}
	
	private void releaseAuthIds(Map<String,AuthSession> authIds){
		if(authIds==null){
			return;
		}
		for(AuthSession authSession:authIds.values()){
			authSession.unref();
		}
		authIds.clear();
	}
	
	public void term(){
		TimerManager.clearInterval(timerId);
		timerId=null;
		synchronized(this){
			releaseAuthIds(authIds1);
			releaseAuthIds(authIds2);
		}
		authIds1=null;
		authIds2=null;
		roles=null;
	}

	//定期的にauthIds1を2に移し変えることで使われないauthIdは削除する
	public void onTimer(Object userContext) {
		synchronized(this){
			Map<String,AuthSession>tmp=authIds2;
			authIds2=authIds1;
			releaseAuthIds(tmp);			
			authIds1=tmp;
		}
	}
	
	private boolean isAccess(User user){
		List<String> userRoles = user.getRolesList();
		if (userRoles.contains(User.ROLE_ADMIN)) {// admin roleを持つものはなんでも使える
			return true;
		}
		if (roles.isEmpty()) {// roleを持つたないmappingは誰でも使える
			return true;
		}
		Iterator<String> itr = roles.iterator();
		while (itr.hasNext()) {
			if (userRoles.contains(itr.next())) {
				return true;
			}
		}
		return false;
	}
	
	private AuthSession authenticate(Map<String,String>authParam,String method){
		User user=authenticator.headerAuthenticate(authParam, method,scheme);
		if(user==null){
			return null;
		}
		if(isAccess(user)){
			return authenticator.createAuthSession(user);
		}
		return null;
	}
	
	public AuthSession authorize(HeaderParser requestHeader){
		//ヘッダを解釈してauth情報を取得
		Map<String,String>authParam=authenticator.parseAuthHeaders(requestHeader, authHeaderName, scheme, realm);
		if(authParam==null){
			return null;
		}
		String authId=authParam.get(authIdKey);
		AuthSession authSession=authIds1.get(authId);
		if(authSession!=null){
			return authSession;
		}
		authSession=authIds2.remove(authId);
		if(authSession!=null){
			synchronized(this){
				authIds1.put(authId,authSession);
			}
			return authSession;
		}
		authSession=authenticate(authParam,requestHeader.getMethod());
		if(authSession!=null){
			synchronized(this){
				authIds1.put(authId,authSession);
			}
			return authSession;
		}
		return null;
	}
	
	public String createAuthenticateHeader(){
		if(scheme==Authenticator.BASIC){
			return authenticator.createBasicAuthenticateHeader(realm);
		}else if(scheme==Authenticator.DIGEST){
			return authenticator.createDigestAuthenticateHeader(realm);
		}
		return null;
	}

	public boolean isProxy() {
		return isProxy;
	}
}
