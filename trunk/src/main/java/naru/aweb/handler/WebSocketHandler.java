/**
 * 
 */
package naru.aweb.handler;

import java.nio.ByteBuffer;

import naru.async.AsyncBuffer;
import naru.async.cache.CacheBuffer;
import naru.aweb.auth.LogoutEvent;
import naru.aweb.handler.ws.WsHybiFrame;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;

import org.apache.log4j.Logger;

/**
 * WebSocket���󂯕t�����ꍇ�ɂ́AWebSocket�v���g�R��������
 * �w�łȂ��ꍇ�́Ahttp���N�G�X�g�������ł���悤�ɂ���WebServerHandler�Ɠ����̓���
 * 
 * @author Naru
 *
 */
public abstract class WebSocketHandler extends WebServerHandler implements LogoutEvent{
	private static Logger logger=Logger.getLogger(WebSocketHandler.class);
	protected boolean isWs;//WebSocket���n���h�����O���Ă��邩�ۂ�
	private boolean isHandshaked;
	private WsProtocol wsProtocol;
	
	/* ���̃N���X���p������application����Ăяo����� */
	/* ���b�Z�[�W�𑗐M����ꍇ(text) */
	/* ������postMessage���󂯕t���鎖�͂ł��Ȃ��̂�synchronized */
	protected synchronized void postMessage(String message){
//		wsProtocol.tracePostMessage(message);
		wsProtocol.postMessage(message);
	}
	
	/* ���b�Z�[�W�𑗐M����ꍇ(binary) */
	/* ������postMessage���󂯕t���鎖�͂ł��Ȃ��̂�synchronized */
	protected synchronized void postMessage(ByteBuffer[] message){
//		wsProtocol.tracePostMessage(message);
		wsProtocol.postMessage(message);
	}
	
	protected synchronized void postMessage(AsyncBuffer message){
		wsProtocol.postMessage(null,message,0,message.bufferLength());
	}
	
	protected synchronized void postMessage(AsyncBuffer message, long offset, long length){
		wsProtocol.postMessage(null,message, offset, length);
	}
	/* header�ɂ́A���̉���ŗL�̏����ڂ��� */
	protected synchronized void postMessage(ByteBuffer header,AsyncBuffer message){
		wsProtocol.postMessage(header,message,0,message.bufferLength());
	}
	
	protected synchronized void postMessage(ByteBuffer header,AsyncBuffer message, long offset, long length){
		wsProtocol.postMessage(header,message, offset, length);
	}
	
	/* �ʐM����߂�ꍇ */
	/**
	 * statusCode �ڑ��O�������ꍇ�A�u���E�U�ɕԋp����statusCode
	 */
	protected void closeWebSocket(String statusCode,short code,String reason){
		if(isHandshaked){
			wsProtocol.onClose(code,reason);
//			wsCloseTrace(code,reason);
		}else{
			completeResponse(statusCode);
		}
	}
	
	protected void closeWebSocket(String statusCode){
		closeWebSocket(statusCode,WsHybiFrame.CLOSE_NORMAL,"OK");
	}
	
	
//	public abstract void startWebSocketResponse(HeaderParser requestHeader);
	public abstract void onWsOpen(String subprotocol);
	public abstract void onWsClose(short code,String reason);
	public abstract void onMessage(String msgs);
	public abstract void onMessage(CacheBuffer  msgs);
	
	/**
	 * WebSocket�ڑ����ɃZ�V�������ꂽ�ꍇ�̒ʒm
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
    WebSocket-Protocol: sample		 *���ɂ���Ή���ɂ��K�v 
	 */
	
	public void startResponse() {
		logger.debug("#doResponse.cid:"+getChannelId());
		HeaderParser requestHeader=getRequestHeader();
		if(!requestHeader.isWs()){
			super.startResponse();//body�������Ɍp�������N���X��startResponseReqBody�ɒʒm�����
			return;
		}
		isWs=true;
		/* ���O�o�̓^�C�v���擾 */
		MappingResult mapping=getRequestMapping();
//		logType=mapping.getLogType();
//		onMessageCount=0;
//		postMessageCount=0;
		/* logoff����onLogout�C�x���g���ʒm�����悤�ɐݒ� */
		RequestContext requestContext=getRequestContext();
		requestContext.registerLogoutEvnet(this);
		wsProtocol=WsProtocol.createWsProtocol(requestHeader,getRequestMapping());
		if(wsProtocol==null){
			completeResponse("400");
			logger.warn("not found WebSocket Protocol");
			return;
		}
		logger.debug("wsProtocol class:"+wsProtocol.getClass().getName());
		//subprotocol�����
		String selectSubprotocol=null;
		String reqSubprotocols=wsProtocol.getRequestSubProtocols(requestHeader);
		if(reqSubprotocols==null){
			if(wsProtocol.isUseSubprotocol()){//subprotocol��K�v�Ƃ���̂ɂȂ�
				completeResponse("400");
				return;
			}
		}else{
			selectSubprotocol=wsProtocol.checkSubprotocol(reqSubprotocols);
			if(selectSubprotocol==null){//subprotocol����v���Ȃ�
				logger.debug("WsHybi10#suprotocol error.webSocketProtocol:"+reqSubprotocols);
				completeResponse("400");
				return;
			}
		}
		startWebSocketResponse(requestHeader,selectSubprotocol);
	}
	
	/* ������handshake�������Ȃ��ꍇ�́A���̃��\�b�h���I�[�o���C�h���� */
	/* �������������Ƃ���� doHandshake���Ăяo���Ώ������p���ł��� */
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
			wsProtocol.onClose(WsHybiFrame.CLOSE_UNKOWN,null);
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
	
	/*----asyncBuffer����----*/
	//posteMessage�̑��M������ʒm,override���Ďg��
	public void onPosted(){
	}

	@Override
	public void onWrittenPlain(Object userContext) {
		if(wsProtocol!=null){
			wsProtocol.onWrittenPlain(userContext);
		}
		super.onWrittenPlain(userContext);
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
