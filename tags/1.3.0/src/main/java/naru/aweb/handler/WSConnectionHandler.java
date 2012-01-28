package naru.aweb.handler;

import java.nio.ByteBuffer;

import naru.aweb.config.Config;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.HeaderParser;
import naru.aweb.mapping.MappingResult;

import org.apache.log4j.Logger;

/**
 * Connectionを消費するためのWebSocketハンドラ
 * 
 * @author Naru
 *
 */
public class WSConnectionHandler extends WebSocketHandler {
	private static Logger logger=Logger.getLogger(WebSocketHandler.class);
	private static Config config=Config.getConfig();
	
	@Override
	public 	void startWebSocketResponse(HeaderParser requestHeader,String subprotocol){
		MappingResult mapping=getRequestMapping();
		String ip=(String)mapping.getOption("ip");
		if(ip!=null){
			String remoteIp=getRemoteIp();
			if(ip.equals(remoteIp)){
				closeWebSocket("403");
				return;
			}
		}
		//handshake開始
		doHandshake(subprotocol);
	}

	@Override
	public void onMessage(String msgs) {
		logger.debug("#message text cid:"+getChannelId());
		if("doClose".equals(msgs)){
			closeWebSocket("500");
		}else{
			postMessage(msgs);
		}
	}

	@Override
	public void onMessage(ByteBuffer[] msgs) {
		logger.debug("#message bin cid:"+getChannelId());
	}

	@Override
	public void onWsClose(short code, String reason) {
		logger.debug("#wsClose cid:"+getChannelId());
	}

	@Override
	public void onWsOpen(String subprotocol) {
		logger.debug("#wsOpen cid:"+getChannelId());
		postMessage("OK");
	}

}
