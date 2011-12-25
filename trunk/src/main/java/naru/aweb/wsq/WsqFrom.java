package naru.aweb.wsq;

import java.util.List;

public class WsqFrom {
	private String clientChid;
	private String sessionChid;
	private String chid;/* clientChid+'#'+sessionChid */
	private String user;
	private List<String> roles;
	
	public WsqFrom(String clientChid,String sessionChid,String user,List<String>roles){
		this.clientChid=clientChid;
		this.sessionChid=sessionChid;
		this.chid=clientChid+"#"+sessionChid;
		this.user=user;
		this.roles=roles;
	}
	
	public String getChid() {
		return chid;
	}
	public String getUser() {
		return user;
	}
	public List<String> getRoles() {
		return roles;
	}

	public String getSessionChid() {
		return sessionChid;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((chid == null) ? 0 : chid.hashCode());
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
		final WsqFrom other = (WsqFrom) obj;
		if (chid == null) {
			if (other.chid != null)
				return false;
		} else if (!chid.equals(other.chid))
			return false;
		return true;
	}
}
