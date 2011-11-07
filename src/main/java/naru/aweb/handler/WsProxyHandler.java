/**
 * 
 */
package naru.aweb.handler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import naru.async.pool.PoolManager;
import naru.async.store.Page;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientHandler;
import naru.aweb.http.WebServerHandler;
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
	
	public 	void startWebSocketResponse(HeaderParser requestHeader,WsProtocol wsProtocol){
		//�ǂ�protocol�Ōq���ɂ�����?��{���̂܂܁H
		String webSocketProtocol=wsProtocol.getRequestSubProtocols(requestHeader);
		wsClientHandler=WsClientHandler.create(webClientConnection);
		wsClientHandler.startRequest(this, null, 10000, webSocketProtocol);
	}
	
	/* �u���E�U����̒ʒm���\�b�h */
	/* �u���E�U��message�𑗐M���Ă����� */
	@Override
	public void onMessage(String message) {
		wsClientHandler.wsPostMessage(message);
	}
	/* �u���E�U��message�𑗐M���Ă����� */
	@Override
	public void onMessage(ByteBuffer[] message) {
		wsClientHandler.wsPostMessage(message);
	}
	
	/* �u���E�U��message�𑗐M���Ă����� */
	@Override
	public void onWsClose(short code, String reason) {
	}
	
	/* WebSocket�T�[�o����̒ʒm���\�b�h */
	/* WebSocket server�Ɛڑ����ꂽ�ꍇ */
	@Override
	public void onWsOpen(String subprotocol) {
		//handshake�J�n
		doHandshake(subprotocol);
	}
	
	/* WebSocket server��ssl�̏ꍇ�A*/
	@Override
	public void onSslHandshaked(Object userContext) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onWsClose(Object userContext) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onWsConnected(Object userContext) {
		
	}
	@Override
	public void onWsFailure(Object userContext, int stat, Throwable t) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onWsHandshaked(Object userContext) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onWsMessage(Object userContext, String message) {
		postMessage(message);
	}
	@Override
	public void onWsMessage(Object userContext, ByteBuffer[] message) {
		postMessage(message);
		
	}
	@Override
	public void onWsProxyConnected(Object userContext) {
		// TODO Auto-generated method stub
	}
}
