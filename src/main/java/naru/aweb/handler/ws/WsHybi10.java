package naru.aweb.handler.ws;

import java.util.List;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import naru.async.BufferGetter;
import naru.async.cache.AsyncBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.aweb.http.HeaderParser;

public class WsHybi10 extends WsProtocol implements BufferGetter{
	private static Logger logger=Logger.getLogger(WsHybi10.class);
	private static final String SPEC="hybi10";
	private static final String GUID="258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
	private static final int SUPPORT_VERSION=13;
	
	private boolean isSendClose=false;
	private byte continuePcode;
	private int continuePayloadLength=0;
//	private List<ByteBuffer> continuePayload=new ArrayList<ByteBuffer>();
	private AsyncBuffer payloadBuffer;
	private WsHybiFrame frame=new WsHybiFrame();

	@Override
	public void recycle() {
		isSendClose=false;
		continuePcode=-1;
		continuePayloadLength=0;
//		PoolManager.poolBufferInstance(continuePayload);
		if(payloadBuffer!=null){
			payloadBuffer.unref();
			payloadBuffer=null;
		}
		frame.init();
		super.recycle();
	}
	
	@Override
	public boolean onHandshake(HeaderParser requestHeader,String subProtocol) {
		logger.debug("WsHybi10#onHandshake cid:"+handler.getChannelId());
		if(!isUseSpec(SPEC)){
			handler.completeResponse("400");
			return false;
		}
		String version=requestHeader.getHeader(SEC_WEBSOCKET_VERSION);
		logger.debug("WebSocket version:"+version);
		int v=Integer.parseInt(version);
		if(v>SUPPORT_VERSION){//version���傫������ꍇ�́A8�ŉ�b����悤�Ɍ���,chrome "16.0.912.12 dev-m"�́A13
			logger.debug("WsHybi10#version error:"+v);
			handler.setHeader(SEC_WEBSOCKET_VERSION, Integer.toString(SUPPORT_VERSION));
			handler.completeResponse("400");
			return false;
		}
		if(subProtocol!=null){
			handler.setHeader(SEC_WEBSOCKET_PROTOCOL, subProtocol);
		}
		
		String key=requestHeader.getHeader(SEC_WEBSOCKET_KEY);
		String accept=DataUtil.digestBase64Sha1((key+GUID).getBytes());
		handler.setHttpVersion("HTTP/1.1");
		handler.setStatusCode("101","Switching Protocols");
		handler.setHeader("Upgrade", "WebSocket");
		handler.setHeader("Connection", "Upgrade");
		handler.setHeader(SEC_WEBSOCKET_ACCEPT,accept);
		
		handler.flushHeaderForWebSocket(SPEC,subProtocol);
		handler.onWsOpen(subProtocol);
		handler.setReadTimeout(getWebSocketPingInterval());//TODO webSocketPingInterbal���w�肳�ꂽ�ꍇ
		handler.asyncRead(null);
		return true;
	}
	
	private void sendClose(short code,String reason){
		if(isSendClose){
			handler.asyncClose(null);
			return;
		}
		isSendClose=true;
		handler.traceClose(code, reason);
		ByteBuffer[] closeBuffer=WsHybiFrame.createCloseFrame(isWebSocketResponseMask(),code,reason);
		handler.asyncWrite(null, closeBuffer);
	}
	
	private void doFrame(){
		logger.debug("WsHybi10#doFrame cid:"+handler.getChannelId());
		byte pcode=frame.getPcode();
		if(payloadBuffer==null){
			payloadBuffer=AsyncBuffer.open();
		}
		ByteBuffer[] payloadBuffers=frame.getPayloadBuffers();
		if(!frame.isFin()){//�ŏIFrame����Ȃ�
			logger.debug("WsHybi10#doFrame not isFin");
			if(pcode!=WsHybiFrame.PCODE_CONTINUE){
				continuePcode=pcode;
			}
			continuePayloadLength+=BuffersUtil.remaining(payloadBuffers);
			payloadBuffer.putBuffer(payloadBuffers);
//			PoolManager.poolArrayInstance(payloadBuffers);
			if(continuePayloadLength>=getWebSocketMessageLimit()){
				logger.debug("WsHybi10#doFrame too long frame.continuePayloadLength:"+continuePayloadLength);
				sendClose(WsHybiFrame.CLOSE_MESSAGE_TOO_BIG,"too long frame");
			}
			return;
		}
		if(pcode==WsHybiFrame.PCODE_CONTINUE){
			//1�̃��b�Z�[�W��������Frame����ł��Ă���ꍇ
			logger.debug("WsHybi10#doFrame pcode CONTINUE");
			pcode=continuePcode;
//			for(ByteBuffer buffer:payloadBuffers){
//				continuePayload.add(buffer);
//			}
//			PoolManager.poolArrayInstance(payloadBuffers);
//			int size=continuePayload.size();
//			payloadBuffers=BuffersUtil.newByteBufferArray(size);
//			for(int i=0;i<size;i++){
//				payloadBuffers[i]=continuePayload.get(i);
//			}
//			continuePayload.clear();
			continuePayloadLength=0;
			continuePcode=-1;
		}
		payloadBuffer.putBuffer(payloadBuffers);
		payloadBuffer.flip();
		switch(pcode){
		case WsHybiFrame.PCODE_TEXT:
			logger.debug("WsHybi10#doFrame pcode TEXT");
			if(!payloadBuffer.isInTopBuffer()){
				//text�̏ꍇ����buffer�͋����Ȃ�
				throw new UnsupportedOperationException("unsuppert big text");
			}
			payloadBuffers=payloadBuffer.popTopBuffer();
			for(ByteBuffer buffer:payloadBuffers){
				convertPutBuffer(buffer);
			}
			PoolManager.poolArrayInstance(payloadBuffers);
			callTextOnMessage();
			break;
		case WsHybiFrame.PCODE_BINARY:
			logger.debug("WsHybi10#doFrame pcode BINARY");
			callBinaryOnMessage(payloadBuffer);
			payloadBuffer=null;
			break;
		case WsHybiFrame.PCODE_CLOSE:
			logger.debug("WsHybi10#doFrame pcode CLOSE");
			PoolManager.poolBufferInstance(payloadBuffers);
			//close��M
			handler.traceOnClose(frame.getCloseCode(),frame.getCloseReason());
			//�K�v�Ȃ�close���M
			sendClose(WsHybiFrame.CLOSE_NORMAL,null);
			break;
		case WsHybiFrame.PCODE_PING:
			logger.debug("WsHybi10#doFrame pcode PING");
			ByteBuffer[] pongBuffer=WsHybiFrame.createPongFrame(isWebSocketResponseMask(), payloadBuffers);
			handler.asyncWrite(null, pongBuffer);
			break;
		case WsHybiFrame.PCODE_PONG:
			logger.debug("WsHybi10#doFrame pcode PONG");
			PoolManager.poolBufferInstance(payloadBuffers);
			//do nothing
			break;
		}
		if(payloadBuffer!=null){
			payloadBuffer.unref();
			payloadBuffer=null;
		}
		if( frame.parseNextFrame() ){
			doFrame();
		}
	}
	
	/* �������f�[�^����M���� */
	@Override
	public void onBuffer(ByteBuffer[] buffers) {
		logger.debug("WsHybi10#onBuffer cid:"+handler.getChannelId());
		try {
			for(ByteBuffer buffer:buffers){
				if( frame.parse(buffer) ){
					//TODO parse ERROR
					doFrame();
				}
			}
			PoolManager.poolArrayInstance(buffers);
			if(frame.getPayloadLength()>getWebSocketMessageLimit()){
				logger.debug("WsHybi10#doFrame too long frame.frame.getPayloadLength():"+frame.getPayloadLength());
				sendClose(WsHybiFrame.CLOSE_MESSAGE_TOO_BIG,"too long frame");
			}
			handler.asyncRead(null);
		} catch (RuntimeException e) {
			logger.error("Hybi10 parse error.",e);
			handler.asyncClose(null);
		}
	}
	
	/* ������ؒf���ꂽ or �A�v������closeWebSocket���Ăяo���ꂽ */
	@Override
	public void onClose(short code,String reason) {
		logger.debug("WsHybi10#onClose cid:"+handler.getChannelId());
		if(handler==null){//handshake�O��finish���Ă��܂����ꍇ
			return;
		}
		logger.debug("WsHybi10#onClose cid:"+handler.getChannelId());
		callOnWsClose(code,reason);
		if(code!=WsHybiFrame.CLOSE_UNKOWN){
			sendClose((short)code,reason);
		}
	}

	/* readTimeout�����ꍇ�́A������ێ����邽�߂�ping�𓊂��� */
	@Override
	public void onReadTimeout() {
		logger.debug("WsHybi10#onReadTimeout cid:"+handler.getChannelId());
		ByteBuffer[] buffers=WsHybiFrame.createPingFrame(isWebSocketResponseMask(),"ping:"+System.currentTimeMillis());
		handler.asyncWrite(null, buffers);
		handler.asyncRead(null);
	}

	/* �A�v����postMessage���Ăяo���� */
	@Override
	public void postMessage(String message) {
		logger.debug("WsHybi10#postMessage(txt) cid:"+handler.getChannelId());
		ByteBuffer[] buffers=WsHybiFrame.createTextFrame(isWebSocketResponseMask(), message);
		handler.asyncWrite(this, buffers);
	}
	
	@Override
	public void postMessage(ByteBuffer[] message) {
		logger.debug("WsHybi10#postMessage(bin) cid:"+handler.getChannelId());
		boolean isFin=true;
		ByteBuffer[] buffers=WsHybiFrame.createBinaryFrame(isFin,isWebSocketResponseMask(), message);
		handler.asyncWrite(this, buffers);
	}
	
	@Override
	public String getWsProtocolName() {
		return "Hibi10";
	}
	@Override
	public String getRequestSubProtocols(HeaderParser requestHeader) {
		return requestHeader.getHeader(SEC_WEBSOCKET_PROTOCOL);
	}

	private AsyncBuffer asyncBuffer;
//	private long offset;
//	private long length;
	private long position;
	private long endPosition;
	
	private void creanupAsyncBuffer(){
		if(asyncBuffer==null){
			return;
		}
		asyncBuffer.unref();
		asyncBuffer=null;
	}
	
	/* �A�v����postMessage���Ăяo���� */
	@Override
	public void postMessage(AsyncBuffer message,long offset,long length) {
		logger.debug("WsHybi10#postMessage(bin) cid:"+handler.getChannelId());
		asyncBuffer=message;
		position=offset;
		endPosition=offset+length;
		asyncBuffer.asyncGet(this,position,this);
	}
	
	@Override
	public void onWrittenPlain(Object userContext) {
		if(asyncBuffer==null||asyncBuffer!=userContext){
			return;
		}
		if(position==endPosition){
			handler.onPostMessage(userContext);
			creanupAsyncBuffer();
			return;
		}
		asyncBuffer.asyncGet(this,position,this);
	}
	
	@Override
	public boolean onBuffer(Object ctx, ByteBuffer[] buffers) {
		long len=BuffersUtil.remaining(buffers);
		if( endPosition<=(position+len)){
			BuffersUtil.cut(buffers, endPosition-position);
			position=endPosition;
		}else{
			position+=len;
		}
//		ByteBuffer[] buffers=WsHybiFrame.createBinaryFrame(isFin,isWebSocketResponseMask(), message);
		handler.asyncWrite(this, buffers);
		return false;
	}

	@Override
	public void onBufferEnd(Object ctx) {
		//���Ȃ��͂��B
		creanupAsyncBuffer();
	}

	@Override
	public void onBufferFailure(Object ctx, Throwable failure) {
		creanupAsyncBuffer();
		handler.onFailure(failure);
	}

}
