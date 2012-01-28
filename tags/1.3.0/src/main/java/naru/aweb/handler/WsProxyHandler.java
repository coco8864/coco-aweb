/**
 * 
 */
package naru.aweb.handler;

import java.nio.ByteBuffer;

import naru.aweb.config.Config;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WsClient;
import naru.aweb.http.WsClientHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

/**
 * websocket proxy�Ƃ��ē���
 * 1)�u���E�U���Aph��WebSocket�T�[�o�Ƃ݂Ȃ��Ă���ꍇ
 * 2)�u���E�U���Aph��Proxy�Ƃ݂Ȃ��Ă���ꍇ
 * @author Naru
 *
 */
public class WsProxyHandler extends  WebSocketHandler implements WsClient{
	private static Logger logger=Logger.getLogger(WsProxyHandler.class);
	private static Config config=Config.getConfig();
	/* WebSocket Server�ƂȂ����Ă���Handler */
	private WsClientHandler wsClientHandler;
	
	@Override
	public void startWebSocketResponse(HeaderParser requestHeader,String subProtocols){
		MappingResult mapping=getRequestMapping();
		ServerParser targetHostServer=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		requestHeader.setPath(path);
		wsClientHandler=WsClientHandler.create(mapping.isResolvedHttps(),targetHostServer.getHost(),targetHostServer.getPort());
		wsClientHandler.ref();
		
		String uri=requestHeader.getRequestUri();
		String origin=requestHeader.getHeader("Origin");
		
		//�ǂ�protocol�Ōq���ɂ�����?��{���̂܂܁H
//		String subProtocols=wsProtocol.getRequestSubProtocols(requestHeader);
		wsClientHandler.startRequest(this, null, 10000, uri,subProtocols,origin);
	}
	
	/* �u���E�U����̒ʒm���\�b�h */
	/* �u���E�U��message�𑗐M���Ă����� */
	@Override
	public void onMessage(String message) {
		wsClientHandler.postMessage(message);
	}
	/* �u���E�U��message�𑗐M���Ă����� */
	@Override
	public void onMessage(ByteBuffer[] message) {
		wsClientHandler.postMessage(message);
	}
	
	/* �u���E�U����close������ꂽ */
	@Override
	public void onWsClose(short code, String reason) {
		wsClientHandler.doClose(code,reason);
	}
	
	/* WebSocket�T�[�o����̒ʒm���\�b�h */
	/* WebSocket server�Ɛڑ����ꂽ�ꍇ */
	@Override
	public void onWsOpen(String subprotocol) {
		ref();//server�ɐڑ������ꍇclose�܂Ŏ����͉�����Ȃ�
	}
	
	/* WebSocket server��ssl�̏ꍇ�A*/
	@Override
	public void onWcSslHandshaked(Object userContext) {
	}
	@Override
	public void onWcConnected(Object userContext) {
		
	}
	/* �T�[�o����close������ꂽ�ꍇ�A�u���E�U�ɂ�code,reason�𑗂肽����... */
	@Override
	public void onWcClose(Object userContext,int stat,short closeCode,String closeReason) {
		logger.debug("#onWcClose cid:"+getChannelId());
		closeWebSocket("500",closeCode,closeReason);
		unref();//server�ɐڑ������ꍇclose�܂Ŏ����͉�����Ȃ�
	}
	@Override
	public void onWcFailure(Object userContext, int stat, Throwable t) {
		logger.debug("#wcFailure cid:"+getChannelId());
		closeWebSocket("500");
		unref();//server�ɐڑ������ꍇclose�܂Ŏ����͉�����Ȃ�
	}
	@Override
	public void onWcHandshaked(Object userContext,String subprotocol) {
		logger.debug("#wcHandshaked cid:"+getChannelId() +" subprotocol:"+subprotocol);
		//handshake�J�n
		doHandshake(subprotocol);
	}
	@Override
	public void onWcMessage(Object userContext, String message) {
		postMessage(message);
	}
	@Override
	public void onWcMessage(Object userContext, ByteBuffer[] message) {
		postMessage(message);
		
	}
	@Override
	public void onWcProxyConnected(Object userContext) {
	}

	@Override
	public void onWcWrittenHeader(Object userContext) {
	}

	@Override
	public void onWcResponseHeader(Object userContext,HeaderParser responseHeader) {
	}

	/*
	@Override
	public void onClosed(Object userContext) {
	}

	@Override
	public void onFailure(Object userContext, Throwable t) {
	}

	@Override
	public void onFinished() {
	}
	*/

	@Override
	public void recycle() {
		if(wsClientHandler!=null){
			wsClientHandler.unref();
			wsClientHandler=null;
		}
		super.recycle();
	}
}
