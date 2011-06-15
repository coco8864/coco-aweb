package naru.aweb.http;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.ssl.SslHandler;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.aweb.handler.ProxyHandler;
import naru.aweb.robot.CallScheduler;

public class WebClientHandler extends SslHandler implements Timer {
	private static final int STAT_INIT = 0;
	private static final int STAT_CONNECT = 1;
	private static final int STAT_SSL_PROXY=2;
	private static final int STAT_SSL_HANDSHAKE=3;
	private static final int STAT_REQUEST_HEADER = 4;
	private static final int STAT_REQUEST_BODY = 5;
	private static final int STAT_RESPONSE_HEADER = 6;
	private static final int STAT_RESPONSE_BODY = 7;
	private static final int STAT_KEEP_ALIVE = 8;
	private static final int STAT_END = 9;

	public static final String CONTEXT_HEADER = "contextHeader";
	public static final String CONTEXT_BODY = "contextBody";
	public static final String CONTEXT_SSL_PROXY_CONNECT = "contextSslProxyConnect";
	
	public static final Throwable FAILURE_CONNECT=new Throwable("WebClientHandler connect");
	public static final Throwable FAILURE_TIMEOUT=new Throwable("WebClientHandler timeout");

	private static Logger logger = Logger.getLogger(WebClientHandler.class);
	private static Config config=Config.getConfig();

	private int stat;
	private boolean isKeepAlive;//keepAlive中か否か
	private boolean isCallerKeepAlive;//keepAliveを希望するか否か
	private long keepAliveTimeout=15000;//keepAliveする場合、keepAliveタイム
	private WebClientConnection webClientConnection=new WebClientConnection();
	private CallScheduler scheduler=null;

	private ByteBuffer[] requestHeaderBuffer;// リクエストヘッダ
	private ByteBuffer[] requestBodyBuffer;// リクエストボディ

	private long requestContentLength;
	private long requestContentWriteLength;
	private HeaderParser responseHeader = new HeaderParser();
	private ChunkContext responseChunk=new ChunkContext();
	private GzipContext gzipContext=new GzipContext();
	//レスポンスがchunk,gzipされていた場合でも、onResponseBodyに可読なbufferをcallback
	//falseの場合、サーバから受け取ったresponseのままをonResponseBodyにcallback
	private boolean isReadableCallback;
	private boolean isGzip;
	
	private long requestHeaderLength;//リクエストヘッダ長
	private long responseHeaderLength;//レスポンスヘッダ長
	
	private long realReadLength=0;//
	private long realWriteLength=0;
	
	
	private WebClient webClient;
	private Object userContext;
	
	public static WebClientHandler create(WebClientConnection webClientConnection){
		WebClientHandler webClientHandler=(WebClientHandler)PoolManager.getInstance(WebClientHandler.class);
		webClientHandler.setWebClientConnection(webClientConnection);
		return webClientHandler;
	}
	/**
	 * webClientConnectionのライフサイクルは、作成されるWebClientHandlerと一致する
	 * @param isHttps
	 * @param targetServer
	 * @param targetPort
	 * @return
	 */
	public static WebClientHandler create(boolean isHttps, String targetServer,int targetPort){
		WebClientHandler webClientHandler=(WebClientHandler)PoolManager.getInstance(WebClientHandler.class);
		webClientHandler.webClientConnection=(WebClientConnection)PoolManager.getInstance(WebClientConnection.class);
		webClientHandler.webClientConnection.init(isHttps, targetServer, targetPort);
		return webClientHandler;
	}
	
	//TODO user単位のsslengineに
	public SSLEngine getSSLEngine() {
		return config.getSslEngine(null);
	}
	
	private void setWebClient(WebClient webClient){
		PoolBase poolBase=null;
		if(webClient!=null){
			if(webClient instanceof PoolBase){
				poolBase=(PoolBase)webClient;
				poolBase.ref();
			}
		}
		if(this.webClient!=null){
			if(this.webClient instanceof PoolBase){
				poolBase=(PoolBase)this.webClient;
				poolBase.unref();
			}
		}
		this.webClient=webClient;
	}
	
	public void recycle() {
		stat = STAT_INIT;
		setScheduler(null);
		setWebClient(null);
		isKeepAlive = false;
		if(requestHeaderBuffer!=null){
			PoolManager.poolBufferInstance(requestHeaderBuffer);
			requestHeaderBuffer=null;
		}
		if(requestBodyBuffer!=null){
			PoolManager.poolBufferInstance(requestBodyBuffer);
			requestBodyBuffer=null;
		}
		requestContentLength = requestContentWriteLength = 0;
		requestHeaderLength=responseHeaderLength=0;
		setScheduler(null);
		responseHeader.recycle();
		gzipContext.recycle();
		isReadableCallback=false;
//		responseChunk=new ChunkContext();
		setWebClientConnection(null);
		super.recycle();
	}

	public void setScheduler(CallScheduler scheduler){
		if(scheduler!=null){
			scheduler.ref();
		}
		if(this.scheduler!=null){
			this.scheduler.unref();
		}
		this.scheduler=scheduler;
	}
	
	private void internalStartRequest() {
		synchronized (this) {
			stat = STAT_REQUEST_HEADER;
			logger.debug("startRequest requestHeaderBuffer length:"+BuffersUtil.remaining(requestHeaderBuffer)+":"+getPoolId()+":cid:"+getChannelId());
			//for sceduler ヘッダの送信
			requestHeaderLength=BuffersUtil.remaining(requestHeaderBuffer);
			if(scheduler!=null){
				scheduler.scheduleWrite(CONTEXT_HEADER, requestHeaderBuffer);
			}else{
				asyncWrite(CONTEXT_HEADER, requestHeaderBuffer);
			}
			requestHeaderBuffer=null;
			if (requestBodyBuffer != null) {
				stat = STAT_REQUEST_BODY;
				//for sceduler bodyの送信
				long length = BuffersUtil.remaining(requestBodyBuffer);
				requestContentWriteLength += length;
				if(scheduler!=null){
					scheduler.scheduleWrite(CONTEXT_BODY, requestBodyBuffer);
				}else{
					asyncWrite(CONTEXT_BODY, requestBodyBuffer);
				}
				requestBodyBuffer=null;
			}
		}
		if (requestContentWriteLength >= requestContentLength) {
			//レスポンスヘッダの読み込み要求は、リクエスト開始時に行う
//			asyncRead(CONTEXT_HEADER);
		}
	}

	public void onConnected(Object userContext) {
		logger.debug("#connected.id:" + getChannelId());
		if (webClientConnection.isHttps()) {
			if (webClientConnection.isUseProxy()) {
				stat=STAT_SSL_PROXY;
				// SSLのproxy接続
				//TODO proxy認証
				StringBuffer sb = new StringBuffer(512);
				sb.append("CONNECT ");
				sb.append(webClientConnection.getTargetServer());
				sb.append(":");
				sb.append(webClientConnection.getTargetPort());
				sb.append(" HTTP/1.0\r\nHost: ");
				sb.append(webClientConnection.getTargetServer());
				sb.append(":");
				sb.append(webClientConnection.getTargetPort());
				sb.append("\r\nContent-Length: 0\r\n\r\n");
				ByteBuffer buf = ByteBuffer.wrap(sb.toString().getBytes());
				asyncWrite(CONTEXT_SSL_PROXY_CONNECT, BuffersUtil.toByteBufferArray(buf));
				asyncRead(CONTEXT_SSL_PROXY_CONNECT);
				return;
			} else {
				stat=STAT_SSL_HANDSHAKE;
				// proxyなしのSSL接続
				sslOpen(true);
				return;
			}
		}
		// http接続
		asyncRead(CONTEXT_HEADER);//リクエストしていないが、先行してレスポンスヘッダ要求を行う
		internalStartRequest();
	}

	public boolean onHandshaked() {
		logger.debug("#handshaked.cid:" + getChannelId());
		// 直接SSL接続する場合もproxy経由の接続の場合もここに来る
		asyncRead(CONTEXT_HEADER);//リクエストしていないが、先行してレスポンスヘッダ要求を行う
		internalStartRequest();
		return false;
	}

	public final void onWrittenPlain(Object userContext) {
		logger.debug("#writtenPlain.cid:" + getChannelId());
		if (userContext == CONTEXT_HEADER) {
			onWrittenRequestHeader();
		}else if (userContext == CONTEXT_BODY) {
			onWrittenRequestBody();
		}
	}

	public void onRead(Object userContext, ByteBuffer[] buffers) {
		if (userContext == CONTEXT_SSL_PROXY_CONNECT) {
			for (int i = 0; i < buffers.length; i++) {
				responseHeader.parse(buffers[i]);
			}
			PoolManager.poolArrayInstance(buffers);
			if (responseHeader.isParseEnd()) {
				if (responseHeader.isParseError()) {
					logger.warn("ssl proxy header error");
					// client.doneResponse("500","fail to ssl proxy connect");
					asyncClose(null);
					return;
				}
			} else {
				asyncRead(CONTEXT_SSL_PROXY_CONNECT);
				return;
			}
			if(!"200".equals(responseHeader.getStatusCode())){
				logger.warn("ssl proxy fail to connect.statusCode;"+responseHeader.getStatusCode());
				onResponseHeader(responseHeader);
				asyncClose(null);
				return;
			}
			responseHeader.recycle();// 本物のヘッダー用に再利用
			stat=STAT_SSL_HANDSHAKE;
			sslOpen(true);
			return;
		}
		super.onRead(userContext, buffers);
	}

	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#readPlain.cid:" + getChannelId());
		if (userContext == CONTEXT_BODY) {
			stat = STAT_RESPONSE_BODY;
			boolean isLast;
			if(isReadableCallback){
				buffers=responseChunk.decodeChunk(buffers);
				isLast=responseChunk.isEndOfData();
				if(isGzip){
					gzipContext.putZipedBuffer(buffers);
					buffers=gzipContext.getPlainBuffer();
				}
			}else{//終端を判断するために必要
				isLast=responseChunk.isEndOfData(buffers);
			}
			
			onResponseBody(buffers);
			if (isLast) {
				endOfResponse();
			} else {
				logger.debug("asyncRead(CONTEXT_BODY) cid:"+getChannelId());
				asyncRead(CONTEXT_BODY);
			}
			return;
		}
		stat = STAT_RESPONSE_HEADER;
		for (int i = 0; i < buffers.length; i++) {
			responseHeader.parse(buffers[i]);
		}
		PoolManager.poolArrayInstance(buffers);//配列を返却
		if (!responseHeader.isParseEnd()) {
			// ヘッダが終わっていない場合後続のヘッダを読み込み
			logger.debug("asyncRead(CONTEXT_HEADER) cid:"+getChannelId());
			asyncRead(CONTEXT_HEADER);
			return;
		}
		// ヘッダを読みきった
		if (responseHeader.isParseError()) {
			logger.warn("http header error");
			asyncClose(null);
			return;
		}
		responseHeaderLength=responseHeader.getHeaderLength();
		String statusCode = responseHeader.getStatusCode();
		String transfer=responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		String encoding=responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER);
		isGzip=false;
		if(HeaderParser.CONTENT_ENCODING_GZIP.equalsIgnoreCase(encoding)){
			isGzip=true;
		}
		
		onResponseHeader(responseHeader);
		if ("304".equals(statusCode) || "204".equals(statusCode)) {
			endOfResponse();
			return;
		}
		
		long responseContentLength = responseHeader.getContentLength();
		if (responseContentLength < 0) {
			responseContentLength = Long.MAX_VALUE;
		}
		boolean isChunked=HeaderParser.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transfer);
		ByteBuffer[] body = responseHeader.getBodyBuffer();
//		BuffersUtil.peekBuffer(body);
		responseChunk.decodeInit(isChunked, responseContentLength);
		boolean isLast;
		if(isReadableCallback){
			body=responseChunk.decodeChunk(body);
			isLast=responseChunk.isEndOfData();
			if(isGzip){
//				BuffersUtil.peekBuffer(body);
				gzipContext.putZipedBuffer(body);
				body=gzipContext.getPlainBuffer();
			}
		}else{
			isLast=responseChunk.isEndOfData(body);
		}
		if (body != null) {
			stat = STAT_RESPONSE_BODY;
			onResponseBody(body);
		}
		if (isLast) {
			endOfResponse();
		}else{
			logger.debug("asyncRead(CONTEXT_BODY) cid:"+getChannelId());
			asyncRead(CONTEXT_BODY);
		}
	}

	private void endOfResponse() {
		if(stat==STAT_END){//回線が切れていたらkeepAliveしようがない
			isKeepAlive=false;
		}
		if (isKeepAlive) {// keepAliveする気がある場合は、サーバの意向を聞く
			String connectionHeader = null;
			String httpVersion = responseHeader.getResHttpVersion();
			if (!webClientConnection.isHttps() && webClientConnection.isUseProxy()) {
				connectionHeader = responseHeader
						.getHeader(HeaderParser.PROXY_CONNECTION_HEADER);
			} else {
				connectionHeader = responseHeader
						.getHeader(HeaderParser.CONNECTION_HEADER);
			}
			// HTTP1.0の場合は、KeepAliveが指定された場合KeepAliveできる
			if (HeaderParser.HTTP_VESION_10.equalsIgnoreCase(httpVersion)) {
				if (!HeaderParser.CONNECION_KEEP_ALIVE
						.equalsIgnoreCase(connectionHeader)) {
					isKeepAlive = false;
				}
			}
			// HTTP1.1の場合は、Closeが指定されなかった場合KeepAliveできる
			if (HeaderParser.HTTP_VESION_11.equalsIgnoreCase(httpVersion)) {
				if (HeaderParser.CONNECION_CLOSE
						.equalsIgnoreCase(connectionHeader)) {
					isKeepAlive = false;
				}
			}
		}
		if (isKeepAlive == false) {
			asyncClose(null);//keepAliveしない場合は、回線が切断された事をみてリクエスト終了とする
			return;
		}
		onRequestEnd(STAT_KEEP_ALIVE);
		requestHeaderBuffer = requestBodyBuffer = null;
		responseHeaderLength=requestHeaderLength=requestContentLength = requestContentWriteLength = 0;
		responseHeader.recycle();
		setReadTimeout(keepAliveTimeout);//keepAliveタイムアウト
		asyncRead(CONTEXT_HEADER);//keepAlive中に切れたらすぐに検出できるように、次にヘッダの読み込み要求
		logger.debug("WebClientHandler keepAlive.cid:"+getChannelId());
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failure.cid:" + getChannelId(), t);
		isKeepAlive = false;
		asyncClose(userContext);
		onRequestFailure(stat,t);
		super.onFailure(userContext, t);
	}
	
	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" + getChannelId());
		asyncClose(userContext);
		if(isKeepAlive==false){//keepAlive中にtimeoutが来るのは問題ない
			onRequestFailure(stat,FAILURE_TIMEOUT);
		}
		isKeepAlive = false;
		super.onTimeout(userContext);
	}

	public void onClosed(Object userContext) {
		logger.debug("#closed.cid:" + getChannelId());
		isKeepAlive = false;
		onRequestEnd(STAT_END);
		super.onClosed(userContext);
	}

	public void onFinished() {
		logger.debug("#finished.cid:" + getChannelId());
		isKeepAlive = false;
		onRequestEnd(STAT_END);
		super.onFinished();
	}
	
	public boolean isSameConnection(boolean isHttps, String targetServer,int targetPort){
		if(stat!=STAT_KEEP_ALIVE){
			logger.debug("isSameConnection not keepAlive stat:"+stat);
			return false;
		}
		return webClientConnection.equalsConnection(isHttps,targetServer,targetPort);
	}
	
//	public void setConnection(boolean isHttps, String targetServer,int targetPort){
//		webClientConnection.init(isHttps, targetServer, targetPort);
//	}
	
	/**
	 * Callerからは直接呼び出される
	 * errorが発生した場合、timer経由（別スレッド）でイベントにエラーを通知する
	 * 
	 * @param webClient
	 * @param userContext
	 * @param connectTimeout
	 * @param requestHeaderBuffer
	 * @param requestContentLength
	 * @param isCallerkeepAlive
	 * @param keepAliveTimeout
	 * @return
	 */
	public final boolean startRequest(WebClient webClient,Object userContext,long connectTimeout,ByteBuffer[] requestHeaderBuffer,long requestContentLength, boolean isCallerkeepAlive, long keepAliveTimeout) {
		synchronized (this) {
			if (this.webClient != null) {
				throw new IllegalStateException("aleardy had webClient:"+((ProxyHandler)this.webClient).getChannelId());
			}
			setWebClient(webClient);
			this.userContext=userContext;
		}
		this.gzipContext.recycle();
		this.isCallerKeepAlive = isCallerkeepAlive;
		this.keepAliveTimeout=keepAliveTimeout;
		this.isKeepAlive = isCallerKeepAlive;
		this.requestHeaderBuffer = requestHeaderBuffer;
		this.requestContentLength = requestContentLength;
		Throwable error;
		if(stat==STAT_KEEP_ALIVE){
			internalStartRequest();
			return true;
		}if(stat==STAT_INIT){
			if(asyncConnect(this, webClientConnection.getRemoteServer(), webClientConnection.getRemotePort(), connectTimeout)){
				stat = STAT_CONNECT;
				return true;
			}
			logger.warn("fail to asyncConnect.");
			error=FAILURE_CONNECT;
		}else{
			logger.error("fail to doRequest.cid="+getChannelId() +":stat:"+stat);
			error=new Throwable("fail to doRequest.cid="+getChannelId() +":stat:"+stat);
		}
		TimerManager.setTimeout(0L, this,error);
		return false;
	}

	/**
	 * リクエスト終端は、methodやcontent-lengthで判断する 終端認識後、レスポンス系callbackハンドラが順次呼び出される。
	 * ProxyHandlerから呼び出される
	 * @param connectTimeout TODO
	 * @param isCallerkeepAlive 呼び出し元がkeepAliveを希望するか否か
	 * @param keepAliveTimeout TODO
	 * @param clientIp 呼び出し元ipアドレス（設定参照用)
	 * @param targetServer 接続先サーバ
	 * @param targetPort 接続先ポート
	 * @param webClient　イベント通知インタフェース
	 * @param requestHeader　リクエストヘッダ(uriはwebサーバ向けであること'/'から始まること前提)
	 * @return
	 */
	public final boolean startRequest(WebClient webClient,Object userContext,long connectTimeout,HeaderParser requestHeader, boolean isCallerkeepAlive, long keepAliveTimeout) {
		String requestLine=webClientConnection.getRequestLine(requestHeader);
		ByteBuffer[] requestHeaderBuffer=webClientConnection.getRequestHeaderBuffer(requestLine,requestHeader,isCallerkeepAlive);
//		logger.debug("requestHeader:" + BuffersUtil.getString(requestHeaderBuffer[0],"ISO8859_1"));
		long requestContentLength = requestHeader.getContentLength();
		if (requestContentLength < 0) {
			requestContentLength = 0;
		}
		return startRequest(webClient,userContext,connectTimeout,requestHeaderBuffer,requestContentLength, isCallerkeepAlive, keepAliveTimeout);
	}
	
	public final void requestBody(ByteBuffer[] buffers) {
		//connect完了前にbodyBufferを受け付けた場合
		synchronized (this) {
			requestBodyBuffer = BuffersUtil.concatenate(requestBodyBuffer,buffers);
			if (stat == STAT_CONNECT) {
				return;
			}
			stat = STAT_REQUEST_BODY;
		}
		//TODO for sceduler bodyの送信
		long length = BuffersUtil.remaining(requestBodyBuffer);
		requestContentWriteLength += length;
		if(scheduler!=null){
			scheduler.scheduleWrite(CONTEXT_BODY, requestBodyBuffer);
		}else{
			asyncWrite(CONTEXT_BODY, requestBodyBuffer);
		}
		requestBodyBuffer=null;
		if (requestContentWriteLength >= requestContentLength) {
			asyncRead(CONTEXT_HEADER);
		}
	}

	public final void cancelRequest() {
		webClient = null;
		asyncClose(null);
	}
	
	public boolean isKeepAlive(){
		return stat==STAT_KEEP_ALIVE|stat==STAT_INIT;
	}

	/*
	 * 以降callbackメソッド
	 */
	private void onWrittenRequestHeader() {
		if (webClient != null) {
			webClient.onWrittenRequestHeader(userContext);
		}
	}
	
	private void onWrittenRequestBody() {
		if (webClient != null) {
			webClient.onWrittenRequestBody(userContext);
		}
	}

	private void onResponseHeader(HeaderParser responseHeader) {
		if (webClient != null) {
			webClient.onResponseHeader(userContext,responseHeader);
		}
	}

	private void onResponseBody(ByteBuffer[] buffer) {
		if (webClient != null) {
			webClient.onResponseBody(userContext,buffer);
		}
	}

	private synchronized void onRequestEnd(int stat) {
		int lastStat=this.stat;
		this.stat=stat;
		if (webClient == null) {
			return;
		}
		WebClient wkWebClient=webClient;
		Object wkUserContext=userContext;
		setWebClient(null);
		userContext=null;
		wkWebClient.onRequestEnd(wkUserContext,lastStat);
	}

	private void onRequestFailure(int stat,Throwable t) {
		synchronized (this) {
			if (webClient == null) {
				return;
			}
			WebClient wkWebClient=webClient;
			Object wkUserContext=userContext;
			setWebClient(null);
			userContext=null;
			wkWebClient.onRequestFailure(wkUserContext,stat,t);
		}
		logger.warn("#requestFailure.",t);
	}

	public long getRequestHeaderLength() {
		return requestHeaderLength;
	}

	public long getResponseHeaderLength() {
		return responseHeaderLength;
	}
	
	public boolean isConnect(){
		if(stat==STAT_INIT||stat==STAT_END){
			return false;
		}
		return true;
	}

	public void setWebClientConnection(WebClientConnection webClientConnection) {
		if(this.webClientConnection!=null){
			this.webClientConnection.unref();
		}
		if(webClientConnection!=null){
			webClientConnection.ref();
		}
		this.webClientConnection = webClientConnection;
	}
	
	public boolean unref() {
		if(stat==STAT_INIT){//接続前に参照をやめる場合は、chanelContext分も減算
			logger.debug("stat INIT unref");
			stat=STAT_END;//もう接続できない,もう一度unrefされても、減算しない
			super.unref();
		}
		return super.unref();
	}
	
	public void setReadableCallback(boolean isReadableCallback) {
		this.isReadableCallback = isReadableCallback;
	}
	
	//startRequestに失敗した場合、timer経由（別スレッドから）でエラーを通知する
	public void onTimer(Object userContext) {
		onRequestFailure(stat,(Throwable)userContext);
	}
}
