package naru.aweb.queue;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import naru.async.Timer;
import naru.async.store.DataUtil;
import naru.async.timer.TimerManager;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class QueueManager implements Timer{
	private static Logger logger = Logger.getLogger(QueueManager.class);
	private static long QUEUE_CHECK_INTERVAL=30000;//30s毎に監視して
	private static long QUEUE_TIMEOUT=10000;//10s反応がないchIdはunsub
	
	/**
	 * subscribedメッセージ電文作成
	 * @param action
	 * @param chId
	 * @param name
	 * @return
	 */
	public static JSON subscribedMessage(String action,String chId,String name){
		JSONObject json=new JSONObject();
		json.put("type", "subscribed");
		json.put("action", action);
		json.put("id", chId);
		json.put("name", name);
		return json;
	}
	
	/**
	 * publishメッセージ電文作成
	 * @param chId
	 * @param action
	 * @param message
	 * @param from
	 * @return
	 */
	public static JSON publishMessage(String chId,String action,Object message,String from){
		JSONObject json=new JSONObject();
		json.put("type", "publish");
		json.put("id", chId);
		json.put("action", action);
		json.put("from", from);
		json.put("message", message);
		json.put("time", System.currentTimeMillis());
		return json;
	}
	
	private SecureRandom random;
//	long timerId;
	private Object interval;
	
	private QueueManager(){
		try {
			random=SecureRandom.getInstance("SHA1PRNG");
			random.setSeed(System.currentTimeMillis());
		} catch (NoSuchAlgorithmException e) {
			logger.error("fail to crate SecureRandom.",e);
		}
		interval=TimerManager.setInterval(QUEUE_CHECK_INTERVAL, this, null);
	}
	private String createId(){
		byte[] bytes=new byte[16];
		while(true){
			synchronized(idQueueMap){
				random.nextBytes(bytes);
				String id=DataUtil.byteToString(bytes);
				if( idQueueMap.get(id)==null){
					return id;
				}
			}

		}
	}
	
	static QueueManager instance=new QueueManager();
	public static QueueManager getInstance(){
		return instance;
	}
	
	//chIdをキーにQueueを見つける
	private Map<String,Queue> idQueueMap=new HashMap<String,Queue>();
	//Queue名をキーにQueueを見つける,Queueが網羅されている。
	private Map<String,Queue> nameQueueMap=new HashMap<String,Queue>();
	
	/**
	 * 非同期メソッドが呼び出す
	 * 1)chIdをクライアントに通知
	 * 2)非同期メソッド完了時にpublish(chId,message)を呼び出す
	 * 
	 * クライアントは、chIdを設定してsubscribe...
	 * @return
	 */
	public String createQueue(){
		return createQueue(true);
	}
	
	public String createQueue(boolean isTimeout){
		String anonName="$AnonQueue"+createId();
		return createQueueByName(anonName,null,true,null,isTimeout);
	}
	
	public String createQueueByName(String name,String user,boolean isNew,String comment){
		return createQueueByName(name,user,isNew,comment,true);
	}

	private String createQueueByName(String name,String user,boolean isNew,String comment,boolean isTimeout){
		Queue queue=null;
		synchronized(nameQueueMap){
			queue=nameQueueMap.get(name);
			if(queue==null){
				if(!isNew){
					return null;
				}
				queue=Queue.create(name,comment,isTimeout);
				nameQueueMap.put(name, queue);
				logger.debug("create queue."+name);
			}
		}
		String chId=createId();
		synchronized(idQueueMap){
			idQueueMap.put(chId, queue);
		}
		queue.createChannel(chId,user);
		logger.debug("create chId."+name +":chId:"+chId);
		return chId;
	}
	
	//completeは、最終publishの事
	public void complete(String chId,Object message){
		publish(chId,message,true,true);
	}
	public void publish(String chId,Object message){
		publish(chId,message,true,false);
	}
	public boolean publish(String chId,Object message,boolean echoback,boolean isComplete){
		Queue queue=idQueueMap.get(chId);//getbyid
		if(queue==null){
			logger.warn("publish not found queue.chId:"+chId);
			return false;
		}
		return queue.publish(chId,message,echoback,isComplete);
	}
	
	//リクエスト受付時
	public void subHandler(WsQueueHandler handler){
	}
	
	public boolean listUsers(String chId){
		Queue queue=idQueueMap.get(chId);//getbyid
		if(queue==null){
			logger.warn("listUsers not found queue.chId:"+chId);
			return false;
		}
		return queue.listUsers(chId);
	}
	
	public boolean changeUser(String chId,String user){
		Queue queue=idQueueMap.get(chId);//getbyid
		if(queue==null){
			logger.warn("changeUser not found queue.chId:"+chId);
			return false;
		}
		return queue.changeUser(chId,user);
	}
	
	public boolean getMessages(String chId,JSONArray result){
		Queue queue=idQueueMap.get(chId);//getbyid
		if(queue==null){
			logger.warn("getMessages not found queue.chId:"+chId);
			return false;
		}
		return queue.getMessages(chId,result);
	}
	
	public boolean subscribeById(String chId,WsQueueHandler handler){
		Queue queue=idQueueMap.get(chId);//getbyid
		if(queue==null){
			logger.warn("subscribeById not found queue.chId:"+chId);
			return false;
		}
		return queue.subscribe(chId,handler);
	}
	
	public void unsubscribe(String chId){
		Queue queue=idQueueMap.get(chId);//getbyid
		if(queue==null){
			logger.warn("unsubscribe not found queue.chId:"+chId);
			return;
		}
		queue.unsubscribe(chId);
	}
	
	public void returnHandler(String chId){
		Queue queue=idQueueMap.get(chId);//getbyid
		if(queue==null){
			logger.warn("subHandler not found evnet.shId:"+chId);
			return;
		}
		 queue.unregHandler(chId);
	}

	public void onTimer(Object userContext) {
		long now=System.currentTimeMillis();
		Object[] names=nameQueueMap.keySet().toArray();
		for(int i=0;i<names.length;i++){
			Queue queue=nameQueueMap.get(names[i]);
			if(queue==null){
				continue;
			}
			//timeoutしたchId列が返却される。
			List<String> timeoutIds=queue.checkTimeout(now, QUEUE_TIMEOUT);
			if(timeoutIds!=null){
				//timeoutしたchIdを削除。
				synchronized(idQueueMap){
					for(String chId:timeoutIds){
						idQueueMap.remove(chId);
					}
				}
			}
			if(queue.isFin()){
				synchronized(nameQueueMap){
					nameQueueMap.remove(queue.getName());
				}
				queue.unref(true);
			}
		}
	}
	
	public void term(){
		TimerManager.clearInterval(interval);
		synchronized(idQueueMap){
			//全てのchIdを無効にする
			idQueueMap.clear();
		}
		synchronized(nameQueueMap){
			Iterator<Queue> itr=nameQueueMap.values().iterator();
			while(itr.hasNext()){
				Queue queue=itr.next();
				queue.unref();
			}
			nameQueueMap.clear();
		}
	}
	
}
