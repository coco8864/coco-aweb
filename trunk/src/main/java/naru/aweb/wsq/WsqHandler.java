/**
 * 
 */
package naru.aweb.wsq;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import naru.async.Timer;
import naru.async.timer.TimerManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.handler.WebSocketHandler;
import naru.aweb.http.ParameterParser;
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
//	private static Config config=Config.getConfig();
	private static Logger logger=Logger.getLogger(WsqHandler.class);
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
	
	private void subscribe(JSONObject msg,JSONArray ress){
		String qname=msg.getString("qname");
		String subId=msg.optString("subId",null);
		WsqPeer from=WsqPeer.create(authSession,srcPath,qname,subId);
//		if(peerQnameMap.get(from)!=null){
//			continue;//既にsubscribe中
//		}
		if( wsqManager.subscribe(from, this) ){
			if(wsqSession.reg(from)){
				from.ref();
			}else{
				logger.error("aleady in session.",new Exception());
			}
		}else{
			JSON res=WsqManager.makeMessage(WsqManager.CB_TYPE_ERROR,qname,subId,"subscribe","not found qname:"+qname);
			ress.add(res);
		}
	}
	
	private void unsubscribe(JSONObject msg,JSONArray ress){
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
	
	private void publish(JSONObject msg,JSONArray ress){
		String qname=msg.getString("qname");
		String subId=msg.optString("subId",null);
		WsqPeer from=WsqPeer.create(authSession,srcPath,qname,subId);
		Object message=msg.opt("message");
		wsqManager.publish(from, message);
		from.unref();
	}
	
	private void getQnames(JSONArray ress){
		Collection<String> qnames=wsqManager.getQnames(srcPath);
		JSON res=WsqManager.makeMessage(WsqManager.CB_TYPE_INFO,null, null,"qnames",qnames);
		ress.add(res);
	}
	
	/**
	 * 
	 * @param json リクエストとして受け取ったjson
	 * @param wsHandler WebSocketプロトコルを処理しているハンドラ
	 * @return
	 */
	private void processMessages(JSON json,JSONArray ress){
		List<JSONObject> messages=new ArrayList<JSONObject>();
		parseMessage(json, messages);
		Iterator<JSONObject> itr=messages.iterator();
//		JSONArray responseMsg=new JSONArray();
		while(itr.hasNext()){
			JSONObject msg=itr.next();
			String type=msg.getString("type");
			if("subscribe".equals(type)){
				subscribe(msg,ress);
			}else if("unsubscribe".equals(type)){
				unsubscribe(msg,ress);
			}else if("publish".equals(type)){
				publish(msg,ress);
			}else if("qnames".equals(type)){
				getQnames(ress);
			}
		}
	}
	
	/**
	 * WebSocketから受けた直接のメッセージ
	 */
	public void onMessage(String msgs){
		logger.debug("onMessage.message:"+msgs);
		JSON json=JSONSerializer.toJSON(msgs);
		JSONArray ress=new JSONArray();
		processMessages(json,ress);
		if(ress.size()>0){
			message(ress);
		}
	}
	
	/**
	 * 端末に送信するメッセージができたところで呼び出される
	 * @param json
	 */
	void message(Object obj){
		if(isWs){
			postMessage(obj.toString());
		}else{
			if(obj instanceof JSONArray){
				responseObjs.addAll((JSONArray)obj);
			}else{
				responseObjs.add(obj);
			}
			if(!isMsgBlock){
				wsqSession.setHandler(wsqManager, null);
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
		wsqSession.setHandler(wsqManager, null);
	}
	
	private AuthSession authSession;
	private WsqSession wsqSession;
	private JSONArray responseObjs=new JSONArray();
	private boolean isMsgBlock=false;
	private boolean isResponse=false;
	private long timerId;
	private String srcPath;
	
	private void setupSession(){
		authSession=getAuthSession();
		wsqSession=(WsqSession)authSession.getAttribute("WsqSession");
		if(wsqSession==null){
			wsqSession=new WsqSession();
			authSession.setAttribute("WsqSession", wsqSession);
		}
		wsqSession.setHandler(wsqManager, this);
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
		//xhrからの開始
		setupSession();
		ParameterParser parameter=getParameterParser();
		JSON json=parameter.getJsonObject();
		isMsgBlock=false;
		isResponse=false;
		processMessages(json,responseObjs);//HTTPで処理している
		wsqSession.collectMessage(wsqManager,responseObjs);
		if(responseObjs.size()>0){
			wsqSession.setHandler(wsqManager, null);
			isMsgBlock=true;
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
		wsqSession.setHandler(wsqManager, null);
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
	public void onMessage(ByteBuffer[] msgs) {
		throw new UnsupportedOperationException();
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
				responseJson(responseObjs);
				responseObjs.clear();
			}else{
				completeResponse("205");
			}
		}else{
			wsqSession.setHandler(wsqManager, null);
			isMsgBlock=true;
			timerId=TimerManager.setTimeout(10, this,null);
		}
	}
}
