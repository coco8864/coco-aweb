package naru.aweb.pa;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.timer.TimerManager;
import net.sf.json.JSON;

public class PaletWrapper implements PaletCtx,Timer{
	private static Logger logger=Logger.getLogger(PaSession.class);
	private Object intervalObj=null;
	private String qname;
	private Palet palet;
	private boolean isTerminate=false;
	private Set<PaPeer> peers=new HashSet<PaPeer>();
	private Map<String,Set<PaPeer>> subnamePeersMap=new HashMap<String,Set<PaPeer>>();
	
	public PaletWrapper(String qname,Palet palet){
		this.qname=qname;
		this.palet=palet;
		isTerminate=false;
	}
	
	void onSubscribe(PaPeer peer){
		String subname=peer.getSubname();
		synchronized(peers){
			if(isTerminate){
				logger.warn("onSubscribe aleady stop");
				return;
			}
			peers.add(peer);
			Set<PaPeer> subnamePeers=subnamePeersMap.get(subname);
			if(subnamePeers==null){
				subnamePeers=new HashSet<PaPeer>();
				subnamePeersMap.put(subname,subnamePeers);
				subnamePeers.add(peer);
			}
		}
		palet.onSubscribe(peer);
	}
	
	void onUnubscribe(PaPeer peer){
		String subname=peer.getSubname();
		synchronized(peers){
			if(isTerminate){
				logger.warn("onUnubscribe aleady stop");
				return;
			}
			peers.remove(peer);
			Set<PaPeer> subnamePeers=subnamePeersMap.get(subname);
			if(subnamePeers!=null){
				subnamePeers.remove(peer);
			}
		}
		palet.onUnsubscribe(peer,null);
	}
	
	void onPublish(PaPeer peer,Object data){
		if(data instanceof String){
			palet.onPublishText(peer,(String)data);
		}else if(data instanceof JSON){
			palet.onPublishObj(peer,(JSON)data);
		}else if(data instanceof BlobMessage){
			palet.onPublishBlob(peer, (BlobMessage)data);
		}else{
			logger.error("onPublish data type" + data.getClass().getName());
		}
	}
	
	/**
	 * 全peerにmessageを送信する
	 * @param data
	 * @return
	 */
	public int message(Object data){
		return message(data,getPeers(),(PaPeer)null);
	}
	
	/**
	 * subnameで指定されたpeerにmessageを送信する
	 * @param data
	 * @param subname
	 * @return
	 */
	public int message(Object data,String subname){
		return message(data,getPeers(subname),(PaPeer)null);
	}
	
	/**
	 * subnameで指定されたpeer(excptPeersを除く)にmessageを送信する
	 * @param data
	 * @param subname
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,String subname,Set<PaPeer> exceptPeers){
		return message(data,getPeers(subname),exceptPeers);
	}
	
	/**
	 * 
	 * @param data
	 * @param peers
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,Set<PaPeer> peers,Set<PaPeer> exceptPeers){
		int count=0;
		for(PaPeer peer:peers){
			if(exceptPeers!=null && exceptPeers.contains(peer)){
				continue;
			}
			if(peer.message(data)){
				count++;
			}
		}
		return count;
	}
	
	@Override
	public int message(Object data, String subname, PaPeer exceptPeer) {
		return message(data,getPeers(subname),exceptPeer);
	}

	@Override
	public int message(Object data, Set<PaPeer> peers, PaPeer exceptPeer) {
		int count=0;
		for(PaPeer peer:peers){
			if(exceptPeer!=null && exceptPeer.equals(peer)){
				continue;
			}
			if(peer.message(data)){
				count++;
			}
		}
		return count;
	}
	
	public String getQname(){
		return qname;
	}
	
	public Set<PaPeer> getPeers(){
		return Collections.unmodifiableSet(peers);
	}
	
	public Set<PaPeer> getPeers(String subname){
		return Collections.unmodifiableSet(subnamePeersMap.get(subname));
	}
	
	
	public boolean setInterval(long interval){
		if(intervalObj!=null){
			TimerManager.clearInterval(interval);
		}
		intervalObj=TimerManager.setInterval(interval, this, null);
		return true;
	}
	
	public boolean terminate(){
		synchronized(peers){
			isTerminate=true;
			for(PaPeer peer:peers){
				peer.unsubscribe();
			}
			peers.clear();
			subnamePeersMap.clear();
		}
		return false;
	}

	@Override
	public void onTimer(Object arg0) {
		palet.onTimer(this);
	}

}
