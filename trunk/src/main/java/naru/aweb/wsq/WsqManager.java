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
	public static String CB_TYPE_ERROR="ERROR";
	public static String CB_TYPE_MESSAGE="MESSAGE";
	public static String CB_TYPE_INFO="INFO";
	
	private static WsqManager instance=new WsqManager();
	public static WsqManager getInstance(){
		instance.createWsq(new SampleWsqlet(),"/wsq","test");
		return instance;
	}
	
	/*/path,qname */
	private Map<String,Map<String,Wsq>> wsqs=new HashMap<String,Map<String,Wsq>>();
	
	/**
	 * peer�ւ̑��M�d���쐬
	 * @param action
	 * @param chId
	 * @param name
	 * @return
	 */
	public static JSON makeMessage(String type,String qname,String subId,String cause,Object message){
		JSONObject json=new JSONObject();
		json.put("type", type);
		json.put("qname", qname);
		json.put("subId", subId);
		json.put("cause", cause);
		json.put("message", message);
		return json;
	}
	
	public Collection<String> getQnames(String srcPath){
		Map<String,Wsq> qmap=wsqs.get(srcPath);
		if(qmap==null){
			return null;
		}
		return qmap.keySet();
	}
	
	public boolean createWsq(Object wsqWatcher,String srcPath,String wsqName){
		synchronized(wsqs){
			Map<String,Wsq> qmap=wsqs.get(srcPath);
			if(qmap==null){
				qmap=new HashMap<String,Wsq>();
				wsqs.put(srcPath, qmap);
			}
			if(qmap.get(wsqName)!=null){
				return false;//���ɓo�^����Ă���
			}
			Wsq wsq=Wsq.createWsq(wsqWatcher, wsqName);
			if(wsq==null){
				return false;
			}
			qmap.put(wsqName,wsq);
		}
		return true;
	}
	
	public boolean removeWsq(String srcPath,String wsqName){
		synchronized(wsqs){
			Map<String,Wsq> qmap=wsqs.get(srcPath);
			if(qmap==null){
				return false;
			}
			Wsq wsq=qmap.remove(wsqName);
			if(wsq==null){
				return false;
			}
			wsq.endQueue();
		}
		return true;
	}
	
	
	private Wsq getWsqFromPeer(WsqPeer peer){
		Map<String,Wsq> qmap=wsqs.get(peer.getSrcPath());
		if(qmap==null){
			return null;
		}
		Wsq wsq=qmap.get(peer.getQname());
		if(wsq==null){
			return null;
		}
		return wsq;
	}
	
	public void publish(WsqPeer from,Object message){
		Wsq wsq=getWsqFromPeer(from);
		if(wsq==null){
			//publish������queue���Ȃ������B
			logger.warn("publish ignore qname:"+from.getQname());
			return;
		}
		wsq.publish(from, message);
	}
	
	public boolean unsubscribe(WsqPeer from){
		Wsq wsq=getWsqFromPeer(from);
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
		Wsq wsq=getWsqFromPeer(from);
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
		Wsq wsq=getWsqFromPeer(from);
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(from);
	}
	
	public boolean setHandler(WsqPeer from,WsqHandler handler){
		Wsq wsq=getWsqFromPeer(from);
		if(wsq==null){
			return false;
		}
		if(wsq.setHandler(from, handler)==false){
			return false;
		}
		return true;
	}
	
}
