package naru.aweb.pa;

import java.util.List;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

public class PaPeer extends PoolBase{
	private PaSession paSession;
//	private String path;//�ڑ�path
//	private String authId;//�F��id
//	private Integer bid;//brawserId
//	private String userId;//userId
//	private List<String> roles;//�������[���Q
	public static PaPeer create(PaSession paSession,String qname,String subname){
		PaPeer peer=(PaPeer)PoolManager.getInstance(PaPeer.class);
		peer.paSession=paSession;
		peer.qname=qname;
		peer.subname=subname;
		return peer;
	}
	private String qname;//queue��
	private String subname;//�N���C�A���gid(�F��id�������ł��u���E�U�̈Ⴂ���ɂ��Aclient�͕ʂ�peer�Őڑ��ł���)
//	private boolean isAllowBlob;//blob���b�Z�[�W�̑��M���������ۂ�
	
	public String getAppId() {
		return paSession.getAppId();
	}

	public String getLoginId() {
		return paSession.getLoginId();
	}

	public List<String> getRoles() {
		return paSession.getRoles();
	}

	public Integer getBid() {
		return paSession.getBid();
	}

	public boolean isWs() {
		return paSession.isWs();
	}
	
	/* API */
	public boolean message(Object data){
		return false;
	}
	
	public String getQname() {
		return qname;
	}

	public String getSubname() {
		return subname;
	}

	/* API */
	public boolean unsubscribe(){
		return false;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((paSession == null) ? 0 : paSession.hashCode());
		result = prime * result + ((qname == null) ? 0 : qname.hashCode());
		result = prime * result + ((subname == null) ? 0 : subname.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PaPeer other = (PaPeer) obj;
		if (paSession == null) {
			if (other.paSession != null)
				return false;
		} else if (!paSession.equals(other.paSession))
			return false;
		if (qname == null) {
			if (other.qname != null)
				return false;
		} else if (!qname.equals(other.qname))
			return false;
		if (subname == null) {
			if (other.subname != null)
				return false;
		} else if (!subname.equals(other.subname))
			return false;
		return true;
	}
}
