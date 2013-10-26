package naru.aweb.link;

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
import naru.aweb.auth.User;
import naru.aweb.link.api.Blob;
import naru.aweb.link.api.LinkMsg;
import naru.aweb.link.api.LinkPeer;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.log4j.Logger;

public class LinkSession extends PoolBase implements LogoutEvent{
	/* req/res���ʂ�key */
	public static final String KEY_TYPE="type";
	public static final String KEY_BID="bid";
	public static final String KEY_TOKEN="token";
	public static final String KEY_QNAME="qname";
	public static final String KEY_SUBNAME="subname";
	public static final String KEY_SUBSCRIBERS="subscribers";
	//public static final String KEY_PALET_CLASS_NAME="paletClassName";
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
	
	/* res������key */
	public static final String KEY_RESULT="result";
	public static final String KEY_REQUEST_TYPE="requestType";
	public static final String KEY_MESSAGE="message";
	public static final String KEY_SESSION_TIME_LIMIT="sessionTimeLimit";
	public static final String KEY_OFFLINE_PASS_HASH="offlinePassHash";
	
	/* response type */
	public static final String TYPE_RESPONSE="response";
	public static final String TYPE_MESSAGE="message";
	public static final String TYPE_DOWNLOAD="download";
	public static final String RESULT_SUCCESS="success";
	public static final String RESULT_ERROR="error";
	
	private static Logger logger=Logger.getLogger(LinkSession.class);
	
	static LinkSession create(String path,LinkManager linkManager,Integer bid,boolean isWs,AuthSession authSession){
		LinkSession paSession=(LinkSession)PoolManager.getInstance(LinkSession.class);
		paSession.path=path;
		paSession.linkManager=linkManager;
		paSession.bid=bid;
		paSession.isWs=isWs;
		
		paSession.appSid=authSession.getSid();
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
		appSid=loginId=null;
		roles=null;
		bid=null;
		isWs=false;
		super.recycle();
	}
	private LinkManager linkManager;
	
	private String path;//�ڑ�path
	
	//authSession�R��
	private String appSid;//�F��id���Y�Z�V������id
	private String loginId;
	private List<String> roles;
	
	private Integer bid;//brawserId
	private boolean isWs;
	//�A�v���ɒʒm���铯��PaPeer�̃C���X�^���X����ӂɂ��邽�߂̏򐅊�
	private Map<LinkPeer,LinkPeer> peers=new HashMap<LinkPeer,LinkPeer>();
	private LinkHandler wsHandler;
	private LinkHandler xhrHandler;
	private List<Object> qdata=new ArrayList<Object>();
	private List<AsyncBuffer> qbin=new ArrayList<AsyncBuffer>();
	
	private void wsSend(LinkHandler handler,Object data){
		if(data instanceof String){
			handler.postMessage((String)data);
		}else if(data instanceof AsyncBuffer){
			handler.postMessage((AsyncBuffer)data);
		}else{//JSON�̏ꍇ
			handler.postMessage(data.toString());
		}
		//TODO impliment
	}
	
	private void xhrSend(LinkHandler handler,List<Object> qdata){
		if(qdata.size()==0){
			handler.completeResponse("204");
		}else{
			/*���X�|���X�ɗL�ׂȃR���e���c������΃A�N�Z�X���O�ɋL�^����*/
			handler.getAccessLog().setSkipPhlog(false);
			handler.responseJson(qdata);
		}
		handler.setDoneXhr();
	}
	
	public synchronized void setupWsHandler(LinkHandler orgHandler,LinkHandler handler){
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
	 * @return response�����ꍇtrue
	 */
	public synchronized void setupXhrHandler(LinkHandler handler){
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
	
	/* ���X�|���X����I�u�W�F�N�g���Ȃ������Ƃ��Ă���莞�Ԍ�ɂ̓��X�|���X���� */
	public synchronized void xhrResponse(LinkHandler handler){
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
		LinkletWrapper paletWrapper=linkManager.getLinkletWrapper(qname);
		if(paletWrapper==null){
			sendError(TYPE_SUBSCRIBE,qname, subname,null);
			return;
		}
		LinkPeer keyPeer=LinkPeer.create(linkManager,this, qname, subname);
		synchronized(peers){
			LinkPeer peer=peers.get(keyPeer);
			if(peer!=null){//���ł�subscribe�ςݏ����͂Ȃ�
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
		LinkPeer keyPeer=LinkPeer.create(linkManager,this, qname, subname);
		if( !unsubscribeFromWrapper(keyPeer) ){
			sendError(TYPE_UNSUBSCRIBE,qname, subname,"not found peer");
			keyPeer.unref(true);
			return;
		}
		unsubscribeByPeer(keyPeer);
		keyPeer.unref(true);
	}
	
	/* API�o�R�ł�unsubscribe, client��unsubscribe(subscribe���s)��ʒm���� */
	public boolean unsubscribeByPeer(LinkPeer peer){
		String qname=peer.getQname();
		synchronized(peers){
			peer=peers.remove(peer);
			if(peer==null){//���ł�unsubscribe�ςݏ����͂Ȃ�
				return false;
			}
			peer.releaseSession();
		}
		//unsubscribe�͉ߋ��ɔ��s���ꂽsubscribe�����������Ƃ��Ēʒm����
		sendSuccess(TYPE_SUBSCRIBE,qname,peer.getSubname(),"unsubscribed by api");
		peer.unref();
		return true;
	}
	
	public void publish(LinkMsg msg){
		String qname=msg.getString(KEY_QNAME);
		String subname=msg.getString(KEY_SUBNAME);
		Object message=msg.getMap(KEY_MESSAGE);
		if(message==null){
			message=msg.getString(KEY_MESSAGE);
		}else{
			((LinkMsg)message).ref();
		}
		msg.unref();
		LinkPeer keyPeer=LinkPeer.create(linkManager,this, qname, subname);
		LinkletWrapper paletWrapper=linkManager.getLinkletWrapper(qname);
		if(subname==null){//���M�����Ȃ�Publish �֋X�I��Peer
			paletWrapper.onPublish(keyPeer, message);
			keyPeer.unref();
			return;
		}
		LinkPeer peer=null;
		synchronized(peers){
			peer=peers.get(keyPeer);
			keyPeer.unref(true);
			if(peer==null){//�o�^�̂Ȃ��Ƃ��납���publish?
				sendError(TYPE_PUBLISH, qname, subname, "not found subname");
				return;
			}
		}
		paletWrapper.onPublish(peer, message);
	}
	
	/* PaletWrapper�����unsubcribe */
	private boolean unsubscribeFromWrapper(LinkPeer peer){
		LinkletWrapper paletWrapper=linkManager.getLinkletWrapper(peer.getQname());
		if(paletWrapper==null){
			return false;
		}
		paletWrapper.onUnubscribe(peer,"client");
		//�����subscribe�����������Ƃ����Ӗ���subscribe�����𕜋A
		sendSuccess(TYPE_SUBSCRIBE,peer.getQname(), peer.getSubname(),"client");
		return true;
	}
	
	private LinkPeer getPeer(){
		synchronized(peers){
			for(LinkPeer peer:peers.keySet()){
				peers.remove(peer);
				return peer;
			}
		}
		return null;
	}
	
	public void close(){
		int count=0;
		while(true){
			LinkPeer peer=getPeer();
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
		Set<String> qnames=linkManager.qnames();
		sendSuccess(TYPE_QNAMES,null, null,JSONSerializer.toJSON(qnames));
	}
	
	public void deploy(JSONObject req){
		if(!roles.contains("admin")){
			sendSuccess(TYPE_DEPLOY,null, null, "forbidden role");
			return;
		}
		String qname=req.getString(KEY_QNAME);
		JSONObject subscribers=req.getJSONObject(KEY_SUBSCRIBERS);
		if(linkManager.deploy(qname, subscribers)!=null){
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
		if( linkManager.undeploy(qname)!=null){
			sendSuccess(TYPE_UNDEPLOY,qname, null,null);
		}else{
			sendError(TYPE_UNDEPLOY,qname, null,"not found");
		}
	}
	
	@Override
	public void onLogout() {
		//logoff�����ꍇ�̑Ώ�,�o�^����Ă���peer��S��unsubscribe����
		while(true){
			Object[] reqs=peers.values().toArray();
			if(reqs.length==0){
				break;
			}
			for(Object peer:reqs){
				if( ((LinkPeer)peer).unsubscribe()==false){
					//TODO �Ӗ��s��������~���Ƀ��[�v����
					logger.error("fail to onLogout for unsubscribe",new Exception());
					unref();//�Z�V�������I�������PaSession���K�v�Ȃ�
					return;
				}
			}
		}
		unref();//�Z�V�������I�������PaSession���K�v�Ȃ�
	}
	
	public String getPath() {
		return path;
	}

	public String getAppSid() {
		return appSid;
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
