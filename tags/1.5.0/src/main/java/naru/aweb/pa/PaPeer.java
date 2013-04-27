package naru.aweb.pa;

import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSONObject;

/*
 * PaPeerは、PaSessionとPaletWrapperに同一のものが保持されるようにする
 * 外れる場合には、同時にreleaseすると共にメンバpaSessionをnullにする
 * !!要チェック!!
 */
public class PaPeer extends PoolBase{
	public static PaPeer create(PaManager paManager,PaSession paSession,String qname,String subname){
		PaPeer peer=(PaPeer)PoolManager.getInstance(PaPeer.class);
		peer.paSession=paSession;
		peer.paManager=paManager;
		if(paSession!=null){
			peer.paSessionId=paSession.getPoolId();
		}else{
			peer.paSessionId=0;
		}
		peer.qname=qname;
		peer.subname=subname;
		return peer;
	}
	
	private PaManager paManager;
	private PaSession paSession;
	private long paSessionId;
	private String qname;//queue名
	private String subname;//クライアントid(認証idが同じでもブラウザの違い等により、clientは別のpeerで接続できる)
	
	public void releaseSession(){
		paSession=null;
	}
	
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
	
	/**
	 * browserからのメッセージか否か
	 * server側のアプリからpublishされた場合は、falseを返却
	 * @return
	 */
	public boolean isPaSession(){
		return (paSession!=null);
	}
	
	/* API */
	public boolean message(Object message){
		if(paSession==null){
			paManager.publish(qname, subname, message);
			return true;
		}
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
		if( paSession!=null && paSession.unsubscribeByPeer(this) ){
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
		result = prime * result	+ (int)paSessionId;
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
		if (paSessionId != other.paSessionId)
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
