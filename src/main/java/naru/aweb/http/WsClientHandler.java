package naru.aweb.http;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import naru.async.pool.PoolManager;
import naru.aweb.config.Config;

public class WsClientHandler {
	private static Logger logger = Logger.getLogger(WsClientHandler.class);
	private static Config config=Config.getConfig();
	
	public static WsClientHandler create(WebClientConnection webClientConnection){
		logger.debug("create:"+webClientConnection);
		WsClientHandler wsClientHandler=(WsClientHandler)PoolManager.getInstance(WsClientHandler.class);
		wsClientHandler.setWebClientConnection(webClientConnection);
		return wsClientHandler;
	}
	
	private WebClientConnection webClientConnection;
	private WsClient wsClient;
	
	private void setWebClientConnection(WebClientConnection webClientConnection) {
		this.webClientConnection=webClientConnection;
		
	}

	public final boolean startRequest(WsClient wsClient,Object userContext,long connectTimeout,String subProtocol) {
		return false;
	}
	
	public final void wsPostMessage(String message){
	}
	
	public final void wsPostMessage(ByteBuffer[] message){
	}

	public final void wsPostMessage(ByteBuffer message){
	}
	
	public final void wsClose(){
	}

}
