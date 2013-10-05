package naru.aweb.link.api;

import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.link.Envelope;
import naru.aweb.link.LinkManager;
import naru.aweb.link.LinkSession;
import naru.aweb.link.LinkletWrapper;
import net.sf.json.JSONObject;

/*
 * linkletから端末を特定するオブジェクト<br/>
 * セション情報+qname+subnameで構成される<br/>
 * セション情報は、ログイン情報+ブラウザ情報+Path情報で構成される<br/>
 * ブラウザ情報は、
 */
public class LinkPeer extends PoolBase{
	public static LinkPeer create(LinkManager linkManager,LinkSession linkSession,String qname,String subname){
		LinkPeer peer=(LinkPeer)PoolManager.getInstance(LinkPeer.class);
		peer.linkSession=linkSession;
		peer.linkManager=linkManager;
		if(linkSession!=null){
			peer.linkSessionId=linkSession.getPoolId();
		}else{
			peer.linkSessionId=0;
		}
		peer.qname=qname;
		peer.subname=subname;
		return peer;
	}
	
	private LinkManager linkManager;
	private LinkSession linkSession;
	private long linkSessionId;
	private String qname;//queue名
	private String subname;//subname
	
	public void releaseSession(){
		linkSession=null;
	}
	
	public String getPath() {
		return linkSession.getPath();
	}

	public String getAppSid() {
		return linkSession.getAppSid();
	}

	public String getLoginId() {
		return linkSession.getLoginId();
	}

	public List<String> getRoles() {
		return linkSession.getRoles();
	}

	public Integer getBid() {
		return linkSession.getBid();
	}

	public boolean isWs() {
		return linkSession.isWs();
	}
	
	/**
	 * browserからのメッセージか否か
	 * server側のアプリからpublishされた場合は、falseを返却
	 * @return
	 */
	public boolean isLinkSession(){
		return (linkSession!=null);
	}
	
	/* API */
	public boolean message(Object message){
		if(linkSession==null){
			linkManager.publish(qname, subname, message);
			return true;
		}
		JSONObject json=new JSONObject();
		json.put(LinkSession.KEY_TYPE, LinkSession.TYPE_MESSAGE);
		json.put(LinkSession.KEY_MESSAGE, message);
		json.put(LinkSession.KEY_QNAME, qname);
		json.put(LinkSession.KEY_SUBNAME, subname);
		//subnameだけはここでは決められない
		Envelope envelope=Envelope.pack(json);
		if(envelope.isBinary()){
			linkSession.sendBinary(envelope.createSendAsyncBuffer(null));
		}else{
			linkSession.sendJson(envelope.getSendJson(null));
		}
		envelope.unref(true);
		return true;
	}
	
	public void sendBinary(AsyncBuffer data){
		linkSession.sendBinary(data);
	}
	
	/* API */
	private long downloadSec=0;
	private synchronized String getDownloadKey(){
		downloadSec++;
		return "PP"+downloadSec;
	}
	
	public boolean download(Blob blob){
		JSONObject json=new JSONObject();
		json.put(LinkSession.KEY_TYPE, LinkSession.TYPE_DOWNLOAD);
		json.put(LinkSession.KEY_KEY, getDownloadKey());
		json.put(LinkSession.KEY_QNAME, qname);
		json.put(LinkSession.KEY_SUBNAME, subname);
		linkSession.download(json,blob);
		return true;
	}
	
	public boolean download(JSONObject message,Blob blob){
		linkSession.download(message,blob);
		return true;
	}
	
	public void sendJson(JSONObject data){
		linkSession.sendJson(data);
	}
	
	public String getQname() {
		return qname;
	}

	public String getSubname() {
		return subname;
	}

	/* API */
	public boolean unsubscribe(){
		return unsubscribe("api");
	}
	
	public boolean unsubscribe(String reason){
		/* clientにunsubscribe(subscribe失敗)を通知する */
		if( linkSession!=null && linkSession.unsubscribeByPeer(this) ){
			/* paletにonUnsubscribeを通知する */
			LinkletWrapper paletWrapper=linkManager.getLinkletWrapper(qname);
			return paletWrapper.onUnubscribe(this,reason);
		}else{
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 0;//super.hashCode();
		result = prime * result	+ (int)linkSessionId;
		result = prime * result + ((qname == null) ? 0 : qname.hashCode());
		result = prime * result + ((subname == null) ? 0 : subname.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (getClass() != obj.getClass())
			return false;
		LinkPeer other = (LinkPeer) obj;
		if (linkSessionId != other.linkSessionId)
			return false;
		if (qname == null) {
			if (other.qname != null)
				return false;
		} else if (!qname.equals(other.qname))
			return false;
		if (subname == null) {
			if (other.subname != null)
				return false;
		} else if (!subname.equals(other.subname))
			return false;
		return true;
	}
}
