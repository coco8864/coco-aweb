package naru.aweb.wsq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.json.JSONArray;

public class WsqSession {
	private Map<String,Map<String,WsqPeer>> qnamePeerMap=new HashMap<String,Map<String,WsqPeer>>();
	private Set<WsqPeer> peers=new HashSet<WsqPeer>();
	
	public synchronized boolean reg(WsqPeer peer){
		if(peers.contains(peer)){
			return false;
		}
		String qname=peer.getQname();
		Map<String,WsqPeer> subscribePeers=qnamePeerMap.get(qname);
		if(subscribePeers==null){
			subscribePeers=new HashMap<String,WsqPeer>();
			qnamePeerMap.put(qname, subscribePeers);
		}
		subscribePeers.put(peer.getSubId(),peer);
		return true;
	}
	
	public synchronized WsqPeer unreg(String qname,String subscribeId){
		Map<String,WsqPeer> subscribePeers=qnamePeerMap.get(qname);
		if(subscribePeers==null){
			return null;
		}
		WsqPeer peer=subscribePeers.remove(subscribeId);
		if(peer==null){
			return null;
		}
		peers.remove(peer);
		return peer;
	}
	
	public synchronized List<WsqPeer> unregs(String qname){
		Map<String,WsqPeer> subscribePeers=qnamePeerMap.get(qname);
		List<WsqPeer> result=new ArrayList<WsqPeer>();
		if(subscribePeers==null){
			return null;
		}
		Iterator<String> itr=subscribePeers.keySet().iterator();
		while(itr.hasNext()){
			String subscribeId=itr.next();
			WsqPeer peer=subscribePeers.get(subscribeId);
			itr.remove();
			result.add(peer);
			peers.remove(peer);
		}
		return result;
	}
	
	public synchronized List<WsqPeer> unregs(){
		List<WsqPeer> result=new ArrayList<WsqPeer>(peers);
		peers.clear();
		qnamePeerMap.clear();
		return result;
	}
	
	/*
	public Map<String,WsqPeer> getPeers(String qname){
		return qnamePeerMap.get(qname);
	}
	
	public Iterator<WsqPeer> peerIterator(){
		return peers.iterator();
	}
	*/
	
	public synchronized int setHandler(WsqManager wsqManager,WsqHandler handler){
		Iterator<WsqPeer> itr=peers.iterator();
		int count=0;
		while(itr.hasNext()){
			WsqPeer peer=itr.next();
			wsqManager.setHandler(peer, handler);
			count++;
		}
		return count;
	}
	
	public synchronized int collectMessage(WsqManager wsqManager,JSONArray result){
		Iterator<WsqPeer> itr=peers.iterator();
		int count=0;
		while(itr.hasNext()){
			WsqPeer peer=itr.next();
			JSONArray array=wsqManager.getMessage(peer);
			for(int i=0;i<array.size();i++){
				result.add(array.get(i));
			}
			count++;
		}
		return count;
	}
	
	
}
