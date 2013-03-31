package naru.aweb.admin;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.pa.PaPeer;
import naru.aweb.pa.Palet;
import naru.aweb.pa.PaletCtx;
import naru.aweb.queue.QueueManager;
import naru.aweb.robot.ConnectChecker;
import naru.aweb.robot.Scenario;
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
		}else if("stress".equals(kind)){
			String list=parameter.getString("list");
			AccessLog[] accessLogs=listToAccessLogs(list);
//			Set<Long> accessLogIds=new HashSet<Long>();
			String name=parameter.getString("name");
			String browserCount=parameter.getString("browserCount");
			String call=parameter.getString("loopCount");
//			String time=parameter.getParameter("time");
//			String trace=parameter.getParameter("trace");
			boolean keepAlive=parameter.getBoolean("keepAlive");
			boolean accessLog=parameter.getBoolean("accessLog");
			boolean tesponseHeaderTrace=parameter.getBoolean("tesponseHeaderTrace");
			boolean tesponseBodyTrace=parameter.getBoolean("tesponseBodyTrace");
			int thinkingTime=parameter.getInt("thinkingTime");
			try {
				doStress(accessLogs,name,
						Integer.parseInt(browserCount),
						Integer.parseInt(call),
						keepAlive,
						thinkingTime,
						accessLog,
						tesponseHeaderTrace,
						tesponseBodyTrace);
			} catch (NumberFormatException e) {
				parameter.put("kind","stressResult");
				parameter.put("result","fail");
				peer.message(parameter);
				return;
			}
		}
	}

	@Override
	public void onPublishArray(PaPeer peer, List<?> data) {
	}
	
	private AccessLog[] listToAccessLogs(String list){
		String[] ids=list.split(",");
		AccessLog[] accessLogs=new AccessLog[ids.length];
		for(int i=0;i<ids.length;i++){
			long accessLogId=Long.parseLong(ids[i]);
			accessLogs[i]=AccessLog.getById(accessLogId);
		}
		return accessLogs;
	}
	private String doStress(AccessLog[] accessLogs,String name,int browserCount,int callCount,
			boolean isCallerKeepAlive,long thinkingTime,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		if( Scenario.run(accessLogs, name, browserCount, callCount, isCallerKeepAlive, thinkingTime, isAccessLog, isResponseHeaderTrace, isResponseBodyTrace,"0")){
			return "0";
		}
		return null;
	}
}
