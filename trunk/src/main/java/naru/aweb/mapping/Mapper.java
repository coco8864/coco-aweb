package naru.aweb.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.jdo.Extent;
import javax.jdo.PersistenceManager;

import naru.aweb.admin.AdminHandler;
import naru.aweb.auth.AuthHandler;
import naru.aweb.auth.Authorizer;
import naru.aweb.auth.MappingAuth;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.Mapping.SecureType;
import naru.aweb.config.Mapping.SourceType;
import naru.aweb.core.RealHost;
import naru.aweb.util.JdoUtil;
import naru.aweb.util.ServerParser;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;
/**
 * mapper�́AServer(Host,port),Path�̃}�b�s���O���s��
 * Query�́ArequestHeader���Q�Ƃ��鎖
 * config�̉Ɨ��Ȃ̂ŁAstatic��Config�������Ȃ���
 * @author Naru
 *
 */
public class Mapper {
	private static final String OPTION_AUTH = "auth";//mapping�x�[�X�ŔF�؂���ꍇ�ɐݒ�
	private static final String OPTION_PAC = "pac";//proxy������pac�ɔ��f����ꍇ�Atrue��ݒ肷��
	private static final String OPTION_AUTH_HANDLER = "authHandler";//authHandler��ture��ݒ�
	private static final String OPTION_PEEK = "peek";//ssl proxy�̓��������ꍇ�Afalse��ݒ�
	private static final String OPTION_PUBLIC_WEB = "publicWeb";//js���Œ�̃R���e���c���_�E�����[�h����URL���v�Z���邽��
	private static final String OPTION_ADMIN_HANDLER = "adminHandler";//admin�ւ�URL���v�Z���邽��
	private static Logger logger = Logger.getLogger(Mapper.class);
	private Config config;
	
	//admin�n�A�K�{mapping
	private Set<Mapping> entryMappings=new HashSet<Mapping>();
	private Set<Mapping> activeMappings=new TreeSet<Mapping>(Mapping.mappingComparator);
	private Set<Mapping> activeSslProxyMappings=new TreeSet<Mapping>(Mapping.mappingComparator);
//	private Mapping authMapping;
	
	private Set<Mapping> activeWebMappings=new TreeSet<Mapping>(Mapping.mappingComparator);
	private Set<Mapping> activeProxyMappings=new TreeSet<Mapping>(Mapping.mappingComparator);
	private Set<Mapping> activeWsMappings=new TreeSet<Mapping>(Mapping.mappingComparator);
	private Set<Mapping> activePeekSslProxyMappings=new TreeSet<Mapping>(Mapping.mappingComparator);
	
	//pac���痘�p
	private Set<String> securePhantomDomains=new HashSet<String>();
	private Set<String> httpPhantomDomains=new HashSet<String>();
//	private RealHost proxyPacRealHost;//pac�ɏo�͂��鎩�T�[�o���

//	private RealHost adminRealHost;
//	private boolean isAdminSsl;
//	private RealHost publicWebRealHost;
//	private boolean isPublicWebSsl;
	private Mapping authMapping=null;//�F�ؗpmapping
	private String publicWebUrl;
	private String adminUrl;
	private int pacProxyPort;//pac�ɏo�͂��鎩�T�[�o�̃|�[�g�ԍ�
	
	public void addMapping(Mapping mapping){
	}
	
	public void delMapping(long id){
	}
	
	public void updateMapping(Mapping mapping){
	}
	
	private void unloadMappings(){
		for(Mapping mapping:entryMappings){
			mapping.tearDown();
		}
		entryMappings.clear();
		activeMappings.clear();
		activeSslProxyMappings.clear();
		securePhantomDomains.clear();
		httpPhantomDomains.clear();
	}
	
	private void loadMappings(){
		PersistenceManager pm=JdoUtil.currentPm();
		Extent<Mapping> extent=pm.getExtent(Mapping.class);
		Iterator<Mapping>itr=extent.iterator();
		while(itr.hasNext()){
			Mapping mapping=itr.next();
			pm.makeTransient(mapping);
//			System.out.println("json:"+mapping.toJson());
			if(mapping.setup()==false){//TODO mapping.setup��false�ŕ��A�����炷�ׂ�rollback���ׂ�?
				logger.error("fail to mapping setup.id:" + mapping.getId()+":note:"+mapping.getNotes());
				continue;
			}
			loadMapping(mapping);
		}
	}
	
	private void setupAuthUrl(Mapping mapping){
		authMapping=mapping;
		Authorizer authorizer=config.getAuthorizer();
		String selfDomain = config.getString("selfDomain");
		String realHostName=mapping.getRealHostName();
		RealHost realHost=RealHost.getRealHost(realHostName);
		if(realHost==null){
			logger.warn("not found auth mapping realHost.realHostName:"+realHostName);
			return;
		}
		authorizer.setupAuthUrl( (mapping.getSecureType()==SecureType.SSL), 
								mapping.getSourcePath(), 
								selfDomain,
								realHost.getBindPort() );
	}
	
	public Mapping getAuthMapping(){
		return authMapping;
	}
	
	private void loadMapping(Mapping mapping){
		entryMappings.add(mapping);
		if(!mapping.isEnabled()){
			return;
		}
		if(Boolean.FALSE.equals(mapping.getOption(OPTION_PEEK))){
			synchronized(activeSslProxyMappings){//ssl proxy�͓���Ȃ��ߕʊǗ�
				activeSslProxyMappings.add(mapping);
			}
		}else{
			synchronized(activeMappings){
				activeMappings.add(mapping);
			}
		}
		String selfDomain=config.getSelfDomain();
		
		RealHost realHost=RealHost.getRealHost(mapping.getRealHostName());
		if(AdminHandler.class.getName().equals(mapping.getDestinationServer())){
			StringBuilder sb=new StringBuilder();
			String sourceServer=mapping.getSourceServer();
			if(sourceServer==null || "".equals(sourceServer)){
				sourceServer=selfDomain;
			}
			if(mapping.getSecureType()==SecureType.SSL){
				sb.append("https://");
			}else{
				sb.append("http://");
			}
			sb.append(sourceServer);
			sb.append(":");
			sb.append(realHost.getBindPort());
			sb.append(mapping.getSourcePath());
			adminUrl=sb.toString();
		}
		Object publicWeb=mapping.getOption(OPTION_PUBLIC_WEB);//publicWeb��port�ƃv���g�R����m�邽��
		if(Boolean.TRUE.equals(publicWeb)){
			StringBuilder sb=new StringBuilder();
			String sourceServer=mapping.getSourceServer();
			if(sourceServer==null || "".equals(sourceServer)){
				sourceServer=selfDomain;
			}
			if(mapping.getSecureType()==SecureType.SSL){
				sb.append("https://");
			}else{
				sb.append("http://");
			}
			sb.append(sourceServer);
			sb.append(":");
			sb.append(realHost.getBindPort());
			sb.append(mapping.getSourcePath());
			publicWebUrl=sb.toString();
		}
		//pac�͕�����mapping�ɂ����Ă悢���A����realHost�͓���ł��鎖
//		Object authHandler=mapping.getOption(OPTION_AUTH_HANDLER);
		if(AuthHandler.class.getName().equals(mapping.getDestinationServer())){
			setupAuthUrl(mapping);//authorizer��auth�}�b�s���O��`��������
		}
		
		//mapping auth��`
		Object auth=mapping.getOption(OPTION_AUTH);
		if(auth!=null&&auth instanceof JSONObject){
			MappingAuth mappingAuth=new MappingAuth(config.getAuthenticator());
			if( mappingAuth.init((JSONObject)auth, mapping.getSourceType()==SourceType.PROXY) ){
				mapping.setMappingAuth(mappingAuth);
			}
		}
		
		Object pac=mapping.getOption(OPTION_PAC);//pac�ɔ��f���邩�ۂ�
		if(!Boolean.TRUE.equals(pac)){
			return;
		}
		switch(mapping.getSourceType()){
		case PROXY:
			switch(mapping.getSecureType()){
			case PLAIN:
				httpPhantomDomains.add(mapping.getSourceServerHost());
				pacProxyPort=realHost.getBindPort();
				break;
			case SSL:
				securePhantomDomains.add(mapping.getSourceServerHost());
				pacProxyPort=realHost.getBindPort();
				break;
			}
			break;
		case WEB:
		}
	}
	
	private MappingResult resolve(Set<Mapping> mappings,
			String realHost,
			SourceType sourceType,
			SecureType secureType,
			ServerParser server,
			String path){
		synchronized(mappings){
			Iterator<Mapping> itr=mappings.iterator();
			while(itr.hasNext()){
				Mapping mapping=itr.next();
				MappingResult result=mapping.resolve(realHost,sourceType,secureType,server,path);
				if(result!=null){
					return result;
				}
			}
		}
		return null;
	}
	
	public Mapper(Config config){
		this.config=config;
		loadMappings();
	}
	
	//mapping��ǂݒ���
	public void reloadMappings(){
		synchronized(activeMappings){
			unloadMappings();
			loadMappings();
		}
	}
	
	/* pac�Ή��A���̃��X�g�ɓ����Ă���domain�́Apahtom proxy�o�R�ł̃A�N�Z�X�ƂȂ� */
	public Set<String> getHttpPhantomDomians(){
		HashSet<String> result=new HashSet<String>(httpPhantomDomains);
		return result;
	}
	
	public Set<String> getSecurePhantomDomians(){
		HashSet<String> result=new HashSet<String>(securePhantomDomains);
		return result;
	}
	
	
	public MappingResult resolveProxy(String realHost, ServerParser server,String path){
//		RealHost host=config.getRealHost(server);
//		if(host!=null){//���T�[�o��proxy���悤�Ƃ��Ă���Ɣ��f
//			MappingResult mappingResult=resolveWeb(host.getName(),false,server,path);
//			return mappingResult;
//		}HeaderParser���őΏ��������ߕK�v�Ȃ�
		MappingResult mapping=resolve(activeMappings,realHost,SourceType.PROXY,SecureType.PLAIN,server,path);
		if(mapping!=null){
			return mapping;
		}
		return null;
	}
	
	public MappingResult resolveWs(String realHost, boolean isSsl,boolean isProxy,ServerParser hostHeader, String path){
		SecureType secureType;
		if(isSsl){
			secureType=SecureType.SSL;
		}else{
			secureType=SecureType.PLAIN;
		}
		SourceType sourceType;
		if(isProxy){
			sourceType=SourceType.WS_PROXY;
		}else{
			sourceType=SourceType.WS;
		}
		MappingResult mapping=resolve(activeMappings,realHost,sourceType,secureType,hostHeader,path);
		if(mapping!=null){
			return mapping;
		}
		return null;
	}
	
	
	public MappingResult resolveWeb(String realHost, boolean isSsl,ServerParser hostHeader, String path){
		SecureType secureType;
		if(isSsl){
			secureType=SecureType.SSL;
		}else{
			secureType=SecureType.PLAIN;
		}
		MappingResult mapping=resolve(activeMappings,realHost,SourceType.WEB,secureType,hostHeader,path);
		return mapping;
	}
	
	public MappingResult resolveSslProxy(String realHost, ServerParser server){
		MappingResult mapping=resolve(activeSslProxyMappings,realHost,SourceType.PROXY,SecureType.SSL,server,null);
		return mapping;
	}
	
	//sslMapping��resolve�͂Q�t�F�[�Y
	//1)CONNECT���N�G�X�g��Server��mapping
	//2)�f�R�[�h=hedader�擾��Path��mapping
	public boolean isPeekSslProxyServer(String realHost, ServerParser server){
		/*
		RealHost host=config.getRealHost(server);
		if(host!=null){//���T�[�o��proxy���悤�Ƃ��Ă���Ɣ��f
			return true;
		}
		*/
		return isPeekSslProxyServer(activeMappings,realHost,server);
	}
	
	private boolean isPeekSslProxyServer(Set<Mapping> mappings,String realHost,ServerParser server){
		synchronized(mappings){
			Iterator<Mapping> itr=mappings.iterator();
			while(itr.hasNext()){
				Mapping mapping=itr.next();
				boolean isPeek=mapping.isPeekSslProxyServer(realHost,server);
				if(isPeek){
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * server��mapping�͍ς݂����Awildcard�̉����ɂ͍ēx��mapping���K�v
	 * @param realHost
	 * @param server
	 * @param path
	 * @return
	 */
	public MappingResult resolvePeekSslProxy(String realHost, ServerParser server,String path){
		MappingResult mapping=resolve(activeMappings,realHost,Mapping.SourceType.PROXY,Mapping.SecureType.SSL,server,path);
		return mapping;
	}
	
	public String getPublicWebUrl(){
		return publicWebUrl;
	}
	public String getAdminUrl(){
		return adminUrl;
	}
	public int getPacProxyPort(){
		return pacProxyPort;
	}
	
	
	//authUrl��CookieLocation�x�[�X�ł̃`�F�b�N���s�����߁Aws�́Ahttp,wss�́Ahttps�Ƃ��đ��݂��m�F����
	public int checkCrossDomainWebWs(boolean isSsl,String path,String originUrl){
		boolean isSelf=RealHost.isSelfOrigin(originUrl);
		for(Mapping mapping:activeMappings){
			if(isSelf==false && mapping.isAllowOrigin(originUrl)==false){
				continue;
			}
			Mapping.SourceType sourceType=mapping.getSourceType();
			if(sourceType!=Mapping.SourceType.WEB&&sourceType!=Mapping.SourceType.WS){
				continue;
			}
			Mapping.SecureType secureType=mapping.getSecureType();
			if(isSsl && secureType==Mapping.SecureType.PLAIN){
				continue;
			}
			if(!isSsl && secureType==Mapping.SecureType.SSL){
				continue;
			}
			String sourcePath=mapping.getSourcePath();
			if(path.equals(sourcePath)){
				//mapping�F�؂������͔F�؂̕K�v�̂Ȃ�Mapping�̓`�F�b�N�̕K�v�Ȃ�
				if( mapping.getRolesList().size()==0 || mapping.getMappingAuth()!=null){
					return CHECK_MATCH_NO_AUTH;
				}else{
					return CHECK_MATCH_AUTH;
				}
			}
		}
		return CHECK_NOT_MATCH;
	}
	
	public static final int CHECK_MATCH_AUTH=1;
	public static final int CHECK_MATCH_NO_AUTH=2;
	public static final int CHECK_NOT_MATCH=3;
	
	public int checkCrossDomainProxy(Mapping.SourceType sourceType,boolean isSsl,String host,int port,String originUrl){
		boolean isSelf=RealHost.isSelfOrigin(originUrl);
		for(Mapping mapping:activeMappings){
			if(isSelf==false && mapping.isAllowOrigin(originUrl)==false){
				continue;
			}
			if(mapping.getSourceType()!=sourceType){
				continue;
			}
			Mapping.SecureType secureType=mapping.getSecureType();
			if(isSsl && secureType==Mapping.SecureType.PLAIN){
				continue;
			}
			if(!isSsl && secureType==Mapping.SecureType.SSL){
				continue;
			}
			if(mapping.matchSourceHost(host)&&mapping.matchSourcePost(port)){
				//mapping�F�؂������͔F�؂̕K�v�̂Ȃ�Mapping�̓`�F�b�N�̕K�v�Ȃ�
				if( mapping.getRolesList().size()==0 || mapping.getMappingAuth()!=null){
					return CHECK_MATCH_NO_AUTH;
				}else{
					return CHECK_MATCH_AUTH;
				}
			}
		}
		return CHECK_NOT_MATCH;
	}
}
