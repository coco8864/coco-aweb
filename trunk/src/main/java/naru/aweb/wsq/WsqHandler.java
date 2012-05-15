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
 * WebSocketで接続に来た場合は、順次処理する。
 * HTTPで接続した場合は、リクエスト時に一括して処理、レスポンスにその時の結果を返却する
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
	 * @param json リクエストとして受け取ったjson
	 * @param wsHandler WebSocketプロトコルを処理しているハンドラ
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
	 * WebSocketから受けた直接のメッセージ
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
	 * 端末に送信するメッセージができたところで呼び出される
	 * @param json
	 */
	void message(Object obj){
		if(isWs){
			//BlobMessageはここを通過しない
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
		//onMessageにバイナリを送ってくるのは、publishしかない
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
		//webSocketでの開始
		setupSession();
	}
	
	/**
	 * HTTP(s)として動作した場合ここでリクエストを受ける
	@Override
	*/
	public void startResponseReqBody() {
		//xhrFrameのコンテンツ処理
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
		
		//xhrからの開始
		setupSession();
		JSON json=parameter.getJsonObject();
		isMsgBlock=false;
		isResponse=false;
		processMessages(json,responseObjs);//HTTPで処理している
		//wsqSession.collectMessage(wsqManager,responseObjs);
		if(responseObjs.size()>0){
			wsqSession.setHandler(wsqManager,this,null);
			isMsgBlock=true;
			//responseObjsの処理は、timerで行う
			timerId=TimerManager.setTimeout(10, this,null);
		}else{
			/* 折り返しにレスポンスするオブジェクトがなければ1秒待つ */
			timerId=TimerManager.setTimeout(1000, this,null);
		}
	}
	
	/**
	 * WebSocketで通信中にセションがログアウトした場合に呼び出される
	 */
	public void onLogout(){
		//logoutによるunsubscribeの実行
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

	/* xhrから利用する場合、メッセージなければしばらく待ってから復帰したいため */
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
