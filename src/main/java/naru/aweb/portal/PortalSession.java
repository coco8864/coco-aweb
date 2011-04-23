package naru.aweb.portal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.config.CommissionAuth;
import naru.aweb.config.Config;
import naru.aweb.config.User;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.http.WebServerHandler;
import naru.aweb.queue.QueueManager;
import naru.aweb.util.ServerParser;
import net.sf.json.JSON;

public class PortalSession extends PoolBase{
	private static Logger logger=Logger.getLogger(PortalSession.class);
	private static QueueManager queueManager=QueueManager.getInstance();
	private static Config config=Config.getConfig();
	
	private static Pattern urlPattern=Pattern.compile("^(http|https)://([^/\\?]*)(/.*)");
	public static final String PORTAL_SESSION_KEY="portal";
	
	//basicAuth�������W�߂Ă���,�L�[�́AauthUrl
	private Map<String,Map<String,CommissionAuth>> basicAuths=new HashMap<String,Map<String,CommissionAuth>>();
	
	//401��200�ɕύX�������N�G�X�g��realm���o���Ă���
	private Map<String,String> authUrlRealm=new HashMap<String,String>();

	//��������basicAuth���W�߂Ă���,�L�[�́Aurl
	private Map<String,CommissionAuth> processingBasicAuths=new HashMap<String,CommissionAuth>();

	public boolean startBasicProcess(String url,CommissionAuth commissionAuth){
		if(commissionAuth==null){
			return true;
		}
		if(!commissionAuth.startBasicProcess()){
			return false;
		}
		processingBasicAuths.put(url, commissionAuth);
		return true;
	}
	
	public void endBasicProcess(String url){
		CommissionAuth commissionAuth=processingBasicAuths.remove(url);
		if(commissionAuth==null){
			return;
		}
		commissionAuth.endBasicProcess();
	}
	
	public String getRealm(String authUrl){
		return authUrlRealm.get(authUrl);
	}
	public void putRealm(String authUrl,String authentication){
		String[] valAuthUrls=variationUrl(authUrl);
		for(String url:valAuthUrls){
			authUrlRealm.put(url, authentication);
		}
	}
	
	private String[] variationUrl(String url){
		Matcher matcher;
		synchronized(urlPattern){
			matcher=urlPattern.matcher(url);
		}
		if(!matcher.matches()){
			return null;
		}
		String proto=matcher.group(1);
		String server=matcher.group(2);
		String path=matcher.group(3);
		String shotcutServer=null;
		ServerParser serverParser=null;
		if("https".equals(proto)){
			serverParser=ServerParser.parse(server,443);
			if(serverParser.getPort()==443){
				shotcutServer=serverParser.getHost();
			}
		}else{
			serverParser=ServerParser.parse(server,80);
			if(serverParser.getPort()==80){
				shotcutServer=serverParser.getHost();
			}
		}
		StringBuffer sb=new StringBuffer();
		sb.append(proto);
		sb.append("://");
		sb.append(serverParser.toString());
		sb.append(path);
		serverParser.unref(true);
		String url1=sb.toString();
		if(shotcutServer==null){
			return new String[]{url1};
		}
		sb.setLength(0);
		sb.append(proto);
		sb.append("://");
		sb.append(shotcutServer);
		sb.append(path);
		return new String[]{url1,sb.toString()};
	}
	
	public CommissionAuth getBasicAuth(String domain,String realm){
		Map<String,CommissionAuth> realmMap=basicAuths.get(domain);
		if(realmMap==null){
			return null;
		}
		return realmMap.get(realm);
	}
	public void putBasicAuth(String domain,String realm,CommissionAuth auth){
		Map<String,CommissionAuth> realmMap=basicAuths.get(domain);
		if(realmMap==null){
			realmMap=new HashMap<String,CommissionAuth>();
			basicAuths.put(domain, realmMap);
		}
		realmMap.put(realm,auth);
	}

	@Override
	public void recycle() {
		if(chId!=null){
			queueManager.unsubscribe(chId);
			chId=null;
		}
		basicAuths.clear();
		authUrlRealm.clear();
	}
	
	//basicAuth���폜�A����basicInAuths�͍폜���Ȃ��̂ŁA���̂܂ܔF�؂𑱂���B
	public void removeBasicAuth(CommissionAuth auth){
		if(CommissionAuth.BASIC.equalsIgnoreCase(auth.getAuthType())){
			basicAuths.remove(auth.getAuthUrl());
		}
	}
	
	public static PortalSession getPortalSession(WebServerHandler handler){
		AuthSession authSession=handler.getAuthSession();
		PortalSession portalSession=(PortalSession)authSession.getAttribute(PORTAL_SESSION_KEY);
		if(portalSession==null){
			User user=(User)handler.getRequestAttribute(ServerBaseHandler.ATTRIBUTE_USER);
			portalSession=(PortalSession)PoolManager.getInstance(PortalSession.class);
			portalSession.init(user);
			authSession.setAttribute(PORTAL_SESSION_KEY,portalSession);
			//authSession���j�������Ƃ���portalSession�������ɔj�����Ă��炤
			portalSession.unref();
		}
		return portalSession;
	}
	
	public PortalSession(){
	}
	private User user;
	private String chId;
	private String chName;
	private Collection<CommissionAuth> commissionAuths;
	
	public String getCommissionAuthsJson(){
		JSON json=CommissionAuth.collectionToJson(commissionAuths);
		return json.toString();
	}
	
	private void init(User user){
		this.user=user;
		this.chName="PS_"+getPoolId();
		//�^�C���A�E�g���Ȃ�Queue���쐬
		this.chId=queueManager.createQueueByName(chName, user.getLoginId(), true, null,false);
		
		/*Collection<CommissionAuth> */commissionAuths=CommissionAuth.getByUser(user);
		Iterator<CommissionAuth> itr=commissionAuths.iterator();
		while(itr.hasNext()){
			CommissionAuth commissionAuth=itr.next();
			if(CommissionAuth.BASIC.equals(commissionAuth.getAuthType())){
				commissionAuth.initBasicProcess();
				putBasicAuth(commissionAuth.getAuthUrl(),commissionAuth.getRealm(),commissionAuth);
			}
		}
	}

	private static Pattern portalPathInfoPattern=Pattern.compile(";phportal=([^\\s;/?]*)");
	public static final String PORTAL_PATHINFO_KEY="portalPathInfo";
	
	public String getChId() {
		return chId;
	}
	public String getChName() {
		return chName;
	}

}
