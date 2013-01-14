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
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.apache.log4j.Logger;
import org.json.JSONArray;

public class PaSession extends PoolBase implements LogoutEvent{
	/* req/res���ʂ�key */
	public static final String KEY_TYPE="type";
	public static final String KEY_BID="bid";
	public static final String KEY_QNAME="qname";
	public static final String KEY_SUBNAME="subname";
	public static final String KEY_PALET_CLASS_NAME="paletClassName";
	
	/* request type */
	public static final String TYPE_NEGOTIATION="negotiation";
	public static final String TYPE_PUBLISH="publish";
	public static final String TYPE_SUBSCRIBE="subscribe";
	public static final String TYPE_UNSUBSCRIBE="unsubscribe";
	public static final String TYPE_DEPLOY="deploy";
	public static final String TYPE_UNDEPLOY="undeploy";
	public static final String TYPE_QNAMES="qnames";
	//subscribe��Ԃ����̂܂܂ɉ���̐ؒf�ʒm���������Ȃ肫���Ă������Ȃ��̂œ��ʗ��p���Ȃ�
	public static final String TYPE_CONNECTION_CLOSE="connectionClose";
	
	/* res������key */
	public static final String KEY_RESULT="result";
	public static final String KEY_REQUEST_TYPE="requestType";
	public static final String KEY_MESSAGE="message";
	
	/* response type */
	public static final String TYPE_RESPONSE="response";
	public static final String TYPE_MESSAGE="message";
	public static final String RESULT_OK="ok";
	public static final String RESULT_ERROR="error";
	
	private static Logger logger=Logger.getLogger(PaSession.class);
	private static PaManager paManager=PaManager.getInstance();
	
	static PaSession create(String path,Integer bid,boolean isWs,AuthSession authSession){
		PaSession paSession=(PaSession)PoolManager.getInstance(PaSession.class);
		paSession.path=path;
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
		super.recycle();
	}
	
	private String path;//�ڑ�path
	
	//authSession�R��
	private String appId;//�F��id���Y�Z�V������id
	private String loginId;
	private List<String> roles;
	
	private Integer bid;//brawserId
	private boolean isWs;
	private Map<PaPeer,PaPeer> peers=new HashMap<PaPeer,PaPeer>();
	private PaHandler wsHandler;
	private PaHandler xhrHandler;
	private List<Object> qdata=new ArrayList<Object>();
	private List<BlobMessage> qbin=new ArrayList<BlobMessage>();
	
	private void wsSend(PaHandler handler,Object data){
		if(data instanceof String){
			handler.postMessage((String)data);
		}else if(data instanceof AsyncBuffer){
			handler.postMessage((AsyncBuffer)data);
		}else{//JSON�̏ꍇ
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
		handler.doneXhr();
	}
	
	public synchronized void setupWsHandler(PaHandler handler){
		if(this.wsHandler!=null && handler==null){
			this.wsHandler=null;
			return;
		}else if(this.wsHandler==null && handler!=null){
			for(Object data:qdata){
				wsSend(handler,data);
			}
			qdata.clear();
			this.wsHandler=handler;
			return;
		}
		logger.error("setupWsHandler");
	}
	
	/**
	 * 
	 * @param handler
	 * @return response�����ꍇtrue
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
			xhrSend(xhrHandler, qdata);
			qdata.clear();
			handler.doneXhr();
			return;
		}
		logger.error("setupXhrHandler");
	}
	
	/* ���X�|���X����I�u�W�F�N�g���Ȃ������Ƃ��Ă���莞�Ԍ�ɂ̓��X�|���X���� */
	public synchronized void xhrTerminal(PaHandler handler){
		if(handler.doneXhr()){
			return;
		}
		xhrSend(xhrHandler,qdata);
		qdata.clear();
		xhrHandler=null;
	}
	
	private JSON makeResponseJson(String result,String qname,String subname,String requestType,Object message){
		JSONObject json=new JSONObject();
		json.put(KEY_TYPE, TYPE_RESPONSE);
		json.put(KEY_RESULT, result);
		json.put(KEY_REQUEST_TYPE, requestType);
		json.put(KEY_QNAME, qname);
		json.put(KEY_SUBNAME, subname);
		json.put(KEY_MESSAGE, message);
		return json;
	}
	
	public synchronized void sendError(String requestType,String qname,String subname,Object message){
		message(makeResponseJson(RESULT_ERROR,requestType,qname, subname,  message));
	}
	
	public synchronized void sendOK(String requestType,String qname,String subname,Object message){
		message(makeResponseJson(RESULT_OK,requestType,qname, subname, message));
	}
	
	public synchronized void message(Object data){
		if(this.wsHandler!=null){
			wsSend(this.wsHandler,data);
		}else if(this.xhrHandler!=null){
			if(data instanceof BlobMessage){
				logger.error("xhrHandler can't send BlobMessage.xhrHandler");
				return;
			}
			qdata.add(data);
			xhrSend(this.xhrHandler,qdata);
			qdata.clear();
			this.xhrHandler=null;
		}else{
			if(data instanceof BlobMessage){
				if(!isWs){
					logger.error("xhrHandler can't send BlobMessage.isWs");
					return;
				}
				qbin.add((BlobMessage)data);
			}else{
				qdata.add(data);
			}
		}
	}
	
	public void subscribe(JSONObject msg){
		String qname=msg.getString(KEY_QNAME);
		String subname=msg.optString(KEY_SUBNAME,null);
		PaPeer keyPeer=PaPeer.create(this, qname, subname);
		synchronized(peers){
			PaPeer peer=peers.get(keyPeer);
			if(peer!=null){//���ł�subscribe�ςݏ����͂Ȃ�
				keyPeer.unref(true);
				return;
			}
			peers.put(keyPeer, keyPeer);
		}
		PaletWrapper paletWrapper=paManager.getPaletWrapper(qname);
		paletWrapper.onSubscribe(keyPeer);
	}
	
	public void unsubscribe(JSONObject msg){
		String qname=msg.getString(KEY_QNAME);
		String subname=msg.optString(KEY_SUBNAME,null);
		PaPeer keyPeer=PaPeer.create(this, qname, subname);
		PaPeer peer=null;
		synchronized(peers){
			peer=peers.get(keyPeer);
			keyPeer.unref(true);
			if(peer==null){//���ł�unsubscribe�ςݏ����͂Ȃ�
				return;
			}
		}
		PaletWrapper paletWrapper=paManager.getPaletWrapper(qname);
		paletWrapper.onUnubscribe(peer,"client");
		//sendOK(TYPE_SUBSCRIBE,qname, subname,null);�����o���Ă��������璷�Ȃ̂ŏȗ�
		sendOK(TYPE_UNSUBSCRIBE,qname, subname,null);
	}
	
	/* API�o�R��unsubscribe�����ꍇ, */
	/* client��unsubscribe(subscribe���s)��ʒm���� */
	public boolean unsubscribeByPeer(PaPeer peer){
		String qname=peer.getQname();
		synchronized(peers){
			peer=peers.remove(peer);
			if(peer==null){//���ł�unsubscribe�ςݏ����͂Ȃ�
				return false;
			}
		}
		//unsubscribe�͉ߋ��ɔ��s���ꂽsubscribe�����������Ƃ��Ēʒm����
		sendOK(TYPE_SUBSCRIBE,qname,peer.getSubname(),"unsubscribed by api");
		return true;
	}
	
	public void publish(JSONObject msg){
		String qname=msg.getString(KEY_QNAME);
		String subname=msg.optString(KEY_SUBNAME,null);
		Object message=msg.get(KEY_MESSAGE);
		PaPeer keyPeer=PaPeer.create(this, qname, subname);
		PaletWrapper paletWrapper=paManager.getPaletWrapper(qname);
		if(subname==null){//���M�����Ȃ�Publish �֋X�I��Peer
			paletWrapper.onPublish(keyPeer, message);
			keyPeer.unref();
			return;
		}
		PaPeer peer=null;
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
	
	public void publishBin(JSONObject msg,BlobMessage blobmessage){
		String qname=msg.getString(KEY_QNAME);
		String subname=msg.optString(KEY_SUBNAME,null);
		if(!isWs){
			sendError(TYPE_PUBLISH,qname, subname,"not support bin publish");
			logger.error("not support bin publish");
			return;
		}
		PaPeer keyPeer=PaPeer.create(this, qname, subname);
		PaletWrapper paletWrapper=paManager.getPaletWrapper(qname);
		if(subname==null){//���M�����Ȃ�Publish �֋X�I��Peer
			paletWrapper.onPublish(keyPeer, blobmessage);
			keyPeer.unref();
			return;
		}
		PaPeer peer=null;
		synchronized(peers){
			peer=peers.get(keyPeer);
			keyPeer.unref(true);
			if(peer==null){//�o�^�̂Ȃ��Ƃ��납���publish?
				sendError(TYPE_PUBLISH, qname, subname, "not found subname");
				return;
			}
		}
		paletWrapper.onPublish(peer, blobmessage);
	}
	
	public void qname(JSONObject req){
		Set<String> qnames=paManager.qnames();
		sendOK(TYPE_QNAMES,null, null,new JSONArray(qnames));
	}
	
	public void deploy(JSONObject req){
		String qname=req.getString(KEY_QNAME);
		String paletClassName=req.getString(KEY_PALET_CLASS_NAME);
		paManager.deploy(qname, paletClassName);
		sendOK(TYPE_DEPLOY,qname, null, null);
	}
	
	public void undeploy(JSONObject req){
		String qname=req.getString(KEY_QNAME);
		paManager.undeploy(qname);
		sendOK(TYPE_UNDEPLOY,qname, null,null);
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
				((PaPeer)peer).unsubscribe();
			}
		}
		unref();//�Z�V�������I�������PaSession���K�v�Ȃ�
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
	
}
