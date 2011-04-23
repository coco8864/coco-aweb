package naru.aweb.robot;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Performance;
import naru.aweb.queue.QueueManager;


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
	private int runnningBrowserCount;
	private List<Browser> browsers=new ArrayList<Browser>();
	private boolean isAccesslog=false;//TODO 外からもらう必要あり
	
	private Performance masterPerformance;
	private Map<String,Performance> requestPerformances=new HashMap<String,Performance>();
	private Map<String,Performance> requestStatusCodePerformances;
	
	public static boolean run(String name,int browserCount,int loopCount,AccessLog[] accessLogs,
			boolean isCallerKeepAlive,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace,
			String chId){
		Scenario scenario=(Scenario)PoolManager.getInstance(Scenario.class);
		if(scenario.setup(name,browserCount,loopCount,accessLogs,isCallerKeepAlive,isAccessLog,isResponseHeaderTrace,isResponseBodyTrace)){
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
	public boolean setup(String name,int browserCount,int loopCount,AccessLog[] accessLogs,
			boolean isCallerkeepAlive,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		this.name=name;
		this.browserCount=browserCount;
		this.requestCount=accessLogs.length;
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
	public void setup(String name,int browserCount,int loopCount,URL[] urls){
		this.name=name;
		this.browserCount=browserCount;
		this.loopCount=loopCount;
		
	}
	
	private synchronized boolean startBrowserIfNeed(Browser browser){
		if(loopCount>loop){
			loop++;
			browser.start();
			return true;
		}
		runnningBrowserCount--;
		if(runnningBrowserCount==0){
			endTime=System.currentTimeMillis();
			isProcessing=false;
			notify();
			logger.info("###Scenario end.name:" +name + " time:"+(endTime-startTime));
			masterPerformance.insert();
			for(Performance performance:requestPerformances.values()){
				performance.insert();
			}
			if(chId!=null){
				QueueManager queueManager=QueueManager.getInstance();
				queueManager.publish(chId, "Scenario name:"+name +" end.time:"+(endTime-startTime));
			}
			browsers.clear();
			unref();//このSceinarioは終了
		}
		return false;
	}
	public synchronized void start(){
		start(null);
	}
	private String chId;
	
	public synchronized void start(String chId){
		logger.debug("#start");
		loop=0;
		this.chId=chId;
		startTime=System.currentTimeMillis();
		isProcessing=true;
		runnningBrowserCount=browsers.size();
		for(Browser browser:browsers){
			if(startBrowserIfNeed(browser)==false){
				browser.cleanup();
				browser.unref();
			}
		}
	}
	
	public void stop(){
		for(Browser browser:browsers){
			browser.asyncStop();
		}
	}
	
	public void onBrowserEnd(Browser browser){
		logger.debug("#onBrowserEnd runnningBrowserCount:"+runnningBrowserCount);
		if(startBrowserIfNeed(browser)==false){
			browser.cleanup();
			browser.unref();
		}
	}
	
	public void onRequest(AccessLog accessLog){
		logger.debug("#onRequest runnningBrowserCount:"+runnningBrowserCount);
		String requestKey=accessLog.getRequestHeaderDigest()+accessLog.getRequestBodyDigest()+accessLog.getStatusCode();
		Performance requestPerformance=null;
		synchronized(requestPerformances){
			if(masterPerformance==null){
				masterPerformance=new Performance();
				masterPerformance.init(true,name,browserCount,requestCount,loopCount, accessLog);
			}else{
				masterPerformance.add(accessLog);
			}
			requestPerformance=requestPerformances.get(requestKey);
			if(requestPerformance==null){
				requestPerformance=new Performance();
				requestPerformance.init(false,name,browserCount,requestCount,loopCount,accessLog);
				requestPerformances.put(requestKey, requestPerformance);
			}else{
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
	}
	

}
