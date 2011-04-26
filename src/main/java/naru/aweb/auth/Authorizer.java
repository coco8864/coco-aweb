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
	//�΂�΂�Ɋo�����������ʂȔr�������Ȃ��Ă悢
	//pathOneceId /auth����T�[�r�Xurl�ɑ΂���redirect����ۂ�path�t��
	private Map<String, SessionId> pathOnceIds = new HashMap<String, SessionId>();
	//temporaryIds�@�T�[�r�Xurl��/auth��redirect����ۂ�cookie��path�ɕt��
	private Map<String, SessionId> temporaryIds = new HashMap<String, SessionId>();
	//primaryId�@auth�ɕt��
	private Map<String, SessionId> primaryIds = new HashMap<String, SessionId>();
	//secondaryId�@�T�[�r�Xurl�ɕt��
	private Map<String, SessionId> secondaryIds = new HashMap<String, SessionId>();

	//temporaryIds�́A/auth���ł́AauthId�Ŏ��ʂ���B
	//authId��ʒm���郊�N�G�X�g�́A�R���e���c���ɂ��T�[�o�ɂ����M����Ȃ����AtemporaryIds�́ACookie�ɐݒ肵�Ă��邽��
	//�ʃ��N�G�X�g�Ŗ\�I����\��������B���̍ۂł��A/auth�ɑ΂���U���͂ł��Ȃ�
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
		count+=freeIds(ids);
		return count;
	}
	
	public void term() {
		TimerManager.clearInterval(timerContext);
		int count;
		count=freeIds(primaryIds);//���̒��ŁAsecondaryIds���N���A�����
		logger.info("Term free primaryIds:"+count);
		count=freeIds(pathOnceIds);
		logger.info("Term free pathOnceIds:"+count);
		count=freeIds(temporaryIds);//���̒��ŁAtemporaryAuthIds���N���A�����
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
		count=timeoutIds(primaryIds,now-sessionTimeout);//���̒��ŁAsecondaryIds���N���A�����
		if(count!=0){
			logger.info("session timeout primaryIds:"+count);
		}
		timeoutIds(pathOnceIds,now-PATH_ONCE_TIMEOUT);
		timeoutIds(temporaryIds,now-TEMPORARY_TIMEOUT);//���̒��ŁAtemporaryAuthIds���N���A�����
	}
	
	public boolean logout(String id){
		SessionId sessionId=getSessionId(Type.PRIMARY, id);
		if(sessionId==null){
			return false;
		}
		return sessionId.logout(id);
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
	
	//�\��������SessionId��ԋp����
	//�m�F����ɂ́A���̌�isMach������Ăяo���K�v������
	private SessionId getSessionId(Type type,String id){
		Map<String,SessionId> ids=getIds(type);
		return ids.get(id);
	}
	
	//���YSessionId�r���̒�����Ăяo�����
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
//				session.id = Config.encodeBase64(bytes);base64��"/"�𐶐����邩�炾��
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
//					session.id = Config.encodeBase64(bytes);base64��"/"�𐶐����邩�炾��
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
				temporaryId.setLastAccessTime();//���������΂�
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
				temporaryId.setLastAccessTime();//���������΂�
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
				temporaryId.setLastAccessTime();//���������΂�
				temporaryId.setAuthSession(authSession);
				return true;
			}
		}
		return false;
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
	
	/**
	 * �w�肳�ꂽid��mapping����,SecondaryId�����A���ݗL�����m�F
	 * 
	 * @param cookies
	 * @return
	 */
	public AuthSession getAuthSessionBySecondaryId(Mapping mapping,String id) {
		SessionId secondaryId = getSessionId(Type.SECONDARY,id);
		if (secondaryId == null) {
			return null;
		}
		SessionId primaryId=secondaryId.getPrimaryId();
		if (primaryId == null) {
			return null;//�u�ԓI�ɊJ������Ă��܂����ꍇ
		}
		
		//�����܂łł����܂ŉ\���̂���AprimaryId��secondaryId��߂܂���
		//�{���ɗ~������̂��ۂ��́A���b�N��m�F����
		synchronized(primaryId){
			if(!primaryId.isMatch(Type.PRIMARY)){
				return null;
			}
			synchronized(secondaryId){
				if(!secondaryId.isMatch(Type.SECONDARY, id,mapping,primaryId)){
					return null;
				}
			}
			if(mapping.isSessionUpdate()){//ws���̓^�C���A�E�g���Ԍv�Z���ɃZ�V�����A�N�Z�X�Ƃ݂Ȃ��Ȃ�
				primaryId.setLastAccessTime();//�ŏI�A�N�Z�X���Ԃ��X�V
			}
			AuthSession authSession=primaryId.getAuthSession();
			authSession.ref();
			return authSession;
		}
	}
	
	/**
	 * �w�肳�ꂽid��url����,CookieOnceId�����A���ݗL�����m�F
	 * �m�F��A���YSessionId�͍폜
	 * 
	 * @param cookies
	 * @return
	 */
	public boolean isTemporaryId(String id,String url){
		SessionId temporaryId = getSessionId(Type.TEMPORARY,id);
		if (temporaryId == null) {
			return false;
		}
		//�����܂łł����܂ŉ\���̂���AprimaryId��secondaryId��߂܂���
		//�{���ɗ~������̂��ۂ��́A���b�N��m�F����
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
		//�����܂łł����܂ŉ\���̂���AprimaryId��secondaryId��߂܂���
		//�{���ɗ~������̂��ۂ��́A���b�N��m�F����
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
			//AuthSession secondarySession=Config.getConfig().getAuthenticator().secondLoginUser(null);//TODO 
			synchronized(secondaryId){
				secondaryId.setPrimaryId(primaryId);
				secondaryId.setAuthSession(secondarySession);
				primaryId.addSecondaryId(mapping, secondaryId);
				return true;
			}
		}
	}

	//�����`�F�b�N�A�F�����{��
	public boolean authorize(Mapping mapping, AuthSession authSession) {
		List<String> mappingRoles=mapping.getRolesList();
		if(mappingRoles.size()==0){//�O�Ƀ`�F�b�N���Ă��邪�O�̂���
			return true;
		}
		User user=authSession.getUser();
		
		// List<String> mappingRoles = mapping.getRolesList();
		if (mappingRoles == null) {// DispatchResponseHandler�̏ꍇ
			return true;
		}
		List<String> userRoles = user.getRolesList();
		if (userRoles.contains(User.ROLE_ADMIN)) {// admin role�������̂͂Ȃ�ł��g����
			return true;
		}
		if (mappingRoles.isEmpty()) {// role�������Ȃ�mapping�͒N�ł��g����
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
	 * @return pathOnceId����PrimaryId�̉\��������ASessionId��ԋp����B(���b�N���͂������߁A�J�������\������j
	 */
	public String createSecondaryCookieFromPathOnceId(String pathId,String url,Mapping mapping,String cookiePath,boolean isSecure){
		SessionId pathOnceId = getSessionId(Type.PATH_ONCE,pathId);
		if (pathOnceId == null) {
			return null;
		}
		SessionId primaryId=pathOnceId.getPrimaryId();
		if (primaryId == null) {
			return null;//�u�ԓI�ɊJ������Ă��܂����ꍇ
		}
		
		//SecondaryId�́Acreate���ƁA���e�ݒ莞�̂Q��lock����
		SessionId secondaryId = SessionId.createSecondaryId();//1���
		String cookieString=secondaryId.getSetCookieString(cookiePath, isSecure);
		if( setupSecondaryId(secondaryId, primaryId, pathId, pathOnceId, url, mapping) ){//2���
			return cookieString;
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
	
	public SessionId createPathOnceIdByPrimary(String url, String cookieId) {
		if(cookieId==null){
			return null;
		}
		SessionId primaryId = getSessionId(Type.PRIMARY,cookieId);
		if (primaryId == null) {
			return null;
		}
		SessionId pathOnceId = SessionId.createPathOnceId(url,primaryId);
		return pathOnceId;
	}

	public SessionId createTemporaryId(String url) {
		SessionId temporaryId = SessionId.createTemporaryId(url);
		return temporaryId;
	}
	
	//�ȍ~authHandler�Ή�
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
