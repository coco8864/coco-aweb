package naru.aweb.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.Cookie;

/*
 * primaryId:/auth�ɕt�������cookie
 *  authentication => sessionTimeout or logoff
 * 
 * cookieOnceId:provider�����F�؎���redirect�Ɠ����ɕt�������cookie
 *  first service => redirect*2��F�Aor timeout
 * 
 * pathOnceId:/auth��redirect����pathinfo�ɕt������id
 *  authkakuninn => redirect��F�Aor timeout
 * 
 * secondaryId://provider���ɕt�������cookie
 *�@�@�F�� => sessionTimeout or logoff(primaryId�Ɠ���)
 * 
 * provider����
 * 1)cookie�����邩 -> no redirect /auth�@cookieOnceId�ǉ�
 * 2)secondaryId�� -> yes �F��
 * 3)cookieOnceId -> no redirect /auth�@cookieOnceId�ǉ�
 * 4)path��񂩂�pathOnceId�擾 -> no redirect /auth�@cookieOnceId�ǉ�
 * 5)cookieOnceId����path,���ۂ�path,pathOnceId����path ��r -> ��v���Ȃ� redirect /auth�@cookieOnceId�ǉ�
 * 6)�F��+secondaryId�ǉ�
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

	public static SessionId createSecondaryId() {
		return createSessionId(Type.SECONDARY,null,null,null);
	}
	
	public static SessionId createTemporaryId(String url) {
		return createSessionId(Type.TEMPORARY,url,null,null);
	}
	
	public static SessionId createPathOnceId(String url,SessionId primaryId) {
		return createSessionId(Type.PATH_ONCE,url,primaryId,null);
	}
	
	public static SessionId createPrimaryId(AuthSession authSession) {
		return createSessionId(Type.PRIMARY,null,null,authSession);
	}

	public static SessionId createSessionId(Type type,String url,SessionId primaryId,AuthSession authSession) {
		SessionId sessionId = (SessionId) PoolManager.getInstance(SessionId.class);
		sessionId.type=type;
		sessionId.isValid = true;
		sessionId.url=url;
		sessionId.setPrimaryId(primaryId);
		sessionId.authSession=authSession;
		if(authSession!=null){
			authSession.setSessionId(sessionId);
		}
		sessionId.lastAccessTime = System.currentTimeMillis();
		//id�̐����́Aauthorizer�ɔC����
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
				authSession.logout();//���̐��remove���Ă΂��
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
	 * �w�肳�ꂽid���������ꍇ�폜����B
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
			//isValid�́AcreateSessionId��true�ɂ��Ă����ł���false��ݒ肵�Ȃ��B
			//�P��SessionID�ɑ΂��āA���̃��[�g���Q�񑖍s���鎖�͂Ȃ�
			authorizer.removeSessionId(type,this.id);
			isValid = false;
			unref();
		}
		return counter;
	}

	public enum Type {
		PRIMARY, SECONDARY, TEMPORARY, PATH_ONCE
	}

	private boolean isValid;// map�����ꂽ�Ƃ��Ă����ꂪfalse�̏ꍇ������Ȃ��������ɂ���
	private Type type;
	private String id;
	private AuthSession authSession;
	private SessionId primaryId;
	private String url;
	private long lastAccessTime;
	private Mapping mapping;//secondary�p�̏ꍇ�A�ǂ�mapping�p��SessionId��

	/*
	public void onTimer(Object userContext) {
		try {
			switch (type) {
			case PRIMARY:// �Z�V�����^�C���A�E�g,30minutes
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
			case COOKIE_ONCE:// �F�؏����͂��x������ꍇ�́A�Ď��s,5seconds
				remove();
				break;
			case PATH_ONCE:// redirect���x��,5seconds
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
	
	public boolean isMatch(Type type,String id,Mapping mapping){
		if(!isMatch(type,id)){
			return false;
		}
		if(mapping.getSourceType()!=Mapping.SourceType.WS){//WebSocket�͒��ڔF�؂��Ă��Ȃ�
			if(this.mapping!=mapping){
				//mapping��reload����ƃC���X�^���X���ς��̂ł��ꂪ��v���Ȃ��Ȃ�
				if(!this.mapping.getId().equals(mapping.getId())){
					return false;
				}
				logger.debug("relaod mapping."+mapping.getId());
				//�Â�mapping�͖Y�ꂽ�����悢
				setMapping(mapping);
//				this.mapping=mapping;
			}
		}
		return true;
	}
	
	public boolean isMatch(Type type,String id,String url){
		if(!isMatch(type,id)){
			return false;
		}
		if(!this.url.equals(url)){
			return false;
		}
		return true;
	}
	
	public boolean isMatch(Type type,String id,String url,SessionId promaryId){
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

	public String getSetCookieString(String path, boolean isSecure) {
		return Cookie.formatSetCookieHeader(SESSION_ID, getId(), null, path,-1, isSecure);
	}
	
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
	 * CookieOnceId�̏ꍇ�A/auth�ɒʒm����id
	 */
	private String authId;
	public void setAuthId(String authId) {
		this.authId=authId;
	}
	public String getAuthId(){
		return authId;
	}

	public void setMapping(Mapping mapping) {
		this.mapping=mapping;
	}

}
