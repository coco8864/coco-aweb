package naru.aweb.http;

import java.nio.ByteBuffer;


/**
 * Webクライアントの通知インタフェース
 * WebClientHandlerに渡し通知を受ける
 * 
 * @author Naru
 *
 */
public interface WsClient {
	public void onWsConnected(Object userContext);

	public void onWsProxyConnected(Object userContext);

	public void onSslHandshaked(Object userContext);
	
	public void onWsHandshaked(Object userContext);
	
	
	public void onWsClose(Object userContext);
	
	public void onMessage(Object userContext,String message);
	public void onMessage(Object userContext,ByteBuffer[] message);
	
	public void onWsFailure(Object userContext,int stat,Throwable t);
}
