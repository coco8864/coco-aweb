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
	private String subId;
	private String userId;
	private List<String> roles;
	
	public static WsqPeer create(AuthSession session,String qname,String subId){
		WsqPeer peer=(WsqPeer)PoolManager.getInstance(WsqPeer.class);
		peer.qname=qname;
		peer.subId=subId;
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

	public String getSubId() {
		return subId;
	}

	public String getUserId() {
		return userId;
	}

	public List<String> getRoles() {
		return roles;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((authId == null) ? 0 : authId.hashCode());
		result = prime * result + ((qname == null) ? 0 : qname.hashCode());
		result = prime * result + ((subId == null) ? 0 : subId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final WsqPeer other = (WsqPeer) obj;
		if (authId == null) {
			if (other.authId != null)
				return false;
		} else if (!authId.equals(other.authId))
			return false;
		if (qname == null) {
			if (other.qname != null)
				return false;
		} else if (!qname.equals(other.qname))
			return false;
		if (subId == null) {
			if (other.subId != null)
				return false;
		} else if (!subId.equals(other.subId))
			return false;
		return true;
	}

}
