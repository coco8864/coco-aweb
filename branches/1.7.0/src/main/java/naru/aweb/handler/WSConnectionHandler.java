package naru.aweb.handler;

import naru.async.cache.Cache;
import naru.async.pool.PoolBase;
import naru.aweb.config.Config;
import naru.aweb.handler.ws.WebSocketHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;

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
	public 	void onWebSocket(HeaderParser requestHeader,String subprotocol){
		MappingResult mapping=getRequestMapping();
		String ip=(String)mapping.getOption("ip");
		if(ip!=null){
			String remoteIp=getRemoteIp();
			if(ip.equals(remoteIp)){
				completeResponse("422");//422 Protocol Extension Refused
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
	public void onMessage(Cache msgs) {
		logger.debug("#message bin cid:"+getChannelId());
		if(msgs instanceof PoolBase){
			((PoolBase)msgs).unref();
		}
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
