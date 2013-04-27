package naru.aweb.robot;

import java.net.URL;

import naru.async.Timer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.WebClientLog;
import naru.aweb.http.WebClientConnection;
import naru.aweb.http.WebClientHandler;
import naru.aweb.pa.api.PaPeer;
import net.sf.json.JSONObject;

public class ServerChecker extends PoolBase implements Timer{
	private static final int READ_TIMEOUT_MAX=31000;
	private static Config config=Config.getConfig();
	
	private PaPeer peer;
	private URL url;
	
	private String status;
	private boolean isTrace;
	private int requestCount;
	private boolean isKeepAlive;

	//接続情報
	private boolean isHttps;
	private boolean isUseProxy;
	private String proxyServer;
	
	//ヘッダ情報
	private String statusLine;
	private String serverHeader;
	private String connectionHeader;
	private String proxyConnectionHeader;
	private String keepAliveHeader;
	private String contentType;
	private long contentLength;
	private String transferEncoding;
	private String contentEncoding;
	
	
	//接続性能
	private String connectTimes;
	private String sslProxyTimes;
	private String handshakeTimes;
	private String requestHeaderTimes;
	private String requestBodyTimes;
	private String responseHeaderTimes;
	private String responseBodyTimes;
	
	private long readTimeout;
	private int maxClients;
	private int listenBacklog;
	
	private Caller caller;
	private WebClientConnection connection;
	
	public static boolean start(URL url,boolean isKeepAlive,int requestCount,boolean isTrace,String browserName,PaPeer peer){
		ServerChecker serverChecker=(ServerChecker)PoolManager.getInstance(ServerChecker.class);
		serverChecker.setup(url,isKeepAlive,requestCount,isTrace,browserName,peer);
		TimerManager.setTimeout(0, serverChecker, null);
		return true;
	}

	private void setup(URL url,boolean isKeepAlive,int requestCount,boolean isTrace,String browserName,PaPeer peer){
		peer.ref();
		this.peer=peer;
		this.url=url;
		this.isKeepAlive=isKeepAlive;
		this.isTrace=isTrace;
		this.requestCount=requestCount;
		//ここでrequest headerはSotreの保存される(digest計算時)
		caller=Caller.create(url,isKeepAlive);
		caller.setBrowserName(browserName);
		connection=caller.getConnection();
//		handler=WebClientHandler.create(connection);
		isHttps=connection.isHttps();
		isUseProxy=connection.isUseProxy();
		if(isUseProxy){
			proxyServer=connection.getRemoteServer()+":"+connection.getRemotePort();
		}
	}
	
	private void checkRequest(WebClientHandler handler,boolean isTrace){
		AccessLog accessLog=(AccessLog)PoolManager.getInstance(AccessLog.class);
		WebClientLog webClientLog=(WebClientLog)PoolManager.getInstance(WebClientLog.class);
		accessLog.setWebClientLog(webClientLog);
		synchronized(webClientLog){
			if(isTrace){
				caller.setResponseHeaderTrace(true);
				caller.setResponseBodyTrace(true);
			}
			caller.startRequest(handler, accessLog,config.getConnectTimeout());
			while(true){
				if(webClientLog.getCheckPoint()>=WebClientLog.CHECK_POINT_RESPONSE_BODY){
					break;
				}
				try {
					webClientLog.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		statusLine=webClientLog.getStatusLine();
		serverHeader=webClientLog.getServerHeader();
		connectionHeader=webClientLog.getConnectionHeader();
		proxyConnectionHeader=webClientLog.getProxyConnectionHeader();
		keepAliveHeader=webClientLog.getKeepAliveHeader();
		contentType=accessLog.getContentType();
		contentLength=accessLog.getResponseLength();
		transferEncoding=accessLog.getTransferEncoding();
		contentEncoding=accessLog.getContentEncoding();
		
		long connectTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_CONNECT);
		long handshakeTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_HANDSHAKE);
		long sslProxyTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_SSL_PROXY);
		long requestHeaderTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_REQUEST_HEADER);
		long requestBodyTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_REQUEST_BODY);
		long responseHeaderTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_RESPONSE_HEADER);
		long responseBodyTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_RESPONSE_BODY);
		
		if(connectTime>=0){
			connectTimes=connectTimes+" "+connectTime;
		}else{
			connectTimes=connectTimes+" -";
		}
		
		if(handshakeTime>=0){
			handshakeTimes=handshakeTimes+" "+handshakeTime;
		}else{
			handshakeTimes=handshakeTimes+" -";
		}
		
		if(sslProxyTime>=0){
			sslProxyTimes=sslProxyTimes+" "+sslProxyTime;
		}else{
			sslProxyTimes=sslProxyTimes+" -";
		}
		
		if(requestHeaderTime>=0){
			requestHeaderTimes=requestHeaderTimes+" "+requestHeaderTime;
		}else{
			requestHeaderTimes=requestHeaderTimes+" -";
		}
		
		if(requestBodyTime>=0){
			requestBodyTimes=requestBodyTimes+" "+requestBodyTime;
		}else{
			requestBodyTimes=requestBodyTimes+" -";
		}
		
		if(responseHeaderTime>=0){
			responseHeaderTimes=responseHeaderTimes+" "+responseHeaderTime;
		}else{
			responseHeaderTimes=responseHeaderTimes+" -";
		}
		
		if(responseBodyTime>=0){
			responseBodyTimes=responseBodyTimes+" "+responseBodyTime;
		}else{
			responseBodyTimes=responseBodyTimes+" -";
		}
		
		if(isTrace){
			accessLog.setPersist(true);
			accessLog.setPeer(peer);
		}
		accessLog.decTrace();
	}
	
	private void checkReadTimeout(){
		WebClientHandler handler=WebClientHandler.create(connection);
		handler.setHeaderSchedule(READ_TIMEOUT_MAX,0);
		AccessLog accessLog=(AccessLog)PoolManager.getInstance(AccessLog.class);
		WebClientLog webClientLog=(WebClientLog)PoolManager.getInstance(WebClientLog.class);
		accessLog.setWebClientLog(webClientLog);
		synchronized(webClientLog){
			caller.startRequest(handler, accessLog,1000);
			while(true){
				if(webClientLog.getCheckPoint()>=WebClientLog.CHECK_POINT_RESPONSE_BODY){
					break;
				}
				try {
					webClientLog.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		String statusCode=accessLog.getStatusCode();
		readTimeout=-1;
		if(statusCode.startsWith("408")){//突然切れたServer側のtimeoutと推定
			readTimeout=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_RESPONSE_HEADER);
		}else if(statusCode.startsWith("%")){//突然切れたServer側のtimeoutと推定
			readTimeout=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_RESPONSE_BODY);
		}
		accessLog.unref(true);
	}
	
	public void onTimer(Object userContext) {
		WebClientHandler handler=null;
		for(int i=0;i<requestCount;i++){
			if(handler==null){
				handler=WebClientHandler.create(connection);
				handler.ref();
			}
			status=Integer.toString(i)+"/" +requestCount;
			peer.message(this);
			checkRequest(handler,isTrace);
			if( !handler.isKeepAlive()){
				handler.unref();
				handler=null;
			}
		}
		if(handler!=null){
			handler.unref();
			handler=null;
		}
		status="done";
		peer.message(this);
		this.unref(true);
	}

	public String getServerHeader() {
		return serverHeader;
	}

	public String getConnectionHeader() {
		return connectionHeader;
	}

	public String getProxyConnectionHeader() {
		return proxyConnectionHeader;
	}

	public String getKeepAliveHeader() {
		return keepAliveHeader;
	}

	public long getReadTimeout() {
		return readTimeout;
	}

	public int getMaxClients() {
		return maxClients;
	}

	public int getListenBacklog() {
		return listenBacklog;
	}

	public boolean isUseProxy() {
		return isUseProxy;
	}

	public String getConnectTimes() {
		return connectTimes;
	}

	public String getSslProxyTimes() {
		return sslProxyTimes;
	}

	public String getHandshakeTimes() {
		return handshakeTimes;
	}

	public String getRequestHeaderTimes() {
		return requestHeaderTimes;
	}
	public String getRequestBodyTimes() {
		return requestBodyTimes;
	}
	public String getResponseHeaderTimes() {
		return responseHeaderTimes;
	}
	public String getResponseBodyTimes() {
		return responseBodyTimes;
	}
	
	public String getProxyServer() {
		return proxyServer;
	}

	public String getUrl() {
		if(url==null){
			return "";
		}
		return url.toString();
	}

	public boolean isHttps() {
		return isHttps;
	}

	@Override
	public void recycle() {
		if(peer!=null){
			peer.unref();
		}
		peer=null;
		url=null;
		isUseProxy=isHttps=false;
		contentEncoding=transferEncoding=statusLine=proxyServer=contentType=serverHeader=
		connectionHeader=proxyConnectionHeader=keepAliveHeader=null;
		connectTimes=sslProxyTimes=handshakeTimes=
			requestBodyTimes=requestHeaderTimes=
			responseHeaderTimes=responseBodyTimes="";
		contentLength=readTimeout=maxClients=listenBacklog=0;
		if(connection!=null){
			connection.unref();
		}
		if(caller!=null){
			caller.unref();
		}
	}
	
	public String getKind(){
		return "checkServerProgress";
	}
	
	public String getStatus() {
		return status;
	}

	public String getStatusLine() {
		return statusLine;
	}

	public String getContentType() {
		return contentType;
	}

	public long getContentLength() {
		return contentLength;
	}

	public String getTransferEncoding() {
		return transferEncoding;
	}

	public String getContentEncoding() {
		return contentEncoding;
	}
}
