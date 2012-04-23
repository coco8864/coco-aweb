package naru.aweb.handler.ws;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import naru.async.AsyncBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.http.HeaderParser;

public class WsHixie75 extends WsProtocol {
	static private Logger logger=Logger.getLogger(WsHixie75.class);
	private static final String SPEC="hixie75";
	private static final byte START_BYTE=(byte)0x00;
	private static final byte END_BYTE=(byte)0xff;
	private static final byte[] START_FRAME={START_BYTE};
	private static final byte[] END_FRAME={END_BYTE};
	private static final int FRAME_MODE_END=0;
	private static final int FRAME_MODE_IN=1;
	
	private int frameMode;
	private int frameLength=0;
	
	@Override
	public boolean onHandshake(HeaderParser requestHeader,String subProtocol) {
		logger.debug("WsHiXie75#onHandshake cid:"+handler.getChannelId());
		if(!isUseSpec(SPEC)){
			handler.completeResponse("400");
			return false;
		}
		
		/*
		String webSocketProtocol=requestHeader.getHeader(WEBSOCKET_PROTOCOL);
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
			handler.setHeader(WEBSOCKET_PROTOCOL, subprotocol);
		}
		*/
		
		String origin=requestHeader.getHeader("Origin");
		String host=requestHeader.getHeader(HeaderParser.HOST_HEADER);
		String path=requestHeader.getPath();
		
		handler.setHttpVersion("HTTP/1.1");
		//順番を守らないとChrome5は、動作しない
//		setStatusCode("101","Web Socket Protocol Handshake");
//		setHeader("Upgrade", "WebSocket");
//		setHeader("Connection", "Upgrade");
		handler.setStatusCode("101","Web Socket Protocol Handshake\r\nUpgrade: WebSocket\r\nConnection: Upgrade");
		
		handler.setHeader(WEBSOCKET_ORIGIN, origin);
		StringBuilder sb=new StringBuilder();
		if(handler.isSsl()){
			sb.append("wss://");
		}else{
			sb.append("ws://");
		}
		sb.append(host);
		sb.append(path);
		handler.setHeader("WebSocket-Location", sb.toString());
		
		if(subProtocol!=null){
			handler.setHeader(WEBSOCKET_PROTOCOL, subProtocol);
		}
		handler.flushHeaderForWebSocket(SPEC,subProtocol);
		frameMode=FRAME_MODE_END;
		handler.onWsOpen(subProtocol);
		handler.setReadTimeout(0);
		handler.asyncRead(null);
		return true;
	}
	
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
				frameLength=0;
				dataTopPos=i+1;
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
		logger.debug("WsHiXie75#onBuffer cid:"+handler.getChannelId());
		for(ByteBuffer buffer:buffers){
			parseMessage(buffer);
		}
		handler.asyncRead(null);
	}
	/* 回線が切断された or AplからcloseWebSocketが呼び出された */
	@Override
	public void onClose(short code,String reason) {
		logger.debug("WsHiXie75#onClose cid:"+handler.getChannelId());
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
		logger.debug("WsHiXie75#postMessage(txt) cid:"+handler.getChannelId());
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
	public String getWsProtocolName() {
		return "Hixie75";
	}

	@Override
	public String getRequestSubProtocols(HeaderParser requestHeader) {
		return requestHeader.getHeader(WEBSOCKET_PROTOCOL);
	}

	@Override
	public void onWrittenPlain(Object userContext) {
	}

	@Override
	public void postMessage(ByteBuffer[] msgs) {
		PoolManager.poolBufferInstance(msgs);
		throw new UnsupportedOperationException("postMessage binary mode");
	}

	@Override
	public void postMessage(AsyncBuffer msgs, long offset, long length) {
		logger.debug("WsHiXie75#postMessage(bin) cid:"+handler.getChannelId());
		if(msgs instanceof PoolBase){
			((PoolBase)msgs).unref();
		}
		throw new UnsupportedOperationException("postMessage binary mode");
	}

}
