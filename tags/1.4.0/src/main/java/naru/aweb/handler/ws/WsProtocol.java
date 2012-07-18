package naru.aweb.handler.ws;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.AsyncBuffer;
import naru.async.cache.CacheBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping.LogType;
import naru.aweb.handler.WebSocketHandler;
import naru.aweb.http.HeaderParser;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.CodeConverter;

/**
 *1)userHandlerの以下メソッドの呼び出し
 *	public abstract void onWsOpen(String subprotocol);
 *	public abstract void onWsClose(int code,String reason);
 *	public abstract void onMessage(String msgs);
 *	public abstract void onMessage(ByteBuffer[] msgs);
 *	
 *2)postMessageの処理
 *その他は、WebSocketHandlerがやる
 * @author Owner
 *
 */
public abstract class WsProtocol extends PoolBase{
	protected static String SEC_WEBSOCKET_KEY1="Sec-WebSocket-Key1";
	protected static String SEC_WEBSOCKET_KEY2="Sec-WebSocket-Key2";
	protected static String SEC_WEBSOCKET_PROTOCOL="Sec-WebSocket-Protocol";
	protected static String WEBSOCKET_PROTOCOL="WebSocket-Protocol";
	protected static String WEBSOCKET_ORIGIN="WebSocket-Origin";
	
	protected static String SEC_WEBSOCKET_KEY="Sec-WebSocket-Key";
	protected static String SEC_WEBSOCKET_VERSION="Sec-WebSocket-Version";
	protected static String SEC_WEBSOCKET_ORIGIN="Sec-WebSocket-Origin";
	protected static String SEC_WEBSOCKET_ACCEPT="Sec-WebSocket-Accept";
	
	private static Logger logger=Logger.getLogger(WsProtocol.class);
	private static Config config=Config.getConfig();
	
	private static int webSocketMessageLimit=config.getInt("webSocketMessageLimit",2048000);
	private static int webSocketPingInterval=config.getInt("webSocketPingInterval",0);
//	private static Set<String> webSocketAllowSubprotocols=null;
	private static Set<String> webSocketSpecs=null;
	private static boolean isWebSocketResponseMask=config.getBoolean("isWebSocketResponseMask",false);
	
	private static void setupWebSocketSpecs(String specs){
		webSocketSpecs=new HashSet<String>();
		if(specs!=null){
			for(String spec:specs.split(",")){
				webSocketSpecs.add(spec.trim());
			}
		}
	}
	
	static{
//		String subprotocols=config.getString("webSocketAllowSubprotocols");
//		setupWebSocketAllowSubprotocols(subprotocols);
		String specs=config.getString("websocketSpecs");
		setupWebSocketSpecs(specs);
	}
	
	public static int getWebSocketMessageLimit() {
		return webSocketMessageLimit;
	}

	public static int getWebSocketPingInterval() {
		return webSocketPingInterval;
	}

	public boolean isUseSubprotocol(){
		return (subprotocolSet!=null && subprotocolSet.size()>0);
	}
	
	public String checkSubprotocol(String subprotocol){
		String[] protocols=subprotocol.split(",");
		if(!isUseSubprotocol()){
			return protocols[0];
		}
		for(String protocol:protocols){
			if(subprotocolSet.contains(protocol)){
				return protocol;
			}
		}
		return null;
	}
	
	public static boolean isUseSpec(String spec){
		return webSocketSpecs.contains(spec);
	}

	public static void setWebSocketMessageLimit(int webSocketMessageLimit) {
		WsProtocol.webSocketMessageLimit = webSocketMessageLimit;
		config.setProperty("webSocketMessageLimit", webSocketMessageLimit);
	}

	public static void setWebSocketPingInterval(int webSocketPingInterval) {
		WsProtocol.webSocketPingInterval = webSocketPingInterval;
		config.setProperty("webSocketPingInterval", webSocketPingInterval);
	}

	/*
	public static void setWebSocketAllowSubprotocols(String subprotocols) {
		setupWebSocketAllowSubprotocols(subprotocols);
		config.setProperty("webSocketAllowSubprotocols",subprotocols);
	}
	*/

	public static void setWebSocketSpecs(String specs) {
		setupWebSocketSpecs(specs);
		config.setProperty("websocketSpecs",specs);
	}

	public static boolean isWebSocketResponseMask() {
		return isWebSocketResponseMask;
	}

	public static void setWebSocketResponseMask(boolean isWebSocketResponseMask) {
		WsProtocol.isWebSocketResponseMask = isWebSocketResponseMask;
		config.setProperty("isWebSocketResponseMask",isWebSocketResponseMask);
	}
	/* 毎回subprotocolを解析したくない mappingと対応付けてここで覚える*/
	private static Map<Long,Set<String>>mappingSubprotocol=new HashMap<Long,Set<String>>();
	
	public static WsProtocol createWsProtocol(HeaderParser requestHeader,MappingResult mapping){
		WsProtocol wsProtocol=null;
		Long id=mapping.getMapping().getId();
		Set<String> subprotocolSet=(Set<String>)mappingSubprotocol.get(id);
		if(subprotocolSet==null){
//			mapping.getMapping().getId();
			String subprotocol=(String)mapping.getOption("subprotocol");
			subprotocolSet=new HashSet<String>();
			if(subprotocol!=null){
				String[] subprotocols=subprotocol.split(",");
				for(String subprot:subprotocols){
					subprot=subprot.trim();
					if(subprot.length()==0){
						continue;
					}
					subprotocolSet.add(subprot);
				}
				mappingSubprotocol.put(id,subprotocolSet);
			}
		}
		String key=requestHeader.getHeader(SEC_WEBSOCKET_KEY);
		String key1=requestHeader.getHeader(SEC_WEBSOCKET_KEY1);
		if(key!=null){
			wsProtocol=(WsProtocol)PoolManager.getInstance(WsHybi10.class);
		}else if(key1==null){
			wsProtocol=(WsProtocol)PoolManager.getInstance(WsHixie75.class);
		}else{
			wsProtocol=(WsProtocol)PoolManager.getInstance(WsHixie76.class);
		}
		wsProtocol.setSubprotocolSet(subprotocolSet);
		wsProtocol.logType=mapping.getLogType();
		wsProtocol.onMessageCount=0;
		wsProtocol.postMessageCount=0;
		return wsProtocol;
	}
	
	protected WebSocketHandler handler;
	private CodeConverter codeConverte=new CodeConverter();
	private Set<String> subprotocolSet;
	private boolean isCallWsClose=false;
	
	@Override
	public void recycle() {
		handler=null;
		isCallWsClose=false;
		codeConverte.recycle();
		subprotocolSet=null;
	}
	
	private void setSubprotocolSet(Set<String> subprotocolSet) {
		this.subprotocolSet = subprotocolSet;
	}

	protected void convertPutBuffer(ByteBuffer buffer){
		codeConverte.putBuffer(buffer);
	}
	
	protected String convertToString(){
		try {
			return codeConverte.convertToString();
		} catch (IOException e) {
			logger.error("codeConvert error.",e);
			handler.asyncClose(null);
			throw new RuntimeException("codeConvert error.");
		}
	}
	
	protected void callBinaryOnMessage(CacheBuffer message){
		try {
			//traceOnMessageは、buffersを消費しない
//			handler.traceOnMessage(message.popTopBuffer());//TODO 複数bufferになった場合
			handler.onMessage(message);
		} catch (Throwable e) {
			logger.warn("callBinaryOnMessage handler exception.",e);
		}
	}
	
	protected void callOnMessage(String msgs){
		try {
			handler.onMessage(msgs);
		} catch (Throwable e) {
			logger.warn("callOnMessage handler exception.",e);
		}
	}
	
	protected void callOnWsOpen(String subprotocol){
		try {
			handler.onWsOpen(subprotocol);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void callOnWsClose(short code,String reason){
		synchronized(handler){
			if(isCallWsClose){
				return;
			}
			traceOnClose(code, reason);
			try {
				handler.onWsClose(code,reason);
			} catch (Throwable e) {
				logger.warn("onWsClose throw exception.",e);
			}
			isCallWsClose=true;
		}
	}
	
	public void setup(WebSocketHandler userHandler){
		this.handler=userHandler;
		try {
			codeConverte.init("utf-8");
		} catch (IOException e) {
		}
	}
	
	/* handshakeに失敗した場合は、このメソッドで,httpとしてリクエストを完了させる */
	public abstract boolean onHandshake(HeaderParser requestHeader,String subProtocol);
	public abstract void onBuffer(ByteBuffer[] data);
	public abstract void onClose(short code,String reason);/* 回線が切断した or アプリがcloseWebSocketを呼び出した */
	public abstract void postMessage(String message);
	public abstract void postMessage(ByteBuffer[] message);
	public abstract void postMessage(AsyncBuffer message);
	public abstract void onReadTimeout();/* 回線readがタイムアウトした */
	public abstract String getWsProtocolName();
	public abstract String getRequestSubProtocols(HeaderParser requestHeader);
	
	//回線にデータを書き込んだ通知を受け、handler.onPostMessageを呼び出す
	public abstract void onWrittenPlain(Object userContext);

	private LogType logType;
	private int onMessageCount;
	private int postMessageCount;
	
	/*-- trace関連の関数群 --*/
	/**
	 * 
	 * @param sourceType
	 * @param contentType
	 * @param contentEncoding
	 * @param transferEncoding
	 * @param message
	 */
	private void wsTrace(char sourceType,String contentType,String comment,String statusCode,ByteBuffer[] message){
		AccessLog accessLog=handler.getAccessLog();
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
	
	private void wsPostTrace(boolean isTop,boolean isFin,String contentType,ByteBuffer[] message){
		//ブラウザにはonMessageが通知されるので
		StringBuilder sb=new StringBuilder();
		sb.append('[');
		sb.append(handler.getChannelId());
		sb.append(":");
		sb.append(onMessageCount);
		if(isTop){
			onMessageCount++;
			sb.append(":top");
		}
		if(isFin){
			sb.append(":fin");
		}
		sb.append(']');
		wsTrace(AccessLog.SOURCE_TYPE_WS_ON_MESSAGE,contentType,sb.toString(),"B<S",message);
	}
	
	private void wsCloseTrace(short code,String reason){
		StringBuilder sb=new StringBuilder();
		sb.append("[ServerClose:");
		sb.append(handler.getChannelId());
		sb.append(":code:");
		sb.append(code);
		sb.append(":reason:");
		sb.append(reason);
		sb.append(']');
		wsTrace(AccessLog.SOURCE_TYPE_WS_ON_MESSAGE,null,sb.toString(),"B<S",null);
	}
	
	private void wsOnTrace(boolean isTop,boolean isFin,String contentType,ByteBuffer[] message){
		//ブラウザのpostMessageに起因して記録されるので
		StringBuilder sb=new StringBuilder();
		sb.append('[');
		sb.append(handler.getChannelId());
		sb.append(":");
		sb.append(postMessageCount);
		if(isTop){
			postMessageCount++;
			sb.append(":top");
		}
		if(isFin){
			sb.append(":fin");
		}
		sb.append(']');
		wsTrace(AccessLog.SOURCE_TYPE_WS_POST_MESSAGE,contentType,sb.toString(),"B>S",message);
	}
	
	private void wsOnCloseTrace(short code,String reason){
		StringBuilder sb=new StringBuilder();
		sb.append("[BrowserClose:");
		sb.append(handler.getChannelId());
		sb.append(":code:");
		sb.append(code);
		sb.append(":reason:");
		sb.append(reason);
		sb.append(']');
		wsTrace(AccessLog.SOURCE_TYPE_WS_POST_MESSAGE,null,sb.toString(),"B>S",null);
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
		wsPostTrace(true,true,"text/plain",messageBuffers);
	}
	
	public void tracePostMessage(boolean isTop,boolean isFin,ByteBuffer[] message){
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
			//TODO dump all data
			messageBuffers=PoolManager.duplicateBuffers(message);
			break;
		}
		wsPostTrace(isTop,isFin,"application/octet-stream",messageBuffers);
	}
	
	public void traceClose(short code,String reason){
		switch(logType){
		case NONE:
			return;
		case ACCESS:
		case REQUEST_TRACE:
			break;
		case RESPONSE_TRACE:
		case TRACE:
			break;
		}
		wsCloseTrace(code,reason);
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
		wsOnTrace(true,true,"text/plain",messageBuffers);
	}

	public void traceOnMessage(boolean isTop,boolean isFin,ByteBuffer[] message){
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
		wsOnTrace(isTop,isFin,"octedstream",messageBuffers);
	}
	
	public void traceOnClose(short code,String reason){
		switch(logType){
		case NONE:
			return;
		case ACCESS:
		case REQUEST_TRACE:
			break;
		case RESPONSE_TRACE:
		case TRACE:
			break;
		}
		wsOnCloseTrace(code,reason);
	}
	
	public void tracePingPong(String pingPong){
		if(logType!=LogType.TRACE){
			return;
		}
		StringBuilder sb=new StringBuilder();
		sb.append("[");
		sb.append(pingPong);
		sb.append(":");
		sb.append(handler.getChannelId());
		sb.append(']');
		wsTrace(AccessLog.SOURCE_TYPE_WS_POST_MESSAGE,null,sb.toString(),"B>S",null);
	}
}
