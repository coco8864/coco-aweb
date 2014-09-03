package naru.aweb.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import naru.async.cache.FileCache;
import naru.aweb.admin.AdminLinklet;
import naru.aweb.auth.Authenticator;
import naru.aweb.auth.Authorizer;
import naru.aweb.auth.User;
import naru.aweb.core.Main;
import naru.aweb.core.RealHost;
import naru.aweb.handler.FilterHelper;
import naru.aweb.handler.InjectionHelper;
import naru.aweb.handler.ReplayHelper;
import naru.aweb.handler.ServerBaseHandler;
import naru.aweb.handler.ServerBaseHandler.SCOPE;
import naru.aweb.link.LinkManager;
import naru.aweb.mapping.Mapper;
import naru.aweb.mapping.Mapping;
import naru.aweb.secure.SslContextPool;
import naru.aweb.spdy.SpdyConfig;
import naru.aweb.util.JdoUtil;
import naru.aweb.util.JsonUtil;
import naru.aweb.util.ServerParser;
import naru.queuelet.QueueletContext;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONSerializer;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
//import org.apache.commons.configuration.DatabaseConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.log4j.Logger;
import org.apache.velocity.app.VelocityEngine;

public class Config {
	private static Logger logger = Logger.getLogger(Config.class);
	private static Config config = new Config();
	
	private static final String USE_FILE_CACHE = "useFileCache";
	private static final String PHANTOM_SERVER_HEADER = "phantomServerHeader";
	private static final String KEEP_ALIVE_TIMEOUT = "keepAliveTimeout";
	private static final String MAX_KEEP_ALIVE_REQUESTS = "maxKeepAliveRequests";
	private static final String IS_WEB_KEEP_ALIVE = "isWebKeepAlive";
	private static final String IS_PROXY_KEEP_ALIVE = "isProxyKeepAlive";
	private static final String CONTENT_ENCODING = "contentEncoding";
	private static final String CONFIG_MARK_CREATE = "_config_create_";
	
	private static String PROPERTIES_ENCODING = "utf-8";
	private static final String READ_TIMEOUT = "readTimeout";
	private static final long READ_TIMEOUT_DEFAULT = 10000;
	private static final String WRITE_TIMEOUT = "writeTimeout";
	private static final long WRITE_TIMEOUT_DEFAULT = 1000;
	private static final String ACCEPT_TIMEOUT = "acceptTimeout";
	private static final long ACCEPT_TIMEOUT_DEFAULT = 1000;
	private static final String CONNECT_TIMEOUT = "connectTimeout";
	private static final long CONNECT_TIMEOUT_DEFAULT = 1000;
	private static final String SSL_PROXY_PORT = "sslProxyPort";
	private static final String SSL_PROXY_SERVER = "sslProxyServer";
	private static final String PROXY_PORT = "proxyPort";
	private static final String PROXY_SERVER = "proxyServer";
	private static final String PATH_APPS_DOCROOT = "path.appsDocroot";
	private static final String PATH_PUBLIC_DOCROOT = "path.publicDocroot";
	private static final String PATH_PORTAL_DOCROOT = "path.portalDocroot";
	private static final String PATH_SETTING = "path.setting";
	private static final String PATH_INJECTION_DIR = "path.injectionDir";

	public static final String CONF_FILENAME = "phantom.properties";
	private static final String DEBUG_TRACE="debugTrace";
	public static final String SELF_DOMAIN = "selfDomain";
	public static final String SELF_PORT = "selfPort";
	public static final String SELF_URL = "selfUrl";
	private static final String REAL_HOSTS = "realHosts";
	private static final String REAL_HOST = "realHost";

	public static final String PASS_HASH_ALGORITHM="passHashAlgorithm";
	public static final String PASS_SALT="passSalt";
	public static final String OFFLINE_PASS_SALT="offlinePassSalt";
	
	public static final String REFUSE_ACCEPT="refuseAccept";
	
	private File adminDocumentRoot;// adminで見えるところ
	private File authDocumentRoot;// authで見えるところ
	private File portalDocumentRoot;// for portal
	private File publicDocumentRoot;// 誰でも見えるところ
	private File appsDocumentRoot;//配備先ディレクトリ
	private File injectionDir;// 注入コンテンツを格納するディレクトリ
	private SSLContext sslContext;
	private File phantomHome;
	private Mapper mapper;
	private Authenticator authenticator;
	private Authorizer authorizer;
	private LogPersister logPersister;
	private SpdyConfig spdyConfig;
	private SslContextPool sslContextPool;
	private ReplayHelper replayHelper;
	private FilterHelper filterHelper;
	private InjectionHelper injectionHelper;
	private Configuration configuration = null;
	private LinkManager linkManager =null;
	private Object stasticsObject;
	private File tmpDir;
	private DiskFileItemFactory uploadItemFactory;
	private File settingDir;
	
	private String selfDomain;
	
	/* コンテンツエンコードに何を許すか?実際zgip or not */
	// private String contentEncoding;
	/* keepAlive関連 */
	// private boolean isProxyKeepAlive;
	// private boolean isWebKeepAlive;
	// private int maxKeepAliveRequests;
	// private int keepAliveTimeout;
	public static boolean getBooleanSafe(Configuration configuraion,
			String key, boolean defaultValue) {
		try {
			return configuraion.getBoolean(key, defaultValue);
		} catch (ConversionException e) {
			logger.warn("fail to getBoolean.key:" + key, e);
			configuraion.clearProperty(key);
			return defaultValue;
		}
	}

	public static int getIntSafe(Configuration configuraion, String key,
			int defaultValue) {
		try {
			return configuraion.getInt(key, defaultValue);
		} catch (ConversionException e) {
			logger.warn("fail to getInt.key:" + key, e);
			configuraion.clearProperty(key);
			return defaultValue;
		}
	}

	public static long getLongSafe(Configuration configuraion, String key,
			long defaultValue) {
		try {
			return configuraion.getLong(key, defaultValue);
		} catch (ConversionException e) {
			logger.warn("fail to getLong.key:" + key, e);
			configuraion.clearProperty(key);
			return defaultValue;
		}
	}

	public static String[] getStringArray(Configuration configuraion, String key) {
		String value = configuraion.getString(key);
		if (value == null) {
			return new String[0];
		}
		return value.split(",");
	}
	
	public void setStringArray(String key,String[] values){
		setStringArray(configuration,key,values);
	}

	public static void setStringArray(Configuration configuraion, String key,
			String[] values) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i != 0) {
				sb.append(",");
			}
			sb.append(values[i]);
		}
		configuraion.setProperty(key, sb.toString());
	}

	public static void setStringSet(Configuration configuraion, String key,
			Set values) {
		StringBuilder sb = new StringBuilder();
		Iterator itr = values.iterator();
		boolean isFirst = true;
		while (itr.hasNext()) {
			if (isFirst == false) {
				sb.append(",");
			}
			isFirst = false;
			sb.append(itr.next().toString());
		}
		configuraion.setProperty(key, sb.toString());
	}
	
	public SecureRandom getRandom(String key) {
		String entoropy = configuration.getString(key);
		if(entoropy==null){
			entoropy=key;
		}
		entoropy = entoropy + System.currentTimeMillis();
		SecureRandom random;
		try {
			random = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		random.setSeed(entoropy.getBytes());
		return random;
	}
	
	public SslContextPool getSslContextPool() {
		return sslContextPool;
	}
	
	public FileCache getFileCache() {
		return FileCache.getInstance();
	}
	
	public void setUseFileCache(boolean useCache){
		getFileCache().setUseCache(useCache);
		configuration.setProperty(USE_FILE_CACHE, useCache);
	}

	public Authenticator getAuthenticator() {
		return authenticator;
	}
	
	public Authorizer getAuthorizer() {
		return authorizer;
	}

	public Mapper getMapper() {
		return mapper;
	}

	public static Config getConfig() {
		return config;
	}

	public SSLEngine getSslEngine(ServerParser server) {
		if (server == null) {//server engineの初期化
			SSLEngine engine=sslContext.createSSLEngine(null, 443);
			return engine;
		} else {//client engineの初期化
			SSLContext sc = sslContextPool.getSSLContext(server.getHost());
			SSLEngine engine=sc.createSSLEngine(server.getHost(), server.getPort());
			return engine;
		}
	}

	private boolean checkAndCreateDb(BasicDataSource dataSource,
			boolean isCleanup) throws SQLException {
		Connection con = null;
		SQLException lastException=null;
		//Windowsの場合、停止直後にhsqldbをオープンするとファイル共有エラーとなる。
		for(int i=0;i<16;i++){
			try {
				con = dataSource.getConnection();
				break;
			} catch (SQLException e1) {
				lastException=e1;
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
			}
		}
		if(con==null){
			throw lastException;
		}
		try {
			Statement st = con.createStatement();
			st.execute("SELECT count(*) FROM CONFIGURATION");
			if (isCleanup) {
				st.executeUpdate("DROP TABLE CONFIGURATION");
				st.executeUpdate("CREATE TABLE CONFIGURATION(KEY VARCHAR(512) NOT NULL PRIMARY KEY,VALUE VARCHAR(512))");
			}
		} catch (SQLException e) {
			if(logger.isDebugEnabled())logger.debug("dbcheck exception.",e);
			if ("S0002".equals(e.getSQLState())) {// テーブルなしって意味
				Statement st = con.createStatement();
				st.executeUpdate("CREATE TABLE CONFIGURATION(KEY VARCHAR(512) NOT NULL PRIMARY KEY,VALUE VARCHAR(512))");
				st.close();
				return true;
			} else {
				throw e;
			}
		} finally {
			if (con != null) {
				con.close();
			}
		}
		return false;
	}

	private BasicDataSource confDataSource = new BasicDataSource();

	private Configuration crateConfiguration(QueueletContext context,boolean isCleanup) {
		InputStream is = Config.class.getClassLoader().getResourceAsStream(CONF_FILENAME);
		if (is == null) {
			throw new RuntimeException("not found in class path.CONF_FILENAME:"	+ CONF_FILENAME);
		}
		Configuration fileConfig = loadConfig(is, "MS932");
		String url = context.resolveProperty(fileConfig.getString("config.url"), null);
		String user = context.resolveProperty(fileConfig.getString("config.user"), null);
		String pass = context.resolveProperty(fileConfig.getString("config.pass"), null);
		String driver = context.resolveProperty(fileConfig.getString("config.driver"), null);
		String table = context.resolveProperty(fileConfig.getString("config.table"), null);
		String keyColumn = context.resolveProperty(fileConfig.getString("config.key"), null);
		String valueColumn = context.resolveProperty(fileConfig.getString("config.value"), null);

		//BasicDataSource dataSource = new BasicDataSource();
		confDataSource.setDriverClassName(driver);
		confDataSource.setUrl(url);
		confDataSource.setUsername(user);
		confDataSource.setPassword(pass);
		confDataSource.setDefaultAutoCommit(true);
		boolean isCreate;
		try {
			isCreate = checkAndCreateDb(confDataSource, isCleanup);
		} catch (SQLException e) {
			logger.error("fail to checkAndCreateDb.", e);
			throw new RuntimeException("fail to checkAndCreateDb.", e);
		}
		CacheConfiguration dbConfig = new CacheConfiguration(confDataSource,table, keyColumn, valueColumn);
		// DBには、","区切で格納しアプリで分解する
		dbConfig.setDelimiterParsingDisabled(true);
		/* phantom.propetiesは最優先、他の設定(web,config)では上書きできないようにする */
		Iterator itr = fileConfig.getKeys();
		while (itr.hasNext()) {
			String key = (String) itr.next();
			String value = context.resolveProperty(fileConfig.getString(key),
					null);
			dbConfig.setProperty(key, value);
			if(logger.isDebugEnabled())logger.debug(key + ":" + value);
		}
		if (isCreate) {
			// configを新規に作成した場合は、他のテーブルが仮に残っていたとしてもcleanup相当の動作をする
			dbConfig.setProperty(CONFIG_MARK_CREATE, "true");
		}else{
			dbConfig.setProperty(CONFIG_MARK_CREATE, "false");
		}
		return dbConfig;
	}

	/**
	 * このメソッドに渡したInputStreamは、このメソッドでcloseします
	 * 
	 * @param propIs
	 * @param enc
	 * @return
	 */
	private Configuration loadConfig(InputStream propIs, String enc) {
		// InputStream is = null;
		PropertiesConfiguration config = null;
		try {
			// is = new FileInputStream(propFile);
			Reader reader = new InputStreamReader(propIs, enc);
			config = new PropertiesConfiguration();
			config.load(reader);
		} catch (IOException e) {
			logger.error("fali to loadConfig.", e);
		} catch (ConfigurationException e) {
			logger.error("fali to loadConfig.", e);
		} finally {
			try {
				propIs.close();
			} catch (IOException ignore) {
			}
		}
		return config;
	}

	private void updateFileProperties(QueueletContext context,
			Configuration targetConfiguration, File propFile, String enc,
			boolean isRename) {
		FileInputStream propIs;
		try {
			propIs = new FileInputStream(propFile);
		} catch (FileNotFoundException e) {
			logger.warn("is not propFile:" + propFile.getAbsolutePath());
			return;
		}
		Configuration propConfig = loadConfig(propIs, enc);
		if (propConfig == null) {
			return;
		}
		Iterator itr = propConfig.getKeys();
		while (itr.hasNext()) {
			String key = (String) itr.next();
			String[] values = propConfig.getStringArray(key);
			if(logger.isDebugEnabled())logger.debug("key:" + key + " values:" + values);
			// 複数指定ができるrealHostsとmappingsは、追加書きにする
			if (key.equals(REAL_HOSTS)) {
				String[] orgValues = Config.getStringArray(targetConfiguration,
						key);
				HashSet<String> newValues = new HashSet<String>();
				for (int i = 0; i < values.length; i++) {
					newValues.add(values[i]);
				}
				for (int i = 0; i < orgValues.length; i++) {
					newValues.add(orgValues[i]);
				}
				values = newValues.toArray(new String[newValues.size()]);
			}
			// keyしか設定されていないものは、削除（デフォルト値となるようにする)
			// 数値を要求するものの例外を防ぐ
			if (values.length == 0) {// ないかもしれない
				targetConfiguration.clearProperty(key);
			} else if (values.length == 1) {
				String value = context.resolveProperty(values[0], null);
				// String value=values[0];
				if ("".equals(value)) {
					targetConfiguration.clearProperty(key);
				} else {
					targetConfiguration.setProperty(key, value);
				}
			} else {
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < values.length; i++) {
					if (i != 0) {
						sb.append(",");
					}
					sb.append(context.resolveProperty(values[i], null));
					// sb.append(values[i]);
				}
				targetConfiguration.setProperty(key, sb.toString());
			}
		}
		if (isRename) {
			SimpleDateFormat format = new SimpleDateFormat(".yyMMddHHmmss");
			File backup = new File(propFile.getAbsolutePath()+ format.format(new Date()));
			propFile.renameTo(backup);
		}
	}

	/* 基本(admin)設定 */
	private Config() {
	}

	private boolean initDatanucleus(QueueletContext queueletContext) {
		// detanumclusの初期化
		InputStream is = Main.class.getClassLoader().getResourceAsStream("datanucleus.properties");
		if (is == null) {
			return false;
		}
		Map resolveProperites = new HashMap();
		Properties datanucleusProperties = new Properties();
		try {
			datanucleusProperties.load(is);
			Iterator itr = datanucleusProperties.keySet().iterator();
			while (itr.hasNext()) {
				String key = (String) itr.next();
				String value = datanucleusProperties.getProperty(key);
				resolveProperites.put(key, queueletContext.resolveProperty(value, null));
			}
			JdoUtil.initPersistenceManagerFactory(resolveProperites);
			return true;
		} catch (IOException e) {
			logger.error("fail to initDatanucleus.", e);
			return false;
		} finally {
			try {
				is.close();
			} catch (IOException ignore) {
			}
		}
	}

	private boolean isAleadyTerm = false;

	public void term() {
		linkManager.undeploy(AdminLinklet.QNAME);
		if (isAleadyTerm) {
			return;
		}
		isAleadyTerm = true;
		if(broadcaster!=null){//initの途中で例外した場合broadcasterの可能性がある。
			broadcaster.term();
			broadcaster=null;
		}
		if(logPersister!=null ){
			logPersister.term();
			logPersister=null;
		}
		if(authenticator!=null){
			authenticator.term();
			authenticator=null;
		}
		if(authorizer!=null){
			authorizer.term();
			authorizer=null;
		}
		if(confDataSource!=null){
			try {
				confDataSource.close();
			} catch (SQLException ignore) {
			}
			confDataSource=null;
		}
	}
	
	public void addRealHost(RealHost realHost){
		RealHost.addRealHost(realHost);
		updateRealHosts();
	}
	
	public void delRealHost(String name){
		RealHost.delRealHost(name);
		updateRealHosts();
	}
	
	public void updateRealHosts(){
		Set<RealHost>realHosts=RealHost.getRealHosts();
		StringBuilder names=new StringBuilder();
		for(RealHost realHost:realHosts){
			String name=realHost.getName();
			names.append(name);
			names.append(",");
			configuration.subset(REAL_HOST).setProperty(name, realHost.toJson());
		}
		configuration.setProperty(REAL_HOSTS, names.toString());
	}

	private JSON readInitFile(File file){
		if(!file.exists()||!file.isFile()||!file.canRead()){
			throw new RuntimeException("readInitFile file access error."+file);
		}
		try {
			int length=(int)file.length();
			byte[] buf=new byte[length];
			InputStream is=new FileInputStream(file);
			if( is.read(buf)<length ){
				throw new RuntimeException("readInitFile file read error."+file);
			}
			is.close();
			String jsonString=new String(buf,"utf-8");
			return JSONSerializer.toJSON(jsonString);
		} catch (IOException e) {
			throw new RuntimeException("readInitFile error."+file,e);
		}
	}
	
	/* 一番最初のloopbackでないipv4の文字列を返却する,見つからない場合は、127.0.0.1 */
	private String getSelfIpv4(){
		java.util.Enumeration enuIfs=null;
		String selfAddress="127.0.0.1";
		try {
			enuIfs = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
		}
		if (null == enuIfs) {
			return selfAddress;
		}
		while (enuIfs.hasMoreElements()) {
			NetworkInterface ni = (NetworkInterface) enuIfs.nextElement();
			java.util.Enumeration enuAddrs = ni.getInetAddresses();
			while (enuAddrs.hasMoreElements()) {
				Object inadder=enuAddrs.nextElement();
				if(!(inadder instanceof Inet4Address)){
					continue;
				}
				Inet4Address in4=(Inet4Address)inadder;
				if(in4.isLoopbackAddress()){
					continue;
				}
				//自ipアドレスを列挙、最後のipを使う
				selfAddress=in4.getHostAddress();
				logger.info("selfAddress list:"+selfAddress);
			}
		}
		return selfAddress;
	}
	
	
	public boolean init(QueueletContext queueletContext, boolean isCleanup) {
//		this.queueletContext = queueletContext;
		configuration = crateConfiguration(queueletContext, isCleanup);
		// 新規にCONFIGテーブルを作った場合はcleanupの動作をおこなう
		if (configuration.getBoolean(CONFIG_MARK_CREATE, false)) {
			System.out.println("create CONFIG table");
			configuration.clearProperty("cleanup");
			isCleanup = true;
		}
		String settingPath = configuration.getString(PATH_SETTING);
		if (settingPath != null) {
			settingDir = new File(settingPath);
			File[] props = settingDir.listFiles();
			if (props != null) {
				for (int i = 0; i < props.length; i++) {
					// .propertiesでないのものは除外
					if (!props[i].getName().endsWith(".properties")) {
						continue;
					} else if (props[i].getName().endsWith(".env.properties")) {
						//queueletに読み込ませるpropertiesファイル,phantom層では読み込まない
						continue;
					} else if (props[i].getName().endsWith(".init.properties")) {
						// init.propertiesは除外(cleanup時に設定するpropertie)
						if (isCleanup) {
							updateFileProperties(queueletContext,
									configuration, props[i],
									PROPERTIES_ENCODING, false);
						}
						continue;
					}
					updateFileProperties(queueletContext, configuration,
							props[i], PROPERTIES_ENCODING, true);
				}
			}
		}
//		queueletContext.resolveProperty();
		String homeDir = configuration.getString("path.phantom", null);
		if (homeDir != null) {
			phantomHome = new File(homeDir);
		} else {
			phantomHome = new File("./");
		}
		if (!initDatanucleus(queueletContext)) {
			logger.error("fail to initDatanucleus.");
			return false;
		}
		//100端末あたりのpool数初期値
		poolInfo_100=(JSONArray)readInitFile(new File(settingPath, "pool.init"));
		
		if (isCleanup) {
			JdoUtil.cleanup(Performance.class);
			JdoUtil.cleanup(AccessLog.class);
			JdoUtil.cleanup(CommissionAuthEntry.class);
			JdoUtil.cleanup(CommissionAuthRole.class);
			JdoUtil.cleanup(CommissionAuthUrl.class);
			JdoUtil.cleanup(CommissionAuth.class);
			JdoUtil.cleanup(User.class);
			JSON mappingJson=readInitFile(new File(settingPath, "mapping.init"));
			Mapping.cleanup((JSONArray)mappingJson);
		}
		
		//self domainの計算
		this.selfDomain=getString(SELF_DOMAIN, null);
		if(this.selfDomain==null){
			this.selfDomain=getSelfIpv4();
		}
		logger.info("selfDomain:"+this.selfDomain);
		
		
		/* ファイルアップロード機能 */
		String tmpPath = configuration.getString("path.tmp");
		if (tmpPath != null) {
			tmpDir = new File(tmpPath);
			if(!tmpDir.exists()){
				logger.info("create tmp dir.ret:"+tmpDir.mkdir());
			}
			uploadItemFactory = new DiskFileItemFactory(0, tmpDir);
		}

		// welcomeFilesの初期化
		String welcomeFiles = configuration.getString("welcomeFiles");
		setWelcomFiles(welcomeFiles);

		replayHelper = new ReplayHelper();
		filterHelper = new FilterHelper();
		authenticator = new Authenticator(this,isCleanup);
		logPersister = new LogPersister(this);
		spdyConfig=new SpdyConfig(this);
		sslContextPool = new SslContextPool(this,spdyConfig.getSslProvider());
		sslContext = sslContextPool.getSSLContext(getSelfDomain());

		String[] hostsArray = Config.getStringArray(configuration, REAL_HOSTS);
		for (int i = 0; i < hostsArray.length; i++) {
			String hostName = hostsArray[i];
			String realHostJson=configuration.subset(REAL_HOST).getString(hostName);
			RealHost realHost;
			try {
				realHost = RealHost.fromJson(realHostJson);
				realHost.setName(hostName);//念のため
			} catch (UnknownHostException e) {
				throw new RuntimeException("failt to create RealHost.",e);
			}
			RealHost.addRealHost(realHost);
		}
		try {
//			String dir = configuration.getString(PATH_PUBLIC_DOCROOT);
//			publicDocumentRoot = new File(dir).getCanonicalFile();
//			dir = configuration.getString(PATH_PORTAL_DOCROOT);
//			portalDocumentRoot = new File(dir).getCanonicalFile();
			String dir = configuration.getString(PATH_INJECTION_DIR);
			injectionDir = new File(dir).getCanonicalFile();
			injectionHelper = new InjectionHelper(this);
			dir = configuration.getString(PATH_APPS_DOCROOT);
			appsDocumentRoot = new File(dir).getCanonicalFile();
		} catch (IOException e) {
			logger.error("getCanonicalFile error.",e);
			return false;
		}
		isAllowChunked=getBoolean("allowChunked",false);
		isDebugTrace=getBoolean(DEBUG_TRACE,false);
		
		contentEncoding=configuration.getString(CONTENT_ENCODING);
		isProxyKeepAlive=configuration.getBoolean(IS_PROXY_KEEP_ALIVE, false);
		isWebKeepAlive=configuration.getBoolean(IS_WEB_KEEP_ALIVE, false);
		maxKeepAliveRequests=configuration.getInt(MAX_KEEP_ALIVE_REQUESTS, 100);
		keepAliveTimeout=configuration.getInt(KEEP_ALIVE_TIMEOUT, 15000);
		
		writeTimeout=getLong(WRITE_TIMEOUT, WRITE_TIMEOUT_DEFAULT);
		readTimeout=getLong(READ_TIMEOUT, READ_TIMEOUT_DEFAULT);
		connectTimeout=getLong(CONNECT_TIMEOUT, CONNECT_TIMEOUT_DEFAULT);
		acceptTimeout=getLong(ACCEPT_TIMEOUT, ACCEPT_TIMEOUT_DEFAULT);
		
		serverHeader=config.getString(PHANTOM_SERVER_HEADER, null);
		
		boolean useCache=getBoolean(USE_FILE_CACHE);
		getFileCache().setUseCache(useCache);
		return true;
	}
	
	//bind完了後のinit
	public void initAfterBind(){
		// Mapper作成には、realHostsが必要、realHostsの初期化の後に呼び出す。
		authorizer=new Authorizer(configuration);
		mapper = new Mapper(this);
		// proxy除外リストの初期化
		String pacUrl = configuration.getString("pacUrl");
		if("".equals(pacUrl)){
			pacUrl=null;
		}
		String proxyServer = configuration.getString("proxyServer");
		if("".equals(proxyServer)){
			proxyServer=null;
		}
		String sslProxyServer = configuration.getString("sslProxyServer");
		if("".equals(sslProxyServer)){
			sslProxyServer=null;
		}
		String exceptProxyDomains = configuration.getString("exceptProxyDomains");
		if("".equals(exceptProxyDomains)){
			exceptProxyDomains=null;
		}
		updateProxyFinder(pacUrl,proxyServer,sslProxyServer,exceptProxyDomains);
		
		//adminハンドラーの設定
		//TODO 動的に設定できるようにする
		Mapping adminMapping=mapper.getAdminMapping();
		linkManager =(LinkManager)adminMapping.getAttribute(Mapping.OPTION_LINK);
		broadcaster=new Broadcaster(this,linkManager);//統計情報監視の開始
	}
	
	private Broadcaster broadcaster;

	public Configuration getConfiguration(String subkey) {
		if (subkey == null) {
			return configuration;
		}
		return configuration.subset(subkey);
	}

	private Set replayHistory = new HashSet();

	public Set getReplayHistory() {
		return replayHistory;
	}

	public void clearReplayHistory() {
		replayHistory.clear();
	}

	private ProxyFinder proxyFinder;

	// private String[] exceptProxyDomainsList;
	public boolean updateProxyFinder(String pacUrl,String proxyServer,String sslProxyServer,String exceptProxyDomains) {
		try {
			ProxyFinder workProxyFinder = ProxyFinder.create(this,pacUrl, proxyServer,sslProxyServer, exceptProxyDomains,getSelfDomain(),getPacProxyPort());
			if(workProxyFinder==null){
				return false;
			}
			if( workProxyFinder.updatePac(phantomHome,mapper.getHttpPhantomDomians(), mapper.getSecurePhantomDomians())==false){
				return false;
			}
			if (proxyFinder != null) {
				proxyFinder.term();
			}
			config.setProperty("pacUrl", pacUrl);
			config.setProperty("proxyServer", proxyServer);
			config.setProperty("sslProxyServer", sslProxyServer);
			config.setProperty("exceptProxyDomains", exceptProxyDomains);
			proxyFinder = workProxyFinder;
			return true;
		} catch (IOException e) {
			logger.error("fail to create ProxyFinder.", e);
			return false;
		}
	}

	private String[] welcomFiles;

	public void setWelcomFiles(String welcomFilesString) {
		if (welcomFilesString == null) {
			welcomFiles = null;
			return;
		}
		welcomFiles = welcomFilesString.split(";");
	}

	public String[] getWelcomFiles() {
		return welcomFiles;
	}

	public ServerParser findProxyServer(boolean isSsl, String domain) {
		return proxyFinder.findProxyServer(isSsl, domain);
	}
	public ServerParser findProxyServer(String uri) {
		try {
			return proxyFinder.findProxyServer(new URI(uri));
		} catch (URISyntaxException e) {
			logger.warn("uri format error."+uri,e);
			return null;
		}
	}
	
	public String getProxyPac(Map param) {
		return proxyFinder.getProxyPac(param);
	}

	public String toJson() {
		return toJson(configuration);
	}

	public String toJson(Configuration configuration) {
		Iterator itr = configuration.getKeys();
		StringBuffer sb = new StringBuffer();
		sb.append("{");
		String sep = "'";
		while (itr.hasNext()) {
			String name = (String) itr.next();
			// ブラウザに見せても意味ないのは返さない
			if (name.startsWith("ReasonPhrase.")
					|| name.startsWith("ContentType.")) {
				continue;
			}
			Object value = configuration.getProperty(name);
			/*
			 * if(value==null){ continue; }
			 */
			sb.append(sep);
			sb.append(name);
			sb.append("'");
			sb.append(":'");
			sb.append(JsonUtil.escape(value));
			sb.append("'");
			sep = ",'";
		}
		sb.append("}");
		return sb.toString();
	}

	// proxyとして動作する時のAuthentication
	// private String proxyAuthenticate = null;
	// webサーバとして動作する時(reverseproxy,file)のAuthentication
	// private String webAuthenticate = null;
	// private String webAuthenticateCookieKey = null;
	// private Pattern webAuthenticatePattern = null;
	private long acceptTimeout;
	public long getAcceptTimeout() {
		return acceptTimeout;
	}

	public void setAcceptTimeout(long acceptTimeout) {
		configuration.setProperty(ACCEPT_TIMEOUT, acceptTimeout);
		this.acceptTimeout=acceptTimeout;
	}

	private long connectTimeout;
	public long getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(long connectTimeout) {
		configuration.setProperty(CONNECT_TIMEOUT, connectTimeout);
		this.connectTimeout=connectTimeout;
	}

	private long readTimeout;
	public long getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(long readTimeout) {
		configuration.setProperty(READ_TIMEOUT, readTimeout);
		this.readTimeout=readTimeout;
	}

	private long writeTimeout;
	public long getWriteTimeout() {
		return writeTimeout;
	}

	public void setWriteTimeout(long writeTimeout) {
		configuration.setProperty(WRITE_TIMEOUT, writeTimeout);
		this.writeTimeout=writeTimeout;
	}

	public void setContentEncoding(String contentEncoding){
		setProperty(CONTENT_ENCODING, contentEncoding);
		this.contentEncoding=contentEncoding;
	}
	
	public String getContentEncoding() {
		return contentEncoding;
	}

	public void setProxyKeepAlive(boolean isProxyKeepAlive){
		setProperty(IS_PROXY_KEEP_ALIVE, isProxyKeepAlive);
		this.isProxyKeepAlive=isProxyKeepAlive;
	}
	
	public boolean isProxyKeepAlive() {
		return isProxyKeepAlive;
	}
	
	public void setWebKeepAlive(boolean isWebKeepAlive){
		setProperty(IS_WEB_KEEP_ALIVE, isWebKeepAlive);
		this.isWebKeepAlive=isWebKeepAlive;
	}

	public boolean isWebKeepAlive() {
		return isWebKeepAlive;
	}

	public void setMaxKeepAliveRequests(int maxKeepAliveRequests){
		setProperty(MAX_KEEP_ALIVE_REQUESTS, maxKeepAliveRequests);
		this.maxKeepAliveRequests=maxKeepAliveRequests;
	}
	
	public int getMaxKeepAliveRequests() {
		return maxKeepAliveRequests;
	}

	public void setKeepAliveTimeout(int keepAliveTimeout){
		setProperty(KEEP_ALIVE_TIMEOUT, keepAliveTimeout);
		this.keepAliveTimeout=keepAliveTimeout;
	}
	
	public int getKeepAliveTimeout() {
		return keepAliveTimeout;
	}

	/* pac対応、個人設定がなければ全体設定を参照する */
	public Set<String> getHttpPhantomDomians() {
		return mapper.getHttpPhantomDomians();
	}

	public Set<String> getSecurePhantomDomians() {
		return mapper.getSecurePhantomDomians();
	}

	public boolean isNextPac() {
		return false;
	}

	public String nextPac() {
		if (isNextPac()) {
			return "";
		}
		return "";
	}

	public String getNextHttpProxy() {
		if (!isNextHttpProxy()) {
			return "";
		}
		return getString(PROXY_SERVER) + ":" + getString(PROXY_PORT);
	}

	public String getNextSecureProxy() {
		if (!isNextSecureProxy()) {
			return "";
		}
		return getString(SSL_PROXY_SERVER) + ":" + getString(SSL_PROXY_PORT);
	}

	public boolean isNextHttpProxy() {
		String proxyServer = getString(PROXY_SERVER);
		if (proxyServer != null && !"".equals(proxyServer)) {
			return true;
		}
		return false;
	}

	public boolean isNextSecureProxy() {
		String sslProxyServer = getString(SSL_PROXY_SERVER);
		if (sslProxyServer != null && !"".equals(sslProxyServer)) {
			return true;
		}
		return false;
	}

	public boolean isNextFtpProxy() {
		return false;
	}

	public boolean isNextSocksProxy() {
		return false;
	}
	
	/* configurationをデリゲート */
	public String getString(String key) {
		return getString(key,"");
	}

	public String getString(String key, String defaultValue) {
		return configuration.getString(key,defaultValue);
	}

	public int getInt(String key) {
		return configuration.getInt(key, 0);
	}

	public int getInt(String key, int defaultValue) {
		return configuration.getInt(key,defaultValue);
	}

	public long getLong(String key) {
		long defaultValue = 0;
		return configuration.getLong(key, defaultValue);
	}

	public long getLong(String key, long defaultValue) {
		return configuration.getLong(key,defaultValue);
	}

	public boolean getBoolean(String key) {
		return configuration.getBoolean(key, false);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		return configuration.getBoolean(key, false);
	}

	public String[] getStringArray(String key) {
		String[] result = configuration.getStringArray(key);
		return result;
	}

	public Object getProperty(String key) {
		Object result = configuration.getProperty(key);
		return result;
	}

	public void setProperty(String key, Object value) {
		configuration.setProperty(key, value);
	}

	public void clearProperty(String key) {
		configuration.clearProperty(key);
	}

	/* realHost関連 */
	public RealHost getRealHost(ServerParser server) {
		return RealHost.getRealHostByServer(server);
	}
	
	//acceptしたけどgetRealHostで見つからなかった場合使用
	public RealHost getRealHostByBindPost(int port) {
		return RealHost.getRealHostByBindPost(port);
	}

	/* 公開ディレクトリ関連 */
	public void setAdminDocumentRoot(File adminDocumentRoot){
		this.adminDocumentRoot=adminDocumentRoot;
	}
	public File getAdminDocumentRoot() {
		return adminDocumentRoot;
	}
	
	public void setAuthDocumentRoot(File authDocumentRoot){
		this.authDocumentRoot=authDocumentRoot;
	}
	public File getAuthDocumentRoot() {
		return authDocumentRoot;
	}
	
	public void setPublicDocumentRoot(File publicDocumentRoot){
		this.publicDocumentRoot=publicDocumentRoot;
	}
	
	public File getPublicDocumentRoot() {
		return publicDocumentRoot;
	}
	
	public File getAppsDocumentRoot() {
		return appsDocumentRoot;
	}

	public File getPortalDocumentRoot() {
		return portalDocumentRoot;
	}

	public File getInjectionDir() {
		return injectionDir;
	}

	public File getTmpDir() {
		return tmpDir;
	}

	public DiskFileItemFactory getUploadItemFactory() {
		return uploadItemFactory;
	}

	public File getPhantomHome() {
		return phantomHome;
	}
	
	//js等staticなコンテンツがあるところ,http://host:1280/pub
	public String getPublicWebUrl(){
		return mapper.getPublicWebUrl();
	}
	
	//管理画面のurl,http://host:1280/admin
	public String getAdminUrl(){
		return mapper.getAdminUrl();
	}
	
	//pac中でポイントする自身のport,1280
	public int getPacProxyPort(){
		return mapper.getPacProxyPort();
	}
	
	public String getSelfDomain() {
		return selfDomain;
	}

	public LogPersister getLogPersister() {
		return logPersister;
	}
	
	public ReplayHelper getReplayHelper() {
		return replayHelper;
	}

	public FilterHelper getFilterHelper() {
		return filterHelper;
	}

	public InjectionHelper getInjectionHelper() {
		return injectionHelper;
	}

	//verocityマクロから利用する
	public String getAuthUrl(){
		if(authorizer==null){//終了間際に呼ばれた場合あり得る
			return null;
		}
		return authorizer.getAuthUrl();
	}

	public File getSettingDir() {
		return settingDir;
	}

	public Object getStasticsObject() {
		return stasticsObject;
	}

	public void setStasticsObject(Object stasticsObject) {
		this.stasticsObject = stasticsObject;
	}
	
	/* Deamon配下で動作している場合、起動情報を格納 Deamon配下でない場合は、両者負の数を設定する */
	private int javaHeapSize;
	private int restartCount;
	public void setJavaHeapSize(int javaHeapSize){
		this.javaHeapSize=javaHeapSize;
	}
	public void setRestartCount(int restartCount){
		this.restartCount=restartCount;
	}
	public int getJavaHeapSize(){
		return javaHeapSize;
	}
	public int getRestartCount(){
		return restartCount;
	}
	
	private boolean isAllowChunked;
	private boolean isWebKeepAlive;
	private boolean isProxyKeepAlive;
	private int maxKeepAliveRequests;
	private String contentEncoding;
	private int keepAliveTimeout;
	private boolean isDebugTrace;
	private JSONArray poolInfo_100;
	
	public void setAllowChaned(boolean isAllowChunked){
		setProperty("allowChunked", Boolean.toString(isAllowChunked));
		this.isAllowChunked=isAllowChunked;
	}
	public boolean isAllowChunked(){
		return isAllowChunked;
	}
	
	public void setDebugTrace(boolean isDebugTrace){
		setProperty(DEBUG_TRACE, Boolean.toString(isDebugTrace));
		this.isDebugTrace=isDebugTrace;
	}
	public boolean isDebugTrace(){
		return isDebugTrace;
	}
	
	private String serverHeader;
	
	public void setServerHeader(String serverHeader){
		setProperty(PHANTOM_SERVER_HEADER, serverHeader);
		this.serverHeader=serverHeader;
	}
	
	public String getServerHeader(){
		return serverHeader;
	}
	
	public JSONArray getPoolInfo100(){
		return poolInfo_100;
	}
	
	public SpdyConfig getSpsyConfig(){
		return spdyConfig;
	}
	
	public LinkManager getAdminLinkManager(){
		return linkManager;
	}
	
	private VelocityEngine velocityEngine = null;
	
	public VelocityEngine getVelocityEngine() {
		if (velocityEngine != null) {
			return velocityEngine;
		}
		velocityEngine = new VelocityEngine();
		InputStream is=getClass().getClassLoader().getResourceAsStream("velocityClasspathResourceLoader.properties");
		Properties prop=new Properties();
		try {
			prop.load(is);
			is.close();
			velocityEngine.init(prop);
		} catch (Exception e) {
			velocityEngine=null;
			throw new RuntimeException("fail to velocityEngine.ini()", e);
		}finally{
			if(is!=null){
				try {
					is.close();
				} catch (IOException ignore) {
				}
			}
		}
		return velocityEngine;
	}
	
	public void forwardVelocityTemplate(ServerBaseHandler handler,String template){
		forwardVelocityTemplate(handler, template, null);
	}
	
	public void forwardVelocityTemplate(ServerBaseHandler handler,String template,Map param){
		if(param!=null){
			for(Object key:param.keySet()){
				handler.setAttribute(SCOPE.REQUEST,(String)key,param.get(key));
			}
		}
		handler.setAttribute(SCOPE.REQUEST,ServerBaseHandler.ATTRIBUTE_VELOCITY_ENGINE,getVelocityEngine());
		handler.setAttribute(SCOPE.REQUEST,ServerBaseHandler.ATTRIBUTE_VELOCITY_TEMPLATE,"/template/" +template);
		handler.forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
	}
	
	private  Configuration contentTypeConfig = null;
	private Configuration getContentTypeConfig() {
		if (contentTypeConfig == null) {
			contentTypeConfig = getConfig().getConfiguration("ContentType");
		}
		return contentTypeConfig;
	}
	
	public String getContentType(String fileName){
		int pos = fileName.lastIndexOf(".");
		String contentType=null;
		if (pos > 0) {
			String ext = fileName.substring(pos + 1).toLowerCase();
			contentType = getContentTypeConfig().getString(ext,null);
		}
		return contentType;
	}
}
