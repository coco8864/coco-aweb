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
	private static WsqManager instance=new WsqManager();
	public static WsqManager getInstance(){
		return instance;
	}
	
	private Map<String,Wsq> wsqs=new HashMap<String,Wsq>();
	
	public boolean createWsq(Object wsqWatcher,String wsqName){
		synchronized(wsqs){
			if(wsqs.get(wsqName)!=null){
				return false;//���ɓo�^����Ă���
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
	
	//wsqHandler��K�؂�Wsq�ɓo�^����
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
	
	//wsqHandler�ɑΉ�����Wsq����message���擾����
	public JSONArray getMessage(String wsqName,WsqPeer from){
		Wsq wsq=wsqs.get(wsqName);
		if(wsq==null){
			return null;
		}
		return wsq.getMessage(from);
	}
}
