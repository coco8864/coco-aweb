package naru.aweb.robot;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;

import naru.async.pool.Pool;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Performance;
import naru.aweb.queue.QueueManager;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;


public class Scenario extends PoolBase{
	private static Logger logger = Logger.getLogger(Scenario.class);
	
	private String name;
	private boolean isProcessing;
	private long startTime;
	private long endTime;
	private int browserCount;
	private int requestCount;
	private int loopCount;
	private int loop;
	private long thinkingTime=0;
	
	private int runnningBrowserCount;
	private List<Browser> browsers=new ArrayList<Browser>();
	private boolean isAccesslog=false;//TODO 外からもらう必要あり
	private Scenario nextScenario=null;
	
	private int scenarioCount=1;//全シナリオ数
	private int scenarioIndex=0;//自分は何番目のシナリオか？
	
	private Performance masterPerformance;
	private Map<String,Performance> requestPerformances=new HashMap<String,Performance>();
	private Map<String,Performance> requestStatusCodePerformances;
	private JSONObject stat=new JSONObject();
	
	private Random random=new Random();
	
	private long calcThinkingtime(){
		double rand=random.nextDouble()-0.5;
		rand*=1000;//前後1秒の幅を作る
		return thinkingTime+(long)rand;
	}
	
	//chidに通知,100単位 or 時間単位 ,scenarioIndex/scenarioCount,loop/loopCount,runnningBrowserCount/browserCount,メモリ使用量,
	private void broadcast(String chId,boolean isComplete){
		if(chId==null){
			return;
		}
		if(loop!=0 && loop!=loopCount && (loop%100)!=0){//TODO 100を動的に変更
			return;
		}
		if(loop==loopCount && runnningBrowserCount!=0 && (runnningBrowserCount%10)!=0 ){
			return;
		}
		Runtime runtime=Runtime.getRuntime();
		stat.element("freeMemory", runtime.freeMemory());
		stat.element("maxMemory", runtime.maxMemory());
		stat.element("loop", loop);
		stat.element("runnningBrowserCount", runnningBrowserCount);
		
		QueueManager queueManager=QueueManager.getInstance();
		if(isComplete){
			queueManager.complete(chId, stat);
		}else{
			queueManager.publish(chId, stat);
		}
	}
	
	/**
	 * 複数のScenarioを連続的に動作させるためのrun
	 * @param accessLogs
	 * @param stresses
	 * @param chId
	 * @return
	 */
	public static boolean run(AccessLog[] accessLogs,JSONArray stresses,String chId){
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
			if(scenario.setup(accessLogs,name,browserCount,loopCount,isCallerKeepAlive,thinkingTime,isAccessLog,isResponseHeaderTrace,isResponseBodyTrace)==false){
				scenario=topScenario;
				while(scenario!=null){
					lastScenario=scenario.nextScenario;
					scenario.unref(true);
					scenario=lastScenario;
				}
				return false;
			}
			lastScenario=scenario;
		}
		topScenario.start(chId);
		return true;
	}
	
	public static boolean run(AccessLog[] accessLogs,String name,int browserCount,int loopCount,
			boolean isCallerKeepAlive,
			long thinkingTime,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace,
			String chId){
		Scenario scenario=(Scenario)PoolManager.getInstance(Scenario.class);
		if(scenario.setup(accessLogs,name,browserCount,loopCount,isCallerKeepAlive,thinkingTime,isAccessLog,isResponseHeaderTrace,isResponseBodyTrace)){
			scenario.start(chId);
			return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @param browserCount
	 * @param loopCount
	 * @param requests リクエストAccessLog
	 */
	public boolean setup(AccessLog[] accessLogs,String name,int browserCount,int loopCount,
			boolean isCallerkeepAlive,
			long thinkingTime,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		this.name=name;
		this.browserCount=browserCount;
		this.requestCount=accessLogs.length;
		this.thinkingTime=thinkingTime;
		this.loopCount=loopCount;
		this.isAccesslog=isAccessLog;
		if(!isAccessLog){//accessLogを採取しないのであればtraceは無意味
			isResponseHeaderTrace=isResponseBodyTrace=false;
		}
		Browser browser=Browser.cleate(this,accessLogs, 
				isCallerkeepAlive,isResponseHeaderTrace,isResponseBodyTrace);
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
		this.loopCount=loopCount;
	}
	
	private synchronized boolean startBrowserIfNeed(Browser browser){
		if(loopCount>loop){
			broadcast(chId,false);
			//TODO browser行方不明問題あり,"...favicon.ico HTTP/1.1" null 0 125#123,-,H,null,null,15,15,0,0,-1"こんな感じに記録される
			loop++;
			if(thinkingTime==0){
				browser.start();
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
			masterPerformance.insert();
			for(Performance performance:requestPerformances.values()){
				performance.insert();
			}
			if(nextScenario!=null){//次のSceinarioを実行
				broadcast(chId,false);
				nextScenario.start(chId);
			}else{
				broadcast(chId,true);
			}
			unref();//このSceinarioは終了
		}else{
			broadcast(chId,false);
		}
		return false;
	}
	public synchronized void start(){
		start(null);
	}
	private String chId;
	
	public synchronized void start(String chId){
		System.gc();
		logger.debug("#start");
		loop=0;
		this.chId=chId;
		startTime=System.currentTimeMillis();
		stat.element("name", name);
		stat.element("startTime", startTime);
		stat.element("scenarioCount", scenarioCount);
		stat.element("scenarioIndex", scenarioIndex);
		stat.element("loopCount", loopCount);
		stat.element("browserCount", browserCount);
		isProcessing=true;
		runnningBrowserCount=browsers.size();
		
		broadcast(chId,false);//開始時のbroadcast
		for(Browser browser:browsers){
			startBrowserIfNeed(browser);
		}
	}
	
	public void stop(){
		for(Browser browser:browsers){
			browser.asyncStop();
		}
	}
	
	public void onBrowserEnd(Browser browser){
		logger.debug("#onBrowserEnd runnningBrowserCount:"+runnningBrowserCount);
		startBrowserIfNeed(browser);
	}
	
	public void onRequest(AccessLog accessLog){
		logger.debug("#onRequest runnningBrowserCount:"+runnningBrowserCount);
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
		nextScenario=null;
		scenarioCount=1;
		scenarioIndex=0;
	}
}
