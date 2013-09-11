package naru.aweb.admin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;
import naru.aweb.link.api.LinkMsg;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.link.api.LinkletCtx;
import net.sf.json.JSONObject;

public class ChatLinklet implements Linklet {
	private static Logger logger = Logger.getLogger(ChatLinklet.class);
	private static Config config=Config.getConfig();

	private Map<LinkPeer,String> names=Collections.synchronizedMap(new HashMap<LinkPeer,String>());
	
	private LinkletCtx ctx;
	@Override
	public void init(String qname,String subname,LinkletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(String reason) {
	}

	@Override
	public void onTimer() {
	}

	@Override
	public void onSubscribe(LinkPeer peer) {
		names.put(peer, peer.getLoginId());
	}

	@Override
	public void onUnsubscribe(LinkPeer peer, String reason) {
		names.remove(peer);
	}

	@Override
	public void onPublish(LinkPeer peer, LinkMsg data) {
		String kind=(String)data.get("kind");
		if("who".equals(kind)){
			JSONObject res=new JSONObject();
			res.put("kind", "who");
			res.put("users", names.values());
			peer.message(res);
			return;
		}
		if(Boolean.TRUE.equals(data.get("echoback"))){
			ctx.message(data, AdminLinklet.SUBNAME_CHAT);
		}else{
			ctx.message(data, AdminLinklet.SUBNAME_CHAT, peer);
		}
	}
}
