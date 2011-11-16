package naru.aweb.robot;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WsClient;
import naru.aweb.http.WsClientHandler;
import naru.aweb.queue.QueueManager;
import net.sf.json.JSONObject;

/* そのサーバでいくつconnectionが張れるかをチェックする */
/* 自サーバの/connection WebScoketに接続する */
public class ConnectChecker extends PoolBase implements Timer,WsClient{
	private static Logger logger=Logger.getLogger(ConnectChecker.class);
	private static Config config=Config.getConfig();
	private List<WsClientHandler> clients=new ArrayList<WsClientHandler>();
	private int failCount;
	private String chId;
	private int count;
	private int maxFailCount;
	private boolean isStop;
	private QueueManager queueManager;
	private long timerId;
	private long startTime;
	private JSONObject eventObj=new JSONObject();
	
	public static ConnectChecker start(int count,int maxFailCount,long timeout,String chId){
		ConnectChecker checker=(ConnectChecker)PoolManager.getInstance(ConnectChecker.class);
		checker.init(count, maxFailCount,chId);
		checker.startTime=System.currentTimeMillis();
		if(timeout>0){
			checker.timerId=TimerManager.setTimeout(timeout, checker, null);
		}else{
			checker.timerId=TimerManager.INVALID_ID;
		}
		checker.run();
		return checker;
	}
	
	public void init(int count,int maxFailCount,String chId){
		this.count=count;
		this.maxFailCount=maxFailCount;
		this.failCount=0;
		this.chId=chId;
		this.isStop=false;
		queueManager=QueueManager.getInstance();
	}
	
	private static Runtime runtime=Runtime.getRuntime();
	private void publish(int count,int failCount){
		eventObj.put("time",(System.currentTimeMillis()-startTime));
		eventObj.put("connectCount", count);
		eventObj.put("failCount", failCount);
		eventObj.put("useMemory", (runtime.totalMemory()-runtime.freeMemory()));
		queueManager.publish(chId, eventObj);
	}
	
	private synchronized void addWsClient(){
		if(isStop){
			return;
		}
		int size=clients.size();
		if((size%100)==0||size==count){
			publish(size,failCount);
		}
		if(size>=count){
			if(timerId==TimerManager.INVALID_ID){
				stop();
			}
			return;
		}
		WsClientHandler wsClientHandler=WsClientHandler.create(false,config.getSelfDomain(),config.getInt(Config.SELF_PORT));
		wsClientHandler.ref();
		wsClientHandler.startRequest(this, wsClientHandler, 10000, "/connect","connect",config.getAdminUrl());
		clients.add(wsClientHandler);
	}
	
	private synchronized void delWsClient(WsClientHandler wsClientHandler,boolean isFail){
		clients.remove(wsClientHandler);
		wsClientHandler.unref();
		int size=clients.size();
		if(isFail){
			failCount++;
			if(failCount>=maxFailCount){
				publish(size,failCount);
				stop();
			}
		}
		if(size!=0){
			if(isFail && size<count){
				addWsClient();
			}
			return;
		}
		publish(0,failCount);
		if(isStop==false){
			return;
		}
		queueManager.complete(chId, eventObj);
		unref();
	}
	
//	private Object timerObj;
	
	private void run(){
		addWsClient();
	}
	
	public void stop(){
		synchronized(this){
			if(isStop){
				logger.warn("aleady stoped");
				return;
			}
			isStop=true;
			for(WsClientHandler client:clients){
				client.postMessage("doClose");
			}
		}
	}
	
	public void postAll(){
		String now=Long.toString(System.currentTimeMillis());
		synchronized(this){
			if(isStop){
				logger.warn("ConnectChecker was stoped");
				return;
			}
			isStop=true;
			for(WsClientHandler client:clients){
				client.postMessage(now);
			}
		}
	}

	public void onTimer(Object userContext) {
		timerId=TimerManager.INVALID_ID;
		int curCount;
		synchronized(this){
			curCount=clients.size();
		}
		publish(curCount,failCount);
		stop();
	}

	@Override
	public void onWcClose(Object userContext, int stat) {
		WsClientHandler wsClientHandler=(WsClientHandler)userContext;
		delWsClient(wsClientHandler,false);
	}

	@Override
	public void onWcConnected(Object userContext) {
	}

	@Override
	public void onWcFailure(Object userContext, int stat, Throwable t) {
		logger.info("#wcFailure",t);
		WsClientHandler wsClientHandler=(WsClientHandler)userContext;
		delWsClient(wsClientHandler,true);
	}

	@Override
	public void onWcHandshaked(Object userContext, String subprotocol) {
	}

	@Override
	public void onWcMessage(Object userContext, String message) {
		if("OK".equals(message)){
			addWsClient();
			return;
		}
		long sendTime=Long.parseLong(message);
		long now=System.currentTimeMillis();
	}

	@Override
	public void onWcMessage(Object userContext, ByteBuffer[] message) {
	}

	@Override
	public void onWcProxyConnected(Object userContext) {
	}

	@Override
	public void onWcResponseHeader(Object userContext,HeaderParser responseHeader) {
	}

	@Override
	public void onWcSslHandshaked(Object userContext) {
	}

	@Override
	public void onWcWrittenHeader(Object userContext) {
	}

}
