package naru.aweb.auth;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.http.Cookie;
import naru.aweb.http.CookieLocation;
import naru.aweb.util.ServerParser;

/*
 * primaryId:/authに付加されるcookie
 *  authentication => sessionTimeout or logoff
 * 
 * cookieOnceId:provider側未認証時にredirectと同時に付加されるcookie
 *  first service => redirect*2後認可、or timeout
 * 
 * pathOnceId:/authがredirect時にpathinfoに付加するid
 *  authkakuninn => redirect後認可、or timeout
 * 
 * secondaryId://provider側に付加されるcookie
 *　　認可 => sessionTimeout or logoff(primaryIdと同期)
 * 
 * provider処理
 * 1)cookieがあるか -> no redirect /auth　cookieOnceId追加
 * 2)secondaryIdか -> yes 認可
 * 3)cookieOnceId -> no redirect /auth　cookieOnceId追加
 * 4)path情報からpathOnceId取得 -> no redirect /auth　cookieOnceId追加
 * 5)cookieOnceIdからpath,実際のpath,pathOnceIdからpath 比較 -> 一致しない redirect /auth　cookieOnceId追加
 * 6)認可+secondaryId追加
 * 
 */
public class SessionId extends PoolBase{
	private static Logger logger = Logger.getLogger(SessionId.class);
	private static Config config=Config.getConfig();
	public static final String SESSION_ID = config.getString("sessionCookieKey", "phId");
	private static Pattern pathInfoPattern = Pattern.compile(";"+SESSION_ID+"=([^\\s;/?]*)");
	private static Authorizer authorizer=config.getAuthorizer();

	public static String getCookieId(List cookies){
		if (cookies == null) {
			return null;
		}
		String id = Cookie.getValue(cookies, SESSION_ID);
		return id;
	}
	
	public static String getPathId(String url,StringBuffer sb){
		Matcher matcher = null;
		synchronized (pathInfoPattern) {
			matcher = pathInfoPattern.matcher(url);
		}
		String id = null;
		while (matcher.find()) {
			// sb=new StringBuffer();
			// matcher.appendReplacement(sb, "");
			id = matcher.group(1);
			matcher.appendReplacement(sb, "");
		}
		matcher.appendTail(sb);
		return id;
	}
	
	public static String delCookieString(String path,boolean isSecure){
		return Cookie.formatSetCookieHeader(SESSION_ID, SESSION_ID, null, path,0, isSecure);
	}

	public static SessionId createSecondaryId(boolean isCookieSecure,String cookieDomain,String cookiePath) {
		return createSessionId(Type.SECONDARY,null,null,null,isCookieSecure, cookieDomain, cookiePath);
	}
	
	public static SessionId createTemporaryId(String url,String cookiePath) {
		if(url!=null){
			boolean isCookieSecure=url.startsWith("https://");
			String domain=null;//TODO url.substring();
			return createSessionId(Type.TEMPORARY,url,null,null,isCookieSecure,domain,cookiePath);
		}
		return createSessionId(Type.TEMPORARY,null,null,null,false,null,cookiePath);
	}
	
	//cookieに設定されないのでcookie関連はダミー
	public static SessionId createPathOnceId(String url,SessionId primaryId) {
		return createSessionId(Type.PATH_ONCE,url,primaryId,null,
				false,null,null);
	}
	
	public static SessionId createPrimaryId(AuthSession authSession) {
		return createSessionId(Type.PRIMARY,null,null,authSession,
				authorizer.isAuthSsl(), config.getSelfDomain()+":"+config.getInt(Config.SELF_PORT), authorizer.getAuthPath());
	}

	public static SessionId createSessionId(Type type,String url,SessionId primaryId,AuthSession authSession,
			boolean isCookieSecure,String cookieDomain,String cookiePath) {
		SessionId sessionId = (SessionId) PoolManager.getInstance(SessionId.class);
		sessionId.type=type;
		sessionId.isValid = true;
		sessionId.url=url;
		sessionId.isDirectUrl=false;
		sessionId.setPrimaryId(primaryId);
		sessionId.authSession=authSession;
		if(authSession!=null){
			authSession.setSessionId(sessionId);
		}
		sessionId.lastAccessTime = System.currentTimeMillis();
		sessionId.cookieLocation=CookieLocation.parse(isCookieSecure,cookieDomain,cookiePath);
//		sessionId.isCookieSecure=isCookieSecure;
//		sessionId.cookieDomain=cookieDomain;
//		sessionId.cookiePath=cookiePath;
//		if(isCookieSecure){
//		}
//		StringBuilder sb=new StringBuilder();
//		if(isCookieSecure){
//			sb.append("https://");
//		}else{
//			sb.append("http://");
//		}
//		sb.append(cookieDomain);
//		sb.append(cookiePath);
//		sessionId.authUrl=sb.toString();
		
		//idの生成は、authorizerに任せる
		authorizer.registerSessionId(sessionId);
		return sessionId;
	}
	
	public boolean logoutIfTimeout(String id,long lastAccessLimit) {
		synchronized (this) {
			if(isValid==false){
				return false;
			}
			if(!id.equals(this.id)){
				return false;
			}
			if(lastAccessLimit>0&&lastAccessTime>lastAccessLimit){
				return false;
			}
			if(this.type==Type.PRIMARY){
				AuthSession authSession=getAuthSession();
				authSession.logout();//この先でremoveが呼ばれる
				return true;
			}
			if(remove(id)==0){
				return false;
			}
			return true;
		}
	}
	
	public int remove() {
		return remove(null);
	}
	
	/**
	 * 指定されたidが正しい場合削除する。
	 * @param id
	 * @return
	 */
	public int remove(String id) {
		int counter = 1;
		synchronized (this) {
			if(isValid==false){
				return 0;
			}
			if(id!=null && !id.equals(this.id)){
				return 0;
			}
			setPrimaryId(null);
			//isValidは、createSessionIdでtrueにしてここでしかfalseを設定しない。
			//１つのSessionIDに対して、このルートが２回走行する事はない
			authorizer.removeSessionId(type,this.id);
			isValid = false;
			unref();
		}
		return counter;
	}

	public enum Type {
		PRIMARY, SECONDARY, TEMPORARY, PATH_ONCE
	}

	private boolean isValid;// mapから取れたとしてもこれがfalseの場合見つからなかった事にする
	private Type type;
	private String id;
	private AuthSession authSession;
	private SessionId primaryId;
	private String url;
	//urlが直接認証を必要とするURLか?wsなどAPIで認証をする場合originは認証画面とは関係しない
	private boolean isDirectUrl;
	private long lastAccessTime;
	
	/* secondaryIdが単一のmappingと結びつくとは限らない WebSocket対応*/
	//	private Mapping mapping;//secondary用の場合、どのmapping用のSessionIdか
	/* secondaryIdがどの範囲で有効かを保持する WebSocket対応*/
	private CookieLocation cookieLocation;
	/*
	private String authUrl;
	private boolean isCookieSecure;
	private String cookieDomain;
	private String cookiePath;
	*/
	
	/* Cookie的に該当するsecondaryかをチェックする */
	public boolean isCookieMatch(String authUrl){
		return this.cookieLocation.isMatch(authUrl);
	}
	
	/* Cookie的に該当するsecondaryかをチェックする */
	public boolean isCookieMatch(boolean isSecure,ServerParser domain,String path){
		return this.cookieLocation.isMatch(isSecure,domain,path);
	}

	/*
	public void onTimer(Object userContext) {
		try {
			switch (type) {
			case PRIMARY:// セションタイムアウト,30minutes
				long now=System.currentTimeMillis();
				long latest=0;
				long nextTimeoutInterval;
				synchronized(this){
					if(!isValid){
						return;
					}
					for(SessionId secondaryId:secondaryIds.values()){
						if(latest<secondaryId.lastAccessTime){
							latest=secondaryId.lastAccessTime;
						}
					}
					nextTimeoutInterval=SESSION_TIMEOUT-(now-latest);
					if(nextTimeoutInterval<=0){
						logoff();
					}
				}
				if(nextTimeoutInterval>0){
					setTimeout(nextTimeoutInterval);
				}
				break;
			case COOKIE_ONCE:// 認証情報入力が遅すぎる場合は、再実行,5seconds
				remove();
				break;
			case PATH_ONCE:// redirectが遅い,5seconds
				remove();
				break;
			}
		} finally {
			unref();
		}
	}
	*/
	public boolean isMatch(Type type){
		if(!isValid){
			return false;
		}
		if(this.type!=type){
			return false;
		}
		return true;
	}
	
	public boolean isMatch(Type type,String id){
		if(!isMatch(type)){
			return false;
		}
		if(!this.id.equals(id)){
			return false;
		}
		return true;
	}
	
	public boolean isMatch(Type type,String id,boolean isSecure,ServerParser domain,String path){
		if(!isMatch(type,id)){
			return false;
		}
		if(!isCookieMatch(isSecure, domain, path)){
			return false;
		}
		//TODO 認証時と同じmappingとは限らない。異なるmappingの場合は再度認証を求めた方がbetter
		//しかし、mappingのreloadをするとPOSTの認証が通らなくなる。
		//ph.auth.setAuthしても、tokenが合わなくなる。
		/*
		if(mapping.getSourceType()!=Mapping.SourceType.WS){//WebSocketは直接認証していない
			if(this.mapping!=mapping){
				return false;
				//mappingをreloadするとインスタンスが変わるのでこれが一致しなくなる
				if(!this.mapping.getId().equals(mapping.getId())){
					return false;
				}
				logger.debug("relaod mapping."+mapping.getId());
				//古いmappingは忘れた方がよい
				setMapping(mapping);
			}
		}
		*/
		return true;
	}
	
	public boolean isMatch(Type type,String id,String url){
		if(!isMatch(type,id)){
			return false;
		}
		//http://xxx:80/path と http://xxx/pathの可能性がある
		if(!ServerParser.equalsUrl(this.url, url)){
			return false;
		}
		return true;
	}
	
	public boolean isMatch(Type type,String id,String url,SessionId primaryId){
		if(!isMatch(type,id,url)){
			return false;
		}
		if(this.primaryId!=primaryId){
			return false;
		}
		return true;
	}
	

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id=id;
	}

	/*
	public String getSetCookieString(String path, boolean isSecure) {
		return Cookie.formatSetCookieHeader(SESSION_ID, getId(), null, path,-1, isSecure);
	}
	*/
	public String getSetCookieString() {
		return Cookie.formatSetCookieHeader(SESSION_ID, getId(),cookieLocation,-1);
	}
	
	//認証先URLのpath情報にauthIdを付加するメソッド
	public String encodeUrl() {
		if(url==null){
			logger.warn("encodeUrl but url=null:id="+id+":authId:"+authId,new Exception());
			return null;
		}
		StringBuilder sb = new StringBuilder();
		int pos = url.indexOf("?");
		if (pos < 0) {
			sb.append(url);
			sb.append(";");
			sb.append(SESSION_ID);
			sb.append("=");
			sb.append(id);
		} else {
			sb.append(url.substring(0, pos));
			sb.append(";");
			sb.append(SESSION_ID);
			sb.append("=");
			sb.append(id);
			sb.append(url.substring(pos));
		}
		return sb.toString();
	}

	public AuthSession getAuthSession() {
		return authSession;
	}
	
	public synchronized AuthSession popAuthSession() {
		AuthSession authSession=this.authSession;
		this.authSession=null;
		return authSession;
	}
	
	
	public void setAuthSession(AuthSession authSession) {
		this.authSession=authSession;
	}
	
	public String getUrl() {
		return url;
	}
	
	public void setUrl(String url) {
		this.url=url;
	}
	
	public void setDirectUrl(boolean isDirectUrl){
		this.isDirectUrl=isDirectUrl;
	}
	
	public boolean isDirectUrl(){
		return isDirectUrl;
	}
	
	/*
	public void addSecondaryId(Mapping mapping,SessionId secondaryId) {
		SessionId orgSecondaryId=secondaryIds.remove(mapping);
		secondaryIds.put(mapping.getId(), secondaryId);
		if(orgSecondaryId!=null){
			orgSecondaryId.remove();
		}
	}
	*/
	
	public void setPrimaryId(SessionId primaryId) {
		if(primaryId!=null){
			primaryId.ref();
		}
		if(this.primaryId!=null){
			this.primaryId.unref();
		}
		this.primaryId=primaryId;
	}

	public SessionId getPrimaryId() {
		return primaryId;
	}

	public void setLastAccessTime() {
		this.lastAccessTime=System.currentTimeMillis();
	}

	public long getLastAccessTime() {
		return lastAccessTime;
	}

	public Type getType() {
		return type;
	}

	/**
	 * CookieOnceIdの場合、/authに通知するid
	 */
	private String authId;
	public void setAuthId(String authId) {
		this.authId=authId;
	}
	public String getAuthId(){
		return authId;
	}

	@Override
	public void recycle() {
		if(cookieLocation!=null){
			cookieLocation.unref();
			cookieLocation=null;
		}
		super.recycle();
	}

	/*
	public void setMapping(Mapping mapping) {
		this.mapping=mapping;
	}
	
	public Mapping getMapping(){
		return mapping;
	}
	*/
	
}
