package naru.aweb.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
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
import naru.aweb.admin.StoreHandler;
import naru.aweb.auth.MappingAuth;
import naru.aweb.core.RealHost;
import naru.aweb.handler.FileSystemHandler;
import naru.aweb.handler.ProxyHandler;
import naru.aweb.handler.SslProxyHandler;
import naru.aweb.handler.VelocityPageHandler;
import naru.aweb.handler.WsProxyHandler;
import naru.aweb.mapping.MappingResult;
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
 * @author naru hayashi
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION,table="MAPPING",detachable="true")
public class Mapping{
	public static Class SSL_PROXY_HANDLER=SslProxyHandler.class;
	public static Class VELOCITY_PAGE_HANDLER=VelocityPageHandler.class;
	public static Class PROXY_HANDLER=ProxyHandler.class;
	public static Class FILE_SYSTEM_HANDLER=FileSystemHandler.class;
	public static Class STORE_HANDLER=StoreHandler.class;
	public static Class WS_PROXY_HANDLE=WsProxyHandler.class;
	
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
		jsonConfig.setExcludes(new String[]{"admin","auth","destinationFile","logType","userId","sourceServerHost","rolesList","sessionUpdate"});
		jsonConfig.setNewBeanInstanceStrategy(new MappingInstanceStrategy());
	}
	
	public static void cleanup(JSONArray mappings){
		JdoUtil.cleanup(Mapping.class);
//		if(!mappingInitFile.exists()){
//			logger.warn("cleanup but mappingInitFile not exists."+mappingInitFile);
//			return;
//		}
//		InputStream is=null;
//		try {
//			is=new FileInputStream(mappingInitFile);//Mapping.class.getResourceAsStream("MappingIni.properties");
//			Properties prop=new Properties();
//			prop.load(is);
			int size=mappings.size();
			for(int i=0;i<size;i++){
//				String jsonString=prop.getProperty(Integer.toString(i));
//				if(jsonString==null){
//					break;
//				}
				JSONObject mapping=mappings.getJSONObject(i);
				Mapping m=fromJson(mapping);
				m.save();
				logger.info("cleanup add mapping."+mapping);
			}
//		} catch (IOException e) {
//			logger.error("fail to init.",e);
//		}finally{
//			if(is!=null){
//				try {
//					is.close();
//				} catch (IOException ignore) {
//				}
//			}
//		}
	}
	
	public static Mapping fromJson(String jsonString){
		JSON json=JSONObject.fromObject(jsonString);
		return fromJson(json);
	}
	
	public static Mapping fromJson(JSON json){
//		JSON json=JSONObject.fromObject(jsonString);
		//ここで以下warning動きは妥当だが
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
		pm.currentTransaction().begin();
		try {
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
	 * 結果を使い終わったあとは、HibernateUtil.clearSession()する事
	 * @param hql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<Mapping> query(String whereSection,int from,int to,String ordering) {
		String queryString=MAPPING_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where句とは限らない
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.evictAll();//L1 casheをクリアしないと変更したオブジェクトが取得できない事がある
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
		return JSONSerializer.toJSON(mappings,jsonConfig);
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
			//sourceが長いものを先頭に持ってくる
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
			//後は、一定の値を返却すればよい
			return (int)(e1.id-e2.id);
		}
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	@Column(name="ID")
	private Long id;

	//有効、無効
	@Persistent
	@Column(name="ENABLED")
	private boolean enabled;
	
	@Persistent
	@Column(name="NOTES",jdbcType="VARCHAR", length=256)
	private String notes;
	
	@Persistent
	@Column(name="USER_ID",jdbcType="VARCHAR", length=16)
	private String userId;//user名は16文字以下!!!
	
	@Persistent
	@Column(name="REAL_HOST_NAME",jdbcType="VARCHAR", length=128)
	private String realHostName;//省略した場合、全realHostを対象とする、（複数指定はサポートしない)
	
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
	
	/* 作業用変数,setupメソッドでPersistent群から計算する */
	@NotPersistent
	private ServerParser sourceServerParser;
	
	@NotPersistent
	private ServerParser destinationServerParser;
	
	@NotPersistent
	private JSONObject optionsJson=new JSONObject();
	
	@NotPersistent
	private boolean isSourceMatchEntry;//正規表現でマッチさせるまでdestinationが決まらないEntry

	//sourcePathから設定
	@NotPersistent
	private Pattern sourcePathPattern;
	
	public Mapping(){
	}
	
	public Mapping(boolean isEnabled,String notes,
			SourceType sourceType,SecureType secureType,String sourceServer,String sourcePath,
			DestinationType destinationType,String destinationServer,String destinationPath,
			String options){
		setEnabled(isEnabled);
		setNotes(notes);
		setSourceType(sourceType);
		setSecureType(secureType);
		setSourceServer(sourceServer);
		setSourcePath(sourcePath);
		setDestinationType(destinationType);
		setDestinationServer(destinationServer);
		setDestinationPath(destinationPath);
		setOptions(options);
		setup();
	}
	
	public enum LogType {
		NONE,
		ACCESS,
		REQUEST_TRACE,
		RESPONSE_TRACE,
		TRACE
	}
	
	public enum SourceType {
		WEB,
		PROXY,
		WS,
		WS_PROXY
	}
	
	public enum SecureType {
		PLAIN,
		SSL
	}
	
	public enum DestinationType {
		HTTP,
		HTTPS,
		SSLPROXY,//WebSocket proxyも含む、内容をケアしないproxy
		FILE,
		HANDLER,//WebSocketの場合は必ずHandlerが必要
		WS,
		WSS
	}
	
	//optionsから設定
	@NotPersistent
	private LogType logType=LogType.NONE;

	private boolean isSessionUpdate=true;

//	@NotPersistent
//	private boolean isReplay=false;
	
//	@NotPersistent
//	private boolean isAuth=false;
	
	@NotPersistent
	private List<String> rolesList=new ArrayList<String>();
	
	//destinationType,destinationServer,destinationPathから設定
	@NotPersistent
	private File destinationFile;
	
	@NotPersistent
	private Class destinationHandlerClass;
	
	//ph-auth.jsを取り込めるサイト列、省略した場合はどこからでも可
	@NotPersistent
	private Set<String> allowOrigins=null;
	
	public JSON toJson(){
		JSON json=JSONSerializer.toJSON(this,jsonConfig);
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
	 * Persistentから読み込んだ後、作業用変数を設定する
	 */
	public boolean setup(){
		String sourcePatternString="^"+this.sourcePath;
		if(!sourcePatternString.endsWith("/")){
			sourcePatternString+="($|[/;\\?])";
//			sourcePatternString+="($|/)";
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
			break;
		case HTTPS:
			destinationServerParser=ServerParser.parse(new ServerParser(),destinationServer,ServerParser.WILD_PORT_NUMBER);
			if(destinationServerParser==null){
				return false;
			}
			destinationHandlerClass=PROXY_HANDLER;
			break;
		case FILE:
			destinationHandlerClass=FILE_SYSTEM_HANDLER;
			//fileの場合、pathにベースとなるディレクトリを指定、ブラウザからのpathはベースからの相対とする
			try {
					if(destinationPath.startsWith("/")||destinationPath.indexOf(":")>0){
						this.destinationFile=new File(destinationPath).getCanonicalFile();
					}else{
						this.destinationFile=new File(config.getPhantomHome(),destinationPath).getCanonicalFile();
					}
				} catch (IOException e1) {
					logger.error("getCanonicalFile error,",e1);
					return false;
				}
//			this.destinationPath="/";
			break;
		case HANDLER:
//ここでロードするとConfigの初期化が再帰で呼ばれてしまう。
//			try {
//				this.destinationHandlerClass= Class.forName(destinationServer);
//			} catch (ClassNotFoundException e) {
//			}
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
			} catch (JSONException e) {//json文字列ではなかった場合
				logger.warn("options error.options:"+options);
				optionsJson=new JSONObject();
			}
			setLogType((String)optionsJson.get("logType"));
//			String authRoles=optionsJson.optString("auth",null);
//			rolesList.clear();
//			if(authRoles!=null){
//				String[] rolesArray=authRoles.split(",");
//				for(int i=0;i<rolesArray.length;i++){
//					rolesList.add(rolesArray[i]);
//				}
//			}
			if(Boolean.FALSE.equals(optionsJson.optBoolean("peek",true))){
				destinationHandlerClass=SSL_PROXY_HANDLER;
			}
			if(Boolean.FALSE.equals(optionsJson.optBoolean("sessionUpdate",true))){
				isSessionUpdate=false;
			}
//			isAuth=optionsJson.optBoolean("auth", false);
//			isReplay=optionsJson.optBoolean("replay", false);
			setupAllowOrigins(optionsJson.optString("allowOrigins"));
		}
		rolesList.clear();
		if(roles!=null && roles.length()!=0){
			String[] rolesArray=roles.split(",");
			for(int i=0;i<rolesArray.length;i++){
				rolesList.add(rolesArray[i]);
			}
		}
		return true;
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
	
	//認証が不要なpathか否かを判定するために使用
	public boolean isResolvePath(String targetPath){
		Matcher matcher=null;
		synchronized(sourcePathPattern){
			matcher=sourcePathPattern.matcher(targetPath);
		}
		return matcher.find();
	}
	
	//https　proxy時に処理対象か否かを判定するために使用
	public boolean isPeekSslProxyServer(String realHostName,ServerParser targetServer){
		if(Boolean.FALSE.equals(getOption("peek"))){//sslproxyの動作を行いたい場合
			return false;//明示的にpeekする事を拒否している場合
		}
		if(!"".equals(this.realHostName)&&this.realHostName!=null && !this.realHostName.equals(realHostName)){
			return false;//realHost違い
		}
		//３つのパターンがある、これ以外は関係なし
		//1)ssl proxy
		//2)wss proxy
		//3)ws proxy
		switch(sourceType){
		case PROXY:
			if(!SecureType.SSL.equals(secureType)){
				return false;
			}
		case WS:
		case WS_PROXY:
			break;
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
//		ServerParser resolveServer=resolveServer(serverMatcher,targetServer.getPort());
//		return resolveServer;
//		return true;
	}
	
	private String resolvePath(Matcher pathMatcher){
		if(pathMatcher==null){
			return destinationPath;
		}
		StringBuffer sb = new StringBuffer();
		if(destinationFile!=null){//ファイルの場合は、pathmatchしない
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
			// ここで返却されるServerParserは、MappingResultに保存される。
			// MappingResult開放時に開放されるため、参照を増やす
			return destinationServerParser;
		}else{
			StringBuffer sb = new StringBuffer();
			//server名に含まれている$数字はここで解決される
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
		
		//pathリソルブ
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
		return false;
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
	
	private MappingAuth mappingAuth=null;//mappingベースの認証をする場合
	
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
	
	//このMappingを呼び出すためのURL
	public String calcSourceUrl(){
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
		case WS:
			if(secureType==SecureType.PLAIN){
				sb.append("ws://");
				defaultPort=80;
			}else{
				sb.append("wss://");
				defaultPort=443;
			}
			break;
		default:
			return null;
		}
		if(sourceServer!=null && !"".equals(sourceServer)){
			sb.append(sourceServer);
		}else{
			sb.append(config.getSelfDomain());
		}
		RealHost realHost=RealHost.getRealHost(realHostName);
		int port=realHost.getBindPort();
		if(defaultPort!=port){
			sb.append(":");
			sb.append(port);
		}
		sb.append(sourcePath);
		return sb.toString();
	}
}
