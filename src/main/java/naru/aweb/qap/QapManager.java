package naru.aweb.qap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import net.sf.json.JSON;
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
public class QapManager {
	private static Logger logger=Logger.getLogger(QapManager.class);
	public static String CB_TYPE_ERROR="ERROR";
	public static String CB_TYPE_MESSAGE="MESSAGE";
	public static String CB_TYPE_INFO="INFO";
	
	private static QapManager instance=new QapManager();
	public static QapManager getInstance(){
		return instance;
	}
	
	/*/path,qname */
	private Map<String,Map<String,Qap>> wsqs=new HashMap<String,Map<String,Qap>>();
	
	/**
	 * peer�ւ̑��M�d���쐬
	 * @param action
	 * @param chId
	 * @param name
	 * @return
	 */
	public static JSON makeMessage(String type,String qname,String subname,String causeType,Object message){
		JSONObject json=new JSONObject();
		json.put("type", type);
		json.put("qname", qname);
		json.put("subname", subname);
		json.put("causeType", causeType);
		json.put("message", message);
		return json;
	}
	
	public Collection<String> qnames(String srcPath){
		Map<String,Qap> qmap=wsqs.get(srcPath);
		if(qmap==null){
			return null;
		}
		return qmap.keySet();
	}
	
	private void termQmap(Map<String,Qap> qmap){
		Iterator<Qap> itr=qmap.values().iterator();
		while(itr.hasNext()){
			Qap wsq=itr.next();
			itr.remove();
			wsq.endQueue();
			wsq.unref();
		}
	}
	
	public void term(){
		synchronized(wsqs){
			Iterator<Map<String,Qap>> itr=wsqs.values().iterator();
			while(itr.hasNext()){
				Map<String,Qap> qmap=itr.next();
				itr.remove();
				termQmap(qmap);
			}
		}
	}
	
	public boolean createWsq(Object wsqlet,String srcPath,String qname){
		synchronized(wsqs){
			Map<String,Qap> qmap=wsqs.get(srcPath);
			if(qmap==null){
				qmap=new HashMap<String,Qap>();
				wsqs.put(srcPath, qmap);
			}
			if(qmap.get(qname)!=null){
				return false;//���ɓo�^����Ă���
			}
			Qap wsq=Qap.createWsq(wsqlet, qname);
			if(wsq==null){
				return false;
			}
			qmap.put(qname,wsq);
		}
		return true;
	}
	
	public boolean removeWsq(String srcPath,String wsqName){
		synchronized(wsqs){
			Map<String,Qap> qmap=wsqs.get(srcPath);
			if(qmap==null){
				return false;
			}
			Qap wsq=qmap.remove(wsqName);
			if(wsq==null){
				return false;
			}
			wsq.endQueue();
			wsq.unref();
		}
		return true;
	}
	
	
	private Qap getWsqFromPeer(QapPeer peer){
		Map<String,Qap> qmap=wsqs.get(peer.getSrcPath());
		if(qmap==null){
			return null;
		}
		Qap wsq=qmap.get(peer.getQname());
		if(wsq==null){
			return null;
		}
		return wsq;
	}
	
	public void publish(QapPeer from,Object message){
		Qap wsq=getWsqFromPeer(from);
		if(wsq==null){
			//publish������queue���Ȃ������B
			logger.warn("publish ignore qname:"+from.getQname());
			return;
		}
		wsq.publish(from, message);
	}
	
	public boolean unsubscribe(QapPeer from){
		Qap wsq=getWsqFromPeer(from);
		if(wsq==null){
			return false;
		}
		if(wsq.unsubscribe(from)==false){
			return false;
		}
		return true;
	}
	
	//wsqHandler��K�؂�Wsq�ɓo�^����
	public boolean subscribe(QapPeer from,QapHandler handler){
		Qap wsq=getWsqFromPeer(from);
		if(wsq==null){
			return false;
		}
		if(wsq.subscribe(from)==false){
			return false;
		}
		wsq.setHandler(from,null,handler);
		return true;
	}
	
	//wsqHandler�ɑΉ�����Wsq����message���擾����
	public List getMessage(QapPeer from){
		Qap wsq=getWsqFromPeer(from);
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(from);
	}
	
	public boolean setHandler(QapPeer from,QapHandler orgHandler,QapHandler handler){
		Qap wsq=getWsqFromPeer(from);
		if(wsq==null){
			return false;
		}
		if(wsq.setHandler(from,orgHandler,handler)==false){
			return false;
		}
		return true;
	}
	
}
