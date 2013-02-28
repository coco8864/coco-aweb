/**
 * 
 */
package naru.aweb.pa;

import java.util.ArrayList;
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

import org.apache.log4j.Logger;

/**
 * WebSocket�Őڑ��ɗ����ꍇ�́A������������B
 * HTTP�Őڑ������ꍇ�́A���N�G�X�g���Ɉꊇ���ď����A���X�|���X�ɂ��̎��̌��ʂ�ԋp����
 * 
 * @author Naru
 *
 */
public class PaHandler extends WebSocketHandler implements Timer{
	private static final String PA_SESSIONS_KEY = "PaSessions";
	private static final String XHR_FRAME_PATH = "/xhrPaFrame.vsp";
	private static final String XHR_FRAME_TEMPLATE = "/template/xhrPaFrame.vsp";
	private static int XHR_SLEEP_TIME=1000;
	private static Config config = Config.getConfig();
	private static Logger logger=Logger.getLogger(PaHandler.class);
	
	private Integer bid;
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
	
	/**
	 * negotiation�O��ŌĂяo�����=paSession���L��
	 * @param msg
	 */
	private void dispatchMessage(JSONObject msg){
		String type=msg.getString("type");
		if(PaSession.TYPE_SUBSCRIBE.equals(type)){
			paSession.subscribe(msg);
		}else if(PaSession.TYPE_UNSUBSCRIBE.equals(type)){
			paSession.unsubscribe(msg);
		}else if(PaSession.TYPE_PUBLISH.equals(type)){
			Map realMessage=Envelope.unpack(msg, null);
			paSession.publish(realMessage);
		}else if(PaSession.TYPE_CLOSE.equals(type)){
			paSession.close();
			AuthSession authSession=getAuthSession();
			PaSessions paSessions=null;
			synchronized(authSession){
				paSessions=(PaSessions)authSession.getAttribute(PA_SESSIONS_KEY);
				paSessions.sessions.remove(bid);
				isNegotiated=false;
				bid=0;
			}
		}else if(PaSession.TYPE_QNAMES.equals(type)){
			paSession.qname(msg);
		}else if(PaSession.TYPE_DEPLOY.equals(type)){
			paSession.deploy(msg);
		}else if(PaSession.TYPE_UNDEPLOY.equals(type)){
			paSession.undeploy(msg);
		}else if(PaSession.TYPE_NEGOTIATE.equals(type)){
			if(!negotiation(msg)){
				paSession.sendError(type,null, null,"fail to negotiation");
				return;
			}
		}else{
			paSession.sendError(type,null, null,"unsuppoerted type");
			logger.warn("unsuppoerted type:"+type);
		}
	}
	
	/**
	 * 
	 * @param json ���N�G�X�g�Ƃ��Ď󂯎����json
	 * @param wsHandler WebSocket�v���g�R�����������Ă���n���h��
	 * @return
	 */
	private void processMessages(JSON json){
		if(!json.isArray()){
			dispatchMessage((JSONObject)json);
			return;
		}
		List<JSONObject> messages=new ArrayList<JSONObject>();
		parseMessage(json, messages);
		Iterator<JSONObject> itr=messages.iterator();
		while(itr.hasNext()){
			JSONObject msg=itr.next();
			dispatchMessage(msg);
		}
	}
	
	/**
	 * WebSocket����󂯂�Text���b�Z�[�W
	 * WebSocket�̏ꍇ�́Amsg�͂P�Â͂�
	 */
	@Override
	public void onMessage(String msg){
		logger.debug("onMessage.message:"+msg);
		JSONObject req=JSONObject.fromObject(msg);
		if(!isNegotiated){
			if(!negotiation(req)){
				//negotiation���s,�v���I����f
				return;
			}
			paSession.setupWsHandler(null,this);
		}
		dispatchMessage(req);
	}
	
	/**
	 * WebSocket����󂯂�Binary���b�Z�[�W
	 * WebSocket�̏ꍇ�́Amsg�͂P�Â͂�
	 */
	@Override
	public void onMessage(CacheBuffer prot) {
		//onMessage�Ƀo�C�i���𑗂��Ă���̂́Anegtiation��,publish
		if(!isNegotiated){
			//negotiation���s,�v���I����f
			return;
		}
		paSession.publish(Envelope.unpack(prot));
	}
	
	
	@Override
	public void onWsClose(short code,String reason) {
		paSession.setupWsHandler(this,null);
	}
	
	/**
	 * negotiation�̎��́A�V�K�̔�
	 * @param bid
	 * @return
	 */
	private boolean negotiation(JSONObject negoreq){
		String type=negoreq.getString(PaSession.KEY_TYPE);
		if(!PaSession.TYPE_NEGOTIATE.equals(type)){
			return false;
		}
		isNegotiated=true;
		bid=negoreq.getInt(PaSession.KEY_BID);
		AuthSession authSession=getAuthSession();
		PaSessions paSessions=null;
		synchronized(authSession){
			paSessions=(PaSessions)authSession.getAttribute(PA_SESSIONS_KEY);
			if(paSessions==null){
				paSessions=new PaSessions();
				authSession.setAttribute(PA_SESSIONS_KEY, paSessions);
			}
			paSession=paSessions.sessions.get(bid);
			if(paSession!=null){
				return true;
			}
		}
		String path=getRequestMapping().getSourcePath();
		synchronized(paSessions){
			paSessions.bidSeq++;
			bid=paSessions.bidSeq;
			paSession=PaSession.create(path,bid, isWs, authSession);
			paSessions.sessions.put(bid, paSession);
			negoreq.put(PaSession.KEY_BID,bid);
			paSession.sendJson(negoreq);
		}
		authSession.addLogoutEvent(paSession);//���O�A�E�g���ɒʒm���󂯂�
		return true;
	}
	
	/* authSession�ɏ悹��PaSession�Ǘ�Class */
	private static class PaSessions{
		Map<Integer,PaSession> sessions=new HashMap<Integer,PaSession>();
		Integer bidSeq=0;
	}

	@Override
	public void onWsOpen(String subprotocol) {
		logger.debug("onWsOpen subprotocol:"+subprotocol);
	}
	
	/**
	 * HTTP(s)�Ƃ��ē��삵���ꍇ�����Ń��N�G�X�g���󂯂�
	@Override
	*/
	public void startResponseReqBody() {
		//xhrFrame�̃R���e���c����
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		if(path.equals(XHR_FRAME_PATH)){
			setRequestAttribute(ATTRIBUTE_VELOCITY_ENGINE,config.getVelocityEngine());
			setRequestAttribute(ATTRIBUTE_VELOCITY_TEMPLATE,XHR_FRAME_TEMPLATE);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return;
		}
		ParameterParser parameter=getParameterParser();
		//xhr����̊J�n
		//[{type:negotiate,bid:bid},{type:xxx}...]
		
		JSONArray reqs=(JSONArray)parameter.getJsonObject();
		JSONObject negoreq=(JSONObject)reqs.remove(0);
		if(!negotiation(negoreq)){
			//negotiation���s
			return;
		}
		doneXhr=false;
		processMessages(reqs);
		paSession.setupXhrHandler(this);
		if(!doneXhr){
			ref();
			TimerManager.setTimeout(XHR_SLEEP_TIME, this,null);
		}
	}
	
	/**
	 * WebSocket�ŒʐM���ɃZ�V���������O�A�E�g�����ꍇ�ɌĂяo�����
	 */
	public void onLogout(){
		super.onLogout();
	}

	@Override
	public void onFinished() {
		super.onFinished();
	}
	
	//xhr���A���X�|���X�ς݂��ۂ���ێ�
	private boolean doneXhr;
	public boolean doneXhr(){
		return doneXhr;
	}
	public void setDoneXhr(){
		doneXhr=true;
	}
	
	/* xhr���痘�p����ꍇ�A���b�Z�[�W�Ȃ���΂��΂炭�҂��Ă��畜�A���������� */
	public void onTimer(Object userContext) {
		paSession.xhrTerminal(this);
		unref();
	}
}
