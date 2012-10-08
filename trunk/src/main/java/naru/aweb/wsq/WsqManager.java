package naru.aweb.wsq;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * wsqNameとchidを指定してpulish,subscribeする
 * wsqNameは、公表名もしくは自動採番(特定のレスポンス待ちを行うcmdの場合)
 * chidは、brawser側で採番する値とsessionIdから構成
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
		return instance;
	}
	
	/*/path,qname */
	private Map<String,Map<String,Wsq>> wsqs=new HashMap<String,Map<String,Wsq>>();
	
	/**
	 * peerへの送信電文作成
	 * @param action
	 * @param chId
	 * @param name
	 * @return
	 */
	public static JSON makeMessage(String type,String qname,String subId,String causeType,Object message){
		JSONObject json=new JSONObject();
		json.put("type", type);
		json.put("qname", qname);
		json.put("subId", subId);
		json.put("causeType", causeType);
		json.put("message", message);
		return json;
	}
	
	public Collection<String> qnames(String srcPath){
		Map<String,Wsq> qmap=wsqs.get(srcPath);
		if(qmap==null){
			return null;
		}
		return qmap.keySet();
	}
	
	private void termQmap(Map<String,Wsq> qmap){
		Iterator<Wsq> itr=qmap.values().iterator();
		while(itr.hasNext()){
			Wsq wsq=itr.next();
			itr.remove();
			wsq.endQueue();
			wsq.unref();
		}
	}
	
	public void term(){
		synchronized(wsqs){
			Iterator<Map<String,Wsq>> itr=wsqs.values().iterator();
			while(itr.hasNext()){
				Map<String,Wsq> qmap=itr.next();
				itr.remove();
				termQmap(qmap);
			}
		}
	}
	
	public boolean createWsq(Object wsqlet,String srcPath,String qname){
		synchronized(wsqs){
			Map<String,Wsq> qmap=wsqs.get(srcPath);
			if(qmap==null){
				qmap=new HashMap<String,Wsq>();
				wsqs.put(srcPath, qmap);
			}
			if(qmap.get(qname)!=null){
				return false;//既に登録されている
			}
			Wsq wsq=Wsq.createWsq(wsqlet, qname);
			if(wsq==null){
				return false;
			}
			qmap.put(qname,wsq);
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
			wsq.unref();
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
			//publishしたがqueueがなかった。
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
	
	//wsqHandlerを適切なWsqに登録する
	public boolean subscribe(WsqPeer from,WsqHandler handler){
		Wsq wsq=getWsqFromPeer(from);
		if(wsq==null){
			return false;
		}
		if(wsq.subscribe(from)==false){
			return false;
		}
		wsq.setHandler(from,null,handler);
		return true;
	}
	
	//wsqHandlerに対応するWsqからmessageを取得する
	public List getMessage(WsqPeer from){
		Wsq wsq=getWsqFromPeer(from);
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(from);
	}
	
	public boolean setHandler(WsqPeer from,WsqHandler orgHandler,WsqHandler handler){
		Wsq wsq=getWsqFromPeer(from);
		if(wsq==null){
			return false;
		}
		if(wsq.setHandler(from,orgHandler,handler)==false){
			return false;
		}
		return true;
	}
	
}
