/**
 * 
 */
package naru.aweb.handler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.LogoutEvent;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.CodeConverter;

import org.apache.log4j.Logger;

/**
 * WebSocketを受け付けた場合には、WebSocketプロトコルを処理
 * 層でない場合は、httpリクエストを処理できるようにするWebServerHandlerと同等の動作
 * 
 * @author Naru
 *
 */
public abstract class WebSocketHandler extends WebServerHandler implements LogoutEvent{
	static private Logger logger=Logger.getLogger(WebSocketHandler.class);
	private static Config config=Config.getConfig();
	private static MessageDigest messageDigest;
	static{
		try {
			messageDigest=MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			logger.error("MessageDigest error",e);
		}
	}
	private static final byte START_BYTE=(byte)0x00;
	private static final byte END_BYTE=(byte)0xff;
	
	private static byte[] frameStart={START_BYTE};
	private static byte[] frameEnd={END_BYTE};
	
	private static final int END_FRAME=0;
	private static final int IN_FRAME=1;
	private long lastIo;
	private int stat;
	private boolean isWs;//WebSocketをハンドリングしているか否か
	private boolean isWsClose;//wsCloseを通知したか否か
	
	private CodeConverter codeConverte=new CodeConverter();
	
	public void postMessage(String message){
		logger.debug("#postMessage.cid:"+getChannelId());
		ByteBuffer[] bufs=BuffersUtil.newByteBufferArray(3);
		bufs[0]=ByteBuffer.wrap(frameStart);
		try {
			bufs[1]=ByteBuffer.wrap(message.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			logger.error("fail to getBytes."+message);
			asyncClose(null);
			return;
		}
		bufs[2]=ByteBuffer.wrap(frameEnd);
		lastIo=System.currentTimeMillis();
		asyncWrite(null,bufs);
	}
	
	public void wsClose(){
		if(isWsClose==false){//厳密ではないがその必要なし
			responseEnd();
		}
	}
	public abstract void onMessage(String msgs);
	public abstract void onWsOpen(String subprotocol);
	public abstract void onWsClose();
	
	/*
    HTTP/1.1 101 Web Socket Protocol Handshake
    Upgrade: WebSocket
    Connection: Upgrade
    WebSocket-Origin: http://example.com
    WebSocket-Location: ws://example.com/demo
    WebSocket-Protocol: sample		 *上りにあれば下りにも必要 
	 */
	private static byte[] WsOkResponse=(//Chrome 6.0.437.3 dev,http://tools.ietf.org/html/draft-hixie-thewebsocketprotocol-76
		"HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
		"Upgrade: WebSocket\r\n" +
		"Connection: Upgrade\r\n").getBytes();
//		"Sec-WebSocket-Origin: http://127.0.0.1:1280\r\n" +
//		"Sec-WebSocket-Location: ws://127.0.0.1:1280/admin/queue\r\n" +
//		"\r\n").getBytes();
//	private static byte[] WsOkResponse2=(//Chrome 5.0.375.70
//		"HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
//		"Upgrade: WebSocket\r\n" +
//		"Connection: Upgrade\r\n" +
//		"WebSocket-Origin: http://a.b.c.d:1280\r\n" +
//		"WebSocket-Location: ws://a.b.c.d:1280/admin/queue\r\n" +
//		"\r\n").getBytes();
	
	private boolean wsShakehand_76(HeaderParser requestHeader,ByteBuffer[] readBody){//Chrome 6.0.437.3用
		handshakeBody=BuffersUtil.concatenate(handshakeBody, readBody);
		long bodyLength=BuffersUtil.remaining(handshakeBody);
		if(bodyLength>8){//handshakeする前に大きなデータを送信してきた異常
			logger.error("too big body before handshake.bodyLength:"+bodyLength);
			completeResponse("403");
			return true;
		}else if(bodyLength<8){//確認用bodyがまだ到達していない
			handshakeStat=1;
			asyncRead(null);
			return false;
		}
		
		String origin=requestHeader.getHeader("Origin");
		String host=requestHeader.getHeader(HeaderParser.HOST_HEADER);
		String path=requestHeader.getPath();
		
		setHttpVersion("HTTP/1.1");
		setStatusCode("101","Web Socket Protocol Handshake");
		setHeader("Upgrade", "WebSocket");
		setHeader("Connection", "Upgrade");
		
		setHeader("Sec-WebSocket-Origin", origin);
		StringBuilder sb=new StringBuilder();
		if(isSsl()){
			sb.append("wss://");
		}else{
			sb.append("ws://");
		}
		sb.append(host);
		sb.append(path);
		setHeader("Sec-WebSocket-Location", sb.toString());
		
		webSocketProtocol=requestHeader.getHeader(SEC_WEBSOCKET_PROTOCOL);
		if(webSocketProtocol!=null){
			setHeader("Sec-WebSocket-Protocol", webSocketProtocol);
		}
		flushHeader();
		
		byte[] response=null;
		String key1=requestHeader.getHeader(SEC_WEBSOCKET_KEY1);
		String key2=requestHeader.getHeader(SEC_WEBSOCKET_KEY2);
		response=responseDigest(key1,key2,handshakeBody);
		asyncWrite(null,BuffersUtil.toByteBufferArray(ByteBuffer.wrap(response)));
		handshaked();
		return true;
	}
	
	private boolean wsShakehand_75(HeaderParser requestHeader){//Chrome 5.0.375.70用
		String origin=requestHeader.getHeader("Origin");
		String host=requestHeader.getHeader(HeaderParser.HOST_HEADER);
		String path=requestHeader.getPath();
		
		setHttpVersion("HTTP/1.1");
		//順番を守らないとChrome5は、動作しない
//		setStatusCode("101","Web Socket Protocol Handshake");
//		setHeader("Upgrade", "WebSocket");
//		setHeader("Connection", "Upgrade");
		setStatusCode("101","Web Socket Protocol Handshake\r\nUpgrade: WebSocket\r\nConnection: Upgrade");
		
		setHeader("WebSocket-Origin", origin);
		StringBuilder sb=new StringBuilder();
		if(isSsl()){
			sb.append("wss://");
		}else{
			sb.append("ws://");
		}
		sb.append(host);
		sb.append(path);
		setHeader("WebSocket-Location", sb.toString());
		
		webSocketProtocol=requestHeader.getHeader(WEBSOCKET_PROTOCOL);
		if(webSocketProtocol!=null){
			setHeader("WebSocket-Protocol", webSocketProtocol);
		}
		flushHeader();
		handshaked();
		return true;
	}
	
	private static String SEC_WEBSOCKET_KEY1="Sec-WebSocket-Key1";
	private static String SEC_WEBSOCKET_KEY2="Sec-WebSocket-Key2";
	private static String SEC_WEBSOCKET_PROTOCOL="Sec-WebSocket-Protocol";
	private static String WEBSOCKET_PROTOCOL="WebSocket-Protocol";
	
	private int part(String key){
		byte[] keybyte=null;
		try {
			keybyte = key.getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("getBytes error.",e);
		}
		int sp=0;
		long number=0;
		for(int i=0;i<keybyte.length;i++){
			byte c=keybyte[i];
			if(c==0x20){
				sp++;
				continue;
			}
			if(c>='0'&&c<='9'){
				number*=10;
				number+=(long)(c-'0');
			}
		}
		if(sp==0){
			throw new RuntimeException("sp error.");
		}
		return (int) (number/(long)sp);
	}
	
	private byte[] responseDigest(String key1,String key2,ByteBuffer[] body){
		int part1=part(key1);
		int part2=part(key2);
		ByteBuffer b=ByteBuffer.allocate(16);
		b.asIntBuffer().put(part1);
		b.position(4);
		b.asIntBuffer().put(part2);
		b.position(8);
		for(ByteBuffer buffer:body){
			b.put(buffer);
		}
		PoolManager.poolBufferInstance(body);//配列、中身ごと回収
		b.flip();
		byte[] response=null;
		synchronized(messageDigest){
			messageDigest.update(b.array(),b.position(),b.limit());
			response=messageDigest.digest();
		}
		return response;
	}
	
//	public static final byte[] HEADER_SEP = ": ".getBytes();
	public static final byte[] CRLF = "\r\n".getBytes();
//	public static final byte[] SPACE = " ".getBytes();
	
	private int handshakeStat=0;//0:handshake前、1:ヘッダは到達したがbodyが未,2:handshake済み
	private ByteBuffer[] handshakeBody;
	private String webSocketProtocol;
	
	private void handshaked(){
		handshakeStat=2;
		stat=END_FRAME;
		try {
			codeConverte.init("utf-8");
		} catch (IOException e) {
			throw new RuntimeException("codeConverter error.",e);
		}
		onWsOpen(webSocketProtocol);
		setReadTimeout(0);
		asyncRead(null);
	}
	
	public void startResponse() {
		logger.debug("#doResponse.cid:"+getChannelId());
		HeaderParser requestHeader=getRequestHeader();
		if(!requestHeader.isWs()){
			super.startResponse();
			return;
		}
		isWs=true;
		RequestContext requestContext=getRequestContext();
		requestContext.registerLogoutEvnet(this);
		
		String key1=requestHeader.getHeader(SEC_WEBSOCKET_KEY1);
		if(key1==null){
			wsShakehand_75(requestHeader);
		}else{
			ByteBuffer[] body=requestHeader.getBodyBuffer();
			wsShakehand_76(requestHeader,body);
		}
		lastIo=System.currentTimeMillis();
	}

	/**
	 * WebSocketで通信中にセションがログアウトした場合に呼び出される
	 */
	public void onLogout(){
		asyncClose(null);
	}
	
	private void callOnMessage(String message){
		try{
			onMessage(message);
		}catch(Throwable t){
			logger.error("onMessage return exception",t);
		}
	}
	
	private void parseMessage(ByteBuffer buffer){
		byte[] array=buffer.array();
		int pos=buffer.position();
		int limit=buffer.limit();
		int dataTopPos=-1;//このbufferのdataTop
		if(stat==IN_FRAME){
			dataTopPos=pos;
		}
		for(int i=pos;i<limit;i++){
			byte c=array[i];
			if(c==START_BYTE){
				if(stat==IN_FRAME){
					logger.error("frame error.0x000x00");
					asyncClose(null);
					return;
				}
				stat=IN_FRAME;
				dataTopPos=i+1;
			}else if(c==END_BYTE){
				if(stat==END_FRAME){
					logger.error("frame error.0xff0xff");
					asyncClose(null);
					return;
				}
				try {
//					int dataEndPos=i;
					if(i>dataTopPos){
						ByteBuffer dupBuffer=PoolManager.duplicateBuffer(buffer);
						dupBuffer.position(dataTopPos);
						dupBuffer.limit(i);
						codeConverte.putBuffer(dupBuffer);
					}
					callOnMessage(codeConverte.convertToString());
				} catch (IOException e) {
					logger.error("fail to getBytes.");
					asyncClose(null);
					return;
				}
				stat=END_FRAME;
			}else{
				if(stat==END_FRAME){
					logger.error("frame error.0xffX");
					asyncClose(null);
					return;
				}
			}
		}
		if(stat==IN_FRAME&&limit>dataTopPos){
			buffer.position(dataTopPos);
			buffer.limit(limit);
			codeConverte.putBuffer(buffer);
		}else{
			PoolManager.poolBufferInstance(buffer);
		}
		return;
	}
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#read.cid:"+getChannelId());
		if(!getRequestHeader().isWs()){
			super.onReadPlain(userContext,buffers);
			return;
		}
		lastIo=System.currentTimeMillis();
		if(handshakeStat==1){//handshake未
			if(wsShakehand_76(getRequestHeader(), buffers)==false){
				return;
			}
			return;
		}
		for(ByteBuffer buffer:buffers){
			parseMessage(buffer);
		}
		PoolManager.poolArrayInstance(buffers);
		asyncRead(null);
	}
	
	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		asyncClose(userContext);
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}

	private void callWsCloseIfNeed(){
		boolean isCallWsClose=false;
		synchronized(this){
			if(!isWsClose){
				isWsClose=true;
				isCallWsClose=true;
			}
		}
		if(isCallWsClose){
			onWsClose();
			responseEnd();
		}
	}
	
	
	public void onClosed(Object userContext) {
		logger.debug("#closed client.cid:"+getChannelId());
		if(!isWs){
			super.onClosed(userContext);
			return;
		}
		callWsCloseIfNeed();
		super.onClosed(userContext);
	}

	@Override
	public void onFinished() {
		callWsCloseIfNeed();
		super.onFinished();
	}


	@Override
	public void recycle() {
		codeConverte.recycle();
		isWs=isWsClose=false;
		handshakeStat=0;
		handshakeBody=null;
		webSocketProtocol=null;
		super.recycle();
	}
}
