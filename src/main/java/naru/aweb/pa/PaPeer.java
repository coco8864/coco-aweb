package naru.aweb.pa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import naru.async.AsyncBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSONObject;

public class PaPeer extends PoolBase{
	private static PaManager paManager=PaManager.getInstance();
	
	public static PaPeer create(PaSession paSession,String qname,String subname){
		PaPeer peer=(PaPeer)PoolManager.getInstance(PaPeer.class);
		peer.paSession=paSession;
		peer.qname=qname;
		peer.subname=subname;
		return peer;
	}
	
	private PaSession paSession;
	private String qname;//queue名
	private String subname;//クライアントid(認証idが同じでもブラウザの違い等により、clientは別のpeerで接続できる)
	
	public String getPath() {
		return paSession.getPath();
	}

	public String getAppId() {
		return paSession.getAppId();
	}

	public String getLoginId() {
		return paSession.getLoginId();
	}

	public List<String> getRoles() {
		return paSession.getRoles();
	}

	public Integer getBid() {
		return paSession.getBid();
	}

	public boolean isWs() {
		return paSession.isWs();
	}
	
	/* API */
	public boolean message(Object message){
		JSONObject json=new JSONObject();
		json.put(PaSession.KEY_TYPE, PaSession.TYPE_MESSAGE);
		json.put(PaSession.KEY_MESSAGE, message);
		json.put(PaSession.KEY_QNAME, qname);
		json.put(PaSession.KEY_SUBNAME, subname);
		//subnameだけはここでは決められない
		Envelope envelope=Envelope.pack(json);
		if(envelope.isBinary()){
			paSession.sendBinary(envelope.createSendAsyncBuffer(null));
		}else{
			paSession.sendJson(envelope.getSendJson(null));
		}
		envelope.unref(true);
		return true;
	}
	
	public void sendBinary(AsyncBuffer data){
		paSession.sendBinary(data);
	}
	
	/* API */
	private long downloadSec=0;
	private synchronized String getDownloadKey(){
		downloadSec++;
		return "PP"+downloadSec;
	}
	
	public boolean download(Blob blob){
		JSONObject json=new JSONObject();
		json.put(PaSession.KEY_TYPE, PaSession.TYPE_DOWNLOAD);
		json.put(PaSession.KEY_KEY, getDownloadKey());
		json.put(PaSession.KEY_QNAME, qname);
		json.put(PaSession.KEY_SUBNAME, subname);
		paSession.download(json,blob);
		return true;
	}
	
	public boolean download(JSONObject message,Blob blob){
		paSession.download(message,blob);
		return true;
	}
	
	public void sendJson(JSONObject data){
		paSession.sendJson(data);
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
	
	boolean unsubscribe(String reason){
		/* clientにunsubscribe(subscribe失敗)を通知する */
		if( paSession.unsubscribeByPeer(this) ){
			/* paletにonUnsubscribeを通知する */
			PaletWrapper paletWrapper=paManager.getPaletWrapper(qname);
			return paletWrapper.onUnubscribe(this,reason);
		}else{
			return false;
		}
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 0;//super.hashCode();
		result = prime * result
				+ ((paSession == null) ? 0 : paSession.hashCode());
		result = prime * result + ((qname == null) ? 0 : qname.hashCode());
		result = prime * result + ((subname == null) ? 0 : subname.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
//		if (!super.equals(obj))
//			return false;
		if (getClass() != obj.getClass())
			return false;
		PaPeer other = (PaPeer) obj;
		if (paSession == null) {
			if (other.paSession != null)
				return false;
		} else if (!paSession.equals(other.paSession))
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
