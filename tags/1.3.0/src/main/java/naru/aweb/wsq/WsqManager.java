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
	private static Map<String,Wsq> wsqs=new HashMap<String,Wsq>();
	
	/* command */
	public static boolean createWsq(Object wsqWatcher){
		return createWsq(wsqWatcher,null);
	}
	
	/* chat */
	public static boolean createWsq(Object wsqWatcher,String wsqName){
		return createWsq(wsqWatcher,wsqName,0);
	}
	
	/* stress,connection,定期的に情報を発信 */
	public static boolean createWsq(Object wsqWatcher,String wsqName,long interval){
		synchronized(wsqs){
			if(wsqName!=null){
				if(wsqs.get(wsqName)!=null){
					return false;//既に登録されている
				}
			}
			Wsq wsq=Wsq.createWsq(wsqWatcher, wsqName, interval);
			if(wsq==null){
				return false;
			}
			wsqs.put(wsq.getWsqName(),wsq);
		}
		return true;
	}
	
	public static boolean removeWsq(String wsqName){
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
	public static boolean subscribe(String wsqName,String chid,WsQueueHandler handler){
		Wsq wsq=wsqs.get(wsqName);
		if(wsq==null){
			return false;
		}
		if(wsq.subscribe(chid, null)==false){
			return false;
		}
		wsq.setHandler(chid, handler);
		return true;
	}
	
	//wsqHandlerに対応するWsqからmessageを取得する
	public static JSONArray getMessage(String wsqName,String chid){
		Wsq wsq=wsqs.get(wsqName);
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(chid);
	}
	
}
