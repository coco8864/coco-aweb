package naru.aweb.handler.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.handler.WebSocketHandler;
import naru.aweb.http.HeaderParser;
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
	
	protected static int webSocketMessageLimit=config.getInt("webSocketMessageLimit",2048000);
	protected static int webSocketPingInterval=config.getInt("webSocketPingInterval",0);
	protected static Set<String> webSocketAllowSubprotocols=null;
	protected static Set<String> webSocketSpecs=null;
	static{
		String subprotocols=config.getString("webSocketAllowSubprotocols");
		if(subprotocols!=null){
			webSocketAllowSubprotocols=new HashSet<String>();
			for(String subprotocol:subprotocols.split(",")){
				webSocketAllowSubprotocols.add(subprotocol.trim());
			}
		}
		String specs=config.getString("websocketSpecs");
		webSocketSpecs=new HashSet<String>();
		if(specs!=null){
			for(String spec:specs.split(",")){
				webSocketSpecs.add(spec.trim());
			}
		}
	}
	
	public static WsProtocol createWsProtocol(HeaderParser requestHeader){
		String key=requestHeader.getHeader(SEC_WEBSOCKET_KEY);
		String key1=requestHeader.getHeader(SEC_WEBSOCKET_KEY1);
		if(key!=null){
			return (WsProtocol)PoolManager.getInstance(WsHybi10.class);
		}else if(key1==null){
			return (WsProtocol)PoolManager.getInstance(WsHixie75.class);
		}else{
			return (WsProtocol)PoolManager.getInstance(WsHixie76.class);
		}
	}
	
	protected WebSocketHandler handler;
	private CodeConverter codeConverte=new CodeConverter();
	
	@Override
	public void recycle() {
		handler=null;
		codeConverte.recycle();
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
	
	protected void callBinaryOnMessage(ByteBuffer[] buffers){
		try {
			handler.onMessage(buffers);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void callTextOnMessage(){
		try {
			handler.onMessage(convertToString());
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void callOnMessage(String msgs){
		try {
			handler.onMessage(msgs);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
	
	private boolean isCallWsClose=false;
	protected void callOnWsClose(){
		synchronized(handler){
			if(isCallWsClose){
				return;
			}
			try {
				handler.onWsClose((short)-1, null);
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			isCallWsClose=true;
		}
	}
	
	public void setup(WebSocketHandler userHandler){
		this.handler=userHandler;
		try {
			codeConverte.init("utf-8");
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
	}
	
	/* handshakeに失敗した場合は、このメソッドで,httpとしてリクエストを完了させる */
	public abstract boolean onHandshake(HeaderParser requestHeader);
	public abstract void onBuffer(ByteBuffer[] data);
	public abstract void onClose(boolean isFromLine);/* 回線が切断した or アプリがcloseWebSocketを呼び出した */
	public abstract void postMessage(String message);
	public abstract void postMessage(ByteBuffer[] message);
	public abstract void onReadTimeout();/* 回線readがタイムアウトした */
}
