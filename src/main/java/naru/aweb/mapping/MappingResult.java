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
	private boolean isForwardAuth=false;//�F�؏����̂��߁AAuthHandler��foward����MappingResult
	// sourceType�́AmappingEntry�ɓ����Ă���
	private Mapping.SecureType targetSecureType;
	private ServerParser targetServer = null;// �u���E�U������͂��ꂽserver
	private String targetPath = null;// �u���E�U������͂��ꂽpath
	private Mapping.DestinationType resolveType;
	private ServerParser resolveServer = null;
	private String resolvePath = null;
	private Class handlerClass = null;
	private Map<String, Object> attribute = new HashMap<String, Object>();// mappingResult�ɕt�����鑮��
	/*
	 * private static Mapping sslProxyMapping= new
	 * Mapping(true,"sslProxyMapping",Mapping.SourceType.PROXY,Mapping.SecureType.SSL,"","/",Mapping.DestinationType.HTTPS,"","/","{}");
	 * private static Mapping proxyMapping= new
	 * Mapping(true,"proxyMapping",Mapping.SourceType.PROXY,Mapping.SecureType.PLAIN,"","/",Mapping.DestinationType.HTTP,"","/","{}");
	 */

	/**
	 * �f�t�H���g��proxy����
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
	 * mappingResult.resolveServer.ref();//���̏ꍇ�ArequestParser�ɂ�����̂����̂܂ܗ��p����̂Ŏ����ō���Ă��Ȃ�
	 * mappingResult.mapping=sslProxyMapping; return mappingResult; }
	 */

	/**
	 * �f�t�H���g��ssl proxy����
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
	 * mappingResult.resolveServer.ref();//���̏ꍇ�ArequestParser�ɂ�����̂����̂܂ܗ��p����̂Ŏ����ō���Ă��Ȃ�
	 * mappingResult.resolvePath=path; mappingResult.mapping=proxyMapping;
	 * return mappingResult; }
	 */
	// Server mapping�͊������Ă���,path mapping�݂̂����s
	// public MappingResult sslProxyWebMapping(String realHostName,ServerParser
	// targetServer,String targetPath) {
	// MappingResult
	// mappingResult=(MappingResult)PoolManager.getInstance(MappingResult.class);
	// return mappingEntry.resolve(realHostName, targetServer, targetPath);
	// }
	public void recycle() {
		targetSecureType = null;
		targetServer = null;// requestParser�ɊǗ�����Ă�����̂��g�p
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
		if(config.isDebugTrace()){//�S���N�G�X�g��trace���̎�
			return Mapping.LogType.TRACE;
		}
		if (mapping == null) {
			// TODO default�̃��O�^�C�v����������
			return Mapping.LogType.NONE;
		}
		if(isForwardAuth){
			//TODO authMapping������������Ă���̂���������
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

	// �����̉ߒ���Destination��ύX�������ꍇ�AResult�w�Ŋo����
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

	// �����̉ߒ���Destination��ύX�������ꍇ�AResult�w�Ŋo����
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
	 * location�w�b�_�̏��������p�A�t�ϊ����� mapping���ʂ��Alocation�ƂȂ�悤�ȓ���URL�͉��ɂȂ邩�H
	 * 
	 * @param location
	 * @return
	 */
	public String reverseResolve(String location) {
		if (resolveServer == null) {
			return location;
		}
		if (targetServer == null) {// createProxyResult���\�b�h�ō��ꂽ�ꍇ
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
