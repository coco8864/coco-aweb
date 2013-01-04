/**
 * 
 */
package naru.aweb.pa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
public class PaHandler extends WebSocketHandler implements Timer{
	private static final String REQ_TYPE_NEGOTIATE = "negotiate";
	private static final String XHR_FRAME_PATH = "/xhrQapFrame.vsp";
	private static final String XHR_FRAME_TEMPLATE = "/template/xhrQapFrame.vsp";
	private static Config config = Config.getConfig();
	private static Logger logger=Logger.getLogger(PaHandler.class);
	
	private String path;
	private Integer bid;
	private List<String> roles;
	private PaSession paSession;
	private boolean isNegotiated=false;
	
	@Override
	public void recycle() {
		bid=null;
		isNegotiated=false;
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
	
	
	private void dispatchMessage(JSONObject msg,List ress){
		String type=msg.getString("type");
		if(isNegotiated==false && REQ_TYPE_NEGOTIATE.equals(type)){
			int bid=msg.getInt("bid");
			qapSession=setupSession(bid,true);
			ress.add(QapManager.makeMessage(QapManager.CB_TYPE_INFO,"","",REQ_TYPE_NEGOTIATE,bid));
			isNegotiated=true;
			return;
		}
		if(isNegotiated==false){
			ress.add(QapManager.makeMessage(QapManager.CB_TYPE_ERROR,msg.getString("qname"),msg.getString("subname"),type,"notNegotiated"));
		}else if("subscribe".equals(type)){
			subscribe(msg,ress);
		}else if("unsubscribe".equals(type)){
			unsubscribe(msg,ress);
		}else if("publish".equals(type)){
			publish(msg,ress);
		}else if("close".equals(type)){
			close(msg,ress);
		}else if("qnames".equals(type)){
			qnames(ress);
		}else if("deploy".equals(type)){
			String qname=msg.getString("qname");
			String className=msg.getString("className");
			deploy(qname,className,ress);
		}else if("undeploy".equals(type)){
			logger.warn("unsuppoerted tyep:"+type);
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
	@Override
	public void onMessage(String msgs){
		logger.debug("onMessage.message:"+msgs);
		JSONArray reqs=JSONArray.fromObject(msgs);
		if(!isNegotiated){
			setupQapSession(reqs);
		}
		List ress=new ArrayList();
		processMessages(json,ress);
		if(ress.size()>0){
			message(ress);
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
		String subname=header.optString("subname",null);
		QapPeer from=QapPeer.create(authSession,srcPath,bid,qname,subname,isWs);
		qapManager.publish(from, envelope.getBlobMessage());
		from.unref();
		envelope.unref();
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
				qapSession.setHandler(qapManager,this,null);
				isMsgBlock=true;
				if(timerId!=-1){
					TimerManager.clearTimeout(timerId);
				}
				timerId=TimerManager.setTimeout(10, this,null);
			}
		}
	}
	
	@Override
	public void onWsClose(short code,String reason) {
		paSession.setHandler(qapManager,this,null);
	}
	

	private PaSession getPaSession(Integer bid){
		AuthSession authSession=getAuthSession();
		Map<Integer,PaSession> paSessions=null;
		synchronized(authSession){
			paSessions=(Map<Integer,PaSession>)authSession.getAttribute("PaSessions");
			if(paSessions==null){
				paSessions=new HashMap<Integer,PaSession>();
				authSession.setAttribute("PaSessions", paSessions);
			}
		}
		PaSession paSession=paSessions.get(bid);
		if(paSession!=null){
			return paSession;
		}
		
		paSession=PaSession.create(path, authSession.getAppId(), bid, isWs);
		roles=authSession.getUser().getRolesList();
		
		
	}
	
	
	/**
	 * negotiationの時は、新規採番
	 * @param bid
	 * @return
	 */
	private boolean setupQapSession(JSONArray reqs){
		if(isNegotiated){
			return true;
		}
		authSession=getAuthSession();
		roles=authSession.getUser().getRolesList();
		path=getRequestMapping().getSourcePath();
		if(reqs.size()<1){
			return false;
		}
		//negotiationの時は、新規採番
		JSONObject obj=reqs.getJSONObject(0);
		if(!"negotiation".equals(obj.getString("type"))){
			return false;
		}
		bid=obj.getInt("bid");
		QapSessions qapSessions=(QapSessions)authSession.getAttribute("QapSessions");
		qapSession=qapSessions.sessions.get(bid);
		if(qapSession!=null){
			return true;
		}
		qapSession=new QapSession();
		synchronized(qapSessions){
			qapSessions.bidSeq++;
			bid=qapSessions.bidSeq;
			qapSessions.sessions.put(bid, qapSession);
		}
		authSession.addLogoutEvent(qapSession);//ログアウト時に通知を受ける
		return true;
	}
	

	@Override
	public void onWsOpen(String subprotocol) {
		//webSocketでの開始
		//setupSession();
	}
	
	/**
	 * HTTP(s)として動作した場合ここでリクエストを受ける
	@Override
	*/
	public void startResponseReqBody() {
		//xhrFrameのコンテンツ処理
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		if(path.equals(XHR_FRAME_PATH)){
			setRequestAttribute(ATTRIBUTE_VELOCITY_ENGINE,config.getVelocityEngine());
			setRequestAttribute(ATTRIBUTE_VELOCITY_TEMPLATE,XHR_FRAME_TEMPLATE);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return;
		}
		ParameterParser parameter=getParameterParser();
		//xhrからの開始
		//[{type:negotiate,bid:bid},{type:xxx}...]
		JSONArray reqs=(JSONArray)parameter.getJsonObject();
		if(!setupQapSession(reqs)){
			//session作成失敗
		}
		isMsgBlock=false;
		isResponse=false;
		processMessages(jsonObject.getJSONArray("data"),responseObjs);//HTTPで処理している
		//wsqSession.collectMessage(wsqManager,responseObjs);
		if(responseObjs.size()>0){
			qapSession.setHandler(qapManager,this,null);
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
			qapSession.setHandler(qapManager,this,null);
			isMsgBlock=true;
			timerId=TimerManager.setTimeout(10, this,null);
		}
	}
}
