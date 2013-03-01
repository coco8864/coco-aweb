package naru.aweb.pa;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.timer.TimerManager;

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
		palet.init(this);
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
			}
			subnamePeers.add(peer);
		}
		palet.onSubscribe(peer);
	}
	
	boolean onUnubscribe(PaPeer peer,String reason){
		boolean exist=false;
		String subname=peer.getSubname();
		synchronized(peers){
			exist=peers.remove(peer);
			Set<PaPeer> subnamePeers=subnamePeersMap.get(subname);
			if(subnamePeers!=null){
				subnamePeers.remove(peer);
			}
		}
		if(exist){
			palet.onUnsubscribe(peer,reason);
			return true;
		}else{
			return false;
		}
	}
	
	void onPublish(PaPeer peer,Object data){
		if(data instanceof String){
			palet.onPublishText(peer,(String)data);
		}else if(data instanceof Map){
			palet.onPublishObj(peer,(Map)data);
		}else if(data instanceof List){
			palet.onPublishArray(peer,(List)data);
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
	
	
	private int messageJson(Envelope envelope,Set<PaPeer> peers,Set<PaPeer> exceptPeers){
		int count=0;
		for(PaPeer peer:peers){
			if(exceptPeers!=null && exceptPeers.contains(peer)){
				continue;
			}
			String subname=peer.getSubname();
			peer.sendJson(envelope.getSendJson(subname));
			count++;
		}
		return count;
	}
	
	private int messageBin(Envelope envelope,Set<PaPeer> peers,Set<PaPeer> exceptPeers){
		int count=0;
		for(PaPeer peer:peers){
			if(exceptPeers!=null && exceptPeers.contains(peer)){
				continue;
			}
			String subname=peer.getSubname();
			peer.sendBinary(envelope.createSendAsyncBuffer(subname));
			count++;
		}
		return count;
	}
	
	
	
	/**
	 * 
	 * @param data
	 * @param peers
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,Set<PaPeer> peers,Set<PaPeer> exceptPeers){
		if(peers==null){
			return 0;
		}
		Map message=new HashMap();
		message.put(PaSession.KEY_TYPE, PaSession.TYPE_MESSAGE);
		message.put(PaSession.KEY_MESSAGE, data);
		message.put(PaSession.KEY_QNAME, qname);
		//subnameだけはここでは決められない
		Envelope envelope=Envelope.pack(message);
		int count=0;
		if(envelope.isBinary()){
			count=messageBin(envelope, peers, exceptPeers);
		}else{
			count=messageJson(envelope, peers, exceptPeers);
		}
		envelope.unref();
		return count;
	}
	
	@Override
	public int message(Object data, String subname, PaPeer exceptPeer) {
		return message(data,getPeers(subname),exceptPeer);
	}

	@Override
	public int message(Object data, Set<PaPeer> peers, PaPeer exceptPeer) {
		Set<PaPeer> exceptPeers=new HashSet<PaPeer>();
		exceptPeers.add(exceptPeer);
		return message(data,peers,exceptPeers);
	}
	
	public String getQname(){
		return qname;
	}
	
	public Set<PaPeer> getPeers(){
		return Collections.unmodifiableSet(peers);
	}
	
	public Set<PaPeer> getPeers(String subname){
		Set<PaPeer> subnamePeers=subnamePeersMap.get(subname);
		if(subnamePeers==null){
			return null;
		}
		return Collections.unmodifiableSet(subnamePeers);
	}
	
	public boolean setInterval(long interval){
		if(intervalObj!=null){
			TimerManager.clearInterval(interval);
		}
		if(interval<0){
			return false;
		}
		intervalObj=TimerManager.setInterval(interval, this, null);
		return true;
	}
	
	public boolean terminate(){
		synchronized(peers){
			isTerminate=true;
			for(PaPeer peer:peers){
				peer.unsubscribe("terminate");
			}
			peers.clear();
			subnamePeersMap.clear();
		}
		palet.term(null);
		return false;
	}

	@Override
	public void onTimer(Object arg0) {
		palet.onTimer();
	}

}
