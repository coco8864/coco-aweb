package naru.aweb.robot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.cache.Cache;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.aweb.handler.ws.WsHybiFrame;
import naru.aweb.http.WsClient;
import naru.aweb.http.WsClientHandler;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ServerParser;
import net.sf.json.JSONObject;

/* そのサーバでいくつconnectionが張れるかをチェックする */
/* 自サーバの/connection WebScoketに接続する */
public class ConnectChecker implements Timer,WsClient{
	private static Logger logger=Logger.getLogger(ConnectChecker.class);
	private static Config config=Config.getConfig();
	private static ConnectChecker instance=new ConnectChecker();
	
	private enum Stat{
		READY,
		STARTING,
		INC,//connection増加中
		KEEP,//保持中
		DEC,//connection切断中
	}
	
	private List<WsClientHandler> clients=new ArrayList<WsClientHandler>();
	private int failCount;
	private int postCount;//websocketクライアントとしてデータを送信した回数
	private int messageCount;//websocketクライアントとしてデータを受信した回数
	private LinkPeer peer;
	private int count;
	private int maxFailCount;
	private Stat stat=Stat.READY;
	private boolean stopForce=false;
	private long timerId;
	private long startTime;
	
	public static boolean start(String url,String subprotocol,String origin,int count,int maxFailCount,long timeout,LinkPeer peer){
		if( instance.init(url,subprotocol,origin,count, maxFailCount,peer)==false ){
			return false;
		}
		instance.run(timeout);
		return true;
	}
	
	public static void sendTest(int count){
		instance.send(count);
	}
	
	public static void end(boolean force){
		instance.stop(force);
	}
	
	private boolean isSsl=false;
	private String server=config.getSelfDomain();
	private int port=config.getInt(Config.SELF_PORT);
	private String uri="/connect";
	private String subprotocol="connect";
	private String origin=config.getAdminUrl();
	
	private synchronized boolean init(String url,String subprotocol,String origin,int count,int maxFailCount,LinkPeer peer){
		if(stat!=Stat.READY){
			logger.error("aleady start."+stat);
			return false;
		}
		if(url==null||"".equals(url)){
			this.isSsl=false;
			this.server=config.getSelfDomain();
			this.port=config.getInt(Config.SELF_PORT);
			this.uri="/connect";
		}else{
			StringBuilder schemeSb=new StringBuilder();
			StringBuilder pathSb=new StringBuilder();
			ServerParser server=ServerParser.parseUrl(url, schemeSb, pathSb);
			this.server=server.getHost();
			this.port=server.getPort();
			server.unref(true);
			String scheme=schemeSb.toString();
			if(scheme.equals("wss")){
				this.isSsl=true;
			}else if(scheme.equals("ws")){
				this.isSsl=false;
			}else{
				logger.error("unkown protocol."+schemeSb);
				return false;
			}
			this.uri=pathSb.toString();
		}
		if(subprotocol==null||"".equals(subprotocol)){
			this.subprotocol="connect";
		}else{
			this.subprotocol=subprotocol;
		}
		if(origin==null||"".equals(origin)){
			this.origin=config.getAdminUrl();
		}else{
			this.origin=origin;
		}
		
		if(this.peer!=null){
			this.peer.unref();
		}
		if(peer!=null){
			peer.ref();
		}
		this.peer=peer;
		this.count=count;
		this.maxFailCount=maxFailCount;
		this.failCount=0;
		this.postCount=this.messageCount=0;
		if(count>0){
			stat=Stat.INC;
		}else{
			stat=Stat.KEEP;
		}
		return true;
	}
	
	private static Runtime runtime=Runtime.getRuntime();
	private void publish(int count,boolean isComplete){
		JSONObject eventObj=new JSONObject();
		eventObj.put("kind","checkConnectProgress");
		eventObj.put("time",(System.currentTimeMillis()-startTime));
		eventObj.put("connectCount", count);
		eventObj.put("postCount", postCount);
		eventObj.put("messageCount", messageCount);
		eventObj.put("failCount", failCount);
		eventObj.put("useMemory", (runtime.totalMemory()-runtime.freeMemory()));
		eventObj.put("stat", stat);
		peer.message(eventObj);
		if(isComplete){
			peer.unref();
			peer=null;
		}
	}
	
	private synchronized void addWsClient(){
		if(stat!=Stat.INC){
			return;
		}
		int size=clients.size();
		if((size%100)==0||size==count){
			publish(size,false);
		}
		if(size>=count){
			stat=Stat.KEEP;
			return;
		}
		/*
		boolean isSsl=false;
		String server=config.getSelfDomain();
		int port=config.getInt(Config.SELF_PORT);
		String uri="/connect";
		String subprotocol="connect";
		String origin=config.getAdminUrl();
		*/
		
		WsClientHandler wsClientHandler=WsClientHandler.create(isSsl,server,port);
		wsClientHandler.ref();
		wsClientHandler.startRequest(this, wsClientHandler, 10000, uri,subprotocol,origin);
		clients.add(wsClientHandler);
	}
	
	private synchronized void delWsClient(WsClientHandler wsClientHandler,boolean isFail){
		clients.remove(wsClientHandler);
		wsClientHandler.unref();
		int size=clients.size();
		if(isFail){
			failCount++;
			if(failCount>=maxFailCount){
				publish(size,false);
				stop(true);
				return;
			}
		}
		switch(stat){
		case INC:
			addWsClient();
			break;
		case DEC:
			if((size%100)==0){
				publish(size,false);
			}
			sendStop();
			break;
		}
	}
	
	private void sendStop(){
		Iterator<WsClientHandler> itr=clients.iterator();
		if(itr.hasNext()){
			WsClientHandler client=itr.next();
			if(stopForce){
				client.doClose(WsHybiFrame.CLOSE_NORMAL,"OK");
			}else{
				client.postMessage("doClose");
			}
		}else{//clientsが空
			stat=Stat.READY;
			publish(0,true);
		}
	}
	
	private void run(long timeout){
		startTime=System.currentTimeMillis();
		if(timeout>0){
			timerId=TimerManager.setTimeout(timeout, this, null);
		}else{
			timerId=TimerManager.INVALID_ID;
		}
		addWsClient();
	}
	
	private synchronized void send(int count){
		if(stat!=Stat.KEEP){
			logger.warn("can't send not keep");
			return;
		}
		logger.info("ws post start");
		startTime=System.currentTimeMillis();
		publish(clients.size(),false);
		Iterator<WsClientHandler> itr=clients.iterator();
		while(itr.hasNext()){
			WsClientHandler client=itr.next();
			count--;
			postCount++;
			client.postMessage("0123456789abcdef");
			if(count<=0){
				break;
			}
		}
		logger.info("ws post end");
		publish(clients.size(),false);
	}
	
	private synchronized void stop(boolean force){
		if(stat==Stat.DEC || stat==Stat.READY){
			logger.warn("aleady stoped");
			return;
		}
		logger.info("ws disconnect start");
		startTime=System.currentTimeMillis();
		publish(clients.size(),false);
		stat=Stat.DEC;
		stopForce=force;
		sendStop();
	}
	
	/*
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
	*/

	public void onTimer(Object userContext) {
		timerId=TimerManager.INVALID_ID;
		int curCount;
		synchronized(this){
			curCount=clients.size();
		}
		publish(curCount,false);
		stop(false);
	}

	@Override
	public void onWcClose(Object userContext, int stat,short closeCode,String closeReason) {
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
		if(stat==Stat.INC){
		//if("OK".equals(message)){
			addWsClient();
			return;
		}else if(stat!=Stat.KEEP){
			return;
		}
		boolean isLast=false;
		synchronized(this){
			messageCount++;
			if(messageCount==postCount){
				isLast=true;
			}
		}
		if(isLast){
			logger.info("messageCount:"+messageCount);
			publish(clients.size(),false);
		}
	}

	@Override
	public void onWcMessage(Object userContext, Cache message) {
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
