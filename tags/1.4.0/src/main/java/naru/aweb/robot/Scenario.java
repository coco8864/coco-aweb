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
import naru.aweb.config.Performance;
import naru.aweb.queue.QueueManager;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class Scenario extends PoolBase{
	private static Logger logger = Logger.getLogger(Scenario.class);
	private static Map<String,Scenario> chIdScenarioMap=new HashMap<String,Scenario>();//���󑖍s����Scenario��ێ��@cancel�Ή�
	
	private String name;
	private boolean isProcessing;
	private boolean isReceiveStop;//stop���N�G�X�g���󂯕t�������ۂ�?
	private long startTime;
	private long endTime;
	private int browserCount;
	private int requestCount;
	private int loopCount;
	private int loop;
	private long thinkingTime=0;
	
	private int idleConnection;/* ���p���Ȃ���� */
	private long idleMemory;/* ���p���Ȃ������� */
	
	private int loopUnit;
	private int runnningBrowserCount;
	private List<Browser> browsers=new ArrayList<Browser>();
	private boolean isAccesslog=false;//TODO �O������炤�K�v����
	private Scenario nextScenario=null;
	
	private int scenarioCount=1;//�S�V�i���I��
	private int scenarioIndex=0;//�����͉��Ԗڂ̃V�i���I���H
	
	private Performance masterPerformance;
	private Map<String,Performance> requestPerformances=new HashMap<String,Performance>();
//	private Map<String,Performance> requestStatusCodePerformances;
	private JSONObject stat=new JSONObject();
	
	private Random random=new Random();
	
	public static String cancelScenario(String chId){
		String name=null;
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
		return name;
	}
	
	//TODO �����w��\�ɂ���,���connect���W�����Ȃ��悤�ɂ��邽��
//	private double dispersion=1000;
	private long calcThinkingtime(){
		/* rand�́A0.0-1.0�̒l */
		double rand=random.nextDouble();
		/* thinkingtime�́A0-thinkingTime�̒l�������_���ɍ̂� */
		return (long)((double)thinkingTime*rand);
	}
	
	//chid�ɒʒm,100�P�� or ���ԒP�� ,scenarioIndex/scenarioCount,loop/loopCount,runnningBrowserCount/browserCount,�������g�p��,
	private void broadcast(String chId,boolean isComplete){
		if(chId==null){
			return;
		}
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
		
		QueueManager queueManager=QueueManager.getInstance();
		if(isComplete){
			queueManager.complete(chId, stat);
		}else{
			queueManager.publish(chId, stat);
		}
	}
	
	/**
	 * ������Scenario��A���I�ɓ��삳���邽�߂�run
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
				//rollback����
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
		scenario.unref(true);
		return false;
	}
	
	/**
	 * 
	 * @param browserCount
	 * @param loopCount
	 * @param requests ���N�G�X�gAccessLog
	 */
	public boolean setup(AccessLog[] accessLogs,String name,int browserCount,int loopCount,
			boolean isCallerkeepAlive,
			long thinkingTime,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		this.name=name;
		this.browserCount=browserCount;
		this.requestCount=accessLogs.length;
		this.thinkingTime=thinkingTime;
		setLoopCont(loopCount);
		this.isAccesslog=isAccessLog;
		if(!isAccessLog){//accessLog���̎悵�Ȃ��̂ł����trace�͖��Ӗ�
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
		random.setSeed((long)this.loopCount);//����stress��`���瓯�������𐶐����邽��
		return true;
	}
	
	public String getName() {
		return name;
	}
	
	/**
	 * 
	 * @param browserCount
	 * @param loopCount
	 * @param requests ���N�G�X�gURL
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
			broadcast(chId,false);
			//TODO browser�s���s����肠��,"...favicon.ico HTTP/1.1" null 0 125#123,-,H,null,null,15,15,0,0,-1"����Ȋ����ɋL�^�����
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
			notify();//Scenario���쐬���ďI����wait�ő҂��Ă���\��������
			logger.info("###Scenario end.name:" +name + " time:"+(endTime-startTime));
			if(masterPerformance!=null){
				masterPerformance.insert();
			}
			for(Performance performance:requestPerformances.values()){
				performance.insert();
			}
			if(!isReceiveStop && nextScenario!=null){//����Sceinario�����s
				broadcast(chId,false);
				nextScenario.start(chId);
			}else{
				broadcast(chId,true);
				synchronized(chIdScenarioMap){
					chIdScenarioMap.remove(chId);
				}
			}
			unref();//����Sceinario�͏I��
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
		synchronized(chIdScenarioMap){
			chIdScenarioMap.put(chId,this);
		}
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
		
		logger.info("###Scenario start.name:" +name);
		broadcast(chId,false);//�J�n����broadcast
		Object[] browserArray=browsers.toArray();//ConcurrentModificationException�΍�
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
				//init����accessLog�̏�����Z(add����)�����{����
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
				//init����accessLog�̏�����Z(add����)�����{����
				requestPerformance.add(accessLog);
			}
		}
		
		//�L�^����ꍇ
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
		isReceiveStop=false;
		isProcessing=false;
		scenarioCount=1;
		scenarioIndex=0;
	}
}
