package naru.aweb.admin;

import java.util.List;
import java.util.Map;

import naru.aweb.pa.PaPeer;
import naru.aweb.pa.Palet;
import naru.aweb.pa.PaletCtx;

public class PaAdmin implements Palet {
	private PaletCtx ctx;

	@Override
	public void init(PaletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(String reason) {
	}

	@Override
	public void onTimer() {
	}

	@Override
	public void onSubscribe(PaPeer peer) {
	}

	@Override
	public void onUnsubscribe(PaPeer peer, String reason) {
	}

	@Override
	public void onPublishText(PaPeer peer, String data) {
		// TODO Auto-generated method stub
	}
	
	private void chat(PaPeer peer,Map<String, ?> data){
		if(Boolean.TRUE.equals(data.get("echoback"))){
			ctx.message(data, "chat");
		}else{
			ctx.message(data, "chat", peer);
		}
	}
	
	private void setting(PaPeer peer,Map<String, ?> data){
		
		/*
		 * %proxy
		 * proxyPacUrl
		 * proxyServer
		 * sslProxyServer
		 * exceptProxyDomains
		 * 
		 * %keepALive
		 * isWebKeepAlive check
		 * isProxyKeepAlive check
		 * maxKeepAliveRequests num
		 * keepAliveTimeout num
		 * allowChunked check
		 * 
		 * %html5
		 * isUseHixie75 check
		 * isUseHixie76 check
		 * isUseHybi10 check
		 * webSocketMessageLimit
		 * webSocketPingInterval
		 * 
		 * 
		 * 
		 */
		
		
		peer.message("done");
		
	}
	

	@Override
	public void onPublishObj(PaPeer peer, Map<String, ?> data) {
		String subname=peer.getSubname();
		if("chat".equals(subname)){
			chat(peer,data);
		}else if("sttics".equals(subname)){
			ctx.message(data, "sttics");
		}else if("setting".equals(subname)){
			setting(peer,data);
		}
	}

	@Override
	public void onPublishArray(PaPeer peer, List<?> data) {
		// TODO Auto-generated method stub
	}

}
