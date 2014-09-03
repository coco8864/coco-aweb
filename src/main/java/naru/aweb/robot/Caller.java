package naru.aweb.robot;

import java.net.URL;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.store.Store;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.WebClientLog;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientConnection;
import naru.aweb.http.WebClientHandler;
import naru.aweb.util.HeaderParser;

/**
 * 何度でも呼び出せるWebリクエスタ
 * 
 * @author naru
 */
public class Caller extends PoolBase implements WebClient/*,BufferGetter*/ {
	private static Logger logger = Logger.getLogger(Caller.class);
	private static Config config=Config.getConfig();
	private Browser browser;
	private String browserName;
	private List<Caller> nextCallers=new ArrayList<Caller>();
	private long startTime=0;
	
	/**
	 * http[s]://server:port形式の文字列
	 */
	private WebClientConnection connection;
	private boolean isCallerkeepAlive;
	
//	private HeaderParser requestHeader;//初回呼び出し時にrequestHeaderBufferに変換して使う
	private ByteBuffer[] requestHeader;
	private String requestHeaderDigest;
	private String requestLine;
	private long requestContentLength;
	private ByteBuffer[] requestBody;
	private String requestBodyDigest;
	private MessageDigest messageDigest;
	private AccessLog orgAccessLog;//元となったAccessLog
	private String resolveDigest;
	
	private AccessLog accessLog;
	private long responseLength;
	
	private boolean isResponseHeaderTrace=false;//TODO 外からもらう必要あり
	private boolean isResponseBodyTrace=false;//TODO 外からもらう必要あり
	
	private Store responseBodyStore=null;
	
	public void recycle() {
		if(requestHeader!=null){
			PoolManager.poolBufferInstance(requestHeader);
			requestHeader=null;
		}
		requestContentLength=0;
		if(requestBody!=null){
			PoolManager.poolBufferInstance(requestBody);
			requestBody=null;
		}
		if(messageDigest==null){
			try {
				messageDigest=MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				logger.error("MessageDigest.getInstance error.",e);
			}
		}else{
			messageDigest.reset();
		}
		setConnection(null);
		nextCallers.clear();
		resolveDigest=null;
		startTime=0;
		browserName=null;
		super.recycle();
	}
	
	/* Callerだけを単独で使用する場合のFactory */
	public static Caller create(URL url,boolean isCallerKeepAlive){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		boolean isHttps="https".equals(url.getProtocol());
		String server=url.getHost();
		int port=url.getPort();
		if(port<=0){
			if(isHttps){
				port=443;
			}else{
				port=80;
			}
		}
		WebClientConnection connection=WebClientConnection.create(isHttps,server,port);
		HeaderParser requestHeader=HeaderParser.createByUrl(url);
//		requestHeader.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.0) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/14.0.794.0 Safari/535.1");
		String requestLine=connection.getRequestLine(requestHeader);
		ByteBuffer[] requestHeaderBuffer=connection.getRequestHeaderBuffer(requestLine,requestHeader, isCallerKeepAlive);
		requestHeader.unref(true);
		caller.setup(null, connection, isCallerKeepAlive,(Caller)null,requestLine,requestHeaderBuffer,null);
		return caller;
	}
	
	public static Caller create(Browser browser,WebClientConnection connection,boolean isCallerKeepAlive,Caller nextCaller,
			ByteBuffer[] requestHeaderBuffer,String requestLine,
			ByteBuffer[] requestBody,AccessLog orgAccessLog){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		caller.setup(browser, connection, isCallerKeepAlive,nextCaller,requestLine,requestHeaderBuffer,requestBody);
		caller.orgAccessLog=orgAccessLog;
		return caller;
	}
	
	public static Caller create(Browser browser,WebClientConnection connection,boolean isCallerKeepAlive,Caller[] nextCallers,
			ByteBuffer[] requestHeaderBuffer,String requestLine,ByteBuffer[] requestBody){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		caller.setup(browser, connection,isCallerKeepAlive,nextCallers,requestLine,requestHeaderBuffer,requestBody);
		return caller;
	}
	
	public static Caller create(Browser browser,WebClientConnection connection,boolean isCallerKeepAlive,List<Caller> nextCallers,
			ByteBuffer[] requestHeaderBuffer,String requestLine,ByteBuffer[] requestBody){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		caller.setup(browser, connection,isCallerKeepAlive,nextCallers,requestLine,requestHeaderBuffer,requestBody);
		return caller;
	}
	
	private void setup(Browser browser,WebClientConnection connection,boolean isCallerKeepAlive,Caller[] nextCallers,
			String requestLine,ByteBuffer[] requestHeaderBuffer,ByteBuffer[] requestBody){
		for(Caller nextCaller:nextCallers){
			this.nextCallers.add(nextCaller);
		}
		setupExceptNextCaller(browser, connection,isCallerKeepAlive,requestLine, requestHeaderBuffer, requestBody);
	}
	
	private void setup(Browser browser,WebClientConnection connection,boolean isCallerKeepAlive,Caller nextCaller,
			String requestLine,ByteBuffer[] requestHeaderBuffer,ByteBuffer[] requestBody){
		if(nextCaller!=null){
			this.nextCallers.add(nextCaller);
		}
		setupExceptNextCaller(browser, connection,isCallerKeepAlive,requestLine, requestHeaderBuffer, requestBody);
	}
	
	private void setup(Browser browser,WebClientConnection connection,boolean isCallerKeepAlive,List<Caller> nextCallers,
			String requestLine,ByteBuffer[] requestHeaderBuffer,ByteBuffer[] requestBody){
		this.nextCallers.addAll(nextCallers);
		setupExceptNextCaller(browser, connection, isCallerKeepAlive,requestLine, requestHeaderBuffer, requestBody);
	}
	
	private String digest(ByteBuffer[] buffers){
		if(buffers==null){
			return null;
		}
		ByteBuffer[] dupBuffers=PoolManager.duplicateBuffers(buffers);
		//logger.info("digest "+System.identityHashCode(dupBuffers));
		Store store=Store.open(true);
		store.putBuffer(dupBuffers);
		store.close();
		return store.getDigest();
	}
	
	/* nextCaller以外の初期化 */
	private void setupExceptNextCaller(Browser browser,WebClientConnection connection,boolean isCallerKeepAlive,
			String requestLine,ByteBuffer[] requestHeader,ByteBuffer[] requestBody){
		this.browser=browser;
//この時点ではまだbrowser名が決まっていない場合がある
//		if(browser!=null){
//			this.browserName=browser.getName();
//		}else{
//			this.browserName="dummyBrowser";
//		}
		setConnection(connection);
		this.isCallerkeepAlive=isCallerKeepAlive;
		this.requestHeader=requestHeader;
		this.requestLine=requestLine;
		this.requestBody=requestBody;
		this.requestContentLength=BuffersUtil.remaining(requestBody);
		this.requestHeaderDigest=digest(requestHeader);
		this.requestBodyDigest=digest(requestBody);
	}
	
	public Caller dup(Browser browser){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		ByteBuffer[] dupRequestHeader=PoolManager.duplicateBuffers(requestHeader);
		ByteBuffer[] dupRequestBody=null;
		if(requestBody!=null){
			dupRequestBody=PoolManager.duplicateBuffers(requestBody);
		}
		caller.setupExceptNextCaller(browser, connection,isCallerkeepAlive,requestLine, dupRequestHeader, dupRequestBody);
		for(Caller nextCaller:nextCallers){
			caller.nextCallers.add(nextCaller.dup(browser));
		}
		//再計算をショートカット
		caller.requestHeaderDigest=requestHeaderDigest;
		caller.requestBodyDigest=requestBodyDigest;
		caller.isResponseHeaderTrace=isResponseHeaderTrace;
		caller.isResponseBodyTrace=isResponseBodyTrace;
		caller.resolveDigest=resolveDigest;
		return caller;
	}
	
	//TODO 異常のシミュレート
	//1)connectして送信開始するまでの時間 (0)
	//2)送信するrequestLine長 0-実requestLine長(実requestLine長)
	//3)requestLine送信後、header送信を開始するまでの時間(0)
	//4)送信するheader長 0-実header長(実header長)
	//5)header送信後、body送信を開始するまでの時間(0)
	//6)送信するbody長　0-実body長(実body長)
	//7)レスポンス待ち時間 0-レスポンス到着時間(readTimeout)
	public void startRequest(WebClientHandler webClientHandler){
		startRequest(webClientHandler,(AccessLog)PoolManager.getInstance(AccessLog.class),config.getConnectTimeout());
	}
	
	public void startRequest(WebClientHandler webClientHandler,AccessLog accessLog,long connectTimeout){
		if(logger.isDebugEnabled())logger.debug("#startRequest:"+browserName);
		if(this.accessLog!=null){
			throw new RuntimeException("this caller is in use.");
		}
		this.accessLog=accessLog;
		WebClientLog webClientLog=accessLog.getWebClientLog();
		
		//simulateからの作成された事をマーク
		String scenarioName="dummyScenario";
		if(browser!=null){
			browserName=browser.getName();
		}
		accessLog.setIp(browserName);//browser情報
		if(browser!=null){
			Scenario scenario=browser.getScenario();
			if(scenario!=null){
				scenarioName=scenario.getName();
				accessLog.setRealHost(scenarioName);//scenario情報
			}
		}
		if(orgAccessLog!=null){
			accessLog.setOriginalLogId(orgAccessLog.getId());
		}
		accessLog.setResolveOrigin(connection.getTargetServer()+":"+connection.getTargetPort());//接続先サーバ
		if(connection.isHttps()){
			accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_HTTPS);
		}else{
			accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_HTTP);
		}
		accessLog.setSourceType(AccessLog.SOURCE_TYPE_SIMULATE);
		
		accessLog.setRequestLine(requestLine);
		accessLog.setRequestHeaderLength(BuffersUtil.remaining(requestHeader));
		startTime=System.currentTimeMillis();
		accessLog.setStartTime(new Date(startTime));
		accessLog.setRequestHeaderDigest(requestHeaderDigest);
		accessLog.setRequestBodyDigest(requestBodyDigest);
		accessLog.setResolveDigest(resolveDigest);
		
		//accessLogは、サーバとしての情報を格納している、そのため、client基準ではread/writeがひっくりかえる
		accessLog.setRawRead(webClientHandler.getTotalWriteLength());
		accessLog.setRawWrite(webClientHandler.getTotalReadLength());
		//時刻、通信長の初期化
		if(webClientLog!=null){
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_START, webClientHandler.getTotalReadLength(), webClientHandler.getTotalWriteLength());
		}
		
		responseLength=0;
		if(isResponseHeaderTrace){
			Store responseHeaderStore=Store.open(true);
			webClientHandler.pushReadPeekStore(responseHeaderStore);
		}
		if( webClientHandler.startRequest(this, webClientHandler,connectTimeout,PoolManager.duplicateBuffers(requestHeader),requestContentLength, isCallerkeepAlive, config.getKeepAliveTimeout())==false){
			logger.error("fail to webClientHandler.startRequest.scenario.getName:"+scenarioName);
			return;
		}
		if(requestBody!=null){
			webClientHandler.requestBody(PoolManager.duplicateBuffers(requestBody));
		}
		return;
	}
	
	public void cancel(){
	}
	
	public void onWrittenRequestHeader(Object userContext) {
		if(logger.isDebugEnabled())logger.debug("#onWrittenRequestHeader:"+browserName);
		accessLog.setTimeCheckPint(AccessLog.TimePoint.requestHeader);
		if(requestBody==null){
			accessLog.setTimeCheckPint(AccessLog.TimePoint.requestBody);
		}
		WebClientLog webClientLog=accessLog.getWebClientLog();
		if(webClientLog!=null){
			WebClientHandler webClientHandler=(WebClientHandler)userContext;
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_REQUEST_HEADER, 
					webClientHandler.getTotalReadLength(), 
					webClientHandler.getTotalWriteLength(),
					webClientHandler.getHeaderActualWriteTime()
					);
		}
	}
	
	public void onWrittenRequestBody(Object userContext) {
		if(logger.isDebugEnabled())logger.debug("#onWrittenRequestBody:"+browserName);
		accessLog.setTimeCheckPint(AccessLog.TimePoint.requestBody);
		WebClientLog webClientLog=accessLog.getWebClientLog();
		if(webClientLog!=null){
			WebClientHandler webClientHandler=(WebClientHandler)userContext;
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_REQUEST_BODY, 
					webClientHandler.getTotalReadLength(), 
					webClientHandler.getTotalWriteLength(),
					webClientHandler.getBodyActualWriteTime()
			);
		}
	}
	
	public void onResponseHeader(Object userContext, HeaderParser responseHeader) {
		if(logger.isDebugEnabled())logger.debug("#onResponseHeader:"+browserName);
		WebClientHandler webClientHandler=(WebClientHandler)userContext;
		Store responseHeaderStore=webClientHandler.popReadPeekStore();
		if(responseHeaderStore!=null){
			accessLog.incTrace();
			responseHeaderStore.close(accessLog,responseHeaderStore);
			accessLog.setResponseHeaderDigest(responseHeaderStore.getDigest());
		}
		accessLog.setTimeCheckPint(AccessLog.TimePoint.responseHeader);
		accessLog.setStatusCode(responseHeader.getStatusCode());
		accessLog.setContentType(responseHeader.getContentType());
		accessLog.setContentEncoding(responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER));
		accessLog.setTransferEncoding(responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER));
		//chunkされていたとしても、onResponseBodyにはデコードして通知される。記録されるstoreがchunkされる事はない。
		//上記はうそ、WebClientHandlerにisReadableCallbackというオプションがあり、これが立っていない場合、生データがcallbackされる
		WebClientLog webClientLog=accessLog.getWebClientLog();
		if(webClientLog!=null){
			webClientLog.responseHeader(responseHeader);
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_RESPONSE_HEADER, 
					webClientHandler.getTotalReadLength(), 
					webClientHandler.getTotalWriteLength()
					);
		}
	}

	public void onResponseBody(Object userContext, ByteBuffer[] buffers) {
		if(logger.isDebugEnabled())logger.debug("#onResponseBody:"+browserName);
		if(responseLength==0){
			accessLog.setTimeCheckPint(AccessLog.TimePoint.responseBody);
		}
		if(buffers==null){
			return;
		}
		responseLength+=BuffersUtil.remaining(buffers);
		if(isResponseBodyTrace){
			if(responseBodyStore==null){
				responseBodyStore=Store.open(true);
			}
			responseBodyStore.putBuffer(buffers);
		}else{
			for(ByteBuffer buffer:buffers){
				messageDigest.update(buffer);
			}
			PoolManager.poolBufferInstance(buffers);
		}
	}
	
	//onRequestFailureからも呼ばれるので注意
	public void onRequestEnd(Object userContext,int stat) {
		if(logger.isDebugEnabled())logger.debug("#onRequestEnd:"+browserName);
		if(accessLog.getStatusCode()==null){
			accessLog.setStatusCode("%" +Integer.toHexString(stat));
		}
		WebClientHandler webClientHandler=(WebClientHandler)userContext;
		//accessLogは、サーバとしての情報を格納している、そのため、client基準ではread/writeがひっくりかえる
		accessLog.setRawRead(webClientHandler.getTotalWriteLength());
		accessLog.setRawWrite(webClientHandler.getTotalReadLength());
		accessLog.setChannelId(webClientHandler.getChannelId());
//		if(accessLog.getStatusCode()==null){
//			//connectに失敗した場合、handshakeに失敗した場合、その他回線が切れた場合
//			if(logger.isDebugEnabled())logger.debug("Caller.onRequestEnd.no status code:"+webClientHandler.getChannelId()+":"+accessLog.getChannelId());
//		}
		accessLog.setResponseLength(responseLength);
		if(responseLength==0){
		}else if(responseBodyStore==null){
			accessLog.setResponseBodyDigest(DataUtil.digest(messageDigest));
		}else{
			accessLog.incTrace();
			responseBodyStore.close(accessLog,responseBodyStore);
			accessLog.setResponseBodyDigest(responseBodyStore.getDigest());
			responseBodyStore=null;
		}
		accessLog.setRequestHeaderLength(webClientHandler.getRequestHeaderLength());
		accessLog.setResponseHeaderLength(webClientHandler.getResponseHeaderLength());
		accessLog.endProcess();
		AccessLog wkAccessLog=accessLog;
		WebClientLog webClientLog=accessLog.getWebClientLog();
		accessLog=null;
		if(webClientLog!=null){
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_RESPONSE_BODY, 
					webClientHandler.getTotalReadLength(), 
					webClientHandler.getTotalWriteLength()
			);
		}
		if(browser!=null){
			browser.onRequestEnd(this,wkAccessLog);
		}
	}

	public void onRequestFailure(Object userContext,int stat,Throwable t) {
		logger.warn("#onRequestFailure:"+browserName+":"+stat,t);
		//statをaccessLogに記録
		String statusCode=accessLog.getStatusCode();
		if(statusCode!=null){
			//response　header受信後にエラーになった,accesslogにはstatだけを記録
			logger.warn("fail after response header.statusCode:"+statusCode+":"+stat);
			accessLog.setStatusCode("#" +Integer.toHexString(stat));
		}else if(t==WebClientHandler.FAILURE_TIMEOUT){
			accessLog.setStatusCode("&" +Integer.toHexString(stat));
		}else{
			accessLog.setStatusCode("$" +Integer.toHexString(stat));
		}
		onRequestEnd(userContext,stat);
	}
	
	public List<Caller> getNextCallers(){
		return nextCallers;
	}
	
	public void setConnection(WebClientConnection connection) {
		if(this.connection!=null){
			this.connection.unref();
		}
		if(connection!=null){
			connection.ref();
		}
		this.connection=connection;
	}

	public WebClientConnection getConnection() {
		return connection;
	}

	public boolean isResponseHeaderTrace() {
		return isResponseHeaderTrace;
	}

	public void setResponseHeaderTrace(boolean isResponseHeaderTrace) {
		this.isResponseHeaderTrace = isResponseHeaderTrace;
	}

	public boolean isResponseBodyTrace() {
		return isResponseBodyTrace;
	}

	public void setResponseBodyTrace(boolean isResponseBodyTrace) {
		this.isResponseBodyTrace = isResponseBodyTrace;
	}

	public void setResolveDigest(String resolveDigest) {
		this.resolveDigest = resolveDigest;
	}
	
	//TODO 時刻を取得
	public void onWebConnected(Object userContext){
		accessLog.setConnectTime(System.currentTimeMillis()-startTime);
		WebClientLog webClientLog=accessLog.getWebClientLog();
		if(webClientLog!=null){
			WebClientHandler webClientHandler=(WebClientHandler)userContext;
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_CONNECT, webClientHandler.getTotalReadLength(), webClientHandler.getTotalWriteLength());
		}
	}
	
	//TODO 時刻を取得
	public void onWebHandshaked(Object userContext){
		accessLog.setHandshakeTime(System.currentTimeMillis()-startTime);
		WebClientLog webClientLog=accessLog.getWebClientLog();
		if(webClientLog!=null){
			WebClientHandler webClientHandler=(WebClientHandler)userContext;
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_HANDSHAKE, webClientHandler.getTotalReadLength(), webClientHandler.getTotalWriteLength());
		}
	}

	@Override
	public void onWebProxyConnected(Object userContext) {
		WebClientLog webClientLog=accessLog.getWebClientLog();
		if(webClientLog!=null){
			WebClientHandler webClientHandler=(WebClientHandler)userContext;
			webClientLog.checkPoing(WebClientLog.CHECK_POINT_SSL_PROXY, 
					webClientHandler.getTotalReadLength(), 
					webClientHandler.getTotalWriteLength(),
					webClientHandler.getSslProxyActualWriteTime()
					);
			
			
		}
	}

	public void setBrowserName(String browserName) {
		this.browserName = browserName;
	}
}
