package naru.aweb.admin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;
import naru.aweb.pa.PaPeer;
import naru.aweb.pa.Palet;
import naru.aweb.pa.PaletCtx;
import net.sf.json.JSONObject;

public class ChatPalet implements Palet {
	private static Logger logger = Logger.getLogger(ChatPalet.class);
	private static Config config=Config.getConfig();

	private Map<PaPeer,String> names=Collections.synchronizedMap(new HashMap<PaPeer,String>());
	
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
		names.put(peer, peer.getLoginId());
	}

	@Override
	public void onUnsubscribe(PaPeer peer, String reason) {
		names.remove(peer);
	}

	@Override
	public void onPublishText(PaPeer peer, String data) {
	}

	@Override
	public void onPublishObj(PaPeer peer, Map<String, ?> data) {
		String kind=(String)data.get("kind");
		if("who".equals(kind)){
			JSONObject res=new JSONObject();
			res.put("kind", "who");
			res.put("users", names.values());
			peer.message(res);
			return;
		}
		if(Boolean.TRUE.equals(data.get("echoback"))){
			ctx.message(data, PaAdmin.SUBNAME_CHAT);
		}else{
			ctx.message(data, PaAdmin.SUBNAME_CHAT, peer);
		}
	}

	@Override
	public void onPublishArray(PaPeer peer, List<?> data) {
	}

}
