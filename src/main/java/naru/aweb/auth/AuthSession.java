package naru.aweb.auth;

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
	private static final String SID_SEQUENCE = "sidSecuence";
	private static final long SID_SAVE_INTERVAL = 1024l;
	
	public static AuthSession UNAUTH_SESSION=new AuthSession(new User(),"");
	private static Logger logger = Logger.getLogger(AuthSession.class);
	private static String serverId=config.getSelfDomain() + config.getString("serverIdEntropy","serverIdEntropy")+System.currentTimeMillis()+".";
	private static long sidSeq=config.getLong(SID_SEQUENCE,0)+SID_SAVE_INTERVAL;
	private static SecureRandom sidRandom=config.getRandom(SID_RANDOM_ENTOROPY);
	
	private User user;
	private String token;//CSRF�΍�
	private String sid;//�N���C�A���g�ł��̃Z�V���������ʂ���ID,���T�[�o���܂߂Ĉ��
	private Map<String,Object> attribute=new HashMap<String,Object>();//session�ɕt�����鑮��
	private boolean isLogout=false;
	private SessionId sessionId;
	private Set<LogoutEvent> logoutEvents=new HashSet<LogoutEvent>();
	private Set<AuthSession> secandarySessions=new HashSet<AuthSession>();
	
	private synchronized static long getSidSeq(){
		sidSeq++;
		if(sidSeq%SID_SAVE_INTERVAL==0){
			config.setProperty(SID_SEQUENCE, sidSeq);
		}
		return sidSeq;
	}
	
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
		this.sid=DataUtil.digestHex((serverId+getSidSeq()).getBytes());
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
					evnet.onLogout();//onLogout�C�x���g��synchronized�̓^�u�[
				}
				secondarySession.logoutEvents.clear();
				SessionId secondaryId=secondarySession.getSessionId();
				secondaryId.remove();
				secondarySession.unref();
			}
		}
		secandarySessions.clear();
		for(LogoutEvent evnet:logoutEvents){
			evnet.onLogout();//onLogout�C�x���g��synchronized�̓^�u�[
		}
		logoutEvents.clear();
		sessionId.remove();//sessionId���J������
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
		return sessionId.getLastAccessTime();
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
	
	/* AuthSession���[�N�����p
	@Override
	public void ref() {
		super.ref();
		logger.debug("ref:"+getPoolId(),new Exception());
	}

	@Override
	public boolean unref() {
		logger.debug("unref:"+getPoolId(),new Exception());
		return super.unref();
	}*/
}
