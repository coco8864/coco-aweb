package naru.aweb.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.AsyncBuffer;
import naru.async.Timer;
import naru.async.cache.CacheBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.ssl.SslHandler;
import naru.async.store.DataUtil;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.aweb.handler.ws.WsHybiFrame;
import naru.aweb.util.CodeConverter;
import naru.aweb.util.HeaderParser;

public class WsClientHandler extends SslHandler implements Timer {
	private static final int STAT_INIT = 0;
	private static final int STAT_CONNECT = 1;
	private static final int STAT_SSL_PROXY=2;
	private static final int STAT_SSL_HANDSHAKE=3;
	private static final int STAT_REQUEST_HEADER = 4;
	private static final int STAT_MESSAGE = 5;
//	private static final int STAT_REQUEST_BODY = 5;
//	private static final int STAT_RESPONSE_HEADER = 6;
//	private static final int STAT_RESPONSE_BODY = 7;
	private static final int STAT_END = 9;
	
	private static final String GUID="258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	public static final String CONTEXT_HEADER = "contextHeader";
	public static final String CONTEXT_MESSAGE = "contextMessage";
	public static final String CONTEXT_SSL_PROXY_CONNECT = "contextSslProxyConnect";
	
	public static final Throwable FAILURE_CONNECT=new Throwable("WsClientHandler connect");
	public static final Throwable FAILURE_TIMEOUT=new Throwable("WsClientHandler timeout");
	public static final Throwable FAILURE_PROTOCOL=new Throwable("WsClientHandler protocol");
	
	private static Logger logger = Logger.getLogger(WsClientHandler.class);
	private static Config config=Config.getConfig();
	private static Random random=config.getRandom("WsClientHandler"+System.currentTimeMillis());
	private static int webSocketMessageLimit=config.getInt("webSocketMessageLimit",2048000);
	//TODO user単位のsslengineに
	public SSLEngine getSSLEngine() {
		return config.getSslEngine(null);
	}
	/**
	 * webClientConnectionのライフサイクルは、作成されるWsClientHandlerと一致する
	 * @param isSsl
	 * @param targetServer
	 * @param targetPort
	 * @return
	 */
	public static WsClientHandler create(boolean isSsl, String targetServer,int targetPort){
		WsClientHandler wsClientHandler=(WsClientHandler)PoolManager.getInstance(WsClientHandler.class);
		wsClientHandler.webClientConnection=(WebClientConnection)PoolManager.getInstance(WebClientConnection.class);
		wsClientHandler.webClientConnection.init(isSsl, targetServer, targetPort,true);
		return wsClientHandler;
	}
	
	private int stat;
	private boolean isSendClose=false;
	private byte continuePcode;
	private int continuePayloadLength=0;
	private List<ByteBuffer> continuePayload=new ArrayList<ByteBuffer>();
	
	private CodeConverter codeConverte=new CodeConverter();
	private WebClientConnection webClientConnection;
	private ByteBuffer[] requestHeaderBuffer;// リクエストヘッダ
	private long requestHeaderLength;
	private HeaderParser responseHeader = new HeaderParser();
	private WsHybiFrame frame=new WsHybiFrame();
	private WsClient wsClient;
	private Object userContext;
	
	private String acceptKey;
	@Override
	public void recycle() {
		isSendClose=false;
		stat=STAT_INIT;
		continuePcode=-1;
		continuePayloadLength=0;
		PoolManager.poolBufferInstance(continuePayload);
		try {
			codeConverte.init("utf-8");
		} catch (IOException e) {
		}
		if(webClientConnection!=null){
			webClientConnection.unref();
			webClientConnection=null;
		}
		PoolManager.poolBufferInstance(requestHeaderBuffer);
		requestHeaderBuffer=null;
		responseHeader.recycle();
		frame.init();
		setWsClient(null);
		userContext=null;
		super.recycle();
	}
	
	private void setWsClient(WsClient wsClient){
		PoolBase poolBase=null;
		if(wsClient!=null){
			if(wsClient instanceof PoolBase){
				poolBase=(PoolBase)wsClient;
				poolBase.ref();
			}
		}
		if(this.wsClient!=null){
			if(this.wsClient instanceof PoolBase){
				poolBase=(PoolBase)this.wsClient;
				poolBase.unref();
			}
		}
		this.wsClient=wsClient;
	}
	
	private void internalStartRequest() {
		synchronized (this) {
			stat = STAT_REQUEST_HEADER;
			logger.debug("startRequest requestHeaderBuffer length:"+BuffersUtil.remaining(requestHeaderBuffer)+":"+getPoolId()+":cid:"+getChannelId());
			requestHeaderLength=BuffersUtil.remaining(requestHeaderBuffer);
			asyncWrite(CONTEXT_HEADER, PoolManager.duplicateBuffers(requestHeaderBuffer));
		}
	}
	
	public void onConnected(Object userContext) {
		logger.debug("#connected.id:" + getChannelId());
		onWcConnected();
		if (webClientConnection.isUseProxy()) {//proxyを使う場合は、CONNECTメソッド経由
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
//				sslProxyActualWriteTime=System.currentTimeMillis();
			asyncWrite(CONTEXT_SSL_PROXY_CONNECT, BuffersUtil.toByteBufferArray(buf));
			asyncRead(CONTEXT_SSL_PROXY_CONNECT);
			return;
		} else if(webClientConnection.isHttps()){
			//この仕組みでは、ssl handshakeの前に時間を置くことはできない。
			stat=STAT_SSL_HANDSHAKE;
			// proxyなしのSSL接続
			sslOpen(true);
			return;
		}
		// http接続
		asyncRead(CONTEXT_HEADER);//リクエストしていないが、先行してレスポンスヘッダ要求を行う
		internalStartRequest();
	}
	
	public boolean onHandshaked() {
		logger.debug("#handshaked.cid:" + getChannelId());
//		onWsHandshaked();
		// 直接SSL接続する場合もproxy経由の接続の場合もここに来る
		asyncRead(CONTEXT_HEADER);//リクエストしていないが、先行してレスポンスヘッダ要求を行う
		internalStartRequest();
		return false;
	}
	
	public final void onWrittenPlain(Object userContext) {
		logger.debug("#writtenPlain.cid:" + getChannelId());
		if (userContext == CONTEXT_HEADER) {
			onWcWrittenHeader();
		}else if (userContext == CONTEXT_MESSAGE) {
//			onWrittenRequestBody();
		}
	}
	
	public void onRead(Object userContext, ByteBuffer[] buffers) {
		if (userContext == CONTEXT_SSL_PROXY_CONNECT) {
			for (int i = 0; i < buffers.length; i++) {
				responseHeader.parse(buffers[i]);
			}
			PoolManager.poolArrayInstance(buffers);
			if (responseHeader.isParseEnd()) {
				onWcProxyConnected();//ProxyConnect完了
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
//				onResponseHeader(responseHeader);
				asyncClose(null);
				return;
			}
			responseHeader.recycle();// 本物のヘッダー用に再利用
			stat=STAT_SSL_HANDSHAKE;
			if(webClientConnection.isHttps()){
				sslOpen(true);
			}else{//CONNECTを使ってもSSLとは限らない
				asyncRead(CONTEXT_HEADER);//リクエストしていないが、先行してレスポンスヘッダ要求を行う
				internalStartRequest();
			}
			return;
		}
		super.onRead(userContext, buffers);
	}
	
	private void sendClose(short code,String reason){
		this.stat=STAT_END;
		if(isSendClose){
			asyncClose(null);
			return;
		}
		isSendClose=true;
		ByteBuffer[] closeBuffer=WsHybiFrame.createCloseFrame(true,code,reason);
		asyncWrite(null, closeBuffer);
	}
	
	private void doFrame(){
		logger.debug("WsClientHandler#doFrame cid:"+getChannelId());
		byte pcode=frame.getPcode();
		ByteBuffer[] payloadBuffers=frame.getPayloadBuffers();
		if(!frame.isFin()){
			logger.debug("WsClientHandler#doFrame not isFin");
			if(pcode!=WsHybiFrame.PCODE_CONTINUE){
				continuePcode=pcode;
			}
			for(ByteBuffer buffer:payloadBuffers){
				continuePayload.add(buffer);
				continuePayloadLength+=buffer.remaining();
			}
			PoolManager.poolArrayInstance(payloadBuffers);
			if(continuePayloadLength>=webSocketMessageLimit){
				logger.debug("WsClientHandler#doFrame too long frame.continuePayloadLength:"+continuePayloadLength);
				sendClose(WsHybiFrame.CLOSE_MESSAGE_TOO_BIG,"too long frame");
			}
			return;
		}
		if(pcode==WsHybiFrame.PCODE_CONTINUE){
			logger.debug("WsClientHandler#doFrame pcode CONTINUE");
			pcode=continuePcode;
			for(ByteBuffer buffer:payloadBuffers){
				continuePayload.add(buffer);
			}
			PoolManager.poolArrayInstance(payloadBuffers);
			int size=continuePayload.size();
			payloadBuffers=BuffersUtil.newByteBufferArray(size);
			for(int i=0;i<size;i++){
				payloadBuffers[i]=continuePayload.get(i);
			}
			continuePayload.clear();
			continuePayloadLength=0;
			continuePcode=-1;
		}
		switch(pcode){
		case WsHybiFrame.PCODE_TEXT:
			logger.debug("WsClientHandler#doFrame pcode TEXT");
			for(ByteBuffer buffer:payloadBuffers){
				codeConverte.putBuffer(buffer);
			}
			PoolManager.poolArrayInstance(payloadBuffers);
			try {
				onWcMessage(codeConverte.convertToString());
			} catch (IOException e) {
				logger.error("codeConvert error.",e);
				sendClose(WsHybiFrame.CLOSE_INVALID_FRAME, "invalid frame");
				throw new RuntimeException("codeConvert error.");
			}
			break;
		case WsHybiFrame.PCODE_BINARY:
			logger.debug("WsClientHandler#doFrame pcode BINARY");
			onWcMessage(CacheBuffer.open(payloadBuffers));
			break;
		case WsHybiFrame.PCODE_CLOSE:
			logger.debug("WsClientHandler#doFrame pcode CLOSE");
			PoolManager.poolBufferInstance(payloadBuffers);
			sendClose(WsHybiFrame.CLOSE_NORMAL,"OK");
			doEndWsClient(frame.getCloseCode(),frame.getCloseReason());
			break;
		case WsHybiFrame.PCODE_PING:
			logger.debug("WsClientHandler#doFrame pcode PING");
			ByteBuffer[] pongBuffer=WsHybiFrame.createPongFrame(true, payloadBuffers);
			asyncWrite(null, pongBuffer);
			break;
		case WsHybiFrame.PCODE_PONG:
			logger.debug("WsClientHandler#doFrame pcode PONG");
			PoolManager.poolBufferInstance(payloadBuffers);
			//do nothing
			break;
		}
//		if( frame.parseNextFrame() ){
//			doFrame();
//		}
	}
	
	private void parseFrame(ByteBuffer[] buffers){
		for (int i = 0; i < buffers.length; i++) {
			ByteBuffer buffer=buffers[i];
			while(buffer.hasRemaining()){
				if( frame.parse(buffer) ){
					doFrame();
				}
			}
			buffers[i]=null;
			PoolManager.poolBufferInstance(buffer);
		}
		PoolManager.poolArrayInstance(buffers);//配列を返却
	}
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#readPlain.cid:" + getChannelId());
		if (userContext == CONTEXT_MESSAGE) {
			parseFrame(buffers);
			asyncRead(CONTEXT_MESSAGE);
			return;
		}
		stat = STAT_MESSAGE;
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
			doEndWsClientFailure(stat, FAILURE_PROTOCOL);
			asyncClose(null);
			return;
		}
		String statusCode = responseHeader.getStatusCode();
		onWcResponseHeader(responseHeader);
		String headerKey=responseHeader.getHeader("Sec-WebSocket-Accept");
		if (!"101".equals(statusCode) || !acceptKey.equals(headerKey)) {
			logger.debug("WsClientHandler fail to handshake.statusCode:"+statusCode+" acceptKey:" + acceptKey +" headerKey:" +headerKey);
			doEndWsClientFailure(stat, FAILURE_PROTOCOL);
			asyncClose(null);
			return;
		}
		
		String subprotocol=responseHeader.getHeader("Sec-WebSocket-Protocol");
		onWcHandshaked(subprotocol);
		
		ByteBuffer[] body = responseHeader.getBodyBuffer();
		if (body != null) {
			parseFrame(body);
		}
		setReadTimeout(0);//handshakeが完了したのでタイムアウトさせない
		logger.debug("asyncRead(CONTEXT_BODY) cid:"+getChannelId());
		asyncRead(CONTEXT_MESSAGE);
	}
	
//	private void setWebClientConnection(WebClientConnection webClientConnection) {
//		this.webClientConnection=webClientConnection;
//		
//	}
	
	private ByteBuffer[] crateWsRequestHeader(WebClientConnection webClientConnection,String uri,String subProtocols,String origin){
		HeaderParser header=(HeaderParser)PoolManager.getInstance(HeaderParser.class);
		header.setMethod(HeaderParser.GET_METHOD);
		header.setRequestUri(uri);
		header.setReqHttpVersion(HeaderParser.HTTP_VESION_11);
		header.setHeader(HeaderParser.HOST_HEADER,
				webClientConnection.getTargetServer()+":"+webClientConnection.getTargetPort());
		header.setHeader("Upgrade", "websocket");
		header.setHeader(HeaderParser.CONNECTION_HEADER, "Upgrade");
		header.setHeader("Origin", origin);
		if(subProtocols!=null){
			header.setHeader("Sec-WebSocket-Protocol", subProtocols);
		}
		header.setHeader("Sec-WebSocket-Version", "8");//401とは返却されても困るからversion 8
		
		byte[] keyBytes = new byte[16];//(byte[]) PoolManager.getArrayInstance(byte.class, 16);
		random.nextBytes(keyBytes);
		String key=DataUtil.digestBase64Sha1(keyBytes);
		//PoolManager.poolArrayInstance(keyBytes);
		acceptKey=DataUtil.digestBase64Sha1((key+GUID).getBytes());
		header.setHeader("Sec-WebSocket-Key", key);
		
		ByteBuffer[] headerBuffer=header.getHeaderBuffer();
		header.unref(true);
		return headerBuffer;
	}

	public final boolean startRequest(WsClient wsClient,Object userContext,long connectTimeout,String uri,String subProtocols,String origin) {
		synchronized (this) {
			if (this.wsClient != null) {
				throw new IllegalStateException("aleardy had wsClient:"+this.wsClient);
			}
			setWsClient(wsClient);
			this.userContext=userContext;
		}
		this.requestHeaderBuffer = crateWsRequestHeader(webClientConnection, uri, subProtocols, origin);
		Throwable error;
		if(stat==STAT_INIT){
			synchronized (this) {
				if(asyncConnect(this, webClientConnection.getRemoteServer(), webClientConnection.getRemotePort(), connectTimeout)){
					//この時点で既にリクエストが終了してしまっている事がある...
					stat = STAT_CONNECT;
					setReadTimeout(config.getReadTimeout());
					return true;
				}
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
	
	public final void postMessage(String message){
		ByteBuffer[] buffers=WsHybiFrame.createTextFrame(true, message);
		asyncWrite(CONTEXT_MESSAGE,buffers);
	}
	
	public final void postMessage(AsyncBuffer message){
		//TODO
//		ByteBuffer[] buffers=WsHybiFrame.createBinaryFrame(true,true, message.popTopBuffer());
//		asyncWrite(CONTEXT_MESSAGE,buffers);
	}

//	public final void postMessage(ByteBuffer message){
//		postMessage(BuffersUtil.toByteBufferArray(message));
//	}
	
	public final void doClose(short closeCode,String closeReason){
		sendClose(closeCode,closeReason);
	}
	
	/*
	 * 以降callbackメソッド
	 */
	private void onWcConnected() {
		logger.debug("#wsConnected cid:"+getChannelId());
		if (wsClient != null) {
			wsClient.onWcConnected(userContext);
		}
	}
	private void onWcProxyConnected() {
		logger.debug("#wsProxyConnected cid:"+getChannelId());
		if (wsClient != null) {
			wsClient.onWcProxyConnected(userContext);
		}
	}
	
	private void onWcHandshaked(String subprotocol) {
		logger.debug("#wsHandshaked cid:"+getChannelId());
		if (wsClient != null) {
			wsClient.onWcHandshaked(userContext,subprotocol);
		}
	}
	
	private void onWcResponseHeader(HeaderParser responseHeader) {
		logger.debug("#writtenRequestHeader cid:"+getChannelId());
		if (wsClient != null) {
			wsClient.onWcResponseHeader(userContext,responseHeader);
		}
	}
	
	
	private void onWcWrittenHeader() {
		logger.debug("#writtenRequestHeader cid:"+getChannelId());
		if (wsClient != null) {
			wsClient.onWcWrittenHeader(userContext);
		}
	}
	
	private void onWcMessage(String message) {
		logger.debug("#message text cid:"+getChannelId());
		if (wsClient != null) {
			wsClient.onWcMessage(userContext, message);
		}
	}
	
	private void onWcMessage(CacheBuffer message) {
		logger.debug("#message binary cid:"+getChannelId());
		if (wsClient != null) {
			wsClient.onWcMessage(userContext, message);
		}
	}

	private synchronized void doEndWsClient(short closeCode,String closeReason) {
		logger.debug("#endWsClient cid:"+getChannelId() +":wsClient:"+wsClient);
		int lastStat=this.stat;
		this.stat=STAT_END;
		if (wsClient == null) {
			return;
		}
		WsClient wkWebClient=wsClient;
		Object wkUserContext=userContext;
		setWsClient(null);
		userContext=null;
		wkWebClient.onWcClose(wkUserContext,lastStat,closeCode,closeReason);
	}

	private void doEndWsClientFailure(int stat,Throwable t) {
		logger.debug("#requestFailure cid:"+getChannelId());
		synchronized (this) {
			this.stat=STAT_END;
			if (wsClient == null) {
				return;
			}
			WsClient wkWebClient=wsClient;
			Object wkUserContext=userContext;
			setWsClient(null);
			userContext=null;
			wkWebClient.onWcFailure(wkUserContext,stat,t);
		}
		if(t==FAILURE_CONNECT){
			logger.warn("#requestFailure.connect failure");
		}else{
			logger.warn("#requestFailure.",t);
		}
	}
	
	//startRequestに失敗した場合、timer経由（別スレッドから）でエラーを通知する
	public void onTimer(Object userContext) {
		doEndWsClientFailure(stat,(Throwable)userContext);
	}
	
	@Override
	public void onFinished() {
		doEndWsClient(WsHybiFrame.CLOSE_ABNORMAL_CLOSURE,null);
		super.onFinished();
	}
	
}
