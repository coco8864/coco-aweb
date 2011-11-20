/**
 * 
 */
package naru.aweb.handler;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Date;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.auth.LogoutEvent;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Mapping.LogType;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;

import org.apache.log4j.Logger;

/**
 * WebSocketを受け付けた場合には、WebSocketプロトコルを処理
 * 層でない場合は、httpリクエストを処理できるようにするWebServerHandlerと同等の動作
 * 
 * @author Naru
 *
 */
public abstract class WebSocketHandler extends WebServerHandler implements LogoutEvent{
	private static Logger logger=Logger.getLogger(WebSocketHandler.class);
	private boolean isWs;//WebSocketをハンドリングしているか否か
	private boolean isHandshaked;
	private WsProtocol wsProtocol;
	private int onMessageCount;
	private int postMessageCount;
	private LogType logType;
	
	/**
	 * 
	 * @param sourceType
	 * @param contentType
	 * @param contentEncoding
	 * @param transferEncoding
	 * @param message
	 */
	private void wsTrace(char sourceType,String contentType,String comment,String statusCode,ByteBuffer[] message){
		AccessLog accessLog=getAccessLog();
		AccessLog wsAccessLog=accessLog.copyForWs();
		wsAccessLog.setContentType(contentType);
		wsAccessLog.setRequestLine(accessLog.getRequestLine() +comment);
		wsAccessLog.setSourceType(sourceType);
		wsAccessLog.setStatusCode(statusCode);
		wsAccessLog.endProcess();
		wsAccessLog.setPersist(true);
		if(message!=null){
			Store store = Store.open(true);
			store.putBuffer(message);
			long responseLength=BuffersUtil.remaining(message);
			wsAccessLog.setResponseLength(responseLength);
			wsAccessLog.incTrace();//close前にカウンタをアップ
			store.close(wsAccessLog,store);//close時にdigestが決まる
			wsAccessLog.setResponseBodyDigest(store.getDigest());
		}
		wsAccessLog.decTrace();//traceを出力する
	}
	
	private void wsPostTrace(String contentType,ByteBuffer[] message){
		//ブラウザにはonMessageが通知されるので
		onMessageCount++;
		StringBuilder sb=new StringBuilder();
		sb.append('[');
		sb.append(getChannelId());
		sb.append(":");
		sb.append(onMessageCount);
		sb.append(']');
		wsTrace(AccessLog.SOURCE_TYPE_WS_ON_MESSAGE,contentType,sb.toString(),"B<S",message);
	}
	
	private void wsOnTrace(String contentType,ByteBuffer[] message){
		//ブラウザのpostMessageに起因して記録されるので
		postMessageCount++;
		StringBuilder sb=new StringBuilder();
		sb.append('[');
		sb.append(getChannelId());
		sb.append(":");
		sb.append(postMessageCount);
		sb.append(']');
		wsTrace(AccessLog.SOURCE_TYPE_WS_POST_MESSAGE,contentType,sb.toString(),"B>S",message);
	}
	
	private ByteBuffer[] stringToBuffers(String message){
		try {
			return BuffersUtil.toByteBufferArray(ByteBuffer.wrap(message.getBytes("utf-8")));
		} catch (UnsupportedEncodingException e) {
			logger.error("stringToBuffers error",e);
			return null;
		}
	}
	
	public void tracePostMessage(String message){
		ByteBuffer [] messageBuffers=null;
		switch(logType){
		case NONE:
			return;
		case ACCESS:
		case RESPONSE_TRACE:
			break;
		case REQUEST_TRACE:
		case TRACE:
			messageBuffers=stringToBuffers(message);
			break;
		}
		wsPostTrace("text/plain",messageBuffers);
	}
	
	public void tracePostMessage(ByteBuffer[] message){
		postMessageCount++;
		ByteBuffer [] messageBuffers=null;
		switch(logType){
		case NONE:
			return;
		case ACCESS:
		case RESPONSE_TRACE:
			break;
		case REQUEST_TRACE:
		case TRACE:
			messageBuffers=PoolManager.duplicateBuffers(message);
			break;
		}
		wsPostTrace("application/octet-stream",messageBuffers);
	}
	
	public void traceOnMessage(String message){
		ByteBuffer [] messageBuffers=null;
		switch(logType){
		case NONE:
			return;
		case ACCESS:
		case REQUEST_TRACE:
			break;
		case RESPONSE_TRACE:
		case TRACE:
			messageBuffers=stringToBuffers(message);
			break;
		}
		wsOnTrace("text/plain",messageBuffers);
	}
	
	public void traceOnMessage(ByteBuffer[] message){
		ByteBuffer [] messageBuffers=null;
		switch(logType){
		case NONE:
			return;
		case ACCESS:
		case REQUEST_TRACE:
			break;
		case RESPONSE_TRACE:
		case TRACE:
			messageBuffers=PoolManager.duplicateBuffers(message);
			break;
		}
		wsOnTrace("octedstream",messageBuffers);
	}
	
	
	/* このクラスを継承したapplicationから呼び出される */
	/* メッセージを送信する場合(text) */
	protected void postMessage(String message){
		tracePostMessage(message);
		wsProtocol.postMessage(message);
	}
	
	/* メッセージを送信する場合(binary) */
	protected void postMessage(ByteBuffer[] message){
		tracePostMessage(message);
		wsProtocol.postMessage(message);
	}
	
	/* 通信をやめる場合 */
	/**
	 * statusCode 接続前だった場合、ブラウザに返却するstatusCode
	 */
	protected void closeWebSocket(String statusCode){
		if(isHandshaked){
			wsProtocol.onClose(false);
		}else{
			completeResponse("500");
		}
	}
	
//	public abstract void startWebSocketResponse(HeaderParser requestHeader);
	public abstract void onWsOpen(String subprotocol);
	public abstract void onWsClose(short code,String reason);
	public abstract void onMessage(String msgs);
	public abstract void onMessage(ByteBuffer[] msgs);
	
	/**
	 * WebSocket接続中にセションきれた場合の通知
	 */
	public void onLogout(){
		closeWebSocket("500");
	}
	
	/*
    HTTP/1.1 101 Web Socket Protocol Handshake
    Upgrade: WebSocket
    Connection: Upgrade
    WebSocket-Origin: http://example.com
    WebSocket-Location: ws://example.com/demo
    WebSocket-Protocol: sample		 *上りにあれば下りにも必要 
	 */
	
	public void startResponse() {
		logger.debug("#doResponse.cid:"+getChannelId());
		HeaderParser requestHeader=getRequestHeader();
		if(!requestHeader.isWs()){
			super.startResponse();//body到着時に継承したクラスのstartResponseReqBodyに通知される
			return;
		}
		isWs=true;
		/* ログ出力タイプを取得 */
		MappingResult mapping=getRequestMapping();
		logType=mapping.getLogType();
		onMessageCount=0;
		postMessageCount=0;
		/* logoff時にonLogoutイベントが通知されるように設定 */
		RequestContext requestContext=getRequestContext();
		requestContext.registerLogoutEvnet(this);
		wsProtocol=WsProtocol.createWsProtocol(requestHeader,getRequestMapping());
		if(wsProtocol==null){
			completeResponse("400");
			logger.warn("not found WebSocket Protocol");
			return;
		}
		logger.debug("wsProtocol class:"+wsProtocol.getClass().getName());
		//subprotocolを特定
		String selectSubprotocol=null;
		String reqSubprotocols=wsProtocol.getRequestSubProtocols(requestHeader);
		if(reqSubprotocols==null){
			if(wsProtocol.isUseSubprotocol()){//subprotocolを必要とするのにない
				completeResponse("400");
				return;
			}
		}else{
			selectSubprotocol=wsProtocol.checkSubprotocol(reqSubprotocols);
			if(selectSubprotocol==null){//subprotocolが一致しない
				logger.debug("WsHybi10#suprotocol error.webSocketProtocol:"+reqSubprotocols);
				completeResponse("400");
				return;
			}
		}
		startWebSocketResponse(requestHeader,selectSubprotocol);
	}
	
	/* すぐにhandshakeしたくない場合は、このメソッドをオーバライドする */
	/* 準備が整ったところで doHandshakeを呼び出せば処理を継続できる */
	public 	void startWebSocketResponse(HeaderParser requestHeader,String subprotocol){
		doHandshake(subprotocol);
	}
	
	public final boolean doHandshake(String subProtocol){
		HeaderParser requestHeader=getRequestHeader();
		wsProtocol.setup(this);
		isHandshaked=wsProtocol.onHandshake(requestHeader,subProtocol);
		return isHandshaked;
	}
	
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#read.cid:"+getChannelId());
		if(!isWs){
			super.onReadPlain(userContext, buffers);
			return;
		}
		wsProtocol.onBuffer(buffers);
	}
	
	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		closeWebSocket("500");
		super.onFailure(userContext, t);
	}

	public void onReadTimeout(Object userContext) {
		logger.debug("#readTimeout.cid:" +getChannelId());
		wsProtocol.onReadTimeout();
	}
	
	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		closeWebSocket("500");
		super.onTimeout(userContext);
	}
	
	public void onClosed(Object userContext) {
		logger.debug("#closed client.cid:"+getChannelId());
		super.onClosed(userContext);
	}

	@Override
	public void onFinished() {
		logger.debug("#finished client.cid:"+getChannelId());
		if(wsProtocol!=null){
			wsProtocol.onClose(true);
		}
		super.onFinished();
	}

	@Override
	public void recycle() {
		isWs=false;
		isHandshaked=false;
		if(wsProtocol!=null){
			wsProtocol.unref(true);
			wsProtocol=null;
		}
		super.recycle();
	}

	/*
	@Override
	public void ref() {
		logger.debug("ref.cid:"+getChannelId(),new Throwable());
		super.ref();
	}

	@Override
	public boolean unref() {
		logger.debug("unref.cid:"+getChannelId(),new Throwable());
		return super.unref();
	}
	*/
}
