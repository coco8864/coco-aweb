package naru.aweb.wsq;

import java.util.HashMap;
import java.util.Map;

import net.sf.json.JSONArray;

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
	private static Map<String,Wsq> wsqs=new HashMap<String,Wsq>();
	
	/* command */
	public static boolean createWsq(Object wsqWatcher){
		return createWsq(wsqWatcher,null);
	}
	
	/* chat */
	public static boolean createWsq(Object wsqWatcher,String wsqName){
		return createWsq(wsqWatcher,wsqName,0);
	}
	
	/* stress,connection,����I�ɏ��𔭐M */
	public static boolean createWsq(Object wsqWatcher,String wsqName,long interval){
		synchronized(wsqs){
			if(wsqName!=null){
				if(wsqs.get(wsqName)!=null){
					return false;//���ɓo�^����Ă���
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
	
	//wsqHandler��K�؂�Wsq�ɓo�^����
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
	
	//wsqHandler�ɑΉ�����Wsq����message���擾����
	public static JSONArray getMessage(String wsqName,String chid){
		Wsq wsq=wsqs.get(wsqName);
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(chid);
	}
	
}
