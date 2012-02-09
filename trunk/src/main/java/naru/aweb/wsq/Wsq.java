package naru.aweb.wsq;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import naru.async.Timer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import net.sf.json.JSONArray;

public class Wsq extends PoolBase implements WsqController,Timer{
	
	/* stress,connection,定期的に情報を発信 */
	public static Wsq createWsq(Object wsqlet,String qname){
		Wsq wsq=(Wsq)PoolManager.getInstance(Wsq.class);
		if(qname==null){
			return null;
		}else{
			wsq.qname=qname;
		}
		wsq.wsqlet=(Wsqlet)wsqlet;
		wsq.wsqlet.onStartQueue(wsq.qname,wsq);
		wsq.onTimer(null);
		return wsq;
	}
	
	private Map<WsqPeer,SubscribeInfo> subscribeInfos=new HashMap<WsqPeer,SubscribeInfo>();
	private String qname;
	private Wsqlet wsqlet;
	private long timerId=-1;
	
	private static class SubscribeInfo{
		WsqPeer from;
		WsqHandler handler;
		JSONArray msgs;
		long lastAccess;
		void setMessage(Object msg){
			if(handler!=null){
				handler.message(msg);
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
					handler.message(msgs);
				}
				msgs=null;
			}
			WsqHandler orgHandler=this.handler;
			this.handler=handler;
			if(orgHandler!=null){
				orgHandler.unref();
			}
		}
	}

	public String getQname(){
		return qname;
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
	
	public void publish(WsqPeer from,Object message){
		wsqlet.onPublish(from, message);
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
	public int message(Object message, Collection<WsqPeer> peers,Collection<WsqPeer> dnyPeers) {
		if(peers==null){
			peers=subscribeInfos.keySet();
		}
		Collection<SubscribeInfo> infos=new HashSet<SubscribeInfo>();
		for(WsqPeer peer:peers){
			if(dnyPeers.contains(peer)){
				continue;
			}
			SubscribeInfo info=subscribeInfos.get(peer);
			if(info==null){
				continue;
			}
			infos.add(info);
		}
		return messageInternal(message,infos);
	}

	@Override
	public int message(Object message, Collection<WsqPeer> peers, WsqPeer dnyPeer) {
		if(peers==null){
			peers=subscribeInfos.keySet();
		}
		Collection<SubscribeInfo> infos=new HashSet<SubscribeInfo>();
		for(WsqPeer peer:peers){
			if(peer.equals(dnyPeer)){
				continue;
			}
			SubscribeInfo info=subscribeInfos.get(peer);
			if(info==null){
				continue;
			}
			infos.add(info);
		}
		return messageInternal(message,infos);
	}

	@Override
	public int message(Object message, Collection<WsqPeer> peers) {
		Collection<SubscribeInfo> infos=new HashSet<SubscribeInfo>();
		for(WsqPeer peer:peers){
			SubscribeInfo info=subscribeInfos.get(peer);
			if(info==null){
				continue;
			}
			infos.add(info);
		}
		return messageInternal(message,infos);
	}
	
	@Override
	public int message(Object message) {
		return messageInternal(message,subscribeInfos.values());
	}

	@Override
	public int message(Object message, WsqPeer peer) {
		SubscribeInfo info=subscribeInfos.get(peer);
		if(info==null){
			return 0;
		}
		info.setMessage(message);
		return 1;
	}

	private int messageInternal(Object message, Collection<SubscribeInfo> infos){
		int count=0;
		for(SubscribeInfo info:infos){
			info.setMessage(message);
			count++;
		}
		return count;
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
