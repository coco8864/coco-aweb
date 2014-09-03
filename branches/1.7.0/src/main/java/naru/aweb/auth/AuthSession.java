package naru.aweb.auth;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.store.DataUtil;
import naru.aweb.config.Config;

public class AuthSession extends PoolBase{
	private static Config config=Config.getConfig();
	private static final String SID_RANDOM_ENTOROPY = "sidRandomEntoropy";
	
	private static Logger logger = Logger.getLogger(AuthSession.class);
	private static ByteBuffer serverId=ByteBuffer.wrap(("12345678"+config.getSelfDomain() + config.getString("serverIdEntropy","serverIdEntropy")+System.currentTimeMillis()).getBytes());
	private static long sidSeq=0;
	private static SecureRandom sidRandom=config.getRandom(SID_RANDOM_ENTOROPY);
	public static AuthSession UNAUTH_SESSION=new AuthSession(new User(),"");
	
	private User user;
	private String token;//CSRF対策
	private String sid;//クライアントでこのセションを識別するID,グローバル一意、乱数を追加して予想不可能にするが、意味的には不要
	private Map<String,Object> attribute=new HashMap<String,Object>();//sessionに付随する属性
	private boolean isLogout=false;
	private SessionId sessionId;
	private Set<LogoutEvent> logoutEvents=new HashSet<LogoutEvent>();
	private Set<AuthSession> secandarySessions=new HashSet<AuthSession>();
	
	public void recycle() {
		Iterator<Object> itr=attribute.values().iterator();
		while(itr.hasNext()){
			Object v=itr.next();
			if(v instanceof PoolBase){
				((PoolBase)v).unref();
			}
		}
		attribute.clear();
		logoutEvents.clear();
		secandarySessions.clear();
		isLogout=false;
		super.recycle();
	}
	
	AuthSession(User user,String token){
		init(user,token);
	}
	public AuthSession(){
	}
	void init(User user,String token){
		this.user=user;
		this.token=token;
		String unique=null;
		synchronized(serverId){
			sidSeq++;
			serverId.putLong(0,sidSeq);
			unique=DataUtil.digestHex(serverId.array());
		}
		this.sid=unique+Authenticator.getNextRandom(sidRandom);
	}
	
	public AuthSession createSecondarySession(){
		AuthSession secodarySession=Authenticator.internalCreateAuthSession(user);
		secandarySessions.add(secodarySession);
		return secodarySession;
	}
	
	public synchronized AuthSession getSecondarySession(String authUrl){
		if(isLogout){
			return null;
		}
		if(sessionId.getType()!=SessionId.Type.PRIMARY){
			logger.error("logout type error."+sessionId.getType(),new Exception());
			return null;
		}
		for(AuthSession secondarySession:secandarySessions){
			if( secondarySession.getSessionId().isCookieMatch(authUrl) ){
				return secondarySession;
			}
		}
		return null;
	}
	
	public User getUser() {
		return user;
	}
	
	public synchronized void logout(){
		if(isLogout){
			return;
		}
		if(sessionId.getType()!=SessionId.Type.PRIMARY){
			logger.error("logout type error."+sessionId.getType(),new Exception());
			return;
		}
		user.logout();
		isLogout=true;
		for(AuthSession secondarySession:secandarySessions){
			synchronized(secondarySession){
				if(secondarySession.isLogout()){
					continue;
				}
				for(LogoutEvent evnet:secondarySession.logoutEvents){
					evnet.onLogout();//onLogoutイベント中synchronizedはタブー
				}
				secondarySession.logoutEvents.clear();
				SessionId secondaryId=secondarySession.getSessionId();
				secondaryId.remove();
				secondarySession.unref();
			}
		}
		secandarySessions.clear();
		for(LogoutEvent evnet:logoutEvents){
			evnet.onLogout();//onLogoutイベント中synchronizedはタブー
		}
		logoutEvents.clear();
		sessionId.remove();//sessionIdを開放する
		unref();
	}
	
	public synchronized boolean addLogoutEvent(LogoutEvent event){
		if(isLogout){
			return false;
		}
		logoutEvents.add(event);
		return true;
	}
	
	public synchronized void removeLogoutEvent(LogoutEvent event){
		logoutEvents.remove(event);
	}
	
	public boolean isLogout(){
		return isLogout;
	}
	
	public Object getAttribute(String name){
		return attribute.get(name);
	}
	public void setAttribute(String name, Object value) {
		if(value!=null && value instanceof PoolBase){
			((PoolBase)value).ref();
		}
		Object obj=attribute.get(name);
		if(obj!=null && obj instanceof PoolBase){
			((PoolBase)obj).unref();
		}
		attribute.put(name, value);
	}
	
	public Iterator<String> getAttributeNames(){
		return attribute.keySet().iterator();
	}

	public String getToken() {
		return token;
	}
	
	public String getSid() {
		return sid;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public void setSessionId(SessionId sessionId) {
		this.sessionId = sessionId;
	}

	public long getLastAccessTime() {
		if(sessionId==null){
			return -1;
		}
		return sessionId.getLastAccessTime();
	}
	public void setLastAccessTime() {
		if(sessionId==null){
			return;
		}
		sessionId.setLastAccessTime();
	}
	
	/*
	public AuthSession getPrimarySession() {
		return primarySession;
	}

	public void setPrimarySession(AuthSession primarySession) {
		if(primarySession!=null){
			primarySession.ref();
		}
		if(this.primarySession!=null){
			primarySession.unref();
		}
		this.primarySession = primarySession;
	}
	*/
	
	/* AuthSessionリーク調査用 */
	/*
	@Override
	public void ref() {
		super.ref();
		if(logger.isDebugEnabled())logger.debug("AS ref:"+getPoolId()+":"+getRef(),new Exception());
	}

	@Override
	public boolean unref() {
		if(logger.isDebugEnabled())logger.debug("AS unref:"+getPoolId()+":"+getRef(),new Exception());
		return super.unref();
	}
	*/
}
