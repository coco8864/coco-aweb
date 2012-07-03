package naru.aweb.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONSerializer;

public class Queue extends PoolBase {
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
	}
	private Map<String,SubscribeInfo> idMap=new HashMap<String,SubscribeInfo>();
//	private Map<Long,SubscribeInfo> handlerMap=new HashMap<Long,SubscribeInfo>();
	private boolean isOneshot;
	private boolean isFin;
	private boolean lastAccess;
	private boolean isTimeout;//timeout���邩�ۂ�
	
	public static Queue create(){
		Queue queue=(Queue)PoolManager.getInstance(Queue.class);
		queue.isOneshot=true;
		queue.isFin=false;
		queue.isTimeout=true;
		return queue;
	}
	
	public static Queue create(String name,String comment,boolean isOneshot,boolean isTimeout){
		Queue queue=(Queue)PoolManager.getInstance(Queue.class);
		queue.name=name;
		queue.comment=comment;
		queue.isOneshot=isOneshot;
		queue.isTimeout=isTimeout;
		queue.isFin=false;
		return queue;
	}
	
	@Override
	public void recycle() {
		name=null;
		isOneshot=false;
		isFin=false;
		Iterator<String> itr=idMap.keySet().iterator();
		while(itr.hasNext()){
			String subId=itr.next();
			SubscribeInfo info=idMap.get(subId);
			if(info.handler!=null){
				info.handler.unref();
				info.handler=null;
			}
			if(info.msgs!=null){
				info.msgs.clear();
			}
		}
		idMap.clear();
	}
	
	public void createChannel(String chId,String user){
		SubscribeInfo info=new SubscribeInfo();
		info.chId=chId;
		info.user=user;
		info.lastAccess=System.currentTimeMillis();
		synchronized(this){
			idMap.put(chId,info);
		}
	}
	
	//Handler�����X�|���X�����ꍇ�imessage�����M�ł��Ȃ��Ȃ����j�ꍇ�ɌĂяo�����
	public synchronized void returnHandler(String chId){
		SubscribeInfo info=idMap.remove(chId);
		if(info==null||info.handler==null){
			return;
		}
		info.handler.unref();
		info.handler=null;
	}
	
	public synchronized void unsubscribe(String chId){
		SubscribeInfo info=idMap.remove(chId);
		if(info==null){
			return;
		}
		if(info.handler!=null){
			info.handler.unref();
			info.handler=null;
		}
	}
	
	public synchronized boolean subscribe(String chId,WsQueueHandler handler){
		if(isFin){
			return false;//�I�����Ă���
		}
		SubscribeInfo info=idMap.get(chId);
		if(info==null){
			return false;
		}
		info.lastAccess=System.currentTimeMillis();
		if(info.msgs!=null){
			handler.publish(JSONSerializer.toJSON(info.msgs));
			info.msgs.clear();
			if(isOneshot){
				unsubscribe(chId);
				isFin=true;
				return true;
			}
		}
		handler.ref();
		info.handler=handler;
		return true;
	}
	
	public synchronized void unregHandler(String chId){
		SubscribeInfo info=idMap.get(chId);
		if(info==null || info.handler==null){
			return;
		}
		info.handler.unref();
		info.handler=null;
	}
	
	public synchronized boolean changeUser(String chId,String user){
		if(isFin){
			return false;//�I�����Ă���
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
			return false;//�I�����Ă���
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
		//���b�Z�[�W�̑g�ݗ���
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
			return false;//�I�����Ă���
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
			if(isOneshot){
				result.add(json);
				break;
			}
			result.add(json);
		}
		if(isOneshot){
			unsubscribe(chId);
			isFin=true;
		}
		return true;
	}
	public boolean publish(String fromChid,Object msg,boolean echoback){
		return publish(fromChid,msg,echoback,null);
	}
	
	public synchronized boolean publish(String fromChid,Object msg,boolean echoback,Set dest){
		if(isFin){
			return false;//�I�����Ă���
		}
		SubscribeInfo fromInfo=idMap.get(fromChid);
		if(fromInfo==null){
			return false;//�s���Ȑl����̃��b�Z�[�W
		}
		fromInfo.lastAccess=System.currentTimeMillis();
		Iterator<String> itr=idMap.keySet().iterator();
		while(itr.hasNext()){
			String chId=itr.next();
			if(!echoback && chId.equals(fromChid)){
				continue;//echoback���K�v�Ȃ��ꍇ�̓X�L�b�v����
			}
			SubscribeInfo info=idMap.get(chId);
			//���Đ�w�肪����A���܂܂�Ă��Ȃ���Α��M���Ȃ�
			if(dest!=null&&!dest.contains(info.user)){
				continue;
			}
			//���b�Z�[�W�̑g�ݗ���
			String action="message";
			if(isOneshot){
				action="complete";
			}
			JSON json= QueueManager.publishMessage(chId, action, msg,fromInfo.user);
			if(info.handler!=null){
				info.handler.publish(json);
				if(isOneshot){//onshot�̏ꍇ�P�݂����炨���܂�
					unsubscribe(chId);
					isFin=true;
					return true;
				}
			}else{
				if(info.msgs==null){
					info.msgs=new JSONArray();
				}
				info.msgs.add(json);
			}
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
			if(subCount==0){//�����subscribe���Ă��Ȃ�
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

	public boolean isOneshot() {
		return isOneshot;
	}
	public boolean isFin() {
		return isFin;
	}

}