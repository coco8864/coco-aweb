/**
 * 
 */
package naru.aweb.queue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import naru.aweb.config.Config;
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
public class WsQueueHandler extends WebSocketHandler {
	static private Logger logger=Logger.getLogger(WsQueueHandler.class);
	private static Config config=Config.getConfig();
	private static QueueManager queueManager=QueueManager.getInstance();
	
	@Override
	public void recycle() {
		super.recycle();
	}
	
	private String subscribeById(JSONObject msg,JSONArray responseMsg,WsQueueHandler handler){
		//subscribeByIdのルート
		String chId=msg.optString("id");
		if(handler!=null){
			if( queueManager.subscribeById(chId,handler)==false){//subscribeできなかった
				responseMsg.add(QueueManager.publishMessage(chId, "error", "fail to subscribe","self"));
				return null;
			}
		}else{
			if( queueManager.getMessages(chId, responseMsg)==false){//subscribeできなかった
				responseMsg.add(QueueManager.publishMessage(chId, "error", "fail to subscribe","self"));
				return null;
			}
		}
		return chId;
	}
	
	private String subscribeByName(JSONObject msg,JSONArray responseMsg,WsQueueHandler handler){
		//subscribeBynameのルート
		String name=msg.optString("name");
		String user=msg.optString("user");
		boolean isNew=msg.optBoolean("isNew", false);//存在しなかった場合、作成するか否か？
		String comment=msg.optString("comment");//作成する際のQueueのcomment
		String chId=queueManager.createQueueByName(name, user,isNew,comment);
		if(chId==null){
			responseMsg.add(QueueManager.subscribedMessage("error","errorId",name));
			return null;
		}
		msg.put("id", chId);
		chId=subscribeById(msg,responseMsg,handler);
		if(chId!=null){
			responseMsg.add(QueueManager.subscribedMessage("success",chId,name));
		}
		return chId;
	}
	
	private void unsubscribe(JSONObject msg){
		//subscribeBynameのルート
		String chId=msg.optString("id");
		queueManager.unsubscribe(chId);
	}
	
	private void listUsers(JSONObject msg,JSONArray responseMsg){
		//subscribeByIdのルート
		String chId=msg.optString("id");
		if( queueManager.listUsers(chId)==false){//subscribeできなかった
			responseMsg.add(QueueManager.publishMessage(chId, "error", "fail to subscribe","self"));
		}
	}
	
	private void changeUser(JSONObject msg,JSONArray responseMsg){
		//subscribeByIdのルート
		String chId=msg.optString("id");
		String user=msg.optString("user");
		if( queueManager.changeUser(chId, user)==false){//subscribeできなかった
			responseMsg.add(QueueManager.publishMessage(chId, "error", "fail to subscribe","self"));
		}
	}
	
	/**
	 * handlerをQUEUEに登録したchIdを返却
	 * 
	 * @param msg
	 * @param responseMsg
	 * @param handler
	 * @return
	 */
	private String subscribe(JSONObject msg,JSONArray responseMsg,WsQueueHandler handler){
		String action=msg.optString("action");
		if("byId".equals(action)){
			return subscribeById(msg, responseMsg, handler);
		}else if("byName".equals(action)){
			return subscribeByName(msg, responseMsg, handler);
		}else if("changeUser".equals(action)){
			changeUser(msg, responseMsg);
		}else if("listUsers".equals(action)){
			listUsers(msg, responseMsg);
		}else if("unsubscribe".equals(action)){
			unsubscribe(msg);
		}		
		return null;
	}
	
	private void publish(JSONObject msg,JSONArray responseMsg){
		String chId=msg.optString("id");
		Object message=msg.opt("message");
		boolean echoback=msg.optBoolean("echoback",true);
		if(queueManager.publish(chId, message,echoback)==false){//server再起動でidが古くなりpublishに失敗する場合がある
			responseMsg.add(QueueManager.publishMessage(chId, "error", "fail to publish","self"));
		}
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
	
	/**
	 * 
	 * @param json リクエストとして受け取ったjson
	 * @param wsHandler WebSocketプロトコルを処理しているハンドラ
	 * @return
	 */
	private JSONArray processMessages(JSON json,WsQueueHandler wsHandler){
		List<JSONObject> messages=new ArrayList<JSONObject>();
		parseMessage(json, messages);
		Iterator<JSONObject> itr=messages.iterator();
		JSONArray responseMsg=new JSONArray();
		while(itr.hasNext()){
			JSONObject msg=itr.next();
			String type=msg.optString("type");
			if("subscribe".equals(type)){
				String cid=subscribe(msg, responseMsg, wsHandler);
				if(cid!=null){
					subscribeIds.add(cid);
				}
			}else if("publish".equals(type)){
				publish(msg,responseMsg);
			}
		}
		return responseMsg;
	}
	
	/**
	 * WebSocketから受けた直接のメッセージ
	 */
	public void onMessage(String msgs){
		logger.debug("onMessage.message:"+msgs);
		JSON json=JSONSerializer.toJSON(msgs);
		JSONArray responseMsg=processMessages(json,this);
		if(responseMsg.size()>0){
			publish(responseMsg);
		}
	}
	
	void publish(JSON json){
		postMessage(json.toString());
	}

	private Set<String> subscribeIds=new HashSet<String>();
	
	@Override
	public void onWsClose() {
		Iterator<String> itr=subscribeIds.iterator();
		while(itr.hasNext()){
			String chId=itr.next();
			queueManager.returnHandler(chId);
			itr.remove();
		}
	}

	@Override
	public void onWsOpen(String subprotocol) {
		subscribeIds.clear();
	}

	/**
	 * HTTP(s)として動作した場合ここでリクエストを受ける
	@Override
	*/
	public void startResponseReqBody() {
		ParameterParser parameter=getParameterParser();
		JSON json=parameter.getJsonObject();
		JSONArray responseMsg=processMessages(json,null);//HTTPで処理している
		responseJson(responseMsg);
	}
}
