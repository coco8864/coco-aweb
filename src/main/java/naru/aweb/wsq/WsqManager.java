package naru.aweb.wsq;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * wsqName��chid���w�肵��pulish,subscribe����
 * wsqName�́A���\���������͎����̔�(����̃��X�|���X�҂����s��cmd�̏ꍇ)
 * chid�́Abrawser���ō̔Ԃ���l��sessionId����\��
 * brawserChid
 * sessionChid
 * 
 * @author Owner
 *
 */
public class WsqManager {
	private static Logger logger=Logger.getLogger(WsqManager.class);
	private static WsqManager instance=new WsqManager();
	public static WsqManager getInstance(){
		return instance;
	}
	
	private Map<String,Wsq> wsqs=new HashMap<String,Wsq>();
	
	/**
	 * peer�ւ̑��M�d���쐬
	 * @param action
	 * @param chId
	 * @param name
	 * @return
	 */
	public static JSON makeMessage(String qname,String subscribeId,String type,String action,String message){
		JSONObject json=new JSONObject();
		json.put("qname", qname);
		json.put("subscribeId", "subscribeId");
		json.put("type", type);
		json.put("action", action);
		json.put("message", message);
		return json;
	}
	
	public Collection<String> getQnames(){
		return wsqs.keySet();
	}
	
	public boolean createWsq(Object wsqWatcher,String wsqName){
		synchronized(wsqs){
			if(wsqs.get(wsqName)!=null){
				return false;//���ɓo�^����Ă���
			}
			Wsq wsq=Wsq.createWsq(wsqWatcher, wsqName);
			if(wsq==null){
				return false;
			}
			wsqs.put(wsq.getQname(),wsq);
		}
		return true;
	}
	
	public boolean removeWsq(String wsqName){
		synchronized(wsqs){
			Wsq wsq=wsqs.remove(wsqName);
			if(wsq==null){
				return false;
			}
			wsq.endQueue();
		}
		return true;
	}
	
	public void publish(WsqPeer from,Object message){
		Wsq wsq=wsqs.get(from.getQname());
		if(wsq==null){
			//publish������queue���Ȃ������B
			logger.warn("publish ignore qname:"+from.getQname());
			return;
		}
		wsq.publish(from, message);
	}
	
	public boolean unsubscribe(WsqPeer from){
		Wsq wsq=wsqs.get(from.getQname());
		if(wsq==null){
			return false;
		}
		if(wsq.unsubscribe(from)==false){
			return false;
		}
		return true;
	}
	
	//wsqHandler��K�؂�Wsq�ɓo�^����
	public boolean subscribe(WsqPeer from,WsqHandler handler){
		Wsq wsq=wsqs.get(from.getQname());
		if(wsq==null){
			return false;
		}
		if(wsq.subscribe(from)==false){
			return false;
		}
		wsq.setHandler(from, handler);
		return true;
	}
	
	//wsqHandler�ɑΉ�����Wsq����message���擾����
	public JSONArray getMessage(WsqPeer from){
		Wsq wsq=wsqs.get(from.getQname());
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(from);
	}
	
	public boolean setHandler(WsqPeer from,WsqHandler handler){
		Wsq wsq=wsqs.get(from.getQname());
		if(wsq==null){
			return false;
		}
		if(wsq.setHandler(from, handler)==false){
			return false;
		}
		return true;
	}
	
}
