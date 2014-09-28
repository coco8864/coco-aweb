package naru.aweb.mapping;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import naru.async.pool.PoolManager;
import naru.aweb.admin.AdminHandler;
import naru.aweb.admin.StoreHandler;
import naru.aweb.auth.MappingAuth;
import naru.aweb.config.AppcacheOption;
import naru.aweb.config.Config;
import naru.aweb.core.RealHost;
import naru.aweb.handler.FileSystemHandler;
import naru.aweb.handler.ProxyHandler;
import naru.aweb.handler.SslProxyHandler;
import naru.aweb.handler.VelocityPageHandler;
import naru.aweb.handler.WsProxyHandler;
import naru.aweb.link.LinkHandler;
import naru.aweb.link.LinkManager;
import naru.aweb.util.JdoUtil;
import naru.aweb.util.ServerParser;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;
import net.sf.json.util.NewBeanInstanceStrategy;

import org.apache.log4j.Logger;

/**
 * ���N�G�X�g�Əo�̓R���|�[�l���g�̃}�b�s���O��\������I�u�W�F�N�g<br/>
 * Phantom Server Console��mapping�^�u�Őݒ肳��Ă�mapping��񂪎Q�Ƃł���<br />
 * 
 * @author naru
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION,table="MAPPING",detachable="true")
public class Mapping{
	public static final String OPTION_LOG_TYPE = "logType";
	public static final String OPTION_VELOCITY_PATTERN = "velocityPattern";
	public static final String OPTION_PROXY_PAC_PATH = "proxyPacPath";
	public static final String OPTION_FILE_WELCOME_FILES = "fileWeblcomeFiles";
	public static final String OPTION_FILE_LISTING = "fileListing";
	public static final String OPTION_FILE_RESOURCE_PATH = "resoucePath";
	public static final String OPTION_VELOCITY_USE = "velocityUse";
	public static final String OPTION_VELOCITY_RESOUCE_PATH = "velocityResoucePath";
	public static final String OPTION_VELOCITY_EXTENTIONS = "velocityExtentions";
	public static final String OPTION_AUTH = "auth";//mapping�x�[�X�ŔF�؂���ꍇ�ɐݒ�
	public static final String OPTION_PAC = "pac";//proxy������pac�ɔ��f����ꍇ�Atrue��ݒ肷��
	public static final String OPTION_PEEK = "peek";//ssl proxy�̓��������ꍇ�Afalse��ݒ�
	public static final String OPTION_PUBLIC_WEB = "publicWeb";//js���Œ�̃R���e���c���_�E�����[�h����URL���v�Z���邽��
	public static final String OPTION_ADMIN_HANDLER = "adminHandler";//admin�ւ�URL���v�Z���邽��
	public static final String OPTION_AUTH_HANDLER = "authHandler";//authHandler��ture��ݒ�
	public static final String OPTION_SUBPROTOCOL="subprotocol";
	public static final String OPTION_SKIP_PH_LOG="skipPhlog";
	public static final String OPTION_SHORT_FORMAT_LOG="shortFormatLog";
	public static final String OPTION_INJECTOR="injector";
	public static final String OPTION_REPLAY="replay";
	public static final String OPTION_FILTER="filter";
	public static final String OPTION_REPLAY_DOCROOT="replayDocroot";
	public static final String OPTION_APPCACHE = "appcache";
	public static final String OPTION_LINK = "link";
	//public static final String OPTION_LINK_NAME = "linkName";
	//public static final String OPTION_QUEUE_NAME = "queueName";
	
	public static Class ADMIN_HANDLER=AdminHandler.class;
	public static Class SSL_PROXY_HANDLER=SslProxyHandler.class;
	public static Class VELOCITY_PAGE_HANDLER=VelocityPageHandler.class;
	public static Class PROXY_HANDLER=ProxyHandler.class;
	public static Class FILE_SYSTEM_HANDLER=FileSystemHandler.class;
	public static Class STORE_HANDLER=StoreHandler.class;
	public static Class WS_PROXY_HANDLE=WsProxyHandler.class;
	public static Class PA_HANDLER=LinkHandler.class;
	
	private static Logger logger = Logger.getLogger(Mapping.class);
	private static JsonConfig jsonConfig;
	
	private static class MappingInstanceStrategy extends NewBeanInstanceStrategy{
		@Override
		public Object newInstance(Class clazz, JSONObject json)
				throws InstantiationException, IllegalAccessException,
				SecurityException, NoSuchMethodException,
				InvocationTargetException {
			if(clazz==Mapping.class){
				Long id=json.optLong("id",-1);
				if(id>=0){
					return getById(id);
				}
			}
			return clazz.newInstance();
		}
	}
		
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(Mapping.class);
		jsonConfig.setIgnoreTransientFields(true);
		jsonConfig.setExcludes(new String[]{"admin","auth","destinationFile",OPTION_LOG_TYPE,"userId","sourceServerHost","rolesList","sessionUpdate","mappingAuth","attributeNames"});
		jsonConfig.setNewBeanInstanceStrategy(new MappingInstanceStrategy());
	}
	
	public static void cleanup(JSONArray mappings){
		JdoUtil.cleanup(Mapping.class);
		int size=mappings.size();
		for(int i=0;i<size;i++){
			JSONObject mapping=mappings.getJSONObject(i);
			Mapping m=fromJson(mapping);
			m.save();
			logger.info("cleanup add mapping."+mapping);
		}
	}
	
	public static Mapping fromJson(String jsonString){
		JSON json=JSONObject.fromObject(jsonString);
		return fromJson(json);
	}
	
	public static Mapping fromJson(JSON json){
//		JSON json=JSONObject.fromObject(jsonString);
		//�����ňȉ�warning�����͑Ó�����
		//WARN  net.sf.json.JSONObject - Can't transform property 'destinationType' from java.lang.String into naru.aweb.config.Mapping$DestinationType. Will register a default Morpher
		//WARN  net.sf.json.JSONObject - Can't transform property 'secureType' from java.lang.String into naru.aweb.config.Mapping$SecureType. Will register a default Morpher
		//WARN  net.sf.json.JSONObject - Can't transform property 'sourceType' from java.lang.String into naru.aweb.config.Mapping$SourceType. Will register a default Morpher
		Mapping mapping=(Mapping)JSONSerializer.toJava(json,jsonConfig);
		return mapping;
	}
	
	public static Mapping getById(Long id){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Mapping mapping=pm.getObjectById(Mapping.class, id);
		return pm.detachCopy(mapping);
	}
	
	public static void deleteById(Long id){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		try {
			pm.currentTransaction().begin();
			Mapping mapping = pm.getObjectById(Mapping.class, id);
			if (mapping == null) {
				return;
			}
			pm.deletePersistent(mapping);
			pm.currentTransaction().commit();
		} finally {
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
	}

	private static final String MAPPING_QUERY="SELECT FROM " + Mapping.class.getName() + " ";
	/**
	 * ���ʂ��g���I��������Ƃ́AHibernateUtil.clearSession()���鎖
	 * @param hql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<Mapping> query(String whereSection,int from,int to,String ordering) {
		String queryString=MAPPING_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where��Ƃ͌���Ȃ�
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.evictAll();//L1 cashe���N���A���Ȃ��ƕύX�����I�u�W�F�N�g���擾�ł��Ȃ���������
		Query q=pm.newQuery(queryString);
		if(from>=0){
			q.setRange(from, to);
		}
		if(ordering!=null){
			q.setOrdering(ordering);
		}
		return (Collection<Mapping>)pm.detachCopyAll((Collection<Mapping>)q.execute());
	}
	
	public static JSON collectionToJson(Collection<Mapping> mappings){
		JSONArray result=new JSONArray();
		for(Mapping mapping:mappings){
			result.add(mapping.toJson());
		}
		return result;
	}
	
	private Config config=Config.getConfig();
	
	public static Comparator<Mapping> mappingComparator=new MappingComparator();
	
	private static class MappingComparator implements Comparator<Mapping>{
		public int compare(Mapping e1, Mapping e2) {
			if(e1.isSourceMatchEntry && !e2.isSourceMatchEntry){
				return -1;
			}
			if(!e1.isSourceMatchEntry && e2.isSourceMatchEntry){
				return 1;
			}
			if(e1.sourceServer==null && e2.sourceServer!=null){
				return -1;
			}
			if(e1.sourceServer!=null && e2.sourceServer==null){
				return 1;
			}
			if(e1.sourceServer!=null && e2.sourceServer!=null){
				int s1=e1.sourceServer.length();
				int s2=e2.sourceServer.length();
				if( s1>s2){
					return -1;
				}else if( s1<s2){
					return 1;
				}
				int sreverResult=e2.sourceServer.compareTo(e1.sourceServer);
				if(sreverResult!=0){
					return sreverResult;
				}
			}
			//source���������̂�擪�Ɏ����Ă���
			int length1=(e1.sourcePath==null)?0:e1.sourcePath.length();
			int length2=(e2.sourcePath==null)?0:e2.sourcePath.length();
			if( length1>length2){
				return -1;
			}else if( length1<length2){
				return 1;
			}
			if(e1.sourcePath==null){
				return 0;
			}
			//��́A���̒l��ԋp����΂悢
			return (int)(e1.id-e2.id);
		}
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	@Column(name="ID")
	private Long id;

	//�L���A����
	@Persistent
	@Column(name="ENABLED")
	private boolean enabled;
	
	@Persistent
	@Column(name="NOTES",jdbcType="VARCHAR", length=256)
	private String notes;
	
	@Persistent
	@Column(name="USER_ID",jdbcType="VARCHAR", length=16)
	private String userId;//user����16�����ȉ�!!!
	
	@Persistent
	@Column(name="REAL_HOST_NAME",jdbcType="VARCHAR", length=128)
	private String realHostName;//�ȗ������ꍇ�A�SrealHost��ΏۂƂ���A�i�����w��̓T�|�[�g���Ȃ�)
	
	/**
	 * WEB,PROXY,WS,WS_PROXY
	 */ 
	@Persistent
	@Column(name="SOURCE_TYPE",jdbcType="VARCHAR", length=16)
	private SourceType sourceType;
	
	/**
	 * SSL,Plain
	 */ 
	@Persistent
	@Column(name="SECURE_TYPE",jdbcType="VARCHAR", length=16)
	private SecureType secureType;
	
	@Persistent
	@Column(name="SOURCE_SERVER",jdbcType="VARCHAR", length=128)
	private String sourceServer;
	
	@Persistent
	@Column(name="SOURCE_PATH",jdbcType="VARCHAR", length=1024)
	private String sourcePath;
	
	@Persistent
	@Column(name="OPTIONS",jdbcType="VARCHAR", length=1024)
	private String options;

	@Persistent
	@Column(name="ROLES",jdbcType="VARCHAR", length=256)
	private String roles;
	
	/**
	 * HTTP,HTTPS,SSLPROXY,FILE,HANDLER
	 */ 
	@Persistent
	@Column(name="DESTINATION_TYPE",jdbcType="VARCHAR", length=16)
	private DestinationType destinationType;
	
	@Persistent
	@Column(name="DESTINATION_SERVER",jdbcType="VARCHAR", length=128)
	private String destinationServer;
	
	@Persistent
	@Column(name="DESTINATION_PATH",jdbcType="VARCHAR", length=1024)
	private String destinationPath;
	
	/* ��Ɨp�ϐ�,setup���\�b�h��Persistent�Q����v�Z���� */
	@NotPersistent
	private ServerParser sourceServerParser;
	
	@NotPersistent
	private ServerParser destinationServerParser;
	
	@NotPersistent
	private JSONObject optionsJson=new JSONObject();
	
	//Mapping����options����ϊ�����runtime�Ɏg����ƃI�u�W�F�N�g��ێ�����BmappingResult����getOption�ŎQ�Ƃ���
	//@NotPersistent
	//private Map optionsObj=new HashMap();
	
	@NotPersistent
	private boolean isSourceMatchEntry;//���K�\���Ń}�b�`������܂�destination�����܂�Ȃ�Entry

	//sourcePath����ݒ�
	@NotPersistent
	private Pattern sourcePathPattern;
	
	@NotPersistent
	private boolean isAdmin=false;
	
	public Mapping(){
	}
	
	/**
	 * ���O�̏o�̓^�C�v��\����
	 * @author naru
	 *
	 */
	public enum LogType {
		/**
		 * ���O���L�^���Ȃ�
		 */
		NONE,
		/**
		 * �A�N�Z�X���O�������L�^����
		 */
		ACCESS,
		/**
		 * �A�N�Z�X���O��request�g���[�X���L�^����
		 */
		REQUEST_TRACE,
		/**
		 * �A�N�Z�X���O��response�g���[�X���L�^����
		 */
		RESPONSE_TRACE,
		/**
		 * �A�N�Z�X���O��request/response�g���[�X���L�^����
		 */
		TRACE
	}
	
	/**
	 * ���N�G�X�g���^�C�v�i�u���E�U��phantom server�����T�[�o���Ɣ��f�����̂��j��\����
	 * @author naru
	 *
	 */
	public enum SourceType {
		/**
		 * web�T�[�o
		 */
		WEB,
		/**
		 * proxy�T�[�o
		 */
		PROXY,
		/**
		 * websocket�T�[�o
		 */
		WS,
		/**
		 * websocket proxy�T�[�o
		 */
		WS_PROXY
	}
	
	/**
	 * SSL�Í����̗L����\���񋓌^
	 * @author naru
	 *
	 */
	public enum SecureType {
		/**
		 * ��SSL�ʐM
		 */
		PLAIN,
		/**
		 * SSL�ʐM
		 */
		SSL
	}
	
	/**
	 * ���X�|���X�^�C�v��\����
	 * @author naru
	 *
	 */
	public enum DestinationType {
		/**
		 * http web�T�[�o���烌�X�|���X
		 */
		HTTP,
		/**
		 * https web�T�[�o���烌�X�|���X
		 */
		HTTPS,
		/**
		 * https web�T�[�o���烌�X�|���X�iphantom�͕��ʂ�SSL proxy�Ƃ��ē���j
		 */
		SSLPROXY,//WebSocket proxy���܂ށA���e���P�A���Ȃ�proxy
		/**
		 * �t�@�C�����烌�X�|���X�B(���ʂ�web�T�[�o�Ƃ��ē���j
		 */
		FILE,
		/**
		 * �n���h���[���烌�X�|���X�B<br/>
		 * WebSocketHandler��������WebSocketHandler���p�������N���X��destinationServer�Ɏw��<br />
		 */
		HANDLER,//WebSocket�̏ꍇ�͕K��Handler���K�v
		/**
		 * ws websocket�T�[�o���烌�X�|���X
		 */
		WS,
		/**
		 * wss websocket�T�[�o���烌�X�|���X
		 */
		WSS
	}
	
	//options����ݒ�
	@NotPersistent
	private LogType logType=LogType.NONE;

	private boolean isSessionUpdate=true;
	
	@NotPersistent
	private List<String> rolesList=new ArrayList<String>();
	
	//destinationType,destinationServer,destinationPath����ݒ�
	@NotPersistent
	private File destinationFile;
	
	@NotPersistent
	private Class destinationHandlerClass;
	
	//ph-auth.js����荞�߂�T�C�g��A�ȗ������ꍇ�͂ǂ�����ł���
	@NotPersistent
	private Set<String> allowOrigins=null;
	
	@NotPersistent
	private Map<String,Object> attribute=new HashMap<String,Object>();
	
	public JSON toJson(){
		JSONObject json=(JSONObject)JSONSerializer.toJSON(this,jsonConfig);
		String options=(String)json.get("options");
		try {
			optionsJson=(JSONObject)JSONSerializer.toJSON(options);
		} catch (JSONException e) {//json������ł͂Ȃ������ꍇ
			optionsJson=new JSONObject();
		}
		json.put("sourceUrl",calcSourceUrl());
		return json;
	}
	
	public void save(){
		JdoUtil.insert(this);
	}
	public void update(){
		JdoUtil.update(this);
	}
	
	public void delete(){
		JdoUtil.delete("SELECT FROM "+ Mapping.class.getName() + " WHERE id=="+this.id);
	}
	
	/**
	 * Persistent����ǂݍ��񂾌�A��Ɨp�ϐ���ݒ肷��
	 */
	public boolean setup(){
		String sourcePatternString="^"+this.sourcePath;
		if(!sourcePatternString.endsWith("/")){
			sourcePatternString+="($|[/;\\?])";
		}
		if( this.sourcePath.indexOf("*")>=0){
			this.isSourceMatchEntry=true;
			sourcePatternString = sourcePatternString.replaceAll("\\*", "(\\\\S*)");
		}
		if( this.sourcePath.indexOf(".")>=0){
			sourcePatternString = sourcePatternString.replaceAll("\\.", "(\\\\.)");
		}
		this.sourcePathPattern=Pattern.compile(sourcePatternString);
		sourceServerParser=ServerParser.parse(new ServerParser(),sourceServer,ServerParser.WILD_PORT_NUMBER);
		if(sourceServerParser==null){
			return false;
		}
		
		switch(destinationType){
		case HTTP:
			destinationServerParser=ServerParser.parse(new ServerParser(),destinationServer,ServerParser.WILD_PORT_NUMBER);
			if(destinationServerParser==null){
				return false;
			}
			destinationHandlerClass=PROXY_HANDLER;
			destinationFile=null;
			break;
		case HTTPS:
			destinationServerParser=ServerParser.parse(new ServerParser(),destinationServer,ServerParser.WILD_PORT_NUMBER);
			if(destinationServerParser==null){
				return false;
			}
			destinationHandlerClass=PROXY_HANDLER;
			destinationFile=null;
			break;
		case FILE:
			destinationHandlerClass=FILE_SYSTEM_HANDLER;
			//file�̏ꍇ�Apath�Ƀx�[�X�ƂȂ�f�B���N�g�����w��A�u���E�U�����path�̓x�[�X����̑��΂Ƃ���
			try {
				File destFile=new File(destinationPath);
				if(destFile.isAbsolute()){
					this.destinationFile=destFile.getCanonicalFile();
				}else{//FILE�̏ꍇ�́AphantomHome����̑��΃p�X
					this.destinationFile=new File(config.getPhantomHome(),destinationPath).getCanonicalFile();
				}
				} catch (IOException e1) {
					logger.error("getCanonicalFile error,",e1);
					return false;
				}
			break;
		case HANDLER:
			try {
				File destFile=new File(destinationPath);
				if(destFile.isAbsolute()){
					this.destinationFile=destFile.getCanonicalFile();
				}else{//HANDLE�̏ꍇ�́AappsRoot����̑��΃p�X
					this.destinationFile=new File(config.getAppsDocumentRoot(),destinationPath).getCanonicalFile();
				}
				} catch (IOException e1) {
					logger.error("getCanonicalFile error,",e1);
					return false;
				}
			break;
		case WS:
		case WSS:
			destinationServerParser=ServerParser.parse(new ServerParser(),destinationServer,ServerParser.WILD_PORT_NUMBER);
			if(destinationServerParser==null){
				return false;
			}
			destinationHandlerClass=WS_PROXY_HANDLE;
			break;
		}
		isSessionUpdate=true;
		if(options!=null){
			try {
				optionsJson=(JSONObject)JSONSerializer.toJSON(options);
			} catch (JSONException e) {//json������ł͂Ȃ������ꍇ
				logger.warn("options error.options:"+options);
				optionsJson=new JSONObject();
			}
			setLogType(optionsJson.optString(OPTION_LOG_TYPE));
			if(Boolean.FALSE.equals(optionsJson.optBoolean("peek",true))){
				destinationHandlerClass=SSL_PROXY_HANDLER;
			}
			if(Boolean.FALSE.equals(optionsJson.optBoolean("sessionUpdate",true))){
				isSessionUpdate=false;
			}
			setupAllowOrigins(optionsJson.optString("allowOrigins"));
			if(optionsJson.optBoolean(OPTION_PUBLIC_WEB,false)){
				config.setPublicDocumentRoot(destinationFile);
			}
			if(optionsJson.optBoolean(OPTION_ADMIN_HANDLER,false)){
				config.setAdminDocumentRoot(destinationFile);
				isAdmin=true;
			}
			if(optionsJson.optBoolean(OPTION_AUTH_HANDLER,false)){
				config.setAuthDocumentRoot(destinationFile);
			}
			
			String velocityPattern=optionsJson.optString(OPTION_VELOCITY_PATTERN,null);
			if(velocityPattern==null){
				velocityPattern=".*\\.vsp$|.*\\.vsf$";
			}
			if(!"".equals(velocityPattern)){//�󔒂�������velocity����
				attribute.put(OPTION_VELOCITY_PATTERN,Pattern.compile(velocityPattern));
			}
			
			String welcomFiles = optionsJson.optString(OPTION_FILE_WELCOME_FILES,null);
			if (welcomFiles == null) {
				welcomFiles="index.html,index.htm,index.vsp";
			}
			if(!"".equals(welcomFiles)){//�󔒂�������welcomFiles�Ȃ�
				attribute.put(OPTION_FILE_WELCOME_FILES,welcomFiles.split(","));
			}
			
			JSONObject link=optionsJson.optJSONObject(OPTION_LINK);
			if(link!=null){
				LinkManager linkManager=LinkManager.getInstance(link);
				attribute.put(OPTION_LINK, linkManager);
			}
			/*
			String linkName = optionsJson.optString(OPTION_LINK_NAME,null);
			if(linkName!=null){
				LinkManager linkManager=LinkManager.getInstance(linkName);
				attribute.put(OPTION_LINK_NAME, linkManager);
				String queueName = optionsJson.optString(OPTION_QUEUE_NAME,null);
				if(queueName!=null){
					linkManager.deploy(queueName, null);
				}
			}
			*/
		}
		rolesList.clear();
		if(roles!=null && !"".equals(roles)){
			String[] rolesArray=roles.split(",");
			for(int i=0;i<rolesArray.length;i++){
				rolesList.add(rolesArray[i]);
			}
		}
		return true;
	}
	
	void setupAppcache(){
		JSONObject appcacheObj=optionsJson.optJSONObject(OPTION_APPCACHE);
		if(appcacheObj!=null){
			AppcacheOption appcacheOption=new AppcacheOption(destinationFile,sourcePath,appcacheObj);
			attribute.put(OPTION_APPCACHE, appcacheOption);
		}
	}
	
	public void tearDown(){
		if(mappingAuth!=null){
			mappingAuth.term();
		}
	}
	
	private void setLogType(String type){
		if( LogType.ACCESS.toString().equalsIgnoreCase(type) ){
			logType=LogType.ACCESS;
		}else if( LogType.REQUEST_TRACE.toString().equalsIgnoreCase(type) ){
			logType=LogType.REQUEST_TRACE;
		}else if( LogType.RESPONSE_TRACE.toString().equalsIgnoreCase(type) ){
			logType=LogType.RESPONSE_TRACE;
		}else if( LogType.TRACE.toString().equalsIgnoreCase(type) ){
			logType=LogType.TRACE;
		}else{
			logType=LogType.NONE;
		}
	}
	
	/**
	 * @return the id
	 */
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id=id;
	}
	
	/**
	 * @return the enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	/**
	 * @return the notes
	 */
	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}
	
	/**
	 * @return the realHostName
	 */
	public String getRealHostName() {
		return realHostName;
	}

	public void setRealHostName(String realHostName) {
		this.realHostName = realHostName;
	}
	
	/**
	 * @return the sourceType
	 */
	public SourceType getSourceType() {
		return sourceType;
	}

	public void setSourceType(SourceType sourceType) {
		this.sourceType=sourceType;
	}
	
	/**
	 * @return the secureType
	 */
	public SecureType getSecureType(){
		return secureType;
	}
	public void setSecureType(SecureType secureType) {
		this.secureType=secureType;
	}
	
	/**
	 * @return the sourceServer
	 */
	public String getSourceServer() {
		return sourceServer;
	}
	
	public void setSourceServer(String sourceServer){
		this.sourceServer=sourceServer;
	}
	
	public String getSourceServerHost(){
		return sourceServerParser.getHost();
	}
	
	/**
	 * @return the sourceType
	 */
	public String getSourcePath() {
		return sourcePath;
	}
	
	public void setSourcePath(String sourcePath) {
		this.sourcePath=sourcePath;
	}
	
	/**
	 * @return the options
	 */
	public String getOptions() {
		return options;
	}
	
	public void setOptions(String options) {
		if(options!=null){
			this.options=options.trim();
		}else{
			this.options=null;
		}
	}
	
	/**
	 * @return the roles
	 */
	public String getRoles() {
		return roles;
	}
	
	public void setRoles(String roles) {
		this.roles=roles;
	}
	
	
	/**
	 * @return the destinationType
	 */
	public DestinationType getDestinationType(){
		return destinationType;
	}

	public void setDestinationType(DestinationType destinationType) {
		this.destinationType=destinationType;
	}

	/**
	 * @return the destinationServer
	 */
	public String getDestinationServer() {
		return destinationServer;
	}
	public void setDestinationServer(String destinationServer){
		this.destinationServer=destinationServer;
	}
	
	/**
	 * @return the destinationPath
	 */
	public String getDestinationPath() {
		return destinationPath;
	}
	public void setDestinationPath(String destinationPath){
		this.destinationPath=destinationPath;
	}
	
	/**
	 * @return the userId
	 */
	public String getUserId() {
		return userId;
	}
	
	public void setUserId(String userId) {
		this.userId=userId;
	}
	
	//�F�؂��s�v��path���ۂ��𔻒肷�邽�߂Ɏg�p
	public boolean isResolvePath(String targetPath){
		Matcher matcher=null;
		synchronized(sourcePathPattern){
			matcher=sourcePathPattern.matcher(targetPath);
		}
		return matcher.find();
	}
	
	//https�@proxy���ɏ����Ώۂ��ۂ��𔻒肷�邽�߂Ɏg�p
	public boolean isPeekSslProxyServer(String realHostName,ServerParser targetServer){
		if(Boolean.FALSE.equals(getOption(OPTION_PEEK))){//sslproxy�̓�����s�������ꍇ
			return false;//�����I��peek���鎖�����ۂ��Ă���ꍇ
		}
		if(!"".equals(this.realHostName)&&this.realHostName!=null && !this.realHostName.equals(realHostName)){
			return false;//realHost�Ⴂ
		}
		//�R�̃p�^�[��������A����ȊO�͊֌W�Ȃ�
		//1)ssl proxy
		//2)wss proxy
		//3)ws proxy
		switch(sourceType){
		case WS_PROXY:
		case PROXY:
			if(!SecureType.SSL.equals(secureType)){
				return false;
			}
			break;
		case WS:
		case WEB:
			return false;
		}
		Matcher serverMatcher=null;
		if(!"".equals(sourceServer)&&sourceServer!=null){
			if( sourceServerParser.getPort()!=ServerParser.WILD_PORT_NUMBER && 
					sourceServerParser.getPort()!=targetServer.getPort()){
				return false;
			}
			if(!sourceServerParser.isWildHost()){
				if( !sourceServerParser.getHost().equals(targetServer.getHost())){
					return false;
				}
			}else{
				serverMatcher=sourceServerParser.hostMatcher(targetServer.getHost());
				if(serverMatcher==null){
					return false;
				}
			}
		}
		return true;
	}
	
	private String resolvePath(Matcher pathMatcher){
		if(pathMatcher==null){
			return destinationPath;
		}
		StringBuffer sb = new StringBuffer();
		if(destinationFile!=null){//�t�@�C���̏ꍇ�́Apathmatch���Ȃ�
			pathMatcher.appendReplacement(sb, "/");
			pathMatcher.appendTail(sb);
		}else if(destinationPath.endsWith("/")){
			pathMatcher.appendReplacement(sb, destinationPath);
			pathMatcher.appendTail(sb);
		}else{
			pathMatcher.appendReplacement(sb, destinationPath);
			StringBuffer sb2 = new StringBuffer();
			pathMatcher.appendTail(sb2);
			if( sb2.length()>=1 ){
				sb.append("/");
				sb.append(sb2);
			}
		}
		return sb.toString();
	}
	
	private ServerParser resolveServer(Matcher serverMatcher,int targetPort){
		if(serverMatcher==null){
			// �����ŕԋp�����ServerParser�́AMappingResult�ɕۑ������B
			// MappingResult�J�����ɊJ������邽�߁A�Q�Ƃ𑝂₷
			return destinationServerParser;
		}else{
			StringBuffer sb = new StringBuffer();
			//server���Ɋ܂܂�Ă���$�����͂����ŉ��������
			serverMatcher.appendReplacement(sb, destinationServerParser.getHost());
			String host=sb.toString();
			int port=destinationServerParser.getPort();
			if(port==ServerParser.WILD_PORT_NUMBER){
				if(targetPort!=ServerParser.WILD_PORT_NUMBER){
					port=targetPort;
				}else if(destinationType==DestinationType.HTTPS || destinationType==DestinationType.WSS){
					port=ServerParser.HTTPS_PORT_NUMBER;
				}else if(destinationType==DestinationType.HTTP  || destinationType==DestinationType.WS){
					port=ServerParser.HTTP_PORT_NUMBER;
				}
			}
			return ServerParser.create(host, port);
		}
	}
	
	private MappingResult destinationResolve(ServerParser targetServer,Matcher serverMatcher,Matcher pathMatcher){
		MappingResult mappingResult=(MappingResult) PoolManager.getInstance(MappingResult.class);
		
		//path���\���u
		String resolvePath=resolvePath(pathMatcher);
		if("".equals(resolvePath)){
			resolvePath="/";
		}
		mappingResult.setResolvePath(resolvePath);
		mappingResult.setHandlerClass(destinationHandlerClass);
		switch(destinationType){
		case HTTP:
		case HTTPS:
		case WS:
		case WSS:
			ServerParser resolveServer=resolveServer(serverMatcher,targetServer.getPort());
			mappingResult.setResolveServer(resolveServer);
			break;
		case HANDLER:
			if(destinationHandlerClass==null){
				try {
					this.destinationHandlerClass= Class.forName(destinationServer);
				} catch (ClassNotFoundException e) {
					logger.error("not found destinationHandlerClass."+destinationServer,e);
					throw new RuntimeException("not found destinationHandlerClass."+destinationServer,e);
				}
				mappingResult.setHandlerClass(destinationHandlerClass);
			}
			break;
		}
		return mappingResult;
	}
	
	public MappingResult resolve(String realHostName,SourceType targetSourceType,SecureType targetSecureType,ServerParser targetServer,String targetPath){
		if(!"".equals(this.realHostName) && this.realHostName!=null && !this.realHostName.equals(realHostName)){
			return null;
		}
		if(!this.sourceType.equals(targetSourceType)){
			return null;
		}
		if(!this.secureType.equals(targetSecureType)){
			return null;
		}
		Matcher pathMatcher=null;
		if(sourcePathPattern!=null && targetPath!=null){
			synchronized (sourcePathPattern) {
				pathMatcher=sourcePathPattern.matcher(targetPath);
			}
			if(!pathMatcher.find()){
				return null;
			}
		}
		Matcher serverMatcher=null;
		if(!"".equals(sourceServer)&&sourceServer!=null){
			if( sourceServerParser.getPort()!=ServerParser.WILD_PORT_NUMBER && 
					sourceServerParser.getPort()!=targetServer.getPort()){
				return null;
			}
			if(!sourceServerParser.isWildHost()){
				if( !sourceServerParser.getHost().equals(targetServer.getHost())){
					return null;
				}
			}else{
				serverMatcher=sourceServerParser.hostMatcher(targetServer.getHost());
				if(serverMatcher==null){
					return null;
				}
			}
		}
		
		MappingResult mappingResult=destinationResolve(targetServer,serverMatcher,pathMatcher);//TODO
		mappingResult.setTargetSecureType(targetSecureType);
		mappingResult.setTargetServer(targetServer);
		mappingResult.setTargetPath(targetPath);
		
		mappingResult.setMapping(this);
		mappingResult.setResolveType(destinationType);
		return mappingResult;
	}

	public File getDestinationFile() {
		return destinationFile;
	}

	public LogType getLogType() {
		return logType;
	}

	public boolean isAdmin() {
		return isAdmin;
	}

	public List<String> getRolesList() {
		return rolesList;
	}

	public Object getOption(String key) {
		return optionsJson.opt(key);
	}

	public void setOption(String key, String value) {
		optionsJson.put(key, value);
		setOptions(optionsJson.toString());
	}
	
	private MappingAuth mappingAuth=null;//mapping�x�[�X�̔F�؂�����ꍇ
	
	public MappingAuth getMappingAuth(){
		return mappingAuth;
	}

	public void setMappingAuth(MappingAuth mappingAuth) {
		this.mappingAuth=mappingAuth;
	}

	public boolean isSessionUpdate() {
		return isSessionUpdate;
	}
	
	public boolean matchSourceHost(String host){
		if(sourceServerParser.isWildHost()){
			Matcher matcher=sourceServerParser.hostMatcher(host);
			return (matcher!=null);
		}
		return host.equals(sourceServerParser.getHost());
	}
	public boolean matchSourcePost(int port){
		int sourcePort=sourceServerParser.getPort();
		if(sourcePort==ServerParser.WILD_PORT_NUMBER){
			return true;
		}
		return (sourcePort==port);
	}
	
	private void setupAllowOrigins(String allowOriginsString){
		if(allowOriginsString==null||"".equals(allowOriginsString)){
			allowOrigins=null;
			return;
		}
		allowOrigins=new HashSet<String>();
		String[] array=allowOriginsString.split(",");
		for(String allowOrigin:array){
			allowOrigins.add(allowOrigin.trim());
		}
	}
	
	public boolean isAllowOrigin(String originUrl){
		if(allowOrigins==null){
			return false;
		}
		for(String allowOrigin:allowOrigins){
			if("*".equals(allowOrigin)){
				return true;
			}
			if(originUrl.startsWith(allowOrigin)){
				return true;
			}
		}
		return false;
	}
	
	//����Mapping���Ăяo�����߂�URL
	public String calcSourceUrl(){
		RealHost realHost=RealHost.getRealHost(realHostName);
		if(!enabled){
			return null;
		}
		if(!realHost.isBinding()){//���쒆�łȂ�realHost��url�͕ԋp���Ȃ�
			return null;
		}
		if( optionsJson.optBoolean("listing", true)==false ){
			return null;
		}
		StringBuffer sb=new StringBuffer();
		int defaultPort;
		switch(sourceType){
		case WEB:
			if(secureType==SecureType.PLAIN){
				sb.append("http://");
				defaultPort=80;
			}else{
				sb.append("https://");
				defaultPort=443;
			}
			break;
		/*
		case WS:
			if(secureType==SecureType.PLAIN){
				sb.append("ws://");
				defaultPort=80;
			}else{
				sb.append("wss://");
				defaultPort=443;
			}
			break;
		*/
		default:
			return null;
		}
		if(sourceServer!=null && !"".equals(sourceServer)){
			sb.append(sourceServer);
		}else{
			sb.append(config.getSelfDomain());
		}
		int port=realHost.getBindPort();
		if(defaultPort!=port){
			sb.append(":");
			sb.append(port);
		}
		sb.append(sourcePath);
		return sb.toString();
	}
	
	public void setAttribute(String name, Object value) {
		attribute.put(name, value);
	}

	public Object getAttribute(String name) {
		return attribute.get(name);
	}
	
	public Iterator<String> getAttributeNames(){
		return attribute.keySet().iterator();
	}
}