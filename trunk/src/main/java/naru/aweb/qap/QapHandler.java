/**
 * 
 */
package naru.aweb.qap;

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
public class QapHandler extends WebSocketHandler implements Timer{
	private static final String XHR_FRAME_TEMPLATE = "/template/xhrQapFrame.vsp";
	private static Config config = Config.getConfig();
	private static Logger logger=Logger.getLogger(QapHandler.class);
	private static QapManager qapManager=QapManager.getInstance();
	
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
	
	private void subscribe(JSONObject msg,List ress){
		String qname=msg.getString("qname");
		String subname=msg.optString("subname",null);
		boolean isAllowBlob=msg.optBoolean("isAllowBlob", false);
		QapPeer from=QapPeer.create(authSession,srcPath,bid,qname,subname,isAllowBlob);
		if( qapManager.subscribe(from, this) ){
			if(!qapSession.reg(from)){
				logger.debug("subscribe aleady in session.");
				from.unref();
			}
			/* subscribeの成功を通知 ...しない*/
//			JSON res=QapManager.makeMessage(QapManager.CB_TYPE_INFO,qname,subname,"subscribe","subscribed");
//			ress.add(res);
		}else{
			/* subscribeの成功を通知 */
			JSON res=QapManager.makeMessage(QapManager.CB_TYPE_ERROR,qname,subname,"subscribe","not found qname:"+qname);
			ress.add(res);
			from.unref();
		}
	}
	
	private void unsubscribe(JSONObject msg,List ress){
		String qname=msg.getString("qname");
		String subname=msg.optString("subname",null);
		JSON res=null;
		if(subname!=null){
			QapPeer peer=qapSession.unreg(qname, subname);
			if(peer!=null){
				qapManager.unsubscribe(peer);
				peer.unref();
				res=QapManager.makeMessage(QapManager.CB_TYPE_INFO,qname, subname,"unsubscribe","unsubscribed");
			}else{
				res=QapManager.makeMessage(QapManager.CB_TYPE_ERROR,qname, subname,"unsubscribe","not subscribed");
			}
			ress.add(res);
		}else{
			List<QapPeer> peers=qapSession.unregs(qname);
			if(peers==null){
				res=QapManager.makeMessage(QapManager.CB_TYPE_ERROR,qname, subname,"unsubscribe","not subscribed");
				ress.add(res);
				return;
			}
			for(QapPeer peer:peers){
				qapManager.unsubscribe(peer);
				peer.unref();
				res=QapManager.makeMessage(QapManager.CB_TYPE_INFO,qname,peer.getSubname(),"unsubscribe","unsubscribed");
				ress.add(res);
			}
		}
	}
	
	private void publish(JSONObject msg,List ress){
		String qname=msg.getString("qname");
		String subname=msg.optString("subname",null);
		QapPeer from=QapPeer.create(authSession,srcPath,bid,qname,subname,isWs);
		Object message=msg.opt("message");
		qapManager.publish(from, message);
		from.unref();
	}
	
	private void close(JSONObject msg,List ress){
		JSONArray subscribes=msg.getJSONArray("subscribes");
		JSON res=null;
		for(int i=0;i<subscribes.size();i++){
			JSONObject subcribe=subscribes.getJSONObject(i);
			String qname=subcribe.getString("qname");
			String subId=subcribe.getString("subId");
			QapPeer peer=qapSession.unreg(qname, subId);
			if(peer!=null){
				qapManager.unsubscribe(peer);
				peer.unref();
				res=QapManager.makeMessage(QapManager.CB_TYPE_INFO,qname, subId,"unsubscribe","unsubscribed");
			}else{
				res=QapManager.makeMessage(QapManager.CB_TYPE_ERROR,qname, subId,"unsubscribe","not subscribed");
			}
			ress.add(res);
		}
		res=QapManager.makeMessage(QapManager.CB_TYPE_INFO,null,null,"close","closed");
		ress.add(res);
	}
	
	private void qnames(List ress){
		Collection<String> qnames=qapManager.qnames(srcPath);
		JSON res=QapManager.makeMessage(QapManager.CB_TYPE_INFO,null, null,"qnames",qnames);
		ress.add(res);
	}
	
	private void deploy(String qname,String className,List ress){
		JSON res=null;
		if( !roles.contains("admin")){//TODO admin name
			res=QapManager.makeMessage(QapManager.CB_TYPE_ERROR,null, null,"deploy","not admin");
			ress.add(res);
			return;
		}
		Throwable t;
		try {
			Class clazz=Class.forName(className);
			Object wsqlet=clazz.newInstance();
			qapManager.createWsq(wsqlet, srcPath, qname);
			res=QapManager.makeMessage(QapManager.CB_TYPE_INFO,null, null,"deploy","deployed");
			ress.add(res);
			return;
		} catch (ClassNotFoundException e) {
			t=e;
		} catch (InstantiationException e) {
			t=e;
		} catch (IllegalAccessException e) {
			t=e;
		}
		res=QapManager.makeMessage(QapManager.CB_TYPE_ERROR,null, null,"deploy","class error");
		ress.add(res);
		logger.error("fail to deploy.",t);
	}
	
	
	private void dispatchMessage(JSONObject msg,List ress){
		String type=msg.getString("type");
		if(isNegotiated==false && "negotiate".equals(type)){
			bid=msg.getInt("bid");
			bid=setupSession(bid,true);
			ress.add(QapManager.makeMessage(QapManager.CB_TYPE_INFO,"","","negotiate",bid));
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
	
	@Override
	public void onWsClose(short code,String reason) {
		qapSession.setHandler(qapManager,this,null);
	}
	
	private AuthSession authSession;
	private List<String> roles;
	private QapSession qapSession;
	private List responseObjs=new ArrayList();
	private boolean isMsgBlock=false;
	private boolean isResponse=false;
	private long timerId;
	private String srcPath;
	private Integer bid;//negosiation時にbidを設定
	private boolean isNegotiated;
	
	/*
	 * 以下で一つのPeerを現す,AuthSessionにbidをキーにQapSessionが保持される
	 * authId
	 * bid
	 * qname
	 * subname
	 * 
	 */
	private Integer setupSession(Integer bid,boolean isCreate){
		authSession=getAuthSession();
		roles=authSession.getUser().getRolesList();
		qapSession=(QapSession)authSession.getAttribute("QapSession@"+bid);
		if(qapSession==null){
			if(isCreate==false){
				return null;
			}
			synchronized(authSession){
				Integer id=(Integer)authSession.getAttribute("QapSession@bid");
				if(id==null){
					bid=1;//bidは1からはじめる
				}else{
					bid=id+1;
				}
				authSession.setAttribute("QapSession@bid",bid);
			}
			qapSession=new QapSession();
			authSession.setAttribute("QapSession@"+bid, qapSession);
		}
		srcPath=getRequestMapping().getSourcePath();
		return bid;
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
		if(path.equals("/xhrQapFrame.vsp")){
			setRequestAttribute(ATTRIBUTE_VELOCITY_ENGINE,config.getVelocityEngine());
			setRequestAttribute(ATTRIBUTE_VELOCITY_TEMPLATE,XHR_FRAME_TEMPLATE);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return;
		}
		ParameterParser parameter=getParameterParser();
		//xhrからの開始
		//{bid:bid,data:[{type:xxx},{type:xxx}...]}
		JSONObject jsonObject=(JSONObject)parameter.getJsonObject();
		bid=jsonObject.getInt("bid");
		if(bid!=0){//bid==0はnegosiation用
			bid=setupSession(bid, false);
			if(bid!=null){
				isNegotiated=true;
			}else{
				isNegotiated=false;
			}
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
		//logoutによるunsubscribeの実行
		qapSession.setHandler(qapManager,null,null);
		List<QapPeer> peers=qapSession.unregs();
		for(QapPeer peer:peers){
			qapManager.unsubscribe(peer);
			JSON json=QapManager.makeMessage(peer.getQname(), peer.getSubname(),"subscribe","unsubscribe","logout");
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
			qapSession.setHandler(qapManager,this,null);
			isMsgBlock=true;
			timerId=TimerManager.setTimeout(10, this,null);
		}
	}
}
