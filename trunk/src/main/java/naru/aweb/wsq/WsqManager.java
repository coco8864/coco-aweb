package naru.aweb.wsq;

import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONArray;

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
	private static WsqManager instance=new WsqManager();
	public static WsqManager getInstance(){
		return instance;
	}
	
	private Map<String,Wsq> wsqs=new HashMap<String,Wsq>();
	
	public boolean createWsq(Object wsqWatcher,String wsqName){
		synchronized(wsqs){
			if(wsqs.get(wsqName)!=null){
				return false;//既に登録されている
			}
			Wsq wsq=Wsq.createWsq(wsqWatcher, wsqName);
			if(wsq==null){
				return false;
			}
			wsqs.put(wsq.getWsqName(),wsq);
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
	
	//wsqHandlerを適切なWsqに登録する
	public boolean subscribe(String wsqName,WsqPeer from,WsqHandler handler){
		Wsq wsq=wsqs.get(wsqName);
		if(wsq==null){
			return false;
		}
		if(wsq.subscribe(from)==false){
			return false;
		}
		wsq.setHandler(from, handler);
		return true;
	}
	
	//wsqHandlerに対応するWsqからmessageを取得する
	public JSONArray getMessage(String wsqName,WsqPeer from){
		Wsq wsq=wsqs.get(wsqName);
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(from);
	}
}
