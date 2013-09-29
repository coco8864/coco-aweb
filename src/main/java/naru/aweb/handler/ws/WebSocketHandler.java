/**
 * 
 */
package naru.aweb.handler.ws;

import java.nio.ByteBuffer;

import naru.async.AsyncBuffer;
import naru.async.cache.CacheBuffer;
import naru.aweb.auth.LogoutEvent;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.RequestContext;
import naru.aweb.util.HeaderParser;

import org.apache.log4j.Logger;

/**
 * websocket�ɑ΂��ă��X�|���X����handler�̊��N���X<br/>
 * http���N�G�X�g��proxy���N�G�X�g�ɑ΂��ẮAWebServerHandler�Ƃ��Ă�����ł���B<br/>
 * ���̃N���X���p�����āAonWsOpen(websocket�̊J�n),onWsClose(websocket�̏I��),onMessage(websocket����̃f�[�^�ʒm)
 * ���������邱�Ƃɂ��Awebsocket�Ή��̃A�v���P�[�V�������쐬�ł���B
 * 
 * @author Naru
 */
public abstract class WebSocketHandler extends WebServerHandler implements LogoutEvent{
	private static Logger logger=Logger.getLogger(WebSocketHandler.class);
	protected boolean isWs;//WebSocket���n���h�����O���Ă��邩�ۂ�
	private boolean isHandshaked;
	private WsProtocol wsProtocol;
	
	/* ���̃N���X���p������application����Ăяo����� */
	/* ���b�Z�[�W�𑗐M����ꍇ(text) */
	/* ������postMessage���󂯕t���鎖�͂ł��Ȃ��̂�synchronized */
	/**
	 * �N���C�A���g��text���b�Z�[�W�𑗐M���܂��B
	 * @param message ���M���b�Z�[�W
	 */
	public synchronized void postMessage(String message){
		wsProtocol.postMessage(message);
	}
	
	/* ���b�Z�[�W�𑗐M����ꍇ(binary) */
	/* ������postMessage���󂯕t���鎖�͂ł��Ȃ��̂�synchronized */
	/**
	 * �N���C�A���g��binary���b�Z�[�W�𑗐M���܂��B
	 * @param message ���M���b�Z�[�W
	 */
	public synchronized void postMessage(ByteBuffer[] message){
		wsProtocol.postMessage(message);
	}
	
	/**
	 * �N���C�A���g��binary���b�Z�[�W�𑗐M���܂��B
	 * @param message ���M���b�Z�[�W
	 */
	public synchronized void postMessage(AsyncBuffer message){
		wsProtocol.postMessage(message);
	}
	
	/* �ʐM����߂�ꍇ */
	/**
	 * websocket�ڑ��O��̏�Ԃɉ����ĉ����ؒf���܂��B
	 * @param statusCode websocket�ڑ��O�̏ꍇ�ؒf����http�X�e�[�^�X�R�[�h
	 * @param code websockte�ڑ���̏ꍇ�A�N���C�A���g��close�ɒʒm����code
	 * @param reason�@websocket�ڑ���̏ꍇ�A�N���C�A���g��close�ɒʒm���錴��������
	 */
	public void closeWebSocket(String statusCode,short code,String reason){
		if(isHandshaked){
			wsProtocol.onClose(code,reason);
		}else{
			completeResponse(statusCode);
		}
	}
	
	/**
	 * websocket�ڑ��O��̏�Ԃɉ����ĉ����ؒf���܂��B<br/>
	 * websocket�ڑ���͐���N���[�Y�𑗐M
	 * @param statusCode websocket�ڑ��O�̏ꍇ�ؒf����http�X�e�[�^�X�R�[�h
	 */
	public void closeWebSocket(String statusCode){
		closeWebSocket(statusCode,WsHybiFrame.CLOSE_NORMAL,"OK");
	}
	
	/**
	 * websocket�ʐM�̊J�n��ʒm���܂��B
	 * @param subprotocol�@�T�u�v���g�R��
	 */
	public abstract void onWsOpen(String subprotocol);
	
	/**
	 * websocket�ʐM�̏I����ʒm���܂��B
	 * @param code �I���R�[�h
	 * @param reason ����������
	 */
	public abstract void onWsClose(short code,String reason);
	
	
	/**
	 * text���b�Z�[�W����M�������Ƃ�ʒm���܂��B
	 * @param msgs ��M���b�Z�[�W
	 */
	public abstract void onMessage(String msgs);
	
	/**
	 * binary���b�Z�[�W����M�������Ƃ�ʒm���܂��B
	 * @param msgs ��M���b�Z�[�W
	 */
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
	
	/**
	 * override���Ȃ�
	 */
	public void onRequestHeader() {
		logger.debug("#doResponse.cid:"+getChannelId());
		HeaderParser requestHeader=getRequestHeader();
		if(!requestHeader.isWs()){
			super.onRequestHeader();//body�������Ɍp�������N���X��startResponseReqBody�ɒʒm�����
			return;
		}
		isWs=true;
		/* ���O�o�̓^�C�v���擾 */
		/* logoff����onLogout�C�x���g���ʒm�����悤�ɐݒ� */
		wsProtocol=WsProtocol.createWsProtocol(requestHeader,getRequestMapping());
		if(wsProtocol==null){
			//webSocket�Őڑ������ۂ���̂͌��܂������@���Ȃ�
			//http://blog.aklaswad.com/2012/000517.html
			completeResponse("422");//422 Protocol Extension Refused
			logger.warn("not found WebSocket Protocol");
			return;
		}
		logger.debug("wsProtocol class:"+wsProtocol.getClass().getName());
		//subprotocol�����
		String selectSubprotocol=null;
		String reqSubprotocols=wsProtocol.getRequestSubProtocols(requestHeader);
		if(reqSubprotocols==null){
			if(wsProtocol.isUseSubprotocol()){//subprotocol��K�v�Ƃ���̂ɂȂ�
				completeResponse("422");//422 Protocol Extension Refused
				return;
			}
		}else{
			selectSubprotocol=wsProtocol.checkSubprotocol(reqSubprotocols);
			if(selectSubprotocol==null){//subprotocol����v���Ȃ�
				logger.debug("WsHybi10#suprotocol error.webSocketProtocol:"+reqSubprotocols);
				completeResponse("422");//422 Protocol Extension Refused
				return;
			}
		}
		RequestContext requestContext=getRequestContext();
		requestContext.registerLogoutEvnet(this);
		onWebSocket(requestHeader,selectSubprotocol);
	}
	
	/**
	 * websocket�����̊J�n<br/>
	 * handshake�͂܂��������Ă��Ȃ��B<br/>
	 * doHandshake���\�b�h���Ăяo����handshake�������������AonWsOpen���\�b�h���Ăяo�����B<br/>
	 * ������handshake�����Ɏ��O�������K�v�ȏꍇ��override���ė��p
	 * @param requestHeader websocket�v���w�b�_
	 * @param subprotocol�@�v�����ꂽsuprotocol
	 */
	public 	void onWebSocket(HeaderParser requestHeader,String subprotocol){
		doHandshake(subprotocol);
	}
	
	//web socket��handshake�����{
	/**
	 * websocket��handshake�������s���܂��B<br/>
	 * handshake�ɐ��������ꍇ�AonWsOpen�ɒʒm����܂��B<br/>
	 * @param subProtocol
	 * @return�@handshake�����������ꍇtrue
	 */
	public boolean doHandshake(String subProtocol){
		HeaderParser requestHeader=getRequestHeader();
		wsProtocol.setup(this);
		isHandshaked=wsProtocol.onHandshake(requestHeader,subProtocol);
		return isHandshaked;
	}
	
	/**
	 * �f�[�^����M�������Ƃ�ʒm<br/>
	 * override���Ȃ�<br/>
	 */
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#read.cid:"+getChannelId());
		if(!isWs){
			super.onReadPlain(userContext, buffers);
			return;
		}
		wsProtocol.onBuffer(buffers);
	}
	
	/**
	 * io�����s�������Ƃ�ʒm<br/>
	 * override���Ȃ�<br/>
	 */
	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		if(isWs){
			closeWebSocket("500");
		}
		super.onFailure(userContext, t);
	}

	/**
	 * read�������^�C���A�E�g�������Ƃ�ʒm<br/>
	 * override���Ȃ�<br/>
	 */
	public void onReadTimeout(Object userContext) {
		logger.debug("#readTimeout.cid:" +getChannelId());
		if(isWs){
			wsProtocol.onReadTimeout();
		}else{
			super.onReadTimeout(userContext);
		}
	}
	
	/**
	 * io���^�C���A�E�g�������Ƃ�ʒm�B<br/>
	 * override���Ȃ�<br/>
	 */
	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		if(isWs){
			closeWebSocket("500");
		}
		super.onTimeout(userContext);
	}

	/**
	 * ���Yhandler�ŏ��u���ɉ����������ꂽ�ꍇ�ɒʒm����܂��B<br/>
	 * override����ꍇ�́A�����\�b�h���Ăяo���Ă��������B<br/>
	 */
	@Override
	public void onFinished() {
		logger.debug("#finished client.cid:"+getChannelId());
		if(wsProtocol!=null){
			wsProtocol.onClose(WsHybiFrame.CLOSE_UNKOWN,null);
		}
		super.onFinished();
	}

	/**
	 * ���̃I�u�W�F�N�g���ė��p����ۂɌĂяo�����B<br/>
	 * override�����ꍇ�́A�K�������\�b�h���Ăяo���Ă��������B
	 */
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
	/**
	 * posteMessage�̑��M������ʒm
	 */
	public void onPosted(){
	}

	/**
	 * �������݂������������Ƃ�ʒm�B<br/>
	 * override���Ȃ�<br/>
	 */
	@Override
	public void onWrittenPlain(Object userContext) {
		if(wsProtocol!=null){
			wsProtocol.onWrittenPlain(userContext);
		}
		super.onWrittenPlain(userContext);
	}

	/**
	 * websocket�̃��X�|���X�w�b�_��ԋp���܂��B
	 */
	protected void flushHeaderForWebSocket(String spec,String subprotocol) {
		super.flushHeaderForWebSocket(spec, subprotocol);
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
