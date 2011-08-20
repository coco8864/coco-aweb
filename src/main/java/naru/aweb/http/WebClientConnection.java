package naru.aweb.http;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.auth.SessionId;
import naru.aweb.config.Config;
import naru.aweb.util.ServerParser;

public class WebClientConnection extends PoolBase {
	private static Logger logger = Logger.getLogger(WebClientConnection.class);
	private static Config config=Config.getConfig();
	
	public void recycle() {
		isUseProxy=isHttps=false;
		remoteServer=targetServer=null;
		remotePort=targetPort=-1;
		super.recycle();
	}
	private boolean isUseProxy;// proxy���o�R���Đڑ��������ۂ�
	private boolean isHttps;// https���ۂ�
	private String remoteServer;// �ڑ����Ă���T�[�o,isUseProxy�̏ꍇproxy�T�[�o
	private int remotePort;// �ڑ����Ă���T�[�o�̃|�[�g,isUseProxy�̏ꍇproxy�T�[�o
	private String targetServer;// ���N�G�X�g��T�[�o
	private int targetPort;// ���N�G�X�g��|�[�g
	
	public static WebClientConnection create(boolean isHttps, String targetServer,int targetPort){
		WebClientConnection webClinetConnection=(WebClientConnection)PoolManager.getInstance(WebClientConnection.class);
		webClinetConnection.init(isHttps, targetServer, targetPort);
		return webClinetConnection;
	}
	
	public void init(boolean isHttps, String targetServer,int targetPort){
		this.targetServer = targetServer;
		this.targetPort = targetPort;
		this.isHttps = isHttps;
		ServerParser parser=config.findProxyServer(isHttps, targetServer);
		if(parser!=null){
			this.isUseProxy=true;
			this.remoteServer = parser.getHost();
			this.remotePort = parser.getPort();
		}else{
			this.isUseProxy=false;
			this.remoteServer = targetServer;
			this.remotePort = targetPort;
		}
		/*
		this.isUseProxy = config.isUseProxy(isHttps,targetServer);
		if (this.isUseProxy) {
			if (isHttps) {
				this.remoteServer = config.getString("sslProxyServer");
				this.remotePort = config.getInt("sslProxyPort");
			} else {
				this.remoteServer = config.getString("proxyServer");
				this.remotePort = config.getInt("proxyPort");
			}
		} else {
			this.remoteServer = targetServer;
			this.remotePort = targetPort;
		}
		*/
	}
	
	private static final Set<String> DELETE_HEADERS=new HashSet<String>();
	static{
		DELETE_HEADERS.add(HeaderParser.CONNECTION_HEADER);
		DELETE_HEADERS.add(HeaderParser.PROXY_CONNECTION_HEADER);
	}
	/**
	 * TODO requestHeader��ύX�̂����Ȃ��̂����͂����肷��
	 * 
	 * getTargetServer�ɐڑ�����ۂ̃��N�G�X�g�w�b�_��buffer�ŕԋp����B
	 * @param requestHeader
	 * @param isCallerkeepAlive
	 * @return
	 */
	public ByteBuffer[] getRequestHeaderBuffer(String requestLine,HeaderParser requestHeader,boolean isCallerkeepAlive){
//		String orgUri=requestHeader.getRequestUri();
//		String requestUri = requestUri(requestHeader);
//		requestHeader.setRequestUri(requestUri);
		Map<String,String> overloadHeaders=new HashMap<String,String>();
		//���N�G�X�g�w�b�_�ɂ�Phantom Proxy��sessionId���t���Ă��邩������Ȃ��̂ō폜
		requestHeader.getAndRemoveCookieHeader(SessionId.SESSION_ID);
		//TODO mapping�F�؂����g���[�X�������ꍇ�Aauthentication header�����Ă���A�폜
		//�������o�b�N��proxy���K�v�Ƃ��Ă���\��������
		requestHeader.removeHeader(HeaderParser.PROXY_AUTHORIZATION_HEADER);
		//TODO Referer��ph.xxx�ɂȂ��Ă��邩������Ȃ��폜�������͏C�������ق����悢
		overloadHeaders.put(HeaderParser.HOST_HEADER,getTargetServer()+":"+getTargetPort());
		KeepAliveContext.setConnectionHandler(overloadHeaders,(!isHttps() && isUseProxy()),isCallerkeepAlive);
		ByteBuffer[] requestHeaderBuffer = requestHeader.getHeaderBuffer(requestLine,overloadHeaders,DELETE_HEADERS);
//		System.out.println(new String(requestHeaderBuffer[0].array(),requestHeaderBuffer[0].position(),requestHeaderBuffer[0].limit()));
		
		return requestHeaderBuffer;
	}
	
//	public ByteBuffer[] getRequestHeaderBuffer(HeaderParser requestHeader,boolean isCallerkeepAlive){
//		return getRequestHeaderBuffer(null,requestHeader,isCallerkeepAlive);
//	}
	
	public String getRequestLine(HeaderParser requestHeader){
		String requestUri=requestUri(requestHeader);
//		requestHeader.setRequestUri(requestUri);
		StringBuilder sb=new StringBuilder(requestHeader.getMethod());
		sb.append(" ");
		sb.append(requestUri);
		sb.append(" ");
		sb.append(requestHeader.getReqHttpVersion());
		return sb.toString();
	}
	
	/**
	 * ���N�G�X�g���C���̕ҏW
	 */
	private String requestUri(HeaderParser requestHeader) {
		String path=requestHeader.getPath();
		String query=requestHeader.getQuery();
		if(query!=null){
			path=path+"?"+query;
		}
		if (!isUseProxy || isHttps) {
			// ���ڃ��N�G�X�g����ꍇ�A��������https,https proxy�̏ꍇ
			return path;
		}
		// http proxy��ʉ߂���ꍇ
		return "http://" + targetServer + ":" + targetPort + path;
	}
	
	public boolean equalsConnection(boolean isHttps, String targetServer,int targetPort){
		if(this.isHttps!=isHttps){
			logger.debug("equalsConnection not equals isHttps:"+this.isHttps+":"+isHttps);
			return false;
		}
		if(!this.targetServer.equals(targetServer)){
			logger.debug("equalsConnection not equals targetServer:"+this.targetServer+":"+targetServer);
			return false;
		}
		if(this.targetPort!=targetPort){
			logger.debug("equalsConnection not equals targetPort:"+this.targetPort+":"+targetPort);
			return false;
		}
		return true;
	}

	public boolean isUseProxy() {
		return isUseProxy;
	}

	public boolean isHttps() {
		return isHttps;
	}

	/**
	 * proxy�o�R�̏ꍇ�Aproxy�T�[�o���|�C���g
	 * Web�T�[�o�Ƀ��N�G�X�g����ꍇ�́AtargetServer�Ɠ���
	 * @return
	 */
	public String getRemoteServer() {
		return remoteServer;
	}

	/**
	 * proxy�o�R�̏ꍇ�Aproxy port���|�C���g
	 * Web�T�[�o�Ƀ��N�G�X�g����ꍇ�́AtargetPort�Ɠ���
	 * @return
	 */
	public int getRemotePort() {
		return remotePort;
	}

	public String getTargetServer() {
		return targetServer;
	}

	public int getTargetPort() {
		return targetPort;
	}

	/**
	 * WebClientHandler��pool�Ǘ����邽�߂ɕK�v
	 */
	@Override
	public boolean equals(Object obj) {
		WebClientConnection wcc=(WebClientConnection)obj;
		return equalsConnection(wcc.isHttps,wcc.targetServer,wcc.targetPort);
	}

	@Override
	public int hashCode() {
		int hashCode=targetServer.hashCode()+targetPort;
		if(isHttps){
			hashCode+=0xf0000000;
		}
		return hashCode;
	}
}
