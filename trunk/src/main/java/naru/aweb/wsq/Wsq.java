package naru.aweb.wsq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

public class Wsq extends PoolBase implements WsqCtx,Timer{
	private static Logger logger=Logger.getLogger(Wsq.class);
	
	/* stress,connection,����I�ɏ��𔭐M */
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
		List msgs=null;
		List<BlobEnvelope> blobMsgs=new ArrayList<BlobEnvelope>();
		long lastAccess;
		
		void setMessage(BlobMessage blobMessage){
			if(!from.isAllowBlob()){
				logger.warn("not support BlobMessage."+handler);
				return;
			}
			JSONObject header=(JSONObject)WsqManager.makeMessage(
					WsqManager.CB_TYPE_MESSAGE,
					from.getQname(),
					from.getSubId(),
					null,
					null
			);
			BlobEnvelope envelope=BlobEnvelope.create(header, blobMessage);
			if(handler!=null){
				handler.message(envelope);
				return;
			}else if(msgs==null){
				msgs=new ArrayList();
			}
			logger.warn("not support BlobMessage.");
			synchronized(blobMsgs){
				blobMsgs.add(envelope);
			}
		}
		
		void setMessage(Object msg){
			Object sendMsg=WsqManager.makeMessage(
					WsqManager.CB_TYPE_MESSAGE,
					from.getQname(),
					from.getSubId(),
					null,
					msg
			);
			if(handler!=null){
				handler.message(sendMsg);
				return;
			}else if(msgs==null){
				msgs=new ArrayList();
			}
			msgs.add(sendMsg);
		}
		boolean setHandler(WsqHandler orgHandler,WsqHandler handler){
			if(orgHandler!=null&&orgHandler!=this.handler){
				return false;
			}
			orgHandler=this.handler;
			if(handler!=null){
				handler.ref();
				if(msgs!=null){
					handler.message(msgs);
				}
				msgs=null;
				synchronized(blobMsgs){
					for(BlobEnvelope envelope:blobMsgs){
						handler.message(envelope);
					}
					blobMsgs.clear();
				}
			}
			this.handler=handler;
			if(orgHandler!=null){
				orgHandler.unref();
			}
			return true;
		}
	}

	public String getQname(){
		return qname;
	}

	public boolean setHandler(WsqPeer from,WsqHandler orgHandler,WsqHandler handler){
		SubscribeInfo info=subscribeInfos.get(from);
		if(info==null){
			return false;
		}
		synchronized(info){
			return info.setHandler(orgHandler,handler);
		}
	}
	
	public boolean subscribe(WsqPeer from){
		SubscribeInfo info=new SubscribeInfo();
		info.from=from;
		info.lastAccess=System.currentTimeMillis();
		synchronized(subscribeInfos){
			if(subscribeInfos.get(from)!=null){
				return true;//����subscribe��
			}
			subscribeInfos.put(from, info);
		}
		wsqlet.onSubscribe(info.from);
		return true;
	}
	
	public void publish(WsqPeer from,Object message){
		if(message instanceof String){
			wsqlet.onPublish(from, (String)message);
		}else if(message instanceof JSON){
			wsqlet.onPublish(from, (JSON)message);
		}else if(message instanceof BlobMessage){
			wsqlet.onPublish(from, (BlobMessage)message);
		}else{
			logger.error("publish type error");
		}
	}
	
	/* XHR�̂Ƃ������g���� */
	public List getMessage(WsqPeer from){
		SubscribeInfo info=subscribeInfos.get(from);
		if(info==null){
			return null;
		}
		synchronized(info){
			List orgMsgs=info.msgs;
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

	/*---�ȍ~watcher������̃��N�G�X�g ---*/
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
		if(message instanceof BlobMessage){
			info.setMessage((BlobMessage)message);
		}else{
			info.setMessage(message);
		}
		return 1;
	}

	private int messageInternal(Object message, Collection<SubscribeInfo> infos){
		int count=0;
		if(message instanceof BlobMessage){
			for(SubscribeInfo info:infos){
				info.setMessage((BlobMessage)message);
				count++;
			}
		}else{
			for(SubscribeInfo info:infos){
				info.setMessage(message);
				count++;
			}
		}
		return count;
	}
	
	/*
	 * �ȉ��Q�ӏ�����Ăяo�����
	 * 1)WsqWatcher(�T�[�o�����Ƃ���unsubscribe)
	 * 2)WsqManager(�������unsubscribe)
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
			info.setHandler(null,null);
		}
		return true;
	}

	@Override
	public Set<WsqPeer> getSubscribePeers() {
		Set<WsqPeer> result=null;
		synchronized(subscribeInfos){
			result=Collections.unmodifiableSet(subscribeInfos.keySet());
		}
		return result;
	}
}
