/**
 * 
 */
package naru.aweb.wsq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import naru.async.Timer;
import naru.async.cache.CacheBuffer;
import naru.async.timer.TimerManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.handler.WebSocketHandler;
import naru.aweb.http.ParameterParser;
import naru.aweb.mapping.MappingResult;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.log4j.Logger;

/**
 * WebSocket�Őڑ��ɗ����ꍇ�́A������������B
 * HTTP�Őڑ������ꍇ�́A���N�G�X�g���Ɉꊇ���ď����A���X�|���X�ɂ��̎��̌��ʂ�ԋp����
 * 
 * @author Naru
 *
 */
public class WsqHandler extends WebSocketHandler implements Timer{
	private static Config config = Config.getConfig();
	private static Logger logger=Logger.getLogger(WsqHandler.class);
	private static final String WSQ_PAGE_FILEPATH="/wsq";
	private static WsqManager wsqManager=WsqManager.getInstance();
	
//	private Set<String> subscribeChids=new HashSet<String>();
	@Override
	public void recycle() {
		super.recycle();
	}
	
	private void parseMessage(JSON json,List<JSONObject> result){
		if(json==null){
			return;
		}
		if(json instanceof JSONArray){
			JSONArray jsonArray=(JSONArray)json;
			for(int i=0;i<jsonArray.size();i++){
				JSON msg=(JSON)jsonArray.get(i);
				parseMessage(msg,result);
			}
		}else{
			result.add((JSONObject)json);
		}
	}
	
	private void subscribe(JSONObject msg,List ress){
		String qname=msg.getString("qname");
		String subId=msg.optString("subId",null);
		boolean isAllowBlob=msg.optBoolean("isAllowBlob", false);
		WsqPeer from=WsqPeer.create(authSession,srcPath,qname,subId,isAllowBlob);
		if( wsqManager.subscribe(from, this) ){
			if(!wsqSession.reg(from)){
				logger.debug("subscribe aleady in session.");
				from.unref();
			}
			/*
			JSON res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,qname,subId,"subscribe","subscribed");
			ress.add(res);
			*/
		}else{
			JSON res=WsqManager.makeMessage(WsqManager.CB_TYPE_ERROR,qname,subId,"subscribe","not found qname:"+qname);
			ress.add(res);
			from.unref();
		}
	}
	
	private void unsubscribe(JSONObject msg,List ress){
		String qname=msg.getString("qname");
		String subId=msg.optString("subId",null);
		JSON res=null;
		if(subId!=null){
			WsqPeer peer=wsqSession.unreg(qname, subId);
			if(peer!=null){
				wsqManager.unsubscribe(peer);
				peer.unref();
				res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,qname, subId,"unsubscribe","unsubscribed");
			}else{
				res=WsqManager.makeMessage(WsqManager.CB_TYPE_ERROR,qname, subId,"unsubscribe","not subscribed");
			}
			ress.add(res);
		}else{
			List<WsqPeer> peers=wsqSession.unregs(qname);
			if(peers==null){
				res=WsqManager.makeMessage(WsqManager.CB_TYPE_ERROR,qname, subId,"unsubscribe","not subscribed");
				ress.add(res);
				return;
			}
			for(WsqPeer peer:peers){
				wsqManager.unsubscribe(peer);
				peer.unref();
				res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,qname,peer.getSubId(),"unsubscribe","unsubscribed");
				ress.add(res);
			}
		}
	}
	
	private void publish(JSONObject msg,List ress){
		String qname=msg.getString("qname");
		String subId=msg.optString("subId",null);
		WsqPeer from=WsqPeer.create(authSession,srcPath,qname,subId,isWs);
		Object message=msg.opt("message");
		wsqManager.publish(from, message);
		from.unref();
	}
	
	private void close(List ress){
		JSON res=null;
		List<WsqPeer> peers=wsqSession.unregs();
		for(WsqPeer peer:peers){
			wsqManager.unsubscribe(peer);
			peer.unref();
			res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,peer.getQname(),peer.getSubId(),"unsubscribe","unsubscribed");
			ress.add(res);
		}
		res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,null,null,"close","closed");
		ress.add(res);
	}
	
	private void getQnames(List ress){
		Collection<String> qnames=wsqManager.getQnames(srcPath);
		JSON res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,null, null,"getQnames",qnames);
		ress.add(res);
	}
	
	private void deploy(String qname,String className,List ress){
		JSON res=null;
		if( !roles.contains("admin")){//TODO admin name
			res=WsqManager.makeMessage(WsqManager.CB_TYPE_ERROR,null, null,"deploy","not admin");
			ress.add(res);
			return;
		}
		Throwable t;
		try {
			Class clazz=Class.forName(className);
			Object wsqlet=clazz.newInstance();
			wsqManager.createWsq(wsqlet, srcPath, qname);
			res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,null, null,"deploy","deployed");
			ress.add(res);
			return;
		} catch (ClassNotFoundException e) {
			t=e;
		} catch (InstantiationException e) {
			t=e;
		} catch (IllegalAccessException e) {
			t=e;
		}
		res=WsqManager.makeMessage(WsqManager.CB_TYPE_ERROR,null, null,"deploy","class error");
		ress.add(res);
		logger.error("fail to deploy.",t);
	}
	
	
	private void dispatchMessage(JSONObject msg,List ress){
		String type=msg.getString("type");
		if("subscribe".equals(type)){
			subscribe(msg,ress);
		}else if("unsubscribe".equals(type)){
			unsubscribe(msg,ress);
		}else if("publish".equals(type)){
			publish(msg,ress);
		}else if("close".equals(type)){
			close(ress);
		}else if("getQnames".equals(type)){
			getQnames(ress);
		}else if("deploy".equals(type)){
			String qname=msg.getString("qname");
			String className=msg.getString("className");
			deploy(qname,className,ress);
		}else{
			logger.warn("unsuppoerted tyep:"+type);
		}
	}
	
	/**
	 * 
	 * @param json ���N�G�X�g�Ƃ��Ď󂯎����json
	 * @param wsHandler WebSocket�v���g�R�����������Ă���n���h��
	 * @return
	 */
	private void processMessages(JSON json,List ress){
		if(!json.isArray()){
			dispatchMessage((JSONObject)json,ress);
			return;
		}
		List<JSONObject> messages=new ArrayList<JSONObject>();
		parseMessage(json, messages);
		Iterator<JSONObject> itr=messages.iterator();
		while(itr.hasNext()){
			JSONObject msg=itr.next();
			dispatchMessage(msg,ress);
		}
	}
	
	/**
	 * WebSocket����󂯂����ڂ̃��b�Z�[�W
	 */
	public void onMessage(String msgs){
		logger.debug("onMessage.message:"+msgs);
		JSON json=JSONSerializer.toJSON(msgs);
		List ress=new ArrayList();
		processMessages(json,ress);
		if(ress.size()>0){
			message(ress);
		}
	}
	
	void message(BlobEnvelope envelope){
		postMessage(envelope);
	}
	
	/**
	 * �[���ɑ��M���郁�b�Z�[�W���ł����Ƃ���ŌĂяo�����
	 * @param json
	 */
	void message(Object obj){
		if(isWs){
			//BlobMessage�͂�����ʉ߂��Ȃ�
			postMessage(obj.toString());
		}else{
			if(obj instanceof List){
				responseObjs.addAll((List)obj);
			}else{
				responseObjs.add(obj);
			}
			if(!isMsgBlock){
				wsqSession.setHandler(wsqManager,this,null);
				isMsgBlock=true;
				if(timerId!=-1){
					TimerManager.clearTimeout(timerId);
				}
				timerId=TimerManager.setTimeout(10, this,null);
			}
		}
	}
	@Override
	public void onMessage(CacheBuffer message) {
		//onMessage�Ƀo�C�i���𑗂��Ă���̂́Apublish�����Ȃ�
		BlobEnvelope envelope=BlobEnvelope.parse(message);
		JSONObject header=envelope.getHeader();
		String type=header.getString("type");
		if(!"publish".equals(type)){
			logger.error("onMessage CacheBuffer type:"+type);
			envelope.unref();
			return;
		}
		String qname=header.getString("qname");
		String subId=header.optString("subId",null);
		WsqPeer from=WsqPeer.create(authSession,srcPath,qname,subId,isWs);
		wsqManager.publish(from, envelope.getBlobMessage());
		from.unref();
		envelope.unref();
	}
	
	@Override
	public void onWsClose(short code,String reason) {
		wsqSession.setHandler(wsqManager,this,null);
	}
	
	private AuthSession authSession;
	private List<String> roles;
	private WsqSession wsqSession;
	private List responseObjs=new ArrayList();
	private boolean isMsgBlock=false;
	private boolean isResponse=false;
	private long timerId;
	private String srcPath;
	
	private void setupSession(){
		authSession=getAuthSession();
		roles=authSession.getUser().getRolesList();
		wsqSession=(WsqSession)authSession.getAttribute("WsqSession");
		if(wsqSession==null){
			wsqSession=new WsqSession();
			authSession.setAttribute("WsqSession", wsqSession);
		}
//		wsqSession.setHandler(wsqManager, this);
		srcPath=getRequestMapping().getSourcePath();
	}

	@Override
	public void onWsOpen(String subprotocol) {
		//webSocket�ł̊J�n
		setupSession();
	}
	
	/**
	 * HTTP(s)�Ƃ��ē��삵���ꍇ�����Ń��N�G�X�g���󂯂�
	@Override
	*/
	public void startResponseReqBody() {
		//xhrFrame�̃R���e���c����
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		if(path.endsWith(".html")||path.endsWith(".vsp")||path.endsWith(".vsf")){
			if(path.startsWith("/")){
				mapping.setResolvePath(WSQ_PAGE_FILEPATH + path);
			}else{
				mapping.setResolvePath(WSQ_PAGE_FILEPATH + "/" +path);
			}
			mapping.setDesitinationFile(config.getAdminDocumentRoot());
			forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
			return;
		}
		ParameterParser parameter=getParameterParser();
		
		//xhr����̊J�n
		setupSession();
		JSON json=parameter.getJsonObject();
		isMsgBlock=false;
		isResponse=false;
		processMessages(json,responseObjs);//HTTP�ŏ������Ă���
		//wsqSession.collectMessage(wsqManager,responseObjs);
		if(responseObjs.size()>0){
			wsqSession.setHandler(wsqManager,this,null);
			isMsgBlock=true;
			//responseObjs�̏����́Atimer�ōs��
			timerId=TimerManager.setTimeout(10, this,null);
		}else{
			/* �܂�Ԃ��Ƀ��X�|���X����I�u�W�F�N�g���Ȃ����1�b�҂� */
			timerId=TimerManager.setTimeout(1000, this,null);
		}
	}
	
	/**
	 * WebSocket�ŒʐM���ɃZ�V���������O�A�E�g�����ꍇ�ɌĂяo�����
	 */
	public void onLogout(){
		//logout�ɂ��unsubscribe�̎��s
		wsqSession.setHandler(wsqManager,null,null);
		List<WsqPeer> peers=wsqSession.unregs();
		for(WsqPeer peer:peers){
			wsqManager.unsubscribe(peer);
			JSON json=WsqManager.makeMessage(peer.getQname(), peer.getSubId(),"subscribe","unsubscribe","logout");
			postMessage(json.toString());
			peer.unref();
		}
		super.onLogout();
	}

	@Override
	public void onFinished() {
		super.onFinished();
	}

	/* xhr���痘�p����ꍇ�A���b�Z�[�W�Ȃ���΂��΂炭�҂��Ă��畜�A���������� */
	public void onTimer(Object userContext) {
		timerId=-1;
		if(isMsgBlock){
			synchronized(this){
				if(isResponse){
					return;
				}
				isResponse=true;
			}
			if(responseObjs.size()>0){
				JSONArray res=new JSONArray();
				res.addAll(responseObjs);
				responseJson(res);
				responseObjs.clear();
			}else{
				completeResponse("205");
			}
		}else{
			wsqSession.setHandler(wsqManager,this,null);
			isMsgBlock=true;
			timerId=TimerManager.setTimeout(10, this,null);
		}
	}
	
//	private Blob blob;
//	
//	@Override
//	public void onWrittenPlain(Object userContext) {
//		if(!isWs){
//			super.onWrittenPlain(userContext);
//			return;
//		}
//	}
}
