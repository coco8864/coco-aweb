package naru.aweb.pa;

import java.util.List;

import naru.async.pool.PoolBase;

public class PaPeer extends PoolBase{
	private PaSession paSession;
	private String path;//接続path
	private String authId;//認証id
	private Integer bid;//brawserId
	private String userId;//userId
	private List<String> roles;//所属ロール群
	
	private String qname;//queue名
	private String subname;//クライアントid(認証idが同じでもブラウザの違い等により、clientは別のpeerで接続できる)
	private boolean isAllowBlob;//blobメッセージの送信を許すか否か
	
	public boolean message(Object data){
		return false;
	}
	
	public boolean unsubscribe(){
		return false;
	}

}
