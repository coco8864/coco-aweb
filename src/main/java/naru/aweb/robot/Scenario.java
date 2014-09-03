package naru.aweb.robot;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Performance;
import naru.aweb.link.api.LinkPeer;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class Scenario extends PoolBase{
	private static Logger logger = Logger.getLogger(Scenario.class);
	private static Config config=Config.getConfig();
//	private static Map<String,Scenario> chIdScenarioMap=new HashMap<String,Scenario>();//現状走行中のScenarioを保持　cancel対応
	
	private String name;
	private boolean isProcessing;
	private boolean isReceiveStop;//stopリクエストを受け付けたか否か?
	private long startTime;
	private long endTime;
	private int browserCount;
	private int requestCount;
	private int loopCount;
	private int loop;
	private long thinkingTime=0;
	
	private int idleConnection;/* 利用しない回線 */
	private long idleMemory;/* 利用しないメモリ */
	
	private int loopUnit;
	private int runnningBrowserCount;
	private List<Browser> browsers=new ArrayList<Browser>();
	private boolean isAccesslog=false;//TODO 外からもらう必要あり
	private Scenario nextScenario=null;
	
	private int scenarioCount=1;//全シナリオ数
	private int scenarioIndex=0;//自分は何番目のシナリオか？
	
	private Performance masterPerformance;
	private Map<String,Performance> requestPerformances=new HashMap<String,Performance>();
	private JSONObject stat=new JSONObject();
	private LinkPeer peer;
	
	private Random random=new Random();
	
	public static String cancelScenario(String chId){
		String name=null;
		/*
		synchronized(chIdScenarioMap){
			Scenario scenario=chIdScenarioMap.get(chId);
			if(scenario!=null){
				name=scenario.getName();
				logger.info("cancelScenario:"+name);
				scenario.stop();
			}else{
				logger.info("cancelScenario notFound Scenario.chId:"+chId);
			}
		}
		*/
		return name;
	}
	
	//TODO 幅を指定可能にする,主にconnectが集中しないようにするため
//	private double dispersion=1000;
	private long calcThinkingtime(){
		/* randは、0.0-1.0の値 */
		double rand=random.nextDouble();
		/* thinkingtimeは、0-thinkingTimeの値をランダムに採る */
		return (long)((double)thinkingTime*rand);
	}
	
	//chidに通知,100単位 or 時間単位 ,scenarioIndex/scenarioCount,loop/loopCount,runnningBrowserCount/browserCount,メモリ使用量,
	private void broadcast(boolean isComplete){
		if(loop!=0 && loop!=loopCount && (loop%loopUnit)!=0 && !isComplete){
			return;
		}
		if(loop==loopCount && runnningBrowserCount!=0 && (runnningBrowserCount%10)!=0 && runnningBrowserCount>10 && !isComplete){
			return;
		}
		Runtime runtime=Runtime.getRuntime();
		stat.element("freeMemory", runtime.freeMemory());
		stat.element("maxMemory", runtime.maxMemory());
		stat.element("loop", loop);
		stat.element("runnningBrowserCount", runnningBrowserCount);
		stat.element("kind", "stressProgress");
		stat.element("currentTime", System.currentTimeMillis());
		stat.element("isComplete", isComplete);
		peer.message(stat);
		/*
		PaManager paManager=config.getAdminPaManager();
		paManager.publish(PaAdmin.QNAME, PaAdmin.SUBNAME_PERF,stat);
		
		QueueManager queueManager=QueueManager.getInstance();
		if(isComplete){
			stat
			queueManager.complete(chId, stat);
		}else{
			queueManager.publish(chId, stat);
		}
		*/
	}
	
	/**
	 * 複数のScenarioを連続的に動作させるためのrun
	 * @param accessLogs
	 * @param stresses
	 * @param chId
	 * @return
	 */
	public static Scenario run(AccessLog[] accessLogs,JSONArray stresses,LinkPeer peer){
		int n=stresses.size();
		Scenario topScenario=null;
		Scenario lastScenario=null;
		for(int i=0;i<n;i++){
			Scenario scenario=(Scenario)PoolManager.getInstance(Scenario.class);
			scenario.scenarioCount=n;
			scenario.scenarioIndex=i;
			if(lastScenario!=null){
				lastScenario.nextScenario=scenario;
			}else{
				topScenario=scenario;
			}
			JSONObject stress=stresses.getJSONObject(i);
			String name=stress.getString("name");
			int browserCount=stress.getInt("browserCount");
			int loopCount=stress.getInt("loopCount");
			boolean isCallerKeepAlive=stress.optBoolean("isCallerKeepAlive",false);
			long thinkingTime=stress.optLong("thinkingTime",0);
			boolean isAccessLog=stress.optBoolean("isAccessLog",false);
			boolean isResponseHeaderTrace=stress.optBoolean("isResponseHeaderTrace",false);
			boolean isResponseBodyTrace=stress.optBoolean("isResponseBodyTrace",false);
			if(scenario.setup(accessLogs,name,browserCount,loopCount,isCallerKeepAlive,thinkingTime,isAccessLog,isResponseHeaderTrace,isResponseBodyTrace,peer)==false){
				//rollback処理
				scenario=topScenario;
				while(scenario!=null){
					lastScenario=scenario.nextScenario;
					scenario.unref(true);
					scenario=lastScenario;
				}
				return null;
			}
			lastScenario=scenario;
		}
		topScenario.start();
		return topScenario;
	}
	
	public static Scenario run(
			AccessLog[] accessLogs,String name,int browserCount,int loopCount,
			boolean isCallerKeepAlive,long thinkingTime,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace,
			LinkPeer peer){
		Scenario scenario=(Scenario)PoolManager.getInstance(Scenario.class);
		if(scenario.setup(accessLogs,name,browserCount,loopCount,isCallerKeepAlive,thinkingTime,isAccessLog,isResponseHeaderTrace,isResponseBodyTrace,peer)){
			scenario.start();
			return scenario;
		}
		scenario.unref(true);
		return null;
	}
	
	/**
	 * 
	 * @param browserCount
	 * @param loopCount
	 * @param requests リクエストAccessLog
	 */
	public boolean setup(
			AccessLog[] accessLogs,String name,int browserCount,int loopCount,
			boolean isCallerkeepAlive,long thinkingTime,boolean isAccessLog,
			boolean isResponseHeaderTrace,boolean isResponseBodyTrace,LinkPeer peer){
		peer.ref();
		this.peer=peer;
		this.name=name;
		this.browserCount=browserCount;
		this.requestCount=accessLogs.length;
		this.thinkingTime=thinkingTime;
		setLoopCont(loopCount);
		this.isAccesslog=isAccessLog;
		if(!isAccessLog){//accessLogを採取しないのであればtraceは無意味
			isResponseHeaderTrace=isResponseBodyTrace=false;
		}
		Browser browser=Browser.create(this,accessLogs, 
				isCallerkeepAlive,isResponseHeaderTrace,isResponseBodyTrace);
		if(browser==null){
			return false;
		}
		browser.setName(name+":0");
		browsers.add(browser);
		for(int i=1;i<browserCount;i++){
			Browser b=browser.dup();
			b.setName(name+":"+i);
			browsers.add(b);
		}
		masterPerformance=null;
		requestPerformances.clear();
		random.setSeed((long)this.loopCount);//同一stress定義から同じ乱数を生成するため
		return true;
	}
	
	public String getName() {
		return name;
	}
	
	/**
	 * 
	 * @param browserCount
	 * @param loopCount
	 * @param requests リクエストURL
	 * 
	 */
	public void setup(URL[] urls,String name,int browserCount,int loopCount){
		this.name=name;
		this.browserCount=browserCount;
		setLoopCont(loopCount);
	}
	
	public void setLoopCont(int loopCount){
		int loopUnit=loopCount/10;
		if(loopUnit<=0){
			loopUnit=1;
		}else if(loopUnit>100){
			loopUnit=100;
		}
		this.loopCount=loopCount;
		this.loopUnit=loopUnit;
	}
	
	private synchronized boolean startBrowserIfNeed(Browser browser){
		if(!isReceiveStop && loopCount>loop){
			broadcast(false);
			//TODO browser行方不明問題あり,"...favicon.ico HTTP/1.1" null 0 125#123,-,H,null,null,15,15,0,0,-1"こんな感じに記録される
			loop++;
			if(thinkingTime==0){
				browser.start(null);
			}else{
				long delay=calcThinkingtime();
				browser.startDelay(delay);
			}
			return true;
		}
		browsers.remove(browser);
		browser.cleanup();
		browser.unref();
		runnningBrowserCount--;
		if(runnningBrowserCount==0){
			endTime=System.currentTimeMillis();
			isProcessing=false;
			notify();//Scenarioを作成して終了をwaitで待っている可能性がある
			logger.info("###Scenario end.name:" +name + " time:"+(endTime-startTime));
			if(masterPerformance!=null){
				masterPerformance.insert();
			}
			for(Performance performance:requestPerformances.values()){
				performance.insert();
			}
			if(!isReceiveStop && nextScenario!=null){//次のSceinarioを実行
				broadcast(false);
				nextScenario.start();
			}else{
				broadcast(true);
			}
//			unref();//このSceinarioは終了
		}else{
			broadcast(false);
		}
		return false;
	}
	
	public synchronized void start(){
		System.gc();
		loop=0;
		startTime=System.currentTimeMillis();
		stat.element("name", name);
		stat.element("startTime", startTime);
		stat.element("scenarioCount", scenarioCount);
		stat.element("scenarioIndex", scenarioIndex);
		stat.element("loopCount", loopCount);
		stat.element("browserCount", browserCount);
		stat.element("currentTime", startTime);
		isProcessing=true;
		runnningBrowserCount=browsers.size();
		
		logger.info("###Scenario start.name:" +name);
		broadcast(false);//開始時のbroadcast
		Object[] browserArray=browsers.toArray();//ConcurrentModificationException対策
		for(Object browser:browserArray){
			startBrowserIfNeed((Browser)browser);
		}
	}
	
	public void stop(){
		isReceiveStop=true;
		Object[] objs=browsers.toArray();
		for(Object browser:objs){
			((Browser)browser).asyncStop();
		}
		if(nextScenario!=null){
			nextScenario.stop();
		}
	}
	
	public void onBrowserEnd(Browser browser){
		if(logger.isDebugEnabled())logger.debug("#onBrowserEnd runnningBrowserCount:"+runnningBrowserCount);
		startBrowserIfNeed(browser);
	}
	
	public void onRequest(AccessLog accessLog){
		if(logger.isDebugEnabled())logger.debug("#onRequest runnningBrowserCount:"+runnningBrowserCount);
		String requestKey=accessLog.getRequestHeaderDigest()+accessLog.getRequestBodyDigest()+accessLog.getStatusCode();
		Performance requestPerformance=null;
		synchronized(requestPerformances){
			if(masterPerformance==null){
				masterPerformance=new Performance();
				//init時にaccessLogの初回加算(add相当)を実施する
				masterPerformance.init(true,name,browserCount,requestCount,loopCount,thinkingTime,accessLog);
			}else{
				masterPerformance.add(accessLog);
			}
			requestPerformance=requestPerformances.get(requestKey);
			if(requestPerformance==null){
				requestPerformance=new Performance();
				requestPerformance.init(false,name,browserCount,requestCount,loopCount,thinkingTime,accessLog);
				requestPerformances.put(requestKey, requestPerformance);
			}else{
				//init時にaccessLogの初回加算(add相当)を実施する
				requestPerformance.add(accessLog);
			}
		}
		
		//記録する場合
		//accessLog.log();
		if(isAccesslog){
			accessLog.setPersist(true);
		}
		accessLog.decTrace();
	}

	public int getRunnningBrowserCount() {
		return runnningBrowserCount;
	}

	@Override
	public void recycle() {
		if(peer!=null){
			peer.unref();
			peer=null;
		}
		if(nextScenario!=null){
			nextScenario.unref();
			nextScenario=null;
		}
		isReceiveStop=false;
		isProcessing=false;
		scenarioCount=1;
		scenarioIndex=0;
	}
}
