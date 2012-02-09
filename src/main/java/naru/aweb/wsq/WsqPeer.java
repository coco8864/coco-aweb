package naru.aweb.wsq;

import java.util.Collections;
import java.util.List;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.config.User;

public class WsqPeer extends PoolBase{
	private String qname;
	private String authId;
	private String subscribeId;
	private String userId;
	private List<String> roles;
	
	public static WsqPeer create(AuthSession session,String qname,String subscribeId){
		WsqPeer peer=(WsqPeer)PoolManager.getInstance(WsqPeer.class);
		peer.qname=qname;
		peer.subscribeId=subscribeId;
		User user=session.getUser();
		peer.authId=session.getToken();
		peer.userId=user.getLoginId();
		peer.roles=Collections.unmodifiableList(user.getRolesList());
		return peer;
	}
	
	public String getQname() {
		return qname;
	}

	public String getAuthId() {
		return authId;
	}

	public String getSubscribeId() {
		return subscribeId;
	}

	public String getUserId() {
		return userId;
	}

	public List<String> getRoles() {
		return roles;
	}

}
