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
	private String chId;
	private int count;
	private boolean isStop=false;
	private QueueManager queueManager;
	
	public static ConnectChecker start(int count,long timeout,String chId){
		ConnectChecker checker=(ConnectChecker)PoolManager.getInstance(ConnectChecker.class);
		checker.init(count, chId);
		checker.run();
		TimerManager.setTimeout(timeout, checker, null);
		return checker;
	}
	
	public void init(int count,String chId){
		this.count=count;
		this.chId=chId;
		this.isStop=false;
		queueManager=QueueManager.getInstance();
	}
	
	private void addWsClient(){
		WsClientHandler wsClientHandler=WsClientHandler.create(false,config.getSelfDomain(),config.getInt(Config.SELF_PORT));
		wsClientHandler.ref();
		synchronized(this){
			if(isStop){
				return;
			}
			if(clients.size()>=count){
				wsClientHandler.unref(true);
				return;
			}
			wsClientHandler.startRequest(this, wsClientHandler, 10000, "/connect","connect","http://test");
			clients.add(wsClientHandler);
		}
	}
	
	private synchronized void delWsClient(WsClientHandler wsClientHandler){
		clients.remove(wsClientHandler);
		wsClientHandler.unref();
		if(clients.size()!=0){
			return;
		}
		eventObj.put("connectCount", 0);
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

	private JSONObject eventObj=new JSONObject();
	public void onTimer(Object userContext) {
		int curCount;
		synchronized(this){
			curCount=clients.size();
		}
		eventObj.put("connectCount", curCount);
		queueManager.publish(chId, eventObj);
		stop();
	}

	@Override
	public void onWcClose(Object userContext, int stat) {
		WsClientHandler wsClientHandler=(WsClientHandler)userContext;
		delWsClient(wsClientHandler);
	}

	@Override
	public void onWcConnected(Object userContext) {
	}

	@Override
	public void onWcFailure(Object userContext, int stat, Throwable t) {
		logger.debug("#wcFailure",t);
		WsClientHandler wsClientHandler=(WsClientHandler)userContext;
		delWsClient(wsClientHandler);
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
