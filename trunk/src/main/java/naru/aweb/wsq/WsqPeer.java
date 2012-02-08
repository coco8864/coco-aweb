package naru.aweb.wsq;

import java.util.List;

public class WsqPeer {
	private String peerSessionId;
	private String peerSubscribeId;
	private String userId;
	private List<String> roles;
	
	public WsqPeer(String peerSessionId,String peerSubscribeId,String userId,List<String>roles){
		this.peerSessionId=peerSessionId;
		this.peerSubscribeId=peerSubscribeId;
		this.userId=userId;
		this.roles=roles;
	}

	public String getPeerSessionId() {
		return peerSessionId;
	}

	public String getPeerSubscribeId() {
		return peerSubscribeId;
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
		result = prime * result
				+ ((peerSessionId == null) ? 0 : peerSessionId.hashCode());
		result = prime * result
				+ ((peerSubscribeId == null) ? 0 : peerSubscribeId.hashCode());
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
		if (peerSessionId == null) {
			if (other.peerSessionId != null)
				return false;
		} else if (!peerSessionId.equals(other.peerSessionId))
			return false;
		if (peerSubscribeId == null) {
			if (other.peerSubscribeId != null)
				return false;
		} else if (!peerSubscribeId.equals(other.peerSubscribeId))
			return false;
		return true;
	}
}
