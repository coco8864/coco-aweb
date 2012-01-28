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
		
		//どのprotocolで繋ぎにいくか?基本そのまま？
//		String subProtocols=wsProtocol.getRequestSubProtocols(requestHeader);
		wsClientHandler.startRequest(this, null, 10000, uri,subProtocols,origin);
	}
	
	/* ブラウザからの通知メソッド */
	/* ブラウザがmessageを送信してきた時 */
	@Override
	public void onMessage(String message) {
		wsClientHandler.postMessage(message);
	}
	/* ブラウザがmessageを送信してきた時 */
	@Override
	public void onMessage(ByteBuffer[] message) {
		wsClientHandler.postMessage(message);
	}
	
	/* ブラウザからcloseが送られた */
	@Override
	public void onWsClose(short code, String reason) {
		wsClientHandler.doClose(code,reason);
	}
	
	/* WebSocketサーバからの通知メソッド */
	/* WebSocket serverと接続された場合 */
	@Override
	public void onWsOpen(String subprotocol) {
		ref();//serverに接続した場合closeまで自分は解放しない
	}
	
	/* WebSocket serverがsslの場合、*/
	@Override
	public void onWcSslHandshaked(Object userContext) {
	}
	@Override
	public void onWcConnected(Object userContext) {
		
	}
	/* サーバからcloseが送られた場合、ブラウザにもcode,reasonを送りたいが... */
	@Override
	public void onWcClose(Object userContext,int stat,short closeCode,String closeReason) {
		logger.debug("#onWcClose cid:"+getChannelId());
		closeWebSocket("500",closeCode,closeReason);
		unref();//serverに接続した場合closeまで自分は解放しない
	}
	@Override
	public void onWcFailure(Object userContext, int stat, Throwable t) {
		logger.debug("#wcFailure cid:"+getChannelId());
		closeWebSocket("500");
		unref();//serverに接続した場合closeまで自分は解放しない
	}
	@Override
	public void onWcHandshaked(Object userContext,String subprotocol) {
		logger.debug("#wcHandshaked cid:"+getChannelId() +" subprotocol:"+subprotocol);
		//handshake開始
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
