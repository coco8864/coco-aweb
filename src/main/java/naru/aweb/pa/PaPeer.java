package naru.aweb.pa;

import java.util.List;

import naru.async.pool.PoolBase;

public class PaPeer extends PoolBase{
	private PaSession paSession;
	private String path;//�ڑ�path
	private String authId;//�F��id
	private Integer bid;//brawserId
	private String userId;//userId
	private List<String> roles;//�������[���Q
	
	private String qname;//queue��
	private String subname;//�N���C�A���gid(�F��id�������ł��u���E�U�̈Ⴂ���ɂ��Aclient�͕ʂ�peer�Őڑ��ł���)
	private boolean isAllowBlob;//blob���b�Z�[�W�̑��M���������ۂ�
	
	public boolean message(Object data){
		return false;
	}
	
	public boolean unsubscribe(){
		return false;
	}

}
