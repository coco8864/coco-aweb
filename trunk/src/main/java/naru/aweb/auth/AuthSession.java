package naru.aweb.auth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;

public class AuthSession extends PoolBase{
	public static AuthSession UNAUTH_SESSION=new AuthSession(new User(),"");
	private static Logger logger = Logger.getLogger(AuthSession.class);
	
	private User user;
	private String token;//CSRF対策
	private Map<String,Object> attribute=new HashMap<String,Object>();//sessionに付随する属性
	private boolean isLogout=false;
	private SessionId sessionId;
//	private AuthSession primarySession;
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
		for(AuthSession seconadSession:secandarySessions){
			seconadSession.logout();
		}
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
	}
	
	public AuthSession createSecondarySession(){
		AuthSession secodarySession=Authenticator.internalCreateAuthSession(user);
//		secodarySession.setPrimarySession(this);
		secandarySessions.add(secodarySession);
		return secodarySession;
	}
	
	public User getUser() {
		return user;
	}
	
	public synchronized void logout(){
		if(isLogout){
			return;
		}
		user.logout();
		isLogout=true;
		for(LogoutEvent evnet:logoutEvents){
			evnet.onLogout();//onLogoutイベント中synchronizedはタブー
		}
		logoutEvents.clear();
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

	public SessionId getSessionId() {
		return sessionId;
	}

	public void setSessionId(SessionId sessionId) {
		this.sessionId = sessionId;
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
	
	/* AuthSessionリーク調査用
	@Override
	public void ref() {
		super.ref();
		logger.debug("ref:"+getPoolId(),new Exception());
	}

	/*
	@Override
	public boolean unref() {
		logger.debug("unref:"+getPoolId(),new Exception());
		return super.unref();
	}
	*/
}
