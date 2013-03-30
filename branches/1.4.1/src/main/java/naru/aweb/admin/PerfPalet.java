package naru.aweb.admin;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;
import naru.aweb.pa.PaPeer;
import naru.aweb.pa.Palet;
import naru.aweb.pa.PaletCtx;
import naru.aweb.robot.ConnectChecker;
import naru.aweb.robot.ServerChecker;
import net.sf.json.JSONObject;

public class PerfPalet implements Palet {
	private static Logger logger = Logger.getLogger(PerfPalet.class);
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

	@Override
	public void onPublishText(PaPeer peer, String data) {
	}

	@Override
	public void onPublishObj(PaPeer peer, Map data) {
		JSONObject parameter=(JSONObject)data;
		if(!peer.fromBrowser()){
			ctx.message(parameter, peer.getSubname());
			return;
		}
		String kind=(String)parameter.get("kind");
		if("checkConnect".equals(kind)){
			Integer count=parameter.getInt("count");
			Integer maxFailCount=parameter.getInt("maxFailCount");
			if( ConnectChecker.start(count, maxFailCount, 0)==false ){
				parameter.put("kind","checkConnectResult");
				parameter.put("result","fail");
				peer.message(parameter);
			}
		}else if("checkServer".equals(kind)){
			try {
				String url=parameter.getString("url");
				Integer requestCount=parameter.getInt("requestCount");
				boolean isKeepAlive=parameter.getBoolean("isKeepAlive");
				boolean isTrace=parameter.getBoolean("isTrace");
				ServerChecker.start(new URL(url),isKeepAlive,requestCount,isTrace,"check",peer);
			} catch (MalformedURLException e) {
				parameter.put("kind","checkServerResult");
				parameter.put("result","fail");
				peer.message(parameter);
			}
		}
	}

	@Override
	public void onPublishArray(PaPeer peer, List<?> data) {
	}

}
