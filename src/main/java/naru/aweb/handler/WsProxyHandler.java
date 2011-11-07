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
 * websocket proxyとして動作
 * 1)ブラウザが、phをWebSocketサーバとみなしている場合
 * 2)ブラウザが、phをProxyとみなしている場合
 * @author Naru
 *
 */
public class WsProxyHandler extends  WebSocketHandler implements WsClient{
	private static Logger logger=Logger.getLogger(WsProxyHandler.class);
	private static Config config=Config.getConfig();
	/* WebSocket ServerとつながっているHandler */
	private WsClientHandler wsClientHandler;
	
	public 	void startWebSocketResponse(HeaderParser requestHeader,WsProtocol wsProtocol){
		//どのprotocolで繋ぎにいくか?基本そのまま？
		String webSocketProtocol=wsProtocol.getRequestSubProtocols(requestHeader);
		wsClientHandler=WsClientHandler.create(webClientConnection);
		wsClientHandler.startRequest(this, null, 10000, webSocketProtocol);
	}
	
	/* ブラウザからの通知メソッド */
	/* ブラウザがmessageを送信してきた時 */
	@Override
	public void onMessage(String message) {
		wsClientHandler.wsPostMessage(message);
	}
	/* ブラウザがmessageを送信してきた時 */
	@Override
	public void onMessage(ByteBuffer[] message) {
		wsClientHandler.wsPostMessage(message);
	}
	
	/* ブラウザがmessageを送信してきた時 */
	@Override
	public void onWsClose(short code, String reason) {
	}
	
	/* WebSocketサーバからの通知メソッド */
	/* WebSocket serverと接続された場合 */
	@Override
	public void onWsOpen(String subprotocol) {
		//handshake開始
		doHandshake(subprotocol);
	}
	
	/* WebSocket serverがsslの場合、*/
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
