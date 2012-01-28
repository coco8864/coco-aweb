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

public class Wsq extends PoolBase implements WsqContext,Timer{
	
	/* stress,connection,定期的に情報を発信 */
	public static Wsq createWsq(Object wsqWatcher,String wsqName,long interval){
		Wsq wsq=(Wsq)PoolManager.getInstance(Wsq.class);
		if(wsqName==null){
			
		}else{
			wsq.wsqName=wsqName;
		}
		wsq.watcher=(WsqWatcher)wsqWatcher;
		wsq.intervalTimer=null;
		if(interval>0){
			wsq.intervalTimer=TimerManager.setInterval(interval, wsq, null);
		}
		wsq.watcher.onStartQueue(wsq.wsqName,wsq);
		return wsq;
	}
	
	private Map<String,SubscribeInfo> subscribeInfos=new HashMap<String,SubscribeInfo>();
	private String wsqName;
	private WsqWatcher watcher;
	private Object intervalTimer;
	
	private static class SubscribeInfo{
		WsqFrom from;
		WsQueueHandler handler;
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
		
		void setHandler(WsQueueHandler handler){
			if(handler!=null){
				handler.ref();
				if(msgs!=null){
					handler.publish(msgs);
				}
				msgs=null;
			}
			WsQueueHandler orgHandler=handler;
			this.handler=handler;
			if(orgHandler!=null){
				orgHandler.unref();
			}
		}
	}

	public String getWsqName(){
		return wsqName;
	}

	public boolean setHandler(String chid,WsQueueHandler handler){
		SubscribeInfo info=subscribeInfos.get(chid);
		if(info==null){
			return false;
		}
		synchronized(info){
			info.setHandler(handler);
		}
		return true;
	}
	
	public boolean subscribe(String chid,WsqFrom from){
		SubscribeInfo info=new SubscribeInfo();
		info.from=from;
		info.lastAccess=System.currentTimeMillis();
		synchronized(subscribeInfos){
			if(subscribeInfos.get(chid)!=null){
				return false;//既にsubscribe中
			}
			subscribeInfos.put(chid, info);
		}
		watcher.onSubscribe(info.from);
		return true;
	}
	
	public JSONArray getMessage(String chid){
		SubscribeInfo info=subscribeInfos.get(chid);
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
		if(watcher.onWatch()==false){
			return;
		}
		publish(watcher);
	}

	/*---以降watcher側からのリクエスト ---*/
	@Override
	public void endQueue() {
		if(intervalTimer!=null){
			TimerManager.clearInterval(intervalTimer);
			intervalTimer=null;
		}
		synchronized(subscribeInfos){
			for(String chid:subscribeInfos.keySet()){
				unsubscribe(chid);
			}
		}
		watcher.onEndQueue();
	}
	
	@Override
	public long getLastAccess(String chid) {
		SubscribeInfo info=subscribeInfos.get(chid);
		if(info==null){
			return -1;
		}
		return info.lastAccess;
	}
	
	@Override
	public int publish(Object message) {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public int publish(Object message, List<String> chids) {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public int publish(Object message, String dnyChid) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	/*
	 * 以下２箇所から呼び出される
	 * 1)WsqWatcher(サーバ処理としてunsubscribe)
	 * 2)WsqManager(回線からunsubscribe)
	 * @see naru.aweb.wsq.WsqContext#unsubscribe(java.lang.String)
	 */
	@Override
	public boolean unsubscribe(String chid) {
		SubscribeInfo info=null;
		synchronized(subscribeInfos){
			info=subscribeInfos.remove(chid);
		}
		if(info==null){
			return false;
		}
		synchronized(info){
			watcher.onUnsubscribe(info.from);
			info.setHandler(null);
		}
		return true;
	}
}
