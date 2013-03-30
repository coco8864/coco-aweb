package naru.aweb.pa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import naru.async.AsyncBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.LogoutEvent;
import naru.aweb.config.User;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.log4j.Logger;

public class PaSession extends PoolBase implements LogoutEvent{
	/* req/res共通のkey */
	public static final String KEY_TYPE="type";
	public static final String KEY_BID="bid";
	public static final String KEY_TOKEN="token";
	public static final String KEY_QNAME="qname";
	public static final String KEY_SUBNAME="subname";
	public static final String KEY_PALET_CLASS_NAME="paletClassName";
	public static final String KEY_KEY="key";
	
	/* request type */
	public static final String TYPE_NEGOTIATE="negotiate";
	public static final String TYPE_PUBLISH="publish";
	public static final String TYPE_SUBSCRIBE="subscribe";
	public static final String TYPE_UNSUBSCRIBE="unsubscribe";
	public static final String TYPE_DEPLOY="deploy";
	public static final String TYPE_UNDEPLOY="undeploy";
	public static final String TYPE_QNAMES="qnames";
	public static final String TYPE_CLOSE="close";
	
	/* resだけのkey */
	public static final String KEY_RESULT="result";
	public static final String KEY_REQUEST_TYPE="requestType";
	public static final String KEY_MESSAGE="message";
	
	/* response type */
	public static final String TYPE_RESPONSE="response";
	public static final String TYPE_MESSAGE="message";
	public static final String TYPE_DOWNLOAD="download";
	public static final String RESULT_SUCCESS="success";
	public static final String RESULT_ERROR="error";
	
	private static Logger logger=Logger.getLogger(PaSession.class);
	
	static PaSession create(String path,Integer bid,boolean isWs,AuthSession authSession){
		PaSession paSession=(PaSession)PoolManager.getInstance(PaSession.class);
		paSession.path=path;
		paSession.paManager=PaManager.getInstance(path);
		paSession.bid=bid;
		paSession.isWs=isWs;
		
		paSession.appId=authSession.getAppId();
		User user=authSession.getUser();
		paSession.loginId=user.getLoginId();
		paSession.roles=Collections.unmodifiableList(user.getRolesList());
		return paSession;
	}
	
	@Override
	public void recycle() {
		peers.clear();
		qdata.clear();
		qbin.clear();
		wsHandler=null;
		xhrHandler=null;
		appId=loginId=null;
		roles=null;
		bid=null;
		isWs=false;
		super.recycle();
	}
	private PaManager paManager;
	
	private String path;//接続path
	
	//authSession由来
	private String appId;//認証id当該セションのid
	private String loginId;
	private List<String> roles;
	
	private Integer bid;//brawserId
	private boolean isWs;
	private Map<PaPeer,PaPeer> peers=new HashMap<PaPeer,PaPeer>();
	private PaHandler wsHandler;
	private PaHandler xhrHandler;
	private List<Object> qdata=new ArrayList<Object>();
	private List<AsyncBuffer> qbin=new ArrayList<AsyncBuffer>();
	
	private void wsSend(PaHandler handler,Object data){
		if(data instanceof String){
			handler.postMessage((String)data);
		}else if(data instanceof AsyncBuffer){
			handler.postMessage((AsyncBuffer)data);
		}else{//JSONの場合
			handler.postMessage(data.toString());
		}
		//TODO impliment
	}
	
	private void xhrSend(PaHandler handler,List<Object> qdata){
		if(qdata.size()==0){
			handler.completeResponse("205");
		}else{
			handler.responseJson(qdata);
		}
		handler.setDoneXhr();
	}
	
	public synchronized void setupWsHandler(PaHandler orgHandler,PaHandler handler){
		if(orgHandler!=null && this.wsHandler!=orgHandler){
			return;
		}
		if(handler==null){
			this.wsHandler=null;
			return;
		}else{
			for(Object data:qdata){
				wsSend(handler,data);
			}
			qdata.clear();
			this.wsHandler=handler;
			return;
		}
	}
	
	/**
	 * 
	 * @param handler
	 * @return responseした場合true
	 */
	public synchronized void setupXhrHandler(PaHandler handler){
		if(this.xhrHandler!=null && handler==null){
			this.xhrHandler=null;
			return;
		}else if(this.xhrHandler==null && handler!=null){
			if(qdata.size()<=0){
				this.xhrHandler=handler;
				return;
			}
			xhrSend( handler, qdata);
			qdata.clear();
			handler.setDoneXhr();
			return;
		}
		logger.error("setupXhrHandler");
	}
	
	/* レスポンスするオブジェクトがなかったとしても一定時間後にはレスポンスする */
	public synchronized void xhrTerminal(PaHandler handler){
		if(handler.doneXhr()){
			return;
		}
		if(handler!=xhrHandler){
			return;
		}
		xhrSend(xhrHandler,qdata);
		qdata.clear();
		xhrHandler=null;
	}
	
	private JSONObject makeResponseJson(String result,String requestType,String qname,String subname,Object message){
		JSONObject json=new JSONObject();
		json.put(KEY_TYPE, TYPE_RESPONSE);
		json.put(KEY_RESULT, result);
		json.put(KEY_REQUEST_TYPE, requestType);
		json.put(KEY_QNAME, qname);
		json.put(KEY_SUBNAME, subname);
		json.put(KEY_MESSAGE, message);
		return json;
	}
	
	public void sendError(String requestType,String qname,String subname,Object message){
		sendJson(makeResponseJson(RESULT_ERROR,requestType,qname, subname,  message));
	}
	
	public void sendSuccess(String requestType,String qname,String subname,Object message){
		sendJson(makeResponseJson(RESULT_SUCCESS,requestType,qname, subname, message));
	}
	
	private Map<String,Blob>downloadBlobs=Collections.synchronizedMap(new HashMap<String,Blob>());
	
	public synchronized void download(JSONObject message,Blob blob){
		downloadBlobs.put(message.getString(KEY_KEY),blob);
		blob.ref();
		sendJson(message);
	}
	
	public synchronized void sendJson(JSONObject data){
		if(this.wsHandler!=null){
			this.wsHandler.postMessage(data.toString());
		}else if(this.xhrHandler!=null){
			qdata.add(data);
			xhrSend(this.xhrHandler,qdata);
			qdata.clear();
			this.xhrHandler=null;
		}else{
			qdata.add(data);
		}
	}
	
	public synchronized void sendBinary(AsyncBuffer data){
		if(this.wsHandler!=null){
			this.wsHandler.postMessage(data);
		}else{
			qbin.add(data);
		}
	}
	
	public void subscribe(JSONObject msg){
		String qname=msg.getString(KEY_QNAME);
		String subname=msg.optString(KEY_SUBNAME,null);
		PaletWrapper paletWrapper=paManager.getPaletWrapper(qname);
		if(paletWrapper==null){
			sendError(TYPE_SUBSCRIBE,qname, subname,null);
			return;
		}
		PaPeer keyPeer=PaPeer.create(paManager,this, qname, subname);
		synchronized(peers){
			PaPeer peer=peers.get(keyPeer);
			if(peer!=null){//すでにsubscribe済み処理はない
				keyPeer.unref(true);
				return;
			}
			peers.put(keyPeer, keyPeer);
		}
		paletWrapper.onSubscribe(keyPeer);
	}
	
	public void unsubscribe(JSONObject msg){
		String qname=msg.getString(KEY_QNAME);
		String subname=msg.optString(KEY_SUBNAME,null);
		PaPeer keyPeer=PaPeer.create(paManager,this, qname, subname);
		if( !unsubscribeFromWrapper(keyPeer) ){
			sendError(TYPE_UNSUBSCRIBE,qname, subname,"not found peer");
			keyPeer.unref(true);
			return;
		}
		unsubscribeByPeer(keyPeer);
		keyPeer.unref(true);
	}
	
	/* API経由でのunsubscribe, clientにunsubscribe(subscribe失敗)を通知する */
	public boolean unsubscribeByPeer(PaPeer peer){
		String qname=peer.getQname();
		synchronized(peers){
			peer=peers.remove(peer);
			if(peer==null){//すでにunsubscribe済み処理はない
				return false;
			}
			peer.releaseSession();
		}
		//unsubscribeは過去に発行されたsubscribeが成功したとして通知する
		sendSuccess(TYPE_SUBSCRIBE,qname,peer.getSubname(),"unsubscribed by api");
		return true;
	}
	
	public void publish(Map msg){
		String qname=(String)msg.get(KEY_QNAME);
		String subname=(String)msg.get(KEY_SUBNAME);
		Object message=msg.get(KEY_MESSAGE);
		PaPeer keyPeer=PaPeer.create(paManager,this, qname, subname);
		PaletWrapper paletWrapper=paManager.getPaletWrapper(qname);
		if(subname==null){//送信元がないPublish 便宜的なPeer
			paletWrapper.onPublish(keyPeer, message);
			keyPeer.unref();
			return;
		}
		PaPeer peer=null;
		synchronized(peers){
			peer=peers.get(keyPeer);
//			keyPeer.unref(true);
			if(peer==null){//登録のないところからのpublish?
				sendError(TYPE_PUBLISH, qname, subname, "not found subname");
				return;
			}
		}
		paletWrapper.onPublish(peer, message);
	}
	
	/* PaletWrapperからのunsubcribe */
	private boolean unsubscribeFromWrapper(PaPeer peer){
		PaletWrapper paletWrapper=paManager.getPaletWrapper(peer.getQname());
		if(paletWrapper==null){
			return false;
		}
		paletWrapper.onUnubscribe(peer,"client");
		//正常にsubscribeを完了したという意味でsubscribe完了を復帰
		sendSuccess(TYPE_SUBSCRIBE,peer.getQname(), peer.getSubname(),"client");
		return true;
	}
	
	private PaPeer getPeer(){
		synchronized(peers){
			for(PaPeer peer:peers.keySet()){
				peers.remove(peer);
				return peer;
			}
		}
		return null;
	}
	
	public void close(){
		int count=0;
		while(true){
			PaPeer peer=getPeer();
			if(peer==null){
				break;
			}
			if(unsubscribeFromWrapper(peer)){
				count++;
			}
			peer.releaseSession();
		}
		sendSuccess(TYPE_CLOSE,null,null,"client:"+count);
	}
	
	public void qname(JSONObject req){
		Set<String> qnames=paManager.qnames();
		sendSuccess(TYPE_QNAMES,null, null,JSONSerializer.toJSON(qnames));
	}
	
	public void deploy(JSONObject req){
		if(!roles.contains("admin")){
			sendSuccess(TYPE_DEPLOY,null, null, "forbidden role");
			return;
		}
		String qname=req.getString(KEY_QNAME);
		String paletClassName=req.getString(KEY_PALET_CLASS_NAME);
		if(paManager.deploy(qname, paletClassName)!=null){
			sendSuccess(TYPE_DEPLOY,qname, null, null);
		}else{
			sendError(TYPE_DEPLOY,qname, null,"aleady deployed");
		}
	}
	
	public void undeploy(JSONObject req){
		if(!roles.contains("admin")){
			sendSuccess(TYPE_DEPLOY,null, null, "forbidden role");
			return;
		}
		String qname=req.getString(KEY_QNAME);
		if( paManager.undeploy(qname)!=null){
			sendSuccess(TYPE_UNDEPLOY,qname, null,null);
		}else{
			sendError(TYPE_UNDEPLOY,qname, null,"not found");
		}
	}
	
	@Override
	public void onLogout() {
		//logoffした場合の対処,登録されているpeerを全部unsubscribeする
		while(true){
			Object[] reqs=peers.values().toArray();
			if(reqs.length==0){
				break;
			}
			for(Object peer:reqs){
				if( ((PaPeer)peer).unsubscribe()==false){
					//TODO 意味不明だが停止時にループした
					logger.error("fail to onLogout for unsubscribe",new Exception());
					unref();//セションが終わったらPaSessionも必要なし
					return;
				}
			}
		}
		unref();//セションが終わったらPaSessionも必要なし
	}
	
	public String getPath() {
		return path;
	}

	public String getAppId() {
		return appId;
	}

	public String getLoginId() {
		return loginId;
	}

	public List<String> getRoles() {
		return roles;
	}
	
	public Integer getBid() {
		return bid;
	}

	public boolean isWs() {
		return isWs;
	}
	
	public Blob popDownloadBlob(String key){
		return downloadBlobs.remove(key);
	}
}
