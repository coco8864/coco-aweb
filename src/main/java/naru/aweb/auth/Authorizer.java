package naru.aweb.auth;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import naru.async.Timer;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.timer.TimerManager;
import naru.aweb.auth.SessionId.Type;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.util.ServerParser;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

public class Authorizer implements Timer{
	private static Logger logger = Logger.getLogger(Authorizer.class);
	private static final String SESSION_TIMEOUT="sessionTimeout";
	private static final String AUTHORIZE_RANDOM_ENTOROPY="authorizeRandomEntoropy";
		
	private static final long INTERVAL=1000*60;
	private static final long PATH_ONCE_TIMEOUT=5000;
	private static final long TEMPORARY_TIMEOUT=5000;
	private SecureRandom random;
	//ばらばらに覚えた方が無駄な排他をしなくてよい
	//pathOneceId /authからサービスurlに対してredirectする際にpath付加
	private Map<String, SessionId> pathOnceIds = new HashMap<String, SessionId>();
	//temporaryIds　サービスurlが/authにredirectする際にcookieとpathに付加
	private Map<String, SessionId> temporaryIds = new HashMap<String, SessionId>();
	//primaryId　authに付加
	private Map<String, SessionId> primaryIds = new HashMap<String, SessionId>();
	//secondaryId　サービスurlに付加
	private Map<String, SessionId> secondaryIds = new HashMap<String, SessionId>();

	//temporaryIdsは、/auth側では、authIdで識別する。
	//authIdを通知するリクエストは、コンテンツ中にもサーバにも送信されないが、temporaryIdsは、Cookieに設定しているため
	//別リクエストで暴露する可能性がある。この際でも、/authに対する攻撃はできない
	private Map<String, SessionId> temporaryAuthIds = new HashMap<String, SessionId>();
	
	private Object timerContext= null;
	private long sessionTimeout;
	private Configuration configuration;
	public Authorizer(Configuration configuration) {
		this.configuration=configuration;
		timerContext = TimerManager.setInterval(INTERVAL, this, null);
		Config config = Config.getConfig();
		random=config.getRandom(AUTHORIZE_RANDOM_ENTOROPY);
		sessionTimeout = configuration.getLong(SESSION_TIMEOUT, 1000*60*30);
	}

	private int freeIds(Map<String, SessionId> ids){
		String snapshootIds[];
		synchronized(ids){
			int size=ids.size();
			if(size==0){
				return 0;
			}
			snapshootIds=ids.keySet().toArray(new String[size]);
		}
		int count=0;
		for(String idString:snapshootIds){
			SessionId id=ids.get(idString);
			if(id==null){
				continue;
			}
			count+=id.remove(idString);
		}
//		count+=freeIds(ids);
		return count;
	}
	
	public void term() {
		TimerManager.clearInterval(timerContext);
		int count=0;
//		count=freeIds(primaryIds);//この中で、secondaryIdsもクリアされる.ここでConcurrentModificationException発生
//		for(SessionId primaryId:primaryIds.values()){
//			AuthSession authSession=primaryId.getAuthSession();
//			if(authSession!=null){
//				count++;
//				authSession.logout();
//			}
//		}
		while(true){
			if(primaryIds.isEmpty()){
				break;
			}
			for(SessionId primaryId:primaryIds.values()){
				AuthSession authSession=primaryId.getAuthSession();
				if(authSession!=null){
					count++;
					authSession.logout();//この先でprimaryIdsを削除する
					break;
				}
			}
		}
		logger.info("Term free primaryIds:"+count);
		count=freeIds(pathOnceIds);
		logger.info("Term free pathOnceIds:"+count);
		count=freeIds(temporaryIds);//この中で、temporaryAuthIdsもクリアされる
		logger.info("Term free temporaryIds:"+count);
	}
	
	private int timeoutIds(Map<String, SessionId> ids,long lastAccessLimit){
		String snapshootIds[];
		synchronized(ids){
			int size=ids.size();
			if(size==0){
				return 0;
			}
			snapshootIds=ids.keySet().toArray(new String[size]);
		}
		int count=0;
		for(String idString:snapshootIds){
			SessionId id=ids.get(idString);
			if(id==null){
				continue;
			}
			if( id.logoutIfTimeout(idString, lastAccessLimit) ){
				count++;
			}
		}
		return count;
	}
	
	public void onTimer(Object userContext) {
		long now=System.currentTimeMillis();
		int count=0;
		count=timeoutIds(primaryIds,now-sessionTimeout);//この中で、secondaryIdsもクリアされる
		if(count!=0){
			logger.info("session timeout primaryIds:"+count);
		}
		timeoutIds(pathOnceIds,now-PATH_ONCE_TIMEOUT);
		timeoutIds(temporaryIds,now-TEMPORARY_TIMEOUT);//この中で、temporaryAuthIdsもクリアされる
	}
	
	public boolean logout(String id){
		SessionId sessionId=getSessionId(Type.PRIMARY, id);
		if(sessionId==null){
			return false;
		}
		AuthSession authSession=sessionId.getAuthSession();
		authSession.logout();
		return true;
	}
	
	private Map<String,SessionId> getIds(Type type){
		switch(type){
		case TEMPORARY:
			return temporaryIds;
		case PATH_ONCE:
			return pathOnceIds;
		case SECONDARY:
			return secondaryIds;
		case PRIMARY:
			return primaryIds;
		}
		return null;
	}
	
	//可能性があるSessionIdを返却する
	//確認するには、その後isMachからを呼び出す必要がある
	private SessionId getSessionId(Type type,String id){
		Map<String,SessionId> ids=getIds(type);
		return ids.get(id);
	}
	
	//当該SessionId排他の中から呼び出される
	public boolean removeSessionId(Type type,String id){
		Map<String,SessionId> ids=getIds(type);
		Map<String,SessionId> authIds=null;
		if(type==Type.TEMPORARY){
			authIds=temporaryAuthIds;
		}
		SessionId removeId=null;
		synchronized(ids){
			removeId=ids.remove(id);
			if( removeId==null){
				return false;
			}
		}
		if(authIds!=null){
			String authId=removeId.getAuthId();
			synchronized(authIds){
				authIds.remove(authId);
			}
		}
		return true;
	}
	
	public void registerSessionId(SessionId sessionId){
		byte[] bytes = (byte[]) PoolManager.getArrayInstance(byte.class, 16);
		Map<String, SessionId> ids=getIds(sessionId.getType());
		Map<String, SessionId> authIds=null;
		if(sessionId.getType()==Type.TEMPORARY){
			authIds=temporaryAuthIds;
		}
		synchronized(sessionId){
			while (true) {
				random.nextBytes(bytes);
//				session.id = Config.encodeBase64(bytes);base64は"/"を生成するからだめ
				String id = DataUtil.byteToString(bytes);// Config.encodeBase64(bytes);
				synchronized(ids){
					if(ids.containsKey(id)){
						continue;
					}
					sessionId.setId(id);
					ids.put(id, sessionId);
				}
				break;
			}
			if(authIds!=null){
				while (true) {
					random.nextBytes(bytes);
//					session.id = Config.encodeBase64(bytes);base64は"/"を生成するからだめ
					String id = DataUtil.byteToString(bytes);// Config.encodeBase64(bytes);
					synchronized(authIds){
						if(authIds.containsKey(id)){
							continue;
						}
						sessionId.setAuthId(id);
						authIds.put(id, sessionId);
					}
					break;
				}
			}
			PoolManager.poolArrayInstance(bytes);
		}
	}
	
	public SessionId getTemporaryId(String authId){
		if(authId==null){
			return null;
		}
		SessionId temporaryId=temporaryAuthIds.get(authId);
		if(temporaryId==null){
			return null;
		}
		synchronized(temporaryId){
			if(temporaryId.isMatch(Type.TEMPORARY)){
				temporaryId.setLastAccessTime();//寿命を延ばす
				return temporaryId;
			}
		}
		return null;
	}
	
	public String getUrlFromTemporaryId(String authId){
		if(authId==null){
			return null;
		}
		SessionId temporaryId=temporaryAuthIds.get(authId);
		if(temporaryId==null){
			return null;
		}
		synchronized(temporaryId){
			if(temporaryId.isMatch(Type.TEMPORARY)){
				temporaryId.setLastAccessTime();//寿命を延ばす
				return temporaryId.getUrl();
			}
		}
		return null;
	}
	
	public boolean setAuthSessionToTemporaryId(String authId,AuthSession authSession){
		if(authId==null){
			return false;
		}
		SessionId temporaryId=temporaryAuthIds.get(authId);
		if(temporaryId==null){
			return false;
		}
		synchronized(temporaryId){
			if(temporaryId.isMatch(Type.TEMPORARY)){
				temporaryId.setLastAccessTime();//寿命を延ばす
				temporaryId.setAuthSession(authSession);
				return true;
			}
		}
		return false;
	}
	
	public static final int CHECK_NO_PRIMARY=1;
	public static final int CHECK_PRIMARY_ONLY=2;
	public static final int CHECK_SECONDARY_OK=3;
	
	public int checkSecondarySessionByPrimaryId(String id,String authUrl,StringBuffer appId){
		if(id==null){
			return CHECK_NO_PRIMARY;
		}
		SessionId primaryId = getSessionId(Type.PRIMARY,id);
		if (primaryId == null) {
			return CHECK_NO_PRIMARY;
		}
		synchronized(primaryId){
			if(primaryId.isMatch(Type.PRIMARY, id)==false){
				return CHECK_NO_PRIMARY;
			}
			AuthSession authSession=primaryId.getAuthSession();
			if(authSession==null){
				return CHECK_NO_PRIMARY;
			}
			AuthSession secondarySession=authSession.getSecondarySession(authUrl);
			if(secondarySession==null){
				return CHECK_PRIMARY_ONLY;
			}
			if(appId!=null){
				String secondaryId=secondarySession.getSessionId().getId();
				appId.append(DataUtil.digestHex(secondaryId.getBytes()));
			}
		}
		return CHECK_SECONDARY_OK;
	}
	
	public User getUserByPrimaryId(String id){
		if(id==null){
			return null;
		}
		SessionId primaryId = getSessionId(Type.PRIMARY,id);
		if (primaryId == null) {
			return null;
		}
		synchronized(primaryId){
			if(primaryId.isMatch(Type.PRIMARY, id)==false){
				return null;
			}
			AuthSession authSession=primaryId.getAuthSession();
			if(authSession==null){
				return null;
			}
			return authSession.getUser();
		}
	}
	
	public boolean isSecondaryId(String id,StringBuffer appId){
		SessionId secondaryId = getSessionId(Type.SECONDARY,id);
		if (secondaryId == null) {
			return false;
		}
		if(appId!=null){
			appId.append(DataUtil.digestHex(secondaryId.getId().getBytes()));
		}
		return true;
	}
	
	/**
	 * 指定されたidとmappingから,SecondaryIdを特定、存在有無を確認
	 * 
	 * @param cookies
	 * @return
	 */
	public AuthSession getAuthSessionBySecondaryId(String id,Mapping mapping,boolean isSecure,ServerParser domain) {
		SessionId secondaryId = getSessionId(Type.SECONDARY,id);
		if (secondaryId == null) {
			return null;
		}
		
		//ここまでであくまで可能性のある、primaryIdとsecondaryIdを捕まえた
		//本当に欲するものか否かは、ロック後確認する
		synchronized(secondaryId){
			if(!secondaryId.isMatch(Type.SECONDARY, id,isSecure,domain,mapping.getSourcePath())){
				return null;
			}
			if(mapping.isSessionUpdate()){//ws等はタイムアウト時間計算時にセションアクセスとみなさない
				SessionId primaryId=secondaryId.getPrimaryId();
				primaryId.setLastAccessTime();//最終アクセス時間を更新
			}
			AuthSession authSession=secondaryId.getAuthSession();
			authSession.ref();
			return authSession;
		}
	}
	
	/**
	 * 指定されたidとurlから,CookieOnceIdを特定、存在有無を確認
	 * 確認後、当該SessionIdは削除
	 * 
	 * @param cookies
	 * @return
	 */
	public boolean isTemporaryId(String id,String url){
		SessionId temporaryId = getSessionId(Type.TEMPORARY,id);
		if (temporaryId == null) {
			return false;
		}
		//ここまでであくまで可能性のある、primaryIdとsecondaryIdを捕まえた
		//本当に要求したものか否かは、ロック後確認する
		synchronized(temporaryId){
			try {
				if(!temporaryId.isMatch(Type.TEMPORARY,id,url)){
					return false;
				}
				return true;
			} finally {
				temporaryId.remove();
			}
		}
	}
	
	private boolean setupSecondaryId(SessionId secondaryId,SessionId primaryId,String pathId,SessionId pathOnceId,String url,Mapping mapping){
		//ここまでであくまで可能性のある、primaryIdとsecondaryIdを捕まえた
		//本当に欲するものか否かは、ロック後確認する
		synchronized(primaryId){
			if(!primaryId.isMatch(Type.PRIMARY)){
				return false;
			}
			synchronized(pathOnceId){
				if(!pathOnceId.isMatch(Type.PATH_ONCE, pathId,url,primaryId)){
					return false;
				}
				pathOnceId.remove();
			}
			AuthSession primarySession=primaryId.getAuthSession();
			AuthSession secondarySession=primarySession.createSecondarySession();
			secondarySession.setSessionId(secondaryId);
			synchronized(secondaryId){
				secondaryId.setPrimaryId(primaryId);
				secondaryId.setAuthSession(secondarySession);
//				secondaryId.setMapping(mapping);
				return true;
			}
		}
	}

	//権限チェック、認可処理本体
	public boolean authorize(Mapping mapping, AuthSession authSession) {
		List<String> mappingRoles=mapping.getRolesList();
		if(mappingRoles.size()==0){//前にチェックしているが念のがめ
			return true;
		}
		User user=authSession.getUser();
		
		// List<String> mappingRoles = mapping.getRolesList();
		//if (mappingRoles == null) {// DispatchResponseHandlerの場合
		//	return true;
		//}
		List<String> userRoles = user.getRolesList();
		if (userRoles.contains(User.ROLE_ADMIN)) {// admin roleを持つものはなんでも使える
			return true;
		}
		if (mappingRoles.isEmpty()) {// roleを持つたないmappingは誰でも使える
			return true;
		}
		Iterator<String> itr = mappingRoles.iterator();
		while (itr.hasNext()) {
			if (userRoles.contains(itr.next())) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * @param id
	 * @param url
	 * @return pathOnceIdからPrimaryIdの可能性がある、SessionIdを返却する。(ロックをはずすため、開放される可能性あり）
	 */
	public SessionId createSecondarySetCookieStringFromPathOnceId(String pathId,String url,Mapping mapping,
			boolean isCookieSecure,String cookieDomain,String cookiePath,StringBuffer appId) {			
		SessionId pathOnceId = getSessionId(Type.PATH_ONCE,pathId);
		if (pathOnceId == null) {
			return null;
		}
		SessionId primaryId=pathOnceId.getPrimaryId();
		if (primaryId == null) {
			return null;//瞬間的に開放されてしまった場合
		}
		
		//SecondaryIdは、create時と、内容設定時の２回lockする
		SessionId secondaryId = SessionId.createSecondaryId(isCookieSecure,cookieDomain,cookiePath);//1回目
//		String cookieString=secondaryId.getSetCookieString(cookiePath, isSecure);
		if( setupSecondaryId(secondaryId, primaryId, pathId, pathOnceId, url, mapping) ){//2回目
			if(appId!=null){
				appId.append(DataUtil.digestHex(secondaryId.getId().getBytes()));
			}
			return secondaryId;
		}
		secondaryId.remove();
		return null;
	}

	public SessionId createPrimaryId(AuthSession authSession) {
		SessionId primaryId = SessionId.createPrimaryId(authSession);
		return primaryId;
	}

	public SessionId createPathOnceIdByPrimary(String cookieId) {
		return createPathOnceIdByPrimary(null,cookieId);
	}
	
	/**
	 * Primaryがあれば対象URL向けのPathOnceIdを作成
	 * Primaryがなければnullを返却
	 * @param url
	 * @param cookieId
	 * @return
	 */
	public SessionId createPathOnceIdByPrimary(String url, String cookieId) {
		if(cookieId==null){
			return null;
		}
		SessionId primaryId = getSessionId(Type.PRIMARY,cookieId);
		if (primaryId == null) {
			return null;
		}
		//TODO このprimaryIdで許されるurlかをチェック,mapping対象か否か?
		//まずは、mapping全体からチェック。
		//このprimaryIdで許されるかどうかはDispatch時に再度チェックされるので必須ではない
		
		SessionId pathOnceId = SessionId.createPathOnceId(url,primaryId);
		return pathOnceId;
	}
	
	public SessionId createTemporaryId(String url,String cookiePath,boolean isDirectUrl) {
		SessionId temporaryId = createTemporaryId(url,cookiePath);
		temporaryId.setDirectUrl(isDirectUrl);
		return temporaryId;
	}

	public SessionId createTemporaryId(String url,String cookiePath) {
		SessionId temporaryId = SessionId.createTemporaryId(url,cookiePath);
		return temporaryId;
	}
	
	//以降authHandler対応
	private String authUrl;
	private String authPath;
	private boolean isAuthSsl;
	private String delCookieString;
	
	public void setupAuthUrl(boolean isAuthSsl,String authPath,String host,int port){
		this.isAuthSsl=isAuthSsl;
		this.authPath=authPath;
		if(isAuthSsl){
			authUrl="https://" + host + ":" + port +authPath;
		}else{
			authUrl="http://" + host + ":" + port +authPath;
		}
		delCookieString=SessionId.delCookieString(getAuthPath(), isAuthSsl());
		logger.info("authUrl:"+authUrl);
	}
	
	public String getAuthUrl(){
		return authUrl;
	}

	public boolean isAuthSsl(){
		return isAuthSsl;
	}
	
	public String getAuthPath(){
		return authPath;
	}
	public String delCookieString(){
		return delCookieString;
	}

	public long getSessionTimeout() {
		return sessionTimeout;
	}

	public void setSessionTimeout(long sessionTimeout) {
		configuration.setProperty(SESSION_TIMEOUT, sessionTimeout);
		this.sessionTimeout = sessionTimeout;
	}
}
