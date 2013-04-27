package naru.aweb.admin;

import java.util.Map;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;
import naru.aweb.pa.api.PaMsg;
import naru.aweb.pa.api.PaPeer;
import naru.aweb.pa.api.Palet;
import naru.aweb.pa.api.PaletCtx;

public class PaAdmin implements Palet {
	public static final String QNAME = "admin";
	public static final String SUBNAME_CHAT = "chat";
	public static final String SUBNAME_PERF = "perf";
	public static final String SUBNAME_ACCESS_LOG = "accessLog";
	public static final String SUBNAME_STASTICS="stastics";
	
	private static Logger logger = Logger.getLogger(PaAdmin.class);
	private static Config config=Config.getConfig();
	
	private PaletCtx ctx;
	@Override
	public void init(String qname,String subname,PaletCtx ctx) {
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
		 */
		peer.message("done");
	}

	@Override
	public void onPublish(PaPeer peer, PaMsg parameter) {
		String subname=peer.getSubname();
		if(SUBNAME_STASTICS.equals(subname)){
			ctx.message(parameter, subname);
		}else if("setting".equals(subname)){
			setting(peer,parameter);
		}
	}

}
