package naru.aweb.http;

import naru.async.cache.CacheBuffer;


/**
 * Webクライアントの通知インタフェース
 * WebClientHandlerに渡し通知を受ける
 * 
 * @author Naru
 *
 */
public interface WsClient {
	//物理的にconnectしたとき
	public void onWcConnected(Object userContext);
	//proxyから200が返却された時
	public void onWcProxyConnected(Object userContext);
	//sslHandshakeが成功した時
	public void onWcSslHandshaked(Object userContext);
	//response headerを受信したとき
	public void onWcResponseHeader(Object userContext,HeaderParser responseHeader);
	//websocket handshakeが成功した時
	public void onWcHandshaked(Object userContext,String subprotocol);
	
	//終了時
	public void onWcClose(Object userContext,int stat,short closeCode,String colseReason);
	//エラー終了時
	public void onWcFailure(Object userContext,int stat,Throwable t);
	//メッセージ受信時
	public void onWcMessage(Object userContext,String message);
	//メッセージ受信時
	public void onWcMessage(Object userContext,CacheBuffer message);
	//header書き込みが完了した時
	public void onWcWrittenHeader(Object userContext);
}
