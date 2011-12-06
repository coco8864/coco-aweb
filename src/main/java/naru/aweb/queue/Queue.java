package naru.aweb.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONSerializer;

public class Queue extends PoolBase implements Timer{
	private static Logger logger = Logger.getLogger(Queue.class);
	private static QueueManager queueManager=QueueManager.getInstance();
	
	private String name;
	private String comment;
	
	private static class SubscribeInfo{
		String chId;
		String user;
		WsQueueHandler handler;
		JSONArray msgs;
		long lastAccess;
		
		void setHandler(WsQueueHandler handler){
			if(handler!=null){
				handler.ref();
			}
			WsQueueHandler orgHandler=handler;
			this.handler=handler;
			if(orgHandler!=null){
				orgHandler.unref();
			}
		}
	}
	private Map<String,SubscribeInfo> idMap=new HashMap<String,SubscribeInfo>();
//	private Map<Long,SubscribeInfo> handlerMap=new HashMap<Long,SubscribeInfo>();
//	private boolean isOneshot;
	private boolean isComplete;//新規subscribeを拒否,現状の参加者に"complete"を通知すればiFinへ
	private boolean isFin;
	private boolean lastAccess;
	private boolean isTimeout;//timeoutするか否か

	private Object watchTimer;
	private Object watchObj;//object監視queue用、監視対象object
	private long watchInterval;//object監視queue用、監視間隔
	
	public static Queue create(){
		Queue queue=(Queue)PoolManager.getInstance(Queue.class);
		queue.isComplete=false;
		queue.isFin=false;
		queue.isTimeout=true;
		return queue;
	}
	
	public static Queue create(String name,String comment,boolean isTimeout){
		Queue queue=(Queue)PoolManager.getInstance(Queue.class);
		queue.name=name;
		queue.comment=comment;
//		queue.isOneshot=isOneshot;
		queue.isTimeout=isTimeout;
		queue.isFin=false;
		return queue;
	}
	
	public static Queue createBloadcaster(String name,String comment,Object watchObj,long watchInterval){
		Queue queue=(Queue)PoolManager.getInstance(Queue.class);
		queue.name=name;
		queue.comment=comment;
//		queue.isOneshot=isOneshot;
		queue.isTimeout=false;
		queue.isFin=false;
		queue.watchObj=watchObj;
		queue.watchInterval=watchInterval;
		queue.watchTimer=TimerManager.setInterval(watchInterval, queue, watchObj);
		return queue;
	}
	
	@Override
	public void recycle() {
		watchObj=null;
		watchInterval=-1;
		if(watchTimer!=null){
			TimerManager.clearInterval(watchTimer);
			watchTimer=null;
		}
		name=null;
		isComplete=false;
		isFin=false;
		Iterator<String> itr=idMap.keySet().iterator();
		while(itr.hasNext()){
			String subId=itr.next();
			SubscribeInfo info=idMap.get(subId);
			info.setHandler(null);
			if(info.msgs!=null){
				info.msgs.clear();
			}
		}
		idMap.clear();
	}
	
	/*
	 * subscribe直後に呼び出される,welcomメッセージがある場合ここでpush
	 */
	public void createChannel(String chId,String user){
		SubscribeInfo info=new SubscribeInfo();
		info.chId=chId;
		info.user=user;
		info.lastAccess=System.currentTimeMillis();
		synchronized(this){
			idMap.put(chId,info);
		}
	}
	
	//Handlerがレスポンスした場合（messageが送信できなくなった）場合に呼び出される
	public synchronized void returnHandler(String chId){
		SubscribeInfo info=idMap.remove(chId);
		if(info!=null){
			info.setHandler(null);
		}
	}
	
	public synchronized void unsubscribe(String chId){
		SubscribeInfo info=idMap.remove(chId);
		if(info!=null){
			info.setHandler(null);
		}
	}
	
	public synchronized boolean subscribe(String chId,WsQueueHandler handler){
		if(isFin){
			return false;//終了している
		}
		SubscribeInfo info=idMap.get(chId);
		if(info==null){
			return false;
		}
		info.lastAccess=System.currentTimeMillis();
		if(info.msgs!=null){
			handler.publish(JSONSerializer.toJSON(info.msgs));
			info.msgs.clear();
			if(isComplete){
				idMap.remove(chId);
				if(idMap.isEmpty()){
					info.setHandler(null);
					unsubscribe(chId);
					isFin=true;
					return true;
				}
			}
		}
		info.setHandler(handler);
		return true;
	}
	
	public synchronized void unregHandler(String chId){
		SubscribeInfo info=idMap.get(chId);
		if(info!=null){
			info.setHandler(null);
		}
	}
	
	public synchronized boolean changeUser(String chId,String user){
		if(isFin){
			return false;//終了している
		}
		SubscribeInfo info=idMap.get(chId);
		info.lastAccess=System.currentTimeMillis();
		if(info==null){
			return false;
		}
		info.user=user;
		return true;
	}
	
	public synchronized boolean listUsers(String requesterChId){
		if(isFin){
			return false;//終了している
		}
		SubscribeInfo requesterInfo=idMap.get(requesterChId);
		requesterInfo.lastAccess=System.currentTimeMillis();
		if(requesterInfo==null){
			return false;
		}
		List<String> users=new ArrayList<String>();
		Iterator<String> itr=idMap.keySet().iterator();
		while(itr.hasNext()){
			String chId=itr.next();
			SubscribeInfo info=idMap.get(chId);
			users.add(info.user);
		}
		//メッセージの組み立て
		String action="listUsers";
		JSON json=QueueManager.publishMessage(requesterChId, action, users,requesterInfo.user);
		if(requesterInfo.handler!=null){
			requesterInfo.handler.publish(json);
		}else{
			if(requesterInfo.msgs==null){
				requesterInfo.msgs=new JSONArray();
			}
			requesterInfo.msgs.add(json);
		}
		return true;
	}
	
	public synchronized boolean getMessages(String chId,JSONArray result){
		if(isFin){
			return false;//終了している
		}
		SubscribeInfo info=idMap.get(chId);
		if(info==null){
			return false;
		}
		info.lastAccess=System.currentTimeMillis();
		if(info.msgs==null){
			return true;
		}
		Iterator<JSON> itr=info.msgs.iterator();
		while(itr.hasNext()){
			JSON json=itr.next();
			itr.remove();
			result.add(json);
		}
		if(isComplete){
			idMap.remove(chId);
			if(idMap.isEmpty()){
				unsubscribe(chId);
				isFin=true;
			}
		}
		return true;
	}
	
	//completeは、最終publishの事
	public boolean complete(String fromChid,Object msg,boolean echoback){
		return publish(fromChid,msg,echoback,null,true);
	}
	public boolean publish(String fromChid,Object msg,boolean echoback,boolean isComplete){
		return publish(fromChid,msg,echoback,null,isComplete);
	}
	public boolean publish(String fromChid,Object msg,boolean echoback){
		return publish(fromChid,msg,echoback,null,false);
	}
	
	private synchronized boolean publish(String fromChid,Object msg,boolean echoback,Set dest,boolean isComplete){
//		isComplete
		if(isFin){
			return false;//終了している
		}
		if(!this.isComplete){
			this.isComplete=isComplete;
		}
		SubscribeInfo fromInfo=idMap.get(fromChid);
		if(fromInfo==null){
			return false;//不明な人からのメッセージ
		}
		String action;
		if(isComplete){
			action="complete";
		}else{
			action="message";
		}
		fromInfo.lastAccess=System.currentTimeMillis();
		Iterator<String> itr=idMap.keySet().iterator();
		while(itr.hasNext()){
			String chId=itr.next();
			if(!echoback && chId.equals(fromChid)){
				continue;//echobackが必要ない場合はスキップする
			}
			SubscribeInfo info=idMap.get(chId);
			//あて先指定があり、かつ含まれていなければ送信しない
			if(dest!=null&&!dest.contains(info.user)){
				continue;
			}
			//メッセージの組み立て
			JSON json= QueueManager.publishMessage(chId, action, msg,fromInfo.user);
			if(info.handler!=null){
				info.handler.publish(json);
				if(this.isComplete){
					itr.remove();
					unsubscribe(chId);
				}
				/*
				if(isOneshot){//onshotの場合１つみつけたらおしまい
					unsubscribe(chId);
					isFin=true;
					return true;
				}
				*/
			}else{
				if(info.msgs==null){
					info.msgs=new JSONArray();
				}
				info.msgs.add(json);
			}
		}
		if(this.isComplete && idMap.isEmpty()){
			isFin=true;
		}
		return true;
	}
	
	private boolean isSubTimeout(SubscribeInfo info,long now,long timeout){
		if(info.handler!=null){
			return false;
		}
		if((now-info.lastAccess)<=timeout){
			return false;
		}
		logger.debug("queue timeout."+name+":chId:"+info.chId);
		return true;
	}
	
	public List<String> checkTimeout(long now,long timeout){
		if(name==null||isTimeout==false){
			return null;
		}
		List<String> timeoutChIds=null;
		int subCount=0;
		synchronized(this){
			for(SubscribeInfo info:idMap.values()){
				if(!isSubTimeout(info,now,timeout)){
					subCount++;
					continue;
				}
				if(timeoutChIds==null){
					timeoutChIds=new ArrayList<String>();
				}
				timeoutChIds.add(info.chId);
			}
			if(timeoutChIds!=null){
				for(String chId:timeoutChIds){
					unsubscribe(chId);
				}
			}
			if(subCount==0){//だれもsubscribeしていない
				isFin=true;
			}
		}
		return timeoutChIds;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

//	public boolean isOneshot() {
//		return isOneshot;
//	}
	public boolean isFin() {
		return isFin;
	}

	public void onTimer(Object userContext) {
		// TODO Auto-generated method stub
		
	}

}
