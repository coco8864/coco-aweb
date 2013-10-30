package naru.aweb.handler.ws;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.log4j.Logger;

import naru.async.AsyncBuffer;
import naru.async.cache.CacheBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.util.HeaderParser;

//"Sec-WebSocket-Origin: http://127.0.0.1:1280\r\n" +
//"Sec-WebSocket-Location: ws://127.0.0.1:1280/admin/queue\r\n" +
//"\r\n").getBytes();
//private static byte[] WsOkResponse2=(//Chrome 5.0.375.70
//"HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
//"Upgrade: WebSocket\r\n" +
//"Connection: Upgrade\r\n" +
//"WebSocket-Origin: http://a.b.c.d:1280\r\n" +
//"WebSocket-Location: ws://a.b.c.d:1280/admin/queue\r\n" +
//"\r\n").getBytes();

public class WsHixie76 extends WsProtocol {
	static private Logger logger=Logger.getLogger(WsHixie76.class);
	private static final String SPEC="hixie76";
	private int frameMode=FRAME_MODE_END;
	private static final byte START_BYTE=(byte)0x00;
	private static final byte END_BYTE=(byte)0xff;
	private static final byte[] START_FRAME={START_BYTE};
	private static final byte[] END_FRAME={END_BYTE};
	private static final int FRAME_MODE_END=0;
	private static final int FRAME_MODE_IN=1;
	private static MessageDigest messageDigest;
	static{
		try {
			messageDigest=MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			logger.error("MessageDigest error",e);
		}
	}
	
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
		b.flip();
		byte[] response=null;
		synchronized(messageDigest){
			messageDigest.update(b.array(),b.position(),b.limit());
			response=messageDigest.digest();
		}
		return response;
	}
	
	private int handshakeStat=0;//0:handshake前、1:ヘッダは到達したがbodyが未,2:handshake済み
	private ByteBuffer[] handshakeBody=null;
	private String subProtocol=null;
	
	private boolean wsShakehand(HeaderParser requestHeader,ByteBuffer[] readBody){//Chrome 6.0.437.3用
		logger.debug("WsHiXie76#wsShakehand cid:"+handler.getChannelId());
		if(!isUseSpec(SPEC)){
			handler.completeResponse("400");
			return false;
		}
		handshakeStat=0;
		handshakeBody=BuffersUtil.concatenate(handshakeBody, readBody);
		long bodyLength=BuffersUtil.remaining(handshakeBody);
		if(bodyLength>8){//handshakeする前に大きなデータを送信してきた異常
			logger.error("too big body before handshake.bodyLength:"+bodyLength);
			handler.completeResponse("403");
			return false;
		}else if(bodyLength<8){//確認用bodyがまだ到達していない
			handshakeStat=1;
			handler.asyncRead(null);
			return true;
		}

		/*
		String webSocketProtocol=requestHeader.getHeader(SEC_WEBSOCKET_PROTOCOL);
		if(webSocketProtocol==null){
			if(isUseSubprotocol()){//subprotocolを必要とするのにない
				handler.completeResponse("400");
				return false;
			}
		}else{
			String subprotocol=checkSubprotocol(webSocketProtocol);
			if(subprotocol==null){//subprotocolが一致しない
				handler.completeResponse("400");
				return false;
			}
			handler.setHeader(SEC_WEBSOCKET_PROTOCOL, subprotocol);
		}
		*/
		if(subProtocol!=null){
			handler.setHeader(SEC_WEBSOCKET_PROTOCOL, subProtocol);
		}
		
		String origin=requestHeader.getHeader("Origin");
		String host=requestHeader.getHeader(HeaderParser.HOST_HEADER);
		String path=requestHeader.getPath();
		
		handler.setHttpVersion(HeaderParser.HTTP_VESION_11);
		handler.setStatusCode("101","Web Socket Protocol Handshake");
		handler.setHeader("Upgrade", "WebSocket");
		handler.setHeader("Connection", "Upgrade");
		handler.setHeader("Sec-WebSocket-Origin", origin);
		StringBuilder sb=new StringBuilder();
		if(handler.isSsl()){
			sb.append("wss://");
		}else{
			sb.append("ws://");
		}
		sb.append(host);
		sb.append(path);
		handler.setHeader("Sec-WebSocket-Location", sb.toString());
		
		handler.flushHeaderForWebSocket(SPEC,subProtocol);
		
		byte[] response=null;
		String key1=requestHeader.getHeader(SEC_WEBSOCKET_KEY1);
		String key2=requestHeader.getHeader(SEC_WEBSOCKET_KEY2);
		response=responseDigest(key1,key2,handshakeBody);
		PoolManager.poolBufferInstance(handshakeBody);//配列、中身ごと回収
		handshakeBody=null;
		
		handler.asyncWrite(null,BuffersUtil.toByteBufferArray(ByteBuffer.wrap(response)));

		handshakeStat=2;
		frameMode=FRAME_MODE_END;
		handler.onWsOpen(subProtocol);
		handler.setReadTimeout(0);
		handler.asyncRead(null);
		return true;
	}
	
	@Override
	public boolean onHandshake(HeaderParser requestHeader,String subProtocol) {
		logger.debug("WsHiXie76#onHandshake cid:"+handler.getChannelId());
		this.subProtocol=subProtocol;
		ByteBuffer[] body=requestHeader.getBodyBuffer();
		if(wsShakehand(requestHeader,body)){
			return true;
		}
		return false;
	}
	
	private int frameLength=0;
	private void parseMessage(ByteBuffer buffer){
		if((frameLength+buffer.remaining())>getWebSocketMessageLimit()){
			logger.error("buffer too long."+getWebSocketMessageLimit());
			handler.asyncClose(null);
			return;
		}
		byte[] array=buffer.array();
		int pos=buffer.position();
		int limit=buffer.limit();
		int dataTopPos=-1;//このbufferのdataTop
		if(frameMode==FRAME_MODE_IN){
			dataTopPos=pos;
		}
		for(int i=pos;i<limit;i++){
			byte c=array[i];
			if(c==START_BYTE){
				if(frameMode==FRAME_MODE_IN){
					logger.error("frame error.0x00x00");
					handler.asyncClose(null);
					return;
				}
				frameMode=FRAME_MODE_IN;
				dataTopPos=i+1;
				frameLength=0;
			}else if(c==END_BYTE){
				if(frameMode==FRAME_MODE_END){
					logger.error("frame error.0xff0xff");
					handler.asyncClose(null);
					return;
				}
//				int dataEndPos=i;
				if(i>dataTopPos){
					ByteBuffer dupBuffer=PoolManager.duplicateBuffer(buffer);
					dupBuffer.position(dataTopPos);
					dupBuffer.limit(i);
					frameLength+=dupBuffer.remaining();
					convertPutBuffer(dupBuffer);
				}
				String textMessage=convertToString();
				//必要な場合traceを採取
				traceOnMessage(textMessage);
				callOnMessage(textMessage);
				frameMode=FRAME_MODE_END;
			}else{
				if(frameMode==FRAME_MODE_END){
					logger.error("frame error.0xffX");
					handler.asyncClose(null);
					return;
				}
			}
		}
		if(frameMode==FRAME_MODE_IN&&limit>dataTopPos){
			buffer.position(dataTopPos);
			buffer.limit(limit);
			frameLength+=buffer.remaining();
			convertPutBuffer(buffer);
		}else{
			PoolManager.poolBufferInstance(buffer);
		}
		return;
	}
	
	/* 回線からデータを受信した */
	@Override
	public void onBuffer(ByteBuffer[] buffers) {
		logger.debug("WsHiXie76#onBuffer cid:"+handler.getChannelId());
		if(handshakeStat==1){
			if(wsShakehand(handler.getRequestHeader(), buffers)==false){
				return;
			}
		}
		for(ByteBuffer buffer:buffers){
			parseMessage(buffer);
		}
		handler.asyncRead(null);
	}
	
	/* 回線が切断された or AplからcloseWebSocketが呼び出された */
	@Override
	public void onClose(short code,String reason) {
		logger.debug("WsHiXie76#onClose cid:"+handler.getChannelId());
		callOnWsClose(code,reason);
		if(code!=WsHybiFrame.CLOSE_UNKOWN){
			handler.asyncClose(null);
		}
	}

	/* この実装ではreadTimeoutはこない */
	@Override
	public void onReadTimeout() {
	}

	/* アプリがpostMessageを呼び出した */
	@Override
	public void postMessage(String message) {
		logger.debug("WsHiXie76#postMessage(txt) cid:"+handler.getChannelId());
		tracePostMessage(message);
		ByteBuffer[] bufs=BuffersUtil.newByteBufferArray(3);
		bufs[0]=ByteBuffer.wrap(START_FRAME);
		try {
			bufs[1]=ByteBuffer.wrap(message.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			logger.error("fail to getBytes."+message);
			handler.asyncClose(null);
			return;
		}
		bufs[2]=ByteBuffer.wrap(END_FRAME);
		handler.asyncWrite(null,bufs);
	}

	/* アプリがpostMessageを呼び出した */
	@Override
	public void postMessage(ByteBuffer[] msgs) {
		logger.debug("WsHiXie76#postMessage(bin) cid:"+handler.getChannelId());
		PoolManager.poolBufferInstance(msgs);
		throw new UnsupportedOperationException("postMessage binary mode");
	}

	@Override
	public void recycle() {
		handshakeStat=0;
		if(handshakeBody!=null){
			PoolManager.poolBufferInstance(handshakeBody);//配列、中身ごと回収
			handshakeBody=null;
		}
		subProtocol=null;
		super.recycle();
	}
	
	@Override
	public String getWsProtocolName() {
		return "Hixie76";
	}
	
	@Override
	public String getRequestSubProtocols(HeaderParser requestHeader) {
		return requestHeader.getHeader(SEC_WEBSOCKET_PROTOCOL);
	}
	
	@Override
	public void onWrittenPlain(Object userContext) {
	}

	@Override
	public void postMessage(AsyncBuffer msgs) {
		logger.debug("WsHiXie75#postMessage(bin) cid:"+handler.getChannelId());
		if(msgs instanceof PoolBase){
			((PoolBase)msgs).unref();
		}
		throw new UnsupportedOperationException("postMessage binary mode");
	}
}
