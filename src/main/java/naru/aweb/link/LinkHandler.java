/**
 * 
 */
package naru.aweb.link;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import naru.async.Timer;
import naru.async.cache.CacheBuffer;
import naru.async.timer.TimerManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.config.Config;
import naru.aweb.handler.ws.WebSocketHandler;
import naru.aweb.link.api.Blob;
import naru.aweb.link.api.LinkMsg;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ParameterParser;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.log4j.Logger;

/**
 * WebSocket�Őڑ��ɗ����ꍇ�́A������������B
 * HTTP�Őڑ������ꍇ�́A���N�G�X�g���Ɉꊇ���ď����A���X�|���X�ɂ��̎��̌��ʂ�ԋp����
 * 
 * @author Naru
 *
 */
public class LinkHandler extends WebSocketHandler implements Timer{
	private static final String LINK_SESSIONS_KEY = "LinkSessions";
	private static final String XHR_FRAME_PATH = "/~xhrPhFrame";
	private static final String XHR_FRAME_TEMPLATE = "~xhrPhFrame.vsp";
	private static final String XHR_POLLING_PATH="/~xhrPolling";
	private static final String DOWNLOAD_PATH="/~download";
	private static final String UPLOAD_PATH="/~upload";
	private static int XHR_SLEEP_TIME=1000;
	private static Config config = Config.getConfig();
	private static Logger logger=Logger.getLogger(LinkHandler.class);
	
	private Integer bid;
	private LinkSession linkSession;
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
		if(LinkSession.TYPE_SUBSCRIBE.equals(type)){
			linkSession.subscribe(msg);
		}else if(LinkSession.TYPE_UNSUBSCRIBE.equals(type)){
			linkSession.unsubscribe(msg);
		}else if(LinkSession.TYPE_PUBLISH.equals(type)){
			LinkMsg realMessage=Envelope.unpack(msg, null);
			linkSession.publish(realMessage);
		}else if(LinkSession.TYPE_CLOSE.equals(type)){
			linkSession.close();
			AuthSession authSession=getAuthSession();
			LinkSessions linkSessions=null;
			synchronized(authSession){
				linkSessions=(LinkSessions)authSession.getAttribute(LINK_SESSIONS_KEY);
				linkSessions.sessions.remove(bid);
				isNegotiated=false;
				bid=0;
			}
		}else if(LinkSession.TYPE_QNAMES.equals(type)){
			linkSession.qname(msg);
		}else if(LinkSession.TYPE_DEPLOY.equals(type)){
			linkSession.deploy(msg);
		}else if(LinkSession.TYPE_UNDEPLOY.equals(type)){
			linkSession.undeploy(msg);
		}else if(LinkSession.TYPE_NEGOTIATE.equals(type)){
			if(!negotiation(msg)){
				linkSession.sendError(type,null, null,"fail to negotiation");
				return;
			}
		}else{
			linkSession.sendError(type,null, null,"unsuppoerted type");
			logger.warn("unsuppoerted type:"+type);
		}
	}
	
	/**
	 * 
	 * @param json ���N�G�X�g�Ƃ��Ď󂯎����json
	 * @param wsHandler WebSocket�v���g�R�����������Ă���n���h��
	 * @return
	 */
	private void processMessages(JSONArray json){
//		if(!json.isArray()){
//			dispatchMessage((JSONObject)json);
//			return;
//		}
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
			linkSession.setupWsHandler(null,this);
			return;
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
		linkSession.publish(Envelope.unpack(prot));
	}
	
	
	@Override
	public void onWsClose(short code,String reason) {
		if(linkSession!=null){
			linkSession.setupWsHandler(this,null);
		}
	}
	
	private LinkSessions getLinkSessions(AuthSession authSession){
		LinkSessions linkSessions=null;
		synchronized(authSession){
			linkSessions=(LinkSessions)authSession.getAttribute(LINK_SESSIONS_KEY);
			if(linkSessions==null){
				linkSessions=new LinkSessions();
				authSession.setAttribute(LINK_SESSIONS_KEY, linkSessions);
			}
			return linkSessions;
		}
	}
	
	private void sendNegotiation(){
		JSONObject negores=new JSONObject();
		AuthSession authSession=getAuthSession();
		negores.put(LinkSession.KEY_TYPE,LinkSession.TYPE_NEGOTIATE);
		negores.put(LinkSession.KEY_BID,bid);
		long lastAccess=authSession.getLastAccessTime();
		long now=System.currentTimeMillis();
		long timeout=config.getAuthorizer().getSessionTimeout();
		negores.put(LinkSession.KEY_SESSION_TIME_LIMIT, ((lastAccess+timeout)-now));
//		User user=authSession.getUser();
//		negores.put(PaSession.KEY_OFFLINE_PASS_HASH,user.getOfflinePassHash());
		linkSession.sendJson(negores);
	}
	
	/**
	 * negotiation�̎��́A�V�K�̔�
	 * @param bid
	 * @return
	 */
	private boolean negotiation(JSONObject negoreq){
		String type=negoreq.getString(LinkSession.KEY_TYPE);
		if(!LinkSession.TYPE_NEGOTIATE.equals(type)){
			return false;
		}
		String token=negoreq.getString(LinkSession.KEY_TOKEN);
		AuthSession authSession=getAuthSession();
		if(!token.equals(authSession.getToken())){
			return false;
		}
		
		bid=negoreq.getInt(LinkSession.KEY_BID);
		boolean needRes=negoreq.getBoolean("needRes");
		LinkSessions paSessions=getLinkSessions(authSession);
		linkSession=paSessions.sessions.get(bid);
		if(linkSession==null && needRes==false){
			bid=-1;
			return false;
		}
		isNegotiated=true;
		if(linkSession!=null){
			if(needRes){
				sendNegotiation();
			}
			return true;
		}
		String path=getRequestMapping().getSourcePath();
		synchronized(paSessions){
			paSessions.bidSeq++;
			bid=paSessions.bidSeq;
			linkSession=LinkSession.create(path,bid, isWs, authSession);
			paSessions.sessions.put(bid, linkSession);
			sendNegotiation();
		}
		authSession.addLogoutEvent(linkSession);//���O�A�E�g���ɒʒm���󂯂�
		return true;
	}
	
	/* authSession�ɏ悹��PaSession�Ǘ�Class */
	private static class LinkSessions{
		Map<Integer,LinkSession> sessions=new HashMap<Integer,LinkSession>();
		Integer bidSeq=0;
	}

	@Override
	public void onWsOpen(String subprotocol) {
		logger.debug("onWsOpen subprotocol:"+subprotocol);
	}
	
	private static final Set<String> UPLOAD_RESERVE_KEY=new HashSet<String>();
	static{
		UPLOAD_RESERVE_KEY.add(LinkSession.KEY_TOKEN);
		UPLOAD_RESERVE_KEY.add(LinkSession.KEY_QNAME);
		UPLOAD_RESERVE_KEY.add(LinkSession.KEY_SUBNAME);
		UPLOAD_RESERVE_KEY.add(LinkSession.KEY_BID);
	}
	private void formPublish(ParameterParser parameter){
		String token=parameter.getParameter(LinkSession.KEY_TOKEN);
		AuthSession authSession=getAuthSession();
		if(!token.equals(authSession.getToken())){
			completeResponse("403");
			return;
		}
		int bid=Integer.parseInt(parameter.getParameter(LinkSession.KEY_BID));
		LinkSessions paSessions=getLinkSessions(authSession);
		linkSession=paSessions.sessions.get(bid);
		if(linkSession==null){
			completeResponse("404");
			return;
		}
		//Map rawParam=parameter.getParameterMap();
		LinkMsg msg=LinkMsg.create();
		Map message=new HashMap();
		msg.put(LinkSession.KEY_TYPE, LinkSession.TYPE_PUBLISH);
		msg.put(LinkSession.KEY_QNAME, parameter.getParameter(LinkSession.KEY_QNAME));
		msg.put(LinkSession.KEY_SUBNAME, parameter.getParameter(LinkSession.KEY_SUBNAME));
		msg.put(LinkSession.KEY_MESSAGE, message);
		
		Iterator itr=parameter.getParameterNames();
		while(itr.hasNext()){
			String name=(String)itr.next();
			if(UPLOAD_RESERVE_KEY.contains(name)){
				continue;
			}
			Object value=parameter.getObject(name);
			if(value instanceof DiskFileItem){
				DiskFileItem item=(DiskFileItem)value;
				Blob blob=Blob.create(item.getStoreLocation(),true);
				blob.setName(item.getName());
				message.put(name, blob);
			}else{
				message.put(name, value);
			}
		}
		linkSession.publish(msg);
		completeResponse("204");
		return;
	}
	
	private void download(ParameterParser parameter){
		String token=parameter.getParameter(LinkSession.KEY_TOKEN);
		AuthSession authSession=getAuthSession();
		if(!token.equals(authSession.getToken())){
			completeResponse("403");
			return;
		}
		int bid=Integer.parseInt(parameter.getParameter(LinkSession.KEY_BID));
		String key=parameter.getParameter("key");
		LinkSessions paSessions=getLinkSessions(authSession);
		linkSession=paSessions.sessions.get(bid);
		if(linkSession==null){
			completeResponse("404");
			return;
		}
		Blob blob=linkSession.popDownloadBlob(key);
		if(blob==null){
			completeResponse("404");
			return;
		}
		blob.download(this);
	}
	
	private void pooling(ParameterParser parameter){
		//xhr����̊J�n
		//[{type:negotiate,bid:bid},{type:xxx}...]
		JSONArray reqs=(JSONArray)parameter.getJsonObject();
		JSONObject negoreq=(JSONObject)reqs.remove(0);
		if(!negotiation(negoreq)){
			//negotiation���s
			return;
		}
		doneXhr=false;
		if(reqs.size()==0){
			//���N�G�X�g��negotiaion�ȊO�ɂȂ����accesslog�ɋL�^���Ȃ�
			getAccessLog().setSkipPhlog(true);
		}else{
			processMessages(reqs);
		}
		linkSession.setupXhrHandler(this);
		if(!doneXhr){
			ref();
			TimerManager.setTimeout(XHR_SLEEP_TIME, this,null);
		}
	}
	
	/**
	 * HTTP(s)�Ƃ��ē��삵���ꍇ�����Ń��N�G�X�g���󂯂�
	@Override
	*/
	public void onRequestBody() {
		ParameterParser parameter=getParameterParser();
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		if(path.equals(XHR_FRAME_PATH)){//xhr frame�̗v��
			config.forwardVelocityTemplate(this, XHR_FRAME_TEMPLATE);
			return;
		}else if(path.equals(XHR_POLLING_PATH)){//xhr frame�����polling
			pooling(parameter);
			return;
		}else if(path.equals(DOWNLOAD_PATH)){//pa�o�R��download�v��
			download(parameter);
			return;
		}else if(path.equals(UPLOAD_PATH)){//form�����publish�v��
			formPublish(parameter);
			return;
		}
		String srcPath=getRequestMapping().getSourcePath();
		LinkManager paManager=LinkManager.getInstance(srcPath);
		forwardHandler(paManager.getNextHandler());
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
		linkSession.xhrResponse(this);
		unref();
	}
}
