package naru.aweb.admin;

import java.util.Map;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;
import naru.aweb.link.api.LinkMsg;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.link.api.LinkletCtx;

public class AdminLinklet implements Linklet {
	public static final String QNAME = "admin";
	public static final String SUBNAME_CHAT = "chat";
	public static final String SUBNAME_PERF = "perf";
	public static final String SUBNAME_ACCESS_LOG = "accessLog";
	public static final String SUBNAME_STASTICS="stastics";
	
	private static Logger logger = Logger.getLogger(AdminLinklet.class);
	private static Config config=Config.getConfig();
	
	private LinkletCtx ctx;
	@Override
	public void init(String qname,String subname,LinkletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(String reason) {
	}

	boolean isSuspend=false;
	long suspendTime=0;
	@Override
	public void onTimer() {
		synchronized(this){
			if(isSuspend==false){
				return;
			}
			if(System.currentTimeMillis()-suspendTime>=3000){
				config.setProperty("phantomSuspend",false);
				isSuspend=false;
			}
		}
	}

	@Override
	public void onSubscribe(LinkPeer peer) {
	}

	@Override
	public void onUnsubscribe(LinkPeer peer, String reason) {
	}
	
	private void setting(LinkPeer peer,Map<String, ?> data){
		
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
	public void onPublish(LinkPeer peer, LinkMsg parameter) {
		String subname=peer.getSubname();
		if(SUBNAME_STASTICS.equals(subname)){
			ctx.message(parameter, subname);
		}else if("setting".equals(subname)){
			setting(peer,parameter);
		}else{
			boolean isSuspend=parameter.getBoolean("suspend");
			synchronized(this){
				config.setProperty("phantomSuspend",isSuspend);
				this.isSuspend=isSuspend;
			}
			if(isSuspend){
				ctx.setInterval(1000);
			}else{
				ctx.setInterval(-1);
			}
			suspendTime=System.currentTimeMillis();
		}
	}
	
	@Override
	public void onPublish(LinkPeer peer, String data) {
		logger.error("unsupport data");
	}

}
