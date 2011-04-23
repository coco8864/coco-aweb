package naru.aweb.auth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.aweb.config.User;

public class AuthSession extends PoolBase{
	public static AuthSession UNAUTH_SESSION=new AuthSession(new User(),"");
	private static Logger logger = Logger.getLogger(AuthSession.class);
	
	private User user;
	private String token;//CSRF�΍�
	private Map<String,Object> attribute=new HashMap<String,Object>();//session�ɕt�����鑮��
	private boolean isLogout=false;
	private Set<LogoutEvent> logoutEvents=new HashSet<LogoutEvent>();
	
	public void recycle() {
		Iterator<Object> itr=attribute.values().iterator();
		while(itr.hasNext()){
			Object v=itr.next();
			if(v instanceof PoolBase){
				((PoolBase)v).unref();
			}
		}
		attribute.clear();
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
	public User getUser() {
		return user;
	}
	
	public synchronized void logout(){
		user.logout();
		isLogout=true;
		for(LogoutEvent evnet:logoutEvents){
			evnet.onLogout();
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
		if(value instanceof PoolBase){
			((PoolBase)value).ref();
		}
		attribute.put(name, value);
	}
	
	public Iterator<String> getAttributeNames(){
		return attribute.keySet().iterator();
	}

	public String getToken() {
		return token;
	}
	
	/* AuthSession���[�N�����p
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
