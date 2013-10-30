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

/**
 * linklet=端末間の接続情報を保持するオブジェクト<br/>
 * サーバ側の構成<br/>
 * qname<br/>
 * subname<br/>
 * 端末情報の構成<br/>
 * appSid ... loginセションid loginId,pathは付随する情報である。<br/>
 * bid　... ブラウザセションid(loginセションは同じでも表示されるタブ毎に違うidとなる)<br/>
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
	
	/**
	 * セション情報を初期化します。<br/>
	 * アプリからの呼び出し不可<br/>
	 */
	public void releaseSession(){
		linkSession=null;
	}
	
	/**
	 * このアプリのpath情報を取得します。<br/>
	 * @return path
	 */
	public String getPath() {
		return linkSession.getPath();
	}

	/**
	 * 当該アプリケーション固有のセッションidを取得します。<br/>
	 * @return appSid
	 */
	public String getAppSid() {
		return linkSession.getAppSid();
	}

	/**
	 * ログインidを取得します。<br/>
	 * @return　loginId
	 */
	public String getLoginId() {
		return linkSession.getLoginId();
	}

	/**
	 * 当該ユーザが所有するrole群を取得します。<br/>
	 * @return　roles
	 */
	public List<String> getRoles() {
		return linkSession.getRoles();
	}

	/**
	 * 同一セション内、ブラウザのタブ単位のidを取得します。<br/>
	 * @return　bid
	 */
	public Integer getBid() {
		return linkSession.getBid();
	}

	/**
	 * websocketで通信中か否かを取得します。<br/>
	 * falseの場合、xhr通信で接続している。<br/>
	 * @return　isWs
	 */
	public boolean isWs() {
		return linkSession.isWs();
	}
	
	/**
	 * browserからのメッセージか否か<br/>
	 * server側のアプリからpublishされた場合は、falseを返却<br/>
	 * この場合、path,appSid,loginId,roles,bid,isWsは取得できない。
	 * @return
	 */
	public boolean isLinkSession(){
		return (linkSession!=null);
	}
	
	/* API */
	/**
	 * 当該端末にメッセージを送信します<br/>
	 * サーバ側アプリからのmessageの場合は、linkletにpublishする</br>
	 * @param message 送信データ
	 */
	public void message(Object message){
		if(linkSession==null){
			linkManager.publish(qname, subname, message);
			return;
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
	}
	
	/**
	 * 当該端末にバイナリメッセージを送信します<br/>
	 * サーバ側アプリからのmessageの場合は、linkletにpublishする</br>
	 * @param message 送信データ
	 */
	public void sendBinary(AsyncBuffer data){
		if(linkSession==null){
			linkManager.publish(qname, subname, data);
			return;
		}
		linkSession.sendBinary(data);
	}
	
	/* API */
	private long downloadSec=0;
	private synchronized String getDownloadKey(){
		downloadSec++;
		return "PP"+downloadSec;
	}
	
	/**
	 * blobデータをブラウザにダウンロードさせます<br/>
	 * @param blob 送信データ
	 */
	public void download(Blob blob){
		JSONObject json=new JSONObject();
		json.put(LinkSession.KEY_TYPE, LinkSession.TYPE_DOWNLOAD);
		json.put(LinkSession.KEY_KEY, getDownloadKey());
		json.put(LinkSession.KEY_QNAME, qname);
		json.put(LinkSession.KEY_SUBNAME, subname);
		linkSession.download(json,blob);
	}
	
	/**
	 * blobデータをブラウザにダウンロードさせます<br/>
	 * アプリケーションからは利用しない。<br/>
	 * @param message
	 * @param blob
	 */
	public void download(JSONObject message,Blob blob){
		linkSession.download(message,blob);
	}
	
	/**
	 * JSONObjectを端末に送信します<br/>
	 * @param data 送信データ
	 */
	public void sendJson(JSONObject data){
		linkSession.sendJson(data);
	}
	
	/**
	 * qnameを取得します。<br/>
	 * @return qname
	 */
	public String getQname() {
		return qname;
	}

	/**
	 * subnameを取得します。<br/>
	 * @return subname
	 */
	public String getSubname() {
		return subname;
	}

	/* API */
	/**
	 * サーバ側からこのlinkPeerの端末をunsubscribeします。<br/>
	 * @return unsubscribeの可否
	 */
	public boolean unsubscribe(){
		return unsubscribe("api");
	}
	
	/**
	 * サーバ側からこのlinkPeerの端末をunsubscribeします。<br/>
	 * @param reason 原因
	 * @return unsubscribeの可否
	 */
	public boolean unsubscribe(String reason){
		/* clientにunsubscribe(subscribe失敗)を通知する */
		if( linkSession!=null && linkSession.unsubscribeByPeer(this) ){
			/* linkletにonUnsubscribeを通知する */
			LinkletWrapper linkletWrapper=linkManager.getLinkletWrapper(qname);
			return linkletWrapper.onUnubscribe(this,reason);
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
