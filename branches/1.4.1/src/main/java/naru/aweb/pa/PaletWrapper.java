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
	private Palet rootPalet;//root palet
	private Map<String,Palet> subscribers=new HashMap<String,Palet>();
	private boolean isTerminate=false;
	private Set<PaPeer> peers=new HashSet<PaPeer>();
	private Map<String,Set<PaPeer>> subnamePeersMap=new HashMap<String,Set<PaPeer>>();
	private Map<String,Object> attribute=new HashMap<String,Object>();//同じqname配下のpalet間で情報を共有する

	public PaletWrapper(String qname,Palet rootPalet){
		this(qname, rootPalet, null);
	}
	public PaletWrapper(String qname,Palet rootPalet,Map<String,Palet> subscribers){
		this.qname=qname;
		this.rootPalet=rootPalet;
		rootPalet.init(qname,null,this);
		if(subscribers!=null){
			for(String subname:subscribers.keySet()){
				Palet palet=subscribers.get(subname);
				palet.init(qname,subname,this);
				this.subscribers.put(subname, palet);
			}
		}
		isTerminate=false;
	}
	
	private Palet getPalet(PaPeer peer){
		String subname=peer.getSubname();
		Palet palet=null;
		if(subname!=null){
			palet=subscribers.get(subname);
		}
		if(palet!=null){
			return palet;
		}
		return rootPalet;
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
		Palet palet=getPalet(peer);
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
			Palet palet=getPalet(peer);
			palet.onUnsubscribe(peer,reason);
			return true;
		}else{
			return false;
		}
	}
	
	void onPublish(PaPeer peer,Object data){
		Palet palet=getPalet(peer);
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
	
	private long downloadSec=0;
	private synchronized String getDownloadKey(){
		downloadSec++;
		return "PW"+downloadSec;
	}
	
	@Override
	public int download(Blob blob, Set<PaPeer> peers, Set<PaPeer> exceptPeers) {
		if(peers==null){
			return 0;
		}
		Map message=new HashMap();
		message.put(PaSession.KEY_TYPE, PaSession.TYPE_DOWNLOAD);
		message.put(PaSession.KEY_KEY, getDownloadKey());
		message.put(PaSession.KEY_QNAME, qname);
		Envelope envelope=Envelope.pack(message);
		int count=0;
		for(PaPeer peer:peers){
			if(exceptPeers!=null && exceptPeers.contains(peer)){
				continue;
			}
			String subname=peer.getSubname();
			peer.download(envelope.getSendJson(subname),blob);
			count++;
		}
		envelope.unref(true);
		blob.unref();
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
	
	private PaPeer getTerminalPeer(){
		synchronized(peers){
			isTerminate=true;
			for(PaPeer peer:peers){
				return peer;
			}
		}
		return null;
	}
	
	public boolean terminate(){
		while(true){
			PaPeer peer=getTerminalPeer();
			if(peer==null){
				break;
			}
			peer.unsubscribe("terminate");
		}
		for(Palet palet:subscribers.values()){
			palet.term(null);
		}
		rootPalet.term(null);
		return false;
	}

	@Override
	public void onTimer(Object arg0) {
		rootPalet.onTimer();
	}

	/**
	 * 同じqname間で情報共有する仕組み
	 */
	@Override
	public Palet getPalet(String subname) {
		if(subname==null){
			return rootPalet;
		}
		return subscribers.get(subname);
	}
	
	public Object getAttribute(String name){
		return attribute.get(name);
	}
	
	public void setAttribute(String name, Object value) {
		attribute.put(name, value);
	}
}
