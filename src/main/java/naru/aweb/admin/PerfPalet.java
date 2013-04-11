package naru.aweb.admin;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.pa.Blob;
import naru.aweb.pa.PaMsg;
import naru.aweb.pa.PaPeer;
import naru.aweb.pa.Palet;
import naru.aweb.pa.PaletCtx;
import naru.aweb.robot.ConnectChecker;
import naru.aweb.robot.Scenario;
import naru.aweb.robot.ServerChecker;
import naru.aweb.util.Event;
import naru.aweb.util.StringConverter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class PerfPalet implements Palet,Event {
	private static Logger logger = Logger.getLogger(PerfPalet.class);
	private static Config config=Config.getConfig();

	private PaletCtx ctx;
	private Scenario scenario=null;
	
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
	public void onPublish(PaPeer peer, PaMsg parameter) {
//		JSONObject parameter=null;
//		if(data instanceof JSONObject){
//			parameter=(JSONObject)data;
//		}
		if(!peer.fromBrowser()){
			if( parameter.getBoolean("isComplete")==true){
				if(scenario!=null){
					scenario.unref();
					scenario=null;
				}
			}
			ctx.message(parameter, peer.getSubname());
			return;
		}
		String kind=parameter.getString("kind");
		if("checkConnect".equals(kind)){
			Integer count=parameter.getInt("count");
			Integer maxFailCount=parameter.getInt("maxFailCount");
			PaPeer publishPeer=PaPeer.create(config.getAdminPaManager(), null,peer.getQname(),peer.getSubname());
			if( ConnectChecker.start(count, maxFailCount, 0,publishPeer)==false ){
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
		}else if("stopStress".equals(kind)){
			if(scenario!=null){
				scenario.stop();
			}else{
				parameter.put("kind","stopStressResult");
				parameter.put("result","fail");
				parameter.put("reason","not running");
				peer.message(parameter);
				return;
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
			if(scenario!=null){
				//aleady runnnig
				parameter.put("kind","stressResult");
				parameter.put("result","fail");
				parameter.put("reason","aleady execute");
				peer.message(parameter);
				return;
			}
			PaPeer publishPeer=PaPeer.create(config.getAdminPaManager(), null,peer.getQname(),peer.getSubname());
			if(!doStress(accessLogs,name,
						Integer.parseInt(browserCount),
						Integer.parseInt(call),
						keepAlive,
						thinkingTime,
						accessLog,
						tesponseHeaderTrace,
						tesponseBodyTrace,
						publishPeer)){
				publishPeer.unref();
				parameter.put("kind","stressResult");
				parameter.put("result","fail");
				parameter.put("reason","doStress error");
				peer.message(parameter);
			}
		}else if("stressFile".equals(kind)){
			stressFileList=parameter.getString("list");
			stressFilePeer=peer;
			Blob blob=parameter.getBlob("stressFile");
			StringConverter.decode(this,blob,"utf-8",4096);
		}
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
	
	private boolean settingScenario(Scenario scenario){
		if(scenario==null){
			return false;
		}
		synchronized(this){
			if(this.scenario!=null){
				scenario.stop();
				return false;
			}
			scenario.ref();
			this.scenario=scenario;
		}
		return true;
	}
	
	private boolean doStress(AccessLog[] accessLogs,String name,int browserCount,int callCount,
			boolean isCallerKeepAlive,long thinkingTime,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace,PaPeer peer){
		Scenario scenario=Scenario.run(accessLogs, name, browserCount, callCount, isCallerKeepAlive, thinkingTime, isAccessLog, isResponseHeaderTrace, isResponseBodyTrace,peer);
		return settingScenario(scenario);
	}
	
	private boolean doStressFile(AccessLog[] accessLogs,JSONArray stressJson){
		PaPeer publishPeer=PaPeer.create(config.getAdminPaManager(), null,stressFilePeer.getQname(),stressFilePeer.getSubname());
		Scenario scenario=Scenario.run(accessLogs, stressJson,publishPeer);
		return settingScenario(scenario);
	}

	private String stressFileList;
	private PaPeer stressFilePeer;

	@Override
	public void done(boolean result, Object obj) {
		if(result){
			JSONArray json=JSONArray.fromObject((String)obj);
			AccessLog[] accessLogs=listToAccessLogs(stressFileList);
			doStressFile(accessLogs,json);
		}else{
			JSONObject res=new JSONObject();
			res.put("kind","stressFileResult");
			res.put("result","fail");
			res.put("reason","doStressFile error");
			stressFilePeer.message(res);
		}
	}
}
