package naru.aweb.qap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QapSession {
	//qname -> subid
	private Map<String,Map<String,QapPeer>> qnamePeerMap=new HashMap<String,Map<String,QapPeer>>();
	private Set<QapPeer> peers=new HashSet<QapPeer>();
	
	public synchronized boolean reg(QapPeer peer){
		if(peers.contains(peer)){
			return false;
		}
		String qname=peer.getQname();
		Map<String,QapPeer> subscribePeers=qnamePeerMap.get(qname);
		if(subscribePeers==null){
			subscribePeers=new HashMap<String,QapPeer>();
			qnamePeerMap.put(qname, subscribePeers);
		}
		subscribePeers.put(peer.getSrcPath(),peer);
		peers.add(peer);
		return true;
	}
	
	public synchronized QapPeer unreg(String qname,String subscribeId){
		Map<String,QapPeer> subscribePeers=qnamePeerMap.get(qname);
		if(subscribePeers==null){
			return null;
		}
		QapPeer peer=subscribePeers.remove(subscribeId);
		if(peer==null){
			return null;
		}
		peers.remove(peer);
		return peer;
	}
	
	public synchronized List<QapPeer> unregs(String qname){
		Map<String,QapPeer> subscribePeers=qnamePeerMap.get(qname);
		if(subscribePeers==null){
			return null;
		}
		List<QapPeer> result=new ArrayList<QapPeer>();
		Iterator<String> itr=subscribePeers.keySet().iterator();
		while(itr.hasNext()){
			String subscribeId=itr.next();
			QapPeer peer=subscribePeers.get(subscribeId);
			itr.remove();
			result.add(peer);
			peers.remove(peer);
		}
		return result;
	}
	
	public synchronized List<QapPeer> unregs(){
		List<QapPeer> result=new ArrayList<QapPeer>(peers);
		peers.clear();
		qnamePeerMap.clear();
		return result;
	}
	
	public synchronized int setHandler(QapManager wsqManager,QapHandler orgHandler,QapHandler handler){
		Iterator<QapPeer> itr=peers.iterator();
		int count=0;
		while(itr.hasNext()){
			QapPeer peer=itr.next();
			wsqManager.setHandler(peer, orgHandler,handler);
			count++;
		}
		return count;
	}
	
	public synchronized int collectMessage(QapManager wsqManager,List result){
		Iterator<QapPeer> itr=peers.iterator();
		int count=0;
		while(itr.hasNext()){
			QapPeer peer=itr.next();
			List array=wsqManager.getMessage(peer);
			if(array==null){
				continue;
			}
			for(int i=0;i<array.size();i++){
				result.add(array.get(i));
			}
			count++;
		}
		return count;
	}
	
	
}
