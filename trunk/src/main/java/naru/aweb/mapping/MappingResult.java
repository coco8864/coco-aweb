package naru.aweb.mapping;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.pool.PoolBase;
import naru.aweb.auth.AuthHandler;
import naru.aweb.auth.MappingAuth;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.util.ServerParser;

public class MappingResult extends PoolBase {
	public static String PARAMETER_FILE_WELCOM_FILES = "fileWeblcomFiles";
	public static String PARAMETER_FILE_LISTING = "fileListing";
	public static String PARAMETER_FILE_RESOURCE_PATH = "resoucePath";
	public static String PARAMETER_VELOCITY_USE = "velocityUse";
	public static String PARAMETER_VELOCITY_RESOUCE_PATH = "velocityResoucePath";
	public static String PARAMETER_VELOCITY_EXTENTIONS = "velocityExtentions";
	private static Config config=Config.getConfig();
	
	private Mapping mapping;
	private boolean isForwardAuth=false;//認証処理のため、AuthHandlerにfowardするMappingResult
	// sourceTypeは、mappingEntryに入っている
	private Mapping.SecureType targetSecureType;
	private ServerParser targetServer = null;// ブラウザから入力されたserver
	private String targetPath = null;// ブラウザから入力されたpath
	private Mapping.DestinationType resolveType;
	private ServerParser resolveServer = null;
	private String resolvePath = null;
	private Class handlerClass = null;
	private Map<String, Object> attribute = new HashMap<String, Object>();// mappingResultに付随する属性
	/*
	 * private static Mapping sslProxyMapping= new
	 * Mapping(true,"sslProxyMapping",Mapping.SourceType.PROXY,Mapping.SecureType.SSL,"","/",Mapping.DestinationType.HTTPS,"","/","{}");
	 * private static Mapping proxyMapping= new
	 * Mapping(true,"proxyMapping",Mapping.SourceType.PROXY,Mapping.SecureType.PLAIN,"","/",Mapping.DestinationType.HTTP,"","/","{}");
	 */

	/**
	 * デフォルトのproxy動作
	 * 
	 * @param uri
	 * @return
	 */
	/*
	 * public static MappingResult createSslProxyResult(ServerParser server) {
	 * MappingResult
	 * mappingResult=(MappingResult)PoolManager.getInstance(MappingResult.class);
	 * mappingResult.setTargetSecureType(Mapping.SecureType.SSL);
	 * mappingResult.setResolveType(Mapping.DestinationType.HTTPS);
	 * mappingResult.handlerClass=Mapping.SSL_PROXY_HANDLER;
	 * mappingResult.resolveServer=server;
	 * mappingResult.resolveServer.ref();//この場合、requestParserにあるものをそのまま流用するので自分で作っていない
	 * mappingResult.mapping=sslProxyMapping; return mappingResult; }
	 */

	/**
	 * デフォルトのssl proxy動作
	 * 
	 * @param uri
	 * @return
	 */
	/*
	 * public static MappingResult createProxyResult(ServerParser server,String
	 * path) { MappingResult
	 * mappingResult=(MappingResult)PoolManager.getInstance(MappingResult.class);
	 * mappingResult.setTargetSecureType(Mapping.SecureType.PLAIN);
	 * mappingResult.setResolveType(Mapping.DestinationType.HTTP);
	 * mappingResult.handlerClass=Mapping.PROXY_HANDLER;
	 * mappingResult.resolveServer=server;
	 * mappingResult.resolveServer.ref();//この場合、requestParserにあるものをそのまま流用するので自分で作っていない
	 * mappingResult.resolvePath=path; mappingResult.mapping=proxyMapping;
	 * return mappingResult; }
	 */
	// Server mappingは完了している,path mappingのみを実行
	// public MappingResult sslProxyWebMapping(String realHostName,ServerParser
	// targetServer,String targetPath) {
	// MappingResult
	// mappingResult=(MappingResult)PoolManager.getInstance(MappingResult.class);
	// return mappingEntry.resolve(realHostName, targetServer, targetPath);
	// }
	public void recycle() {
		targetSecureType = null;
		targetServer = null;// requestParserに管理されているものを使用
		targetPath = null;
		mapping = null;
		resolveType = null;
		if (resolveServer != null) {
			resolveServer.unref();
			resolveServer = null;
		}
		resolvePath = null;
		handlerClass = null;
		curDestinationFile = null;
		curOption.clear();
		super.recycle();
		isForwardAuth=false;
	}

	public void forwardAuth(){
		isForwardAuth=true;
	}
	
	public Mapping.LogType getLogType() {
		if(config.isDebugTrace()){//全リクエストのtraceを採取
			return Mapping.LogType.TRACE;
		}
		if (mapping == null) {
			// TODO defaultのログタイプを持ちたい
			return Mapping.LogType.NONE;
		}
		if(isForwardAuth){
			//TODO authMappingから引っ張ってくるのが正解だが
			return Mapping.LogType.NONE;
		}
		return mapping.getLogType();
	}

	public boolean isAdmin() {
		if (mapping == null) {
			return false;
		}
		return mapping.isAdmin();
	}

	private static final List<String>EMPTY_LIST=new ArrayList<String>();
	public List<String> getRolesList() {
		if (mapping == null) {
			return EMPTY_LIST;
		}
		return mapping.getRolesList();
	}
	
	public MappingAuth getMappingAuth(){
		if (mapping == null) {
			return null;
		}
		return mapping.getMappingAuth();
	}

	public boolean isSourceTypeProxy() {
		return Mapping.SourceType.PROXY.equals(mapping.getSourceType());
	}

	public boolean isSourceTypeWeb() {
		return Mapping.SourceType.WEB.equals(mapping.getSourceType());
	}

	// 処理の過程でDestinationを変更したい場合、Result層で覚える
	private Map<String, Object> curOption = new HashMap<String, Object>();

	public Object getOption(String key) {
		Object value = curOption.get(key);
		if (value != null) {
			return value;
		}
		if(mapping==null){
			return null;
		}
		return mapping.getOption(key);
	}

	public void setOption(String key, Object value) {
		curOption.put(key, value);
	}

	public ServerParser getResolveServer() {
		return resolveServer;
	}

	public Class getHandlerClass() {
		if(isForwardAuth){
			return AuthHandler.class;
		}
		return handlerClass;
	}

	public void setResolveServer(ServerParser resolveServer) {
		this.resolveServer = resolveServer;
	}

	public void setHandlerClass(Class handlerClass) {
		this.handlerClass = handlerClass;
	}

	public String getResolvePath() {
		return resolvePath;
	}

	public void setResolvePath(String resolvePath) {
		this.resolvePath = resolvePath;
	}

	public void setMapping(Mapping mapping) {
		this.mapping = mapping;
	}
	
	public Mapping getMapping() {
		return mapping;
	}

	public void setResolveType(Mapping.DestinationType resolveType) {
		this.resolveType = resolveType;
	}

	// 処理の過程でDestinationを変更したい場合、Result層で覚える
	private File curDestinationFile = null;

	public void setDesitinationFile(File setDesitinationFile) {
		this.curDestinationFile = setDesitinationFile;
	}

	public File getDestinationFile() {
		if (curDestinationFile != null) {
			return curDestinationFile;
		}
		if (mapping == null) {
			return null;
		}
		return mapping.getDestinationFile();
	}

	public boolean isResolvedHttps() {
		if(Mapping.DestinationType.HTTPS.equals(resolveType)){
			return true;
		}
		if(Mapping.DestinationType.WSS.equals(resolveType)){
			return true;
		}
		return false;
	}

	public Mapping.DestinationType getDestinationType() {
		return resolveType;
	}

	public ServerParser getTargetServer() {
		return targetServer;
	}

	public void setTargetServer(ServerParser targetServer) {
		this.targetServer = targetServer;
	}

	public String getTargetPath() {
		return targetPath;
	}

	public String getSourcePath() {
		if (mapping == null) {
			return null;
		}
		return mapping.getSourcePath();
	}

	public void setTargetPath(String targetPath) {
		this.targetPath = targetPath;
	}

	public Mapping.SecureType getTargetSecureType() {
		return targetSecureType;
	}

	public void setTargetSecureType(Mapping.SecureType targetSecureType) {
		this.targetSecureType = targetSecureType;
	}

	private static Pattern locationPattern = Pattern.compile("^([^:]*)://([^/\\s]*)(/\\S*)?");

	/**
	 * locationヘッダの書き換え用、逆変換する mapping結果が、locationとなるような入力URLは何になるか？
	 * 
	 * @param location
	 * @return
	 */
	public String reverseResolve(String location) {
		if (resolveServer == null) {
			return location;
		}
		if (targetServer == null) {// createProxyResultメソッドで作られた場合
			return location;
		}
		Matcher matcher = null;
		synchronized (locationPattern) {
			matcher = locationPattern.matcher(location);
		}
		if (!matcher.find()) {
			return location;
		}
		String locationScehme = matcher.group(1);
		String locationServerString = matcher.group(2);
		String locationPath = matcher.group(3);
		ServerParser locationServer = null;
		if (isResolvedHttps()) {
			if ("http".equals(locationScehme)) {
				return location;
			}
			locationServer = ServerParser.parse(locationServerString, 443);
		} else {
			if ("https".equals(locationScehme)) {
				return location;
			}
			locationServer = ServerParser.parse(locationServerString, 80);
		}
		if (!locationServer.equals(resolveServer)) {
			locationServer.unref(true);
			return location;
		}
		locationServer.unref(true);
		locationServer = null;
		String destinationPath = mapping.getDestinationPath();
		if (!locationPath.startsWith(destinationPath)) {
			return location;
		}
		StringBuilder sb = new StringBuilder();
		switch (targetSecureType) {
		case PLAIN:
			sb.append("http://");
			break;
		case SSL:
			sb.append("https://");
			break;
		default:
			return location;
		}
		sb.append(targetServer.toString());
		String sourcePath = mapping.getSourcePath();
		if (sourcePath.equals(destinationPath)) {
			sb.append(locationPath);
		} else {
			sb.append(sourcePath);
			sb.append(locationPath.substring(destinationPath.length()));
		}
		return sb.toString();
	}

	public String getResolveUrl() {
		StringBuilder sb = new StringBuilder();
		if (isResolvedHttps()) {
			sb.append("https://");
		} else {
			sb.append("http://");
		}
		sb.append(getResolveServer().toString());
		sb.append(getResolvePath().toString());
		return sb.toString();
	}

	public String getResolveDomain() {
		StringBuilder sb = new StringBuilder();
		if (isResolvedHttps()) {
			sb.append("https://");
		} else {
			sb.append("http://");
		}
		sb.append(getResolveServer().toString());
		return sb.toString();
	}

	public void setAttribute(String name, Object value) {
		attribute.put(name, value);
	}

	public Object getAttribute(String name) {
		return attribute.get(name);
	}
}
