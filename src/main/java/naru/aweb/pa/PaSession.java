package naru.aweb.pa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.LogoutEvent;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

public class PaSession extends PoolBase implements LogoutEvent{
	public static final String TYPE_NEGOTIATION="negotiation";
	public static final String TYPE_PUBLISH="publish";
	public static final String TYPE_SUBSCRIBE="subscribe";
	public static final String TYPE_UNSUBSCRIBE="unsubscribe";
	public static final String TYPE_CLOSE="close";
	
	static PaSession create(String path,String authId,Integer bid,boolean isWs){
		PaSession paSession=(PaSession)PoolManager.getInstance(PaSession.class);
		paSession.path=path;
		paSession.authId=authId;
		paSession.bid=bid;
		paSession.isWs=isWs;
		return paSession;
	}
	
	@Override
	public void recycle() {
		peers.clear();
		super.recycle();
	}
	
	private String path;//接続path
	
	//authSession由来
	private String authId;//認証id
	private String userId;
	private List<String> roles;
	
	private Integer bid;//brawserId
	private boolean isWs;
	private Map<String,PaPeer> peers=new HashMap<String,PaPeer>();
	
	private PaHandler handler;
	public void message(Object data){
	}
	
	public void subscribe(JSONObject msg){
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
	
	public void unsubscribe(JSONObject req){
	}
	
	public void publish(JSONObject req){
	}
	
	
	@Override
	public void onLogout() {//logoffした場合の対処
	}

}
