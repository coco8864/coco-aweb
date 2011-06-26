package naru.aweb.robot;

import java.net.MalformedURLException;
import java.net.URL;

import naru.async.Timer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import naru.aweb.config.AccessLog;
import naru.aweb.config.WebClientLog;
import naru.aweb.http.WebClientConnection;
import naru.aweb.http.WebClientHandler;
import naru.aweb.queue.QueueManager;

public class ServerChecker extends PoolBase implements Timer{
	private static final long CONNECT_TEST_TERM=10000;
	private static final int CONNECT_TEST_MAX=256;
	private static final int READ_TIMEOUT_MAX=60000;
	
	private String chId;
	private String url;
	private boolean isHttps;
	private boolean isUseProxy;
	private String proxyServer;
	
	private String httpVersion;
	private String serverHeader;
	private String connectionHeader;
	private String proxyConnectionHeader;
	private String keepAliveHeader;
	
	private long readTimeout;
	
	private double connectTime;
	private double sslProxyTime;
	private double handshakeTime;
	
	private int maxClients;
	private int listenBacklog;
	
	private Caller caller;
	private WebClientConnection connection;
	private long nomalResponseHeaderTime;
	
	//作業用
	private int timeCount;
	private long connectTimeSum;
	private long sslProxyTimeSum;
	private long handshakeTimeSum;
	
	public static boolean start(String url,String chId){
		ServerChecker serverChecker=(ServerChecker)PoolManager.getInstance(ServerChecker.class);
		try{
			serverChecker.setup(url,chId);
		} catch (MalformedURLException e) {
			return false;
		}
		TimerManager.setTimeout(0, serverChecker, null);
		return true;
	}

	private void setup(String url,String chId) throws MalformedURLException{
		this.chId=chId;
		this.url=url;
		caller=Caller.create(new URL(url),true);
		connection=caller.getConnection();
		isHttps=connection.isHttps();
		isUseProxy=connection.isUseProxy();
		if(isUseProxy){
			proxyServer=connection.getRemoteServer()+":"+connection.getRemotePort();
		}
	}
	
	private void checkHeader(){
		WebClientHandler handler=WebClientHandler.create(connection);
		AccessLog accessLog=(AccessLog)PoolManager.getInstance(AccessLog.class);
		WebClientLog webClientLog=(WebClientLog)PoolManager.getInstance(WebClientLog.class);
		accessLog.setWebClientLog(webClientLog);
		long connectTimeout=1000;
		synchronized(webClientLog){
			caller.startRequest(handler, accessLog,connectTimeout);
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
		httpVersion=webClientLog.getHttpVersion();
		serverHeader=webClientLog.getServerHeader();
		connectionHeader=webClientLog.getConnectionHeader();
		proxyConnectionHeader=webClientLog.getProxyConnectionHeader();
		keepAliveHeader=webClientLog.getKeepAliveHeader();
		nomalResponseHeaderTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_REQUEST_HEADER);
		accessLog.unref(true);
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
		if(statusCode.startsWith("%") || statusCode.startsWith("408")){//突然切れたServer側のtimeoutと推定
			readTimeout=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_RESPONSE_BODY);
		}
		accessLog.unref(true);
	}

	
	private boolean waitForStartRequest(WebClientLog webClientLog){
		int checkPoint=webClientLog.getCheckPoint();
		if(isHttps){
			if(checkPoint==WebClientLog.CHECK_POINT_HANDSHAKE){
				timeCount++;
				connectTimeSum+=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_CONNECT);
				handshakeTimeSum+=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_HANDSHAKE);
				if(isUseProxy){
					sslProxyTimeSum+=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_SSL_PROXY);
				}
				return true;
			}else if(checkPoint>WebClientLog.CHECK_POINT_HANDSHAKE){
				//connectTimeoutの可能性が高い
				return true;
			}
			
		}else{
			if(checkPoint==WebClientLog.CHECK_POINT_CONNECT){
				timeCount++;
				connectTimeSum+=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_CONNECT);
				return true;
			}else if(checkPoint>WebClientLog.CHECK_POINT_CONNECT){
				//connectTimeoutの可能性が高い
				return true;
			}
		}
		return false;
	}
	
	private void checkMultipulConnect(){
		Caller[] callers=new Caller[CONNECT_TEST_MAX];
		WebClientHandler[] handlers=new WebClientHandler[callers.length];
		AccessLog accessLogs[]=new AccessLog[callers.length];
		for(int i=0;i<handlers.length;i++){
			if(i==0){
				callers[0]=caller;
			}else{
				callers[i]=caller.dup(null);
			}
			handlers[i]=WebClientHandler.create(connection);
			accessLogs[i]=(AccessLog)PoolManager.getInstance(AccessLog.class);
			WebClientLog webClientLog=(WebClientLog)PoolManager.getInstance(WebClientLog.class);
			accessLogs[i].setWebClientLog(webClientLog);
		}
		
		long startTime=System.currentTimeMillis();
		timeCount=0;
		connectTimeSum=sslProxyTimeSum=handshakeTimeSum=0L;
		for(int i=0;i<handlers.length;i++){
			WebClientLog webClientLog=accessLogs[i].getWebClientLog();
			long timeout=startTime+(CONNECT_TEST_TERM*2)-(i*10)-System.currentTimeMillis();
			synchronized(webClientLog){
				handlers[i].setHeaderSchedule(timeout, 0);
				callers[i].startRequest(handlers[i], accessLogs[i],1000);
				while(true){
					if( waitForStartRequest(webClientLog) ){
						break;
					}
					try {
						webClientLog.wait();
					} catch (InterruptedException e) {
					}
				}
			}
			if( (System.currentTimeMillis()-startTime)>CONNECT_TEST_TERM ){
				break;
			}
		}
		maxClients=0;
		connectTime=0;
		sslProxyTime=0;
		handshakeTime=0;
		if(timeCount==0){
			//一つも接続できなかった
			return;
		}
		connectTime=(double)connectTimeSum/(double)timeCount;
		sslProxyTime=(double)sslProxyTimeSum/(double)timeCount;
		handshakeTime=(double)handshakeTimeSum/(double)timeCount;
		try {
			Thread.sleep(startTime+(CONNECT_TEST_TERM*2)+1000-System.currentTimeMillis());
		} catch (InterruptedException e1) {
		}
		for(int i=0;i<handlers.length;i++){
			WebClientLog webClientLog=accessLogs[i].getWebClientLog();
			if(webClientLog.getCheckPoint()<WebClientLog.CHECK_POINT_RESPONSE_BODY){
				continue;
			}
			long requestHeaderTime=webClientLog.getProcessTime(WebClientLog.CHECK_POINT_REQUEST_HEADER);
			//requestHeaderTimeが極端に大きいのはlisten back logに溜まったから
			if(requestHeaderTime<(nomalResponseHeaderTime*2)){
				maxClients++;
			}
			accessLogs[i].unref();
		}
		listenBacklog=timeCount-maxClients;
	}
	
	public void onTimer(Object userContext) {
		QueueManager queueManager=QueueManager.getInstance();
		checkHeader();
		queueManager.publish(chId, this);
		checkReadTimeout();
		queueManager.publish(chId, this);
		checkMultipulConnect();
		queueManager.publish(chId, this, true, true);
		this.unref(true);
	}

	public String getHttpVersion() {
		return httpVersion;
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

	public double getConnectTime() {
		return connectTime;
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

	public double getSslProxyTime() {
		return sslProxyTime;
	}

	public double getHandshakeTime() {
		return handshakeTime;
	}

	public String getProxyServer() {
		return proxyServer;
	}

	public String getUrl() {
		return url;
	}

	public boolean isHttps() {
		return isHttps;
	}
	
}
