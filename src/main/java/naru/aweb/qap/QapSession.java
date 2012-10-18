package naru.aweb.qap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/*
 * AuthId,bid–ˆ‚Éì¬‚³‚ê‚é
 * ‚±‚Ì‰ñü‚Åsubscribe’†‚ÌPeer‚ª–Ô—…‚³‚ê‚Ä‚¢‚é
 */
public class QapSession {
	//qname -> subid
	//key:qname@subname
	private Map<String,QapPeer> qsubPeerMap=new HashMap<String,QapPeer>();
	
	private String key(String qname,String subname){
		StringBuffer sb=new StringBuffer(qname);
		sb.append("@");
		sb.append(subname);
		return sb.toString();
	}
	
	public synchronized boolean reg(QapPeer peer){
		String key=key(peer.getQname(),peer.getSubname());
		synchronized(qsubPeerMap){
			if(qsubPeerMap.get(key)!=null){
				return false;
			}
			qsubPeerMap.put(key, peer);
		}
		return true;
	}
	
	public synchronized QapPeer unreg(String qname,String subname){
		String key=key(qname,subname);
		synchronized(qsubPeerMap){
			QapPeer peer=qsubPeerMap.remove(key);
			return peer;
		}
	}
	
	public synchronized List<QapPeer> unregs(String qname){
		List<QapPeer> result=new ArrayList<QapPeer>();
		String prefix=qname+"@";
		synchronized(qsubPeerMap){
			Iterator<String> itr=qsubPeerMap.keySet().iterator();
			while(itr.hasNext()){
				String key=itr.next();
				if(!key.startsWith(prefix)){
					continue;
				}
				QapPeer peer=qsubPeerMap.get(key);
				itr.remove();
				result.add(peer);
			}
		}
		return result;
	}
	
	public synchronized List<QapPeer> unregs(){
		List<QapPeer> result=null;
		synchronized(qsubPeerMap){
			result=new ArrayList<QapPeer>(qsubPeerMap.values());
			qsubPeerMap.clear();
		}
		return result;
	}
	
	public synchronized int setHandler(QapManager wsqManager,QapHandler orgHandler,QapHandler handler){
		int count=0;
		synchronized(qsubPeerMap){
			Iterator<QapPeer> itr=qsubPeerMap.values().iterator();
			while(itr.hasNext()){
				QapPeer peer=itr.next();
				wsqManager.setHandler(peer, orgHandler,handler);
				count++;
			}
		}
		return count;
	}
	
	public synchronized int collectMessage(QapManager wsqManager,List result){
		int count=0;
		synchronized(qsubPeerMap){
			Iterator<QapPeer> itr=qsubPeerMap.values().iterator();
			while(itr.hasNext()){
				QapPeer peer=itr.next();
				List array=wsqManager.getMessage(peer);
				if(array==null){
					continue;
				}
				for(int i=0;i<array.size();i++){
					result.add(array.get(i));
					count++;
				}
			}
		}
		return count;
	}
}
