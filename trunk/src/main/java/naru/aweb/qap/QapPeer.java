package naru.aweb.qap;

import java.util.Collections;
import java.util.List;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.config.User;

public class QapPeer extends PoolBase{
	private String path;//接続path
	private String authId;//認証id
	private Integer bid;//brawserId
	private String qname;//queue名
	private String subname;//クライアントid(認証idが同じでもブラウザの違い等により、clientは別のpeerで接続できる)
	
	private String userId;//userId
	private List<String> roles;//所属ロール群
	private boolean isAllowBlob;//blobメッセージの送信を許すか否か
	public static QapPeer create(AuthSession session,String srcPath,Integer bid,String qname,String subId){
		return create(session, srcPath, bid,qname, subId,false);
	}
	
	public static QapPeer create(AuthSession session,String path,Integer bid,String qname,String subname,boolean isAllowBlob){
		QapPeer peer=(QapPeer)PoolManager.getInstance(QapPeer.class);
		peer.path=path;
		peer.bid=bid;
		peer.qname=qname;
		peer.subname=subname;
		User user=session.getUser();
		peer.authId=session.getToken();
		peer.userId=user.getLoginId();
		peer.roles=Collections.unmodifiableList(user.getRolesList());
		peer.isAllowBlob=isAllowBlob;
		return peer;
	}
	
	public String getPath() {
		return path;
	}

	public String getQname() {
		return qname;
	}

	public String getAuthId() {
		return authId;
	}

	public String getSubname() {
		return subname;
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
		result = prime * result + ((bid == null) ? 0 : bid.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((subname == null) ? 0 : subname.hashCode());
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
		final QapPeer other = (QapPeer) obj;
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
		if (bid == null) {
			if (other.bid != null)
				return false;
		} else if (!bid.equals(other.bid))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (subname == null) {
			if (other.subname != null)
				return false;
		} else if (!subname.equals(other.subname))
			return false;
		return true;
	}

	public boolean isAllowBlob() {
		return isAllowBlob;
	}


}
