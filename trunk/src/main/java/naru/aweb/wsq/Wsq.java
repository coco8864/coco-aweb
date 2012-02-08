package naru.aweb.wsq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import naru.async.Timer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class Wsq extends PoolBase implements WsqController,Timer{
	
	/* stress,connection,定期的に情報を発信 */
	public static Wsq createWsq(Object wsqlet,String wsqName){
		Wsq wsq=(Wsq)PoolManager.getInstance(Wsq.class);
		if(wsqName==null){
			
		}else{
			wsq.wsqName=wsqName;
		}
		wsq.wsqlet=(Wsqlet)wsqlet;
		wsq.wsqlet.onStartQueue(wsq.wsqName,wsq);
		wsq.onTimer(null);
		return wsq;
	}
	
	private Map<WsqPeer,SubscribeInfo> subscribeInfos=new HashMap<WsqPeer,SubscribeInfo>();
	private String wsqName;
	private Wsqlet wsqlet;
	private long timerId=-1;
	
	private static class SubscribeInfo{
		WsqPeer from;
		WsqHandler handler;
		JSONArray msgs;
		long lastAccess;
		void setMessage(JSONObject msg){
			if(handler!=null){
				handler.publish(msg);
				return;
			}else if(msgs==null){
				msgs=new JSONArray();
			}
			msgs.add(msg);
		}
		void setHandler(WsqHandler handler){
			if(handler!=null){
				handler.ref();
				if(msgs!=null){
					handler.publish(msgs);
				}
				msgs=null;
			}
			WsqHandler orgHandler=handler;
			this.handler=handler;
			if(orgHandler!=null){
				orgHandler.unref();
			}
		}
	}

	public String getWsqName(){
		return wsqName;
	}

	public boolean setHandler(WsqPeer from,WsqHandler handler){
		SubscribeInfo info=subscribeInfos.get(from);
		if(info==null){
			return false;
		}
		synchronized(info){
			info.setHandler(handler);
		}
		return true;
	}
	
	public boolean subscribe(WsqPeer from){
		SubscribeInfo info=new SubscribeInfo();
		info.from=from;
		info.lastAccess=System.currentTimeMillis();
		synchronized(subscribeInfos){
			if(subscribeInfos.get(from)!=null){
				return false;//既にsubscribe中
			}
			subscribeInfos.put(from, info);
		}
		wsqlet.onSubscribe(info.from);
		return true;
	}
	
	public JSONArray getMessage(WsqPeer from){
		SubscribeInfo info=subscribeInfos.get(from);
		if(info==null){
			return null;
		}
		synchronized(info){
			JSONArray orgMsgs=info.msgs;
			info.msgs=null;
			return orgMsgs;
		}
	}
	
	public void onTimer(Object userContext) {
		long interval=wsqlet.onWatch();
		if(interval>0){
			timerId=TimerManager.setTimeout(interval,this,null);
		}else{
			timerId=-1;
		}
	}

	/*---以降watcher側からのリクエスト ---*/
	@Override
	public void endQueue() {
		if(timerId!=-1){
			TimerManager.clearTimeout(timerId);
			timerId=-1;
		}
		synchronized(subscribeInfos){
			for(WsqPeer peer:subscribeInfos.keySet()){
				unsubscribe(peer);
			}
		}
		wsqlet.onEndQueue();
	}
	

	@Override
	public long getLastAccess(WsqPeer peer) {
		SubscribeInfo info=subscribeInfos.get(peer);
		if(info==null){
			return -1;
		}
		return info.lastAccess;
	}

	@Override
	public int message(Object message, List<WsqPeer> peers,List<WsqPeer> dnyPeers) {
		return 0;
	}

	@Override
	public int message(Object message, List<WsqPeer> peers, WsqPeer dnyPeer) {
		return 0;
	}

	@Override
	public int message(Object message, List<WsqPeer> peers) {
		return 0;
	}

	@Override
	public int message(Object message, WsqPeer peer) {
		return 0;
	}

	@Override
	public int message(Object message) {
		return 0;
	}

	/*
	 * 以下２箇所から呼び出される
	 * 1)WsqWatcher(サーバ処理としてunsubscribe)
	 * 2)WsqManager(回線からunsubscribe)
	 * @see naru.aweb.wsq.WsqContext#unsubscribe(java.lang.String)
	 */
	@Override
	public boolean unsubscribe(WsqPeer peer) {
		SubscribeInfo info=null;
		synchronized(subscribeInfos){
			info=subscribeInfos.remove(peer);
		}
		if(info==null){
			return false;
		}
		synchronized(info){
			wsqlet.onUnsubscribe(info.from);
			info.setHandler(null);
		}
		return true;
	}
}
