package naru.aweb.link;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.timer.TimerManager;
import naru.aweb.link.api.Blob;
import naru.aweb.link.api.LinkMsg;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.link.api.LinkletCtx;

public class LinkletWrapper implements LinkletCtx,Timer{
	private static Logger logger=Logger.getLogger(LinkSession.class);
	private Object intervalObj=null;
	private String qname;
	private Linklet rootLinklet;
	private Map<String,Linklet> subscribers=new HashMap<String,Linklet>();
	private boolean isTerminate=false;
	private Set<LinkPeer> peers=new HashSet<LinkPeer>();
	private Map<String,Set<LinkPeer>> subnamePeersMap=new HashMap<String,Set<LinkPeer>>();
	private Map<String,Object> attribute=new HashMap<String,Object>();//同じqname配下のlinklet間で情報を共有する

	public LinkletWrapper(String qname,Linklet rootLinklet){
		this(qname, rootLinklet, null);
	}
	public LinkletWrapper(String qname,Linklet rootLinklet,Map<String,Linklet> subscribers){
		this.qname=qname;
		this.rootLinklet=rootLinklet;
		if(rootLinklet!=null){
			rootLinklet.init(qname,null,this);
		}
		if(subscribers!=null){
			for(String subname:subscribers.keySet()){
				Linklet linklet=subscribers.get(subname);
				linklet.init(qname,subname,this);
				this.subscribers.put(subname, linklet);
			}
		}
		isTerminate=false;
	}
	
	private Linklet getLinklet(LinkPeer peer){
		String subname=peer.getSubname();
		Linklet linklet=null;
		if(subname!=null){
			linklet=subscribers.get(subname);
		}
		if(linklet==null){
			linklet=rootLinklet;
		}
		return linklet;
	}
	
	boolean onSubscribe(LinkPeer peer){
		String subname=peer.getSubname();
		synchronized(peers){
			if(isTerminate){
				logger.warn("onSubscribe aleady stop");
				return false;
			}
		}
		Linklet linklet=getLinklet(peer);
		if(linklet==null){
			return false;
		}
		boolean isAdd;
		try{
			isAdd=linklet.onSubscribe(peer);
		}catch(Throwable t){
			logger.warn("onSubscribe error.",t);
			return false;
		}
		if(isAdd==false){
			return false;
		}
		synchronized(peers){
			peers.add(peer);
			Set<LinkPeer> subnamePeers=subnamePeersMap.get(subname);
			if(subnamePeers==null){
				subnamePeers=new HashSet<LinkPeer>();
				subnamePeersMap.put(subname,subnamePeers);
			}
			subnamePeers.add(peer);
		}
		return true;
	}
	
	public boolean onUnubscribe(LinkPeer peer,String reason){
		boolean exist=false;
		String subname=peer.getSubname();
		synchronized(peers){
			exist=peers.remove(peer);
			Set<LinkPeer> subnamePeers=subnamePeersMap.get(subname);
			if(subnamePeers!=null){
				subnamePeers.remove(peer);
			}
		}
		if(!exist){
			return false;
		}
		Linklet linklet=getLinklet(peer);
		try{
			linklet.onUnsubscribe(peer,reason);
		}catch(Throwable t){
			logger.warn("unUnsbscribe error.",t);
			return false;
		}
		return true;
	}
	
	void onPublish(LinkPeer peer,Object data){
		Linklet linklet=getLinklet(peer);
		if(data instanceof String){/*　送信データが文字列の場合*/
			linklet.onPublish(peer,(String)data);
			return;
		}
		LinkMsg msg=null;
		if(data instanceof LinkMsg){
			msg=(LinkMsg)data;
		}else if(data instanceof Map){
			msg=LinkMsg.wrap((Map)data);
		}else{
			logger.error("onPublish data type" + data.getClass().getName());
			return;
		}
		/*　送信データがObjectの場合*/		
		try{
			linklet.onPublish(peer,msg);
		}catch(Throwable t){
			logger.warn("onPublish error.",t);
		}finally{
			msg.unref();
		}
	}
	
	/**
	 * 全peerにmessageを送信する
	 * @param data
	 * @return
	 */
	public int message(Object data){
		return message(data,getPeers(),(LinkPeer)null);
	}
	
	/**
	 * subnameで指定されたpeerにmessageを送信する
	 * @param data
	 * @param subname
	 * @return
	 */
	public int message(Object data,String subname){
		return message(data,getPeers(subname),(LinkPeer)null);
	}
	
	/**
	 * subnameで指定されたpeer(excptPeersを除く)にmessageを送信する
	 * @param data
	 * @param subname
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,String subname,Set<LinkPeer> exceptPeers){
		return message(data,getPeers(subname),exceptPeers);
	}
	
	private int messageJson(Envelope envelope,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers){
		int count=0;
		for(LinkPeer peer:peers){
			if(exceptPeers!=null && exceptPeers.contains(peer)){
				continue;
			}
			String subname=peer.getSubname();
			peer.sendJson(envelope.getSendJson(subname));
			count++;
		}
		return count;
	}
	
	private int messageBin(Envelope envelope,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers){
		int count=0;
		for(LinkPeer peer:peers){
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
	public int message(Object data,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers){
		if(peers==null){
			return 0;
		}
		Map message=new HashMap();
		message.put(LinkSession.KEY_TYPE, LinkSession.TYPE_MESSAGE);
		message.put(LinkSession.KEY_MESSAGE, data);
		message.put(LinkSession.KEY_QNAME, qname);
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
	public int download(Blob blob, Set<LinkPeer> peers, Set<LinkPeer> exceptPeers) {
		if(peers==null){
			return 0;
		}
		Map message=new HashMap();
		message.put(LinkSession.KEY_TYPE, LinkSession.TYPE_DOWNLOAD);
		message.put(LinkSession.KEY_KEY, getDownloadKey());
		message.put(LinkSession.KEY_QNAME, qname);
		Envelope envelope=Envelope.pack(message);
		int count=0;
		for(LinkPeer peer:peers){
			if(exceptPeers!=null && exceptPeers.contains(peer)){
				continue;
			}
			String subname=peer.getSubname();
			peer.download(envelope.getSendJson(subname),blob);
			count++;
		}
		envelope.unref(true);
		//blob.unref();
		return count;
	}
	
	@Override
	public int message(Object data, String subname, LinkPeer exceptPeer) {
		return message(data,getPeers(subname),exceptPeer);
	}

	@Override
	public int message(Object data, Set<LinkPeer> peers, LinkPeer exceptPeer) {
		Set<LinkPeer> exceptPeers=new HashSet<LinkPeer>();
		exceptPeers.add(exceptPeer);
		return message(data,peers,exceptPeers);
	}
	
	public String getQname(){
		return qname;
	}
	
	public Set<LinkPeer> getPeers(){
		return Collections.unmodifiableSet(peers);
	}
	
	public Set<LinkPeer> getPeers(String subname){
		Set<LinkPeer> subnamePeers=subnamePeersMap.get(subname);
		if(subnamePeers==null){
			return null;
		}
		return Collections.unmodifiableSet(subnamePeers);
	}
	
	public boolean setInterval(long interval){
		if(intervalObj!=null){
			TimerManager.clearInterval(intervalObj);
		}
		if(interval<0){
			return false;
		}
		intervalObj=TimerManager.setInterval(interval, this, null);
		return true;
	}
	
	private LinkPeer getTerminalPeer(){
		synchronized(peers){
			isTerminate=true;
			for(LinkPeer peer:peers){
				return peer;
			}
		}
		return null;
	}
	
	public boolean terminate(){
		while(true){
			LinkPeer peer=getTerminalPeer();
			if(peer==null){
				break;
			}
			peer.unsubscribe("terminate");
		}
		for(Linklet linklet:subscribers.values()){
			linklet.term(null);
		}
		rootLinklet.term(null);
		return false;
	}

	@Override
	public void onTimer(Object arg0) {
		rootLinklet.onTimer();
		for(Linklet linklet:subscribers.values()){
			linklet.onTimer();
		}
	}

	/**
	 * 同じqname間で情報共有する仕組み
	 */
	@Override
	public Linklet getLinklet(String subname) {
		if(subname==null){
			return rootLinklet;
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
