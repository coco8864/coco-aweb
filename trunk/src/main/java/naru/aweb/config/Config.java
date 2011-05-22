package naru.aweb.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
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

import naru.aweb.auth.Authenticator;
import naru.aweb.auth.Authorizer;
import naru.aweb.core.Main;
import naru.aweb.core.RealHost;
import naru.aweb.handler.FilterHelper;
import naru.aweb.handler.InjectionHelper;
import naru.aweb.handler.ReplayHelper;
import naru.aweb.mapping.Mapper;
import naru.aweb.queue.QueueManager;
import naru.aweb.secure.SslContextPool;
import naru.aweb.util.JdoUtil;
import naru.aweb.util.JsonUtil;
import naru.aweb.util.ServerParser;
import naru.queuelet.QueueletContext;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.DatabaseConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.log4j.Logger;

public class Config {
	private static final String CONFIG_MARK_CREATE = "_config_create_";
	private static Logger logger = Logger.getLogger(Config.class);
	private static String PROPERTIES_ENCODING = "utf-8";
	private static final String READ_TIMEOUT = "readTimeout";
	private static final long READ_TIMEOUT_DEFAULT = 10000;
	private static final String WRITE_TIMEOUT = "writeTimeout";
	private static final long WRITE_TIMEOUT_DEFAULT = 1000;
	private static final String ACCEPT_TIMEOUT = "acceptTimeout";
	private static final long ACCEPT_TIMEOUT_DEFAULT = 100;
	private static final String CONNECT_TIMEOUT = "connectTimeout";
	private static final long CONNECT_TIMEOUT_DEFAULT = 1000;
	private static final String SSL_PROXY_PORT = "sslProxyPort";
	private static final String SSL_PROXY_SERVER = "sslProxyServer";
	private static final String PROXY_PORT = "proxyPort";
	private static final String PROXY_SERVER = "proxyServer";
	private static final String PATH_PUBLIC_DOCROOT = "path.publicDocroot";
	private static final String PATH_PORTAL_DOCROOT = "path.portalDocroot";
	private static final String PATH_ADMIN_DOCROOT = "path.adminDocroot";
	private static final String PATH_SETTING = "path.setting";
	private static final String PATH_INJECTION_DIR = "path.injectionDir";
	private static Config config = new Config();
	private static QueueManager queueManager = QueueManager.getInstance();
	public static String CONF_FILENAME = "phantom.properties";
	public static final String DEBUG_TRACE="debugTrace";
	public static final String SELF_DOMAIN = "selfDomain";
	private static final String REAL_HOSTS = "realHosts";
	private static final String REAL_HOST = "realHost";

	private File adminDocumentRoot;// adminで見えるところ(内部的に使うリソースもここ）
	private File portalDocumentRoot;// for portal
	private File publicDocumentRoot;// 誰でも見えるところ
	private File injectionDir;// 注入コンテンツを格納するディレクトリ
	private static SSLContext sslContext;
//	private File baseDir;
	private File phantomHome;
//	private Set<RealHost> realHosts = new HashSet<RealHost>();
//	private Map<ServerParser, RealHost> serverRealHostMap = new HashMap<ServerParser, RealHost>();
	private Mapper mapper;
	private Authenticator authenticator;
	private Authorizer authorizer;
	private LogPersister logPersister;
	private SslContextPool sslContextPool;
	private ReplayHelper replayHelper;
	private FilterHelper filterHelper;
	private InjectionHelper injectionHelper;
	private Configuration configuration = null;
	private QueueletContext queueletContext = null;
	private Object stasticsObject;
	// private Map authHeaders;
	// private Map replayHistory;
	// private Map replayPaths;
	// private Pattern exceptProxyDomainsPattern;
	private File tmpDir;
	private DiskFileItemFactory uploadItemFactory;
	private File settingDir;

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

	public static String encodeBase64(String text) {
		return encodeBase64(text, "iso8859_1");
	}

	public static String encodeBase64(byte[] data) {
		try {
			byte[] bytes = Base64.encodeBase64(data);
			return new String(bytes, "iso8859_1");
		} catch (UnsupportedEncodingException e) {
			logger.error("unkown enc:iso8859_1", e);
			throw new RuntimeException("unkown enc:iso8859_1", e);
		}
	}

	public static String encodeBase64(String text, String enc) {
		try {
			return encodeBase64(text.getBytes(enc));
		} catch (UnsupportedEncodingException e) {
			logger.error("unkown enc:" + enc, e);
			throw new RuntimeException("unkown enc:" + enc, e);
		}
	}

	public static String decodeBase64(String text) {
		return decodeBase64(text, "iso8859_1");
	}

	public static byte[] decodeBase64Bytes(String text) {
		try {
			return Base64.decodeBase64(text.getBytes("iso8859_1"));
		} catch (UnsupportedEncodingException e) {
			logger.error("unkown enc:iso8859_1", e);
			throw new RuntimeException("unkown enc:iso8859_1", e);
		}
	}

	public static String decodeBase64(String text, String enc) {
		try {
			byte[] bytes = decodeBase64Bytes(text);
			return new String(bytes, enc);
		} catch (UnsupportedEncodingException e) {
			logger.error("unkown enc:" + enc, e);
			throw new RuntimeException("unkown enc:" + enc, e);
		}
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
		if (server == null) {
			return sslContext.createSSLEngine(null, 443);
		} else {
			SSLContext sc = sslContextPool.getSSLContext(server.getHost());
			return sc.createSSLEngine(server.getHost(), server.getPort());
		}
	}

	private boolean checkAndCreateDb(BasicDataSource dataSource,
			boolean isCleanup) throws SQLException {
		Connection con = null;
		try {
			con = dataSource.getConnection();
			Statement st = con.createStatement();
			st.execute("SELECT count(*) FROM CONFIGURATION");
			if (isCleanup) {
				st.executeUpdate("DROP TABLE CONFIGURATION");
				st.executeUpdate("CREATE TABLE CONFIGURATION(KEY VARCHAR(512) NOT NULL PRIMARY KEY,VALUE VARCHAR(512))");
			}
		} catch (SQLException e) {
			logger.debug("dbcheck exception.",e);
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

	private Configuration crateConfiguration(QueueletContext context,
			boolean isCleanup) {
		InputStream is = Config.class.getClassLoader().getResourceAsStream(CONF_FILENAME);
		if (is == null) {
			throw new RuntimeException("not found in class path.CONF_FILENAME:"	+ CONF_FILENAME);
		}
		Configuration fileConfig = loadConfig(is, "MS932");
		String url = context.resolveProperty(
				fileConfig.getString("config.url"), null);
		String user = context.resolveProperty(fileConfig.getString("config.user"), null);
		String pass = context.resolveProperty(fileConfig.getString("config.pass"), null);
		String driver = context.resolveProperty(fileConfig.getString("config.driver"), null);
		String table = context.resolveProperty(fileConfig.getString("config.table"), null);
		String keyColumn = context.resolveProperty(fileConfig.getString("config.key"), null);
		String valueColumn = context.resolveProperty(fileConfig.getString("config.value"), null);

		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setDriverClassName(driver);
		dataSource.setUrl(url);
		dataSource.setUsername(user);
		dataSource.setPassword(pass);
		dataSource.setDefaultAutoCommit(true);
		boolean isCreate;
		try {
			isCreate = checkAndCreateDb(dataSource, isCleanup);
		} catch (SQLException e) {
			logger.error("fail to checkAndCreateDb.", e);
			throw new RuntimeException("fail to checkAndCreateDb.", e);
		}
		DatabaseConfiguration dbConfig = new DatabaseConfiguration(dataSource,
				table, keyColumn, valueColumn);
		// DBには、","区切で格納しアプリで分解する
		dbConfig.setDelimiterParsingDisabled(true);
		/* phantom.propetiesは最優先、他の設定(web,config)では上書きできないようにする */
		Iterator itr = fileConfig.getKeys();
		while (itr.hasNext()) {
			String key = (String) itr.next();
			String value = context.resolveProperty(fileConfig.getString(key),
					null);
			dbConfig.setProperty(key, value);
			logger.debug(key + ":" + value);
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
			logger.debug("key:" + key + " values:" + values);
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
			File backup = new File(propFile.getAbsolutePath()
					+ format.format(new Date()));
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
		if (isAleadyTerm) {
			return;
		}
		isAleadyTerm = true;
		if(broadcaster!=null){//initの途中で例外した場合broadcasterの可能性がある。
			broadcaster.term();
			broadcaster=null;
		}
		queueManager.term();
		logPersister.term();
		authenticator.term();
		authorizer.term();
	}
	
	public void addRealHost(RealHost realHost){
		RealHost.addRealHost(realHost);
		updateRealHosts();
	}
	
	public void delRealHost(String name){
		RealHost.delRealHost(name);
		updateRealHosts();
	}
	
	private void updateRealHosts(){
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

	public boolean init(QueueletContext queueletContext, boolean isCleanup) {
		this.queueletContext = queueletContext;
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
		if (isCleanup) {
			JdoUtil.cleanup(Performance.class);
			JdoUtil.cleanup(AccessLog.class);
			JdoUtil.cleanup(CommissionAuthEntry.class);
			JdoUtil.cleanup(CommissionAuthRole.class);
			JdoUtil.cleanup(CommissionAuthUrl.class);
			JdoUtil.cleanup(CommissionAuth.class);
			JdoUtil.cleanup(User.class);
			Mapping.cleanup(new File(settingPath, "mapping.init"));
		}
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
		sslContextPool = new SslContextPool(this);
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
		// Mapper作成には、realHostsが必要、realHostsの初期化の後に呼び出す。
		authorizer=new Authorizer(configuration);
		mapper = new Mapper(this);
		
		// コンテンツ圧縮
		// contentEncoding = configuration.getString("contentEncoding");

		// keepAlive関連
		// isProxyKeepAlive = configuration.getBoolean("isProxyKeepAlive",
		// false);
		// isWebKeepAlive = configuration.getBoolean("isWebKeepAlive", false);
		// maxKeepAliveRequests =
		// configuration.getInt("maxKeepAliveRequests",100);
		// keepAliveTimeout = configuration.getInt("keepAliveTimeout", 15000);
		String dir = configuration.getString(PATH_PUBLIC_DOCROOT);
		publicDocumentRoot = new File(dir);
		dir = configuration.getString(PATH_ADMIN_DOCROOT);
		adminDocumentRoot = new File(dir);
		dir = configuration.getString(PATH_PORTAL_DOCROOT);
		portalDocumentRoot = new File(dir);
		dir = configuration.getString(PATH_INJECTION_DIR);
		injectionDir = new File(dir);
		injectionHelper = new InjectionHelper(this);
		
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
		
		// proxy除外リストの初期化
		updateProxyFinder(pacUrl,proxyServer,sslProxyServer,exceptProxyDomains);
		broadcaster=new Broadcaster(this);
		return true;
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
			ProxyFinder workProxyFinder = ProxyFinder.create(pacUrl, proxyServer,sslProxyServer, exceptProxyDomains,getSelfDomain(),getPacProxyPort());
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

	public String getProxyPac(String localServer) {
		return proxyFinder.getProxyPac(localServer);
	}

	// / *.bbb.com は、"\\S*.bbb.com"でマッチ
	/*
	 * public boolean isUseProxy(boolean isSsl,String domain) { if (domain ==
	 * null) { return false;// proxyサーバ設定がなければproxyなし } if(isSsl){
	 * if(!isNextSecureProxy()){ return false; } }else{ if(!isNextHttpProxy()){
	 * return false; } } if (exceptProxyDomainsPattern == null) { return true; }
	 * Matcher matcher = null; synchronized (exceptProxyDomainsPattern) {
	 * matcher = exceptProxyDomainsPattern.matcher(domain); } //
	 * マッチしたら、proxyを使わない、マッチしなかったらproxyを使う return !matcher.matches(); }
	 */
	/*
	 * public boolean isSslProxyMappingServer(String realHost, ServerParser
	 * server) { return mapper.isResolveSslProxyServer(realHost,server); }
	 * 
	 * public MappingResult sslProxyMapping(String realHost, ServerParser
	 * server,String path) { return
	 * mapper.resolveSslProxy(realHost,server,path); } public MappingResult
	 * proxyMapping(String realHost, ServerParser server,String path) { return
	 * mapper.resolveProxy(realHost,server,path); } public MappingResult
	 * webMapping(String realHost, boolean isSsl,ServerParser hostHeader,String
	 * path) { return mapper.resolveWeb(realHost,isSsl,hostHeader,path); }
	 */

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
	public long getAcceptTimeout() {
		return getLong(ACCEPT_TIMEOUT, ACCEPT_TIMEOUT_DEFAULT);
	}

	public void setAcceptTimeout(long acceptTimeout) {
		configuration.setProperty(ACCEPT_TIMEOUT, acceptTimeout);
	}

	public long getConnectTimeout() {
		return getLong(CONNECT_TIMEOUT, CONNECT_TIMEOUT_DEFAULT);
	}

	public void setConnectTimeout(long connectTimeout) {
		configuration.setProperty(CONNECT_TIMEOUT, connectTimeout);
	}

	public long getReadTimeout() {
		return getLong(READ_TIMEOUT, READ_TIMEOUT_DEFAULT);
	}

	public void setReadTimeout(long readTimeout) {
		configuration.setProperty(READ_TIMEOUT, readTimeout);
	}

	public long getWriteTimeout() {
		return getLong(WRITE_TIMEOUT, WRITE_TIMEOUT_DEFAULT);
	}

	public void setWriteTimeout(long writeTimeout) {
		configuration.setProperty(WRITE_TIMEOUT, writeTimeout);
	}

	public String getContentEncoding() {
		return configuration.getString("contentEncoding");
	}

	public boolean isProxyKeepAlive() {
		return configuration.getBoolean("isProxyKeepAlive", false);
	}

	public boolean isWebKeepAlive() {
		return configuration.getBoolean("isWebKeepAlive", false);
	}

	public int getMaxKeepAliveRequests() {
		return configuration.getInt("maxKeepAliveRequests", 100);
	}

	public int getKeepAliveTimeout() {
		return configuration.getInt("keepAliveTimeout", 15000);
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

	/*
	 * private static String[] NO_DOMEINS=new String[0]; public String[]
	 * getExceptDomians() { if(!isNextSecureProxy()&&!isNextHttpProxy()){ return
	 * NO_DOMEINS; } return exceptProxyDomainsList; }
	 */

	/* configurationをデリゲート */
	public String getString(String key) {
		String result = configuration.getString(key);
		return result;
	}

	public String getString(String key, String defaultValue) {
		return configuration.getString(key, defaultValue);
	}

	public int getInt(String key) {
		int defaultValue = 0;
		return configuration.getInt(key, defaultValue);
	}

	public int getInt(String key, int defaultValue) {
		return configuration.getInt(key, defaultValue);
	}

	public long getLong(String key) {
		long defaultValue = 0;
		return configuration.getLong(key, defaultValue);
	}

	public long getLong(String key, long defaultValue) {
		return configuration.getLong(key, defaultValue);
	}

	public boolean getBoolean(String key) {
		return configuration.getBoolean(key, false);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		return configuration.getBoolean(key, defaultValue);
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
//	public Set<RealHost> getRealHosts() {
//		return realHosts;
//	}

	public RealHost getRealHost(ServerParser server) {
		return RealHost.getRealHostByServer(server);
	}

	/* 公開ディレクトリ関連 */
	public File getAdminDocumentRoot() {
		return adminDocumentRoot;
	}

	public File getPublicDocumentRoot() {
		return publicDocumentRoot;
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
		return getString(SELF_DOMAIN, "127.0.0.1");
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
}
