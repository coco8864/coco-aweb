package naru.aweb.link.api;

import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.link.Envelope;
import naru.aweb.link.LinkManager;
import naru.aweb.link.LinkSession;
import naru.aweb.link.LinkletWrapper;
import net.sf.json.JSONObject;

/**
 * linklet=�[���Ԃ̐ڑ�����ێ�����I�u�W�F�N�g<br/>
 * �T�[�o���̍\��<br/>
 * qname<br/>
 * subname<br/>
 * �[�����̍\��<br/>
 * appSid ... login�Z�V����id loginId,path�͕t��������ł���B<br/>
 * bid�@... �u���E�U�Z�V����id(login�Z�V�����͓����ł��\�������^�u���ɈႤid�ƂȂ�)<br/>
 */
public class LinkPeer extends PoolBase{
	public static LinkPeer create(LinkManager linkManager,LinkSession linkSession,String qname,String subname){
		LinkPeer peer=(LinkPeer)PoolManager.getInstance(LinkPeer.class);
		peer.linkSession=linkSession;
		peer.linkManager=linkManager;
		if(linkSession!=null){
			peer.linkSessionId=linkSession.getPoolId();
		}else{
			peer.linkSessionId=0;
		}
		peer.qname=qname;
		peer.subname=subname;
		return peer;
	}
	
	private LinkManager linkManager;
	private LinkSession linkSession;
	private long linkSessionId;
	private String qname;//queue��
	private String subname;//subname
	
	/**
	 * �Z�V�����������������܂��B<br/>
	 * �A�v������̌Ăяo���s��<br/>
	 */
	public void releaseSession(){
		linkSession=null;
	}
	
	/**
	 * ���̃A�v����path�����擾���܂��B<br/>
	 * @return path
	 */
	public String getPath() {
		return linkSession.getPath();
	}

	/**
	 * ���Y�A�v���P�[�V�����ŗL�̃Z�b�V����id���擾���܂��B<br/>
	 * @return appSid
	 */
	public String getAppSid() {
		return linkSession.getAppSid();
	}

	/**
	 * ���O�C��id���擾���܂��B<br/>
	 * @return�@loginId
	 */
	public String getLoginId() {
		return linkSession.getLoginId();
	}

	/**
	 * ���Y���[�U�����L����role�Q���擾���܂��B<br/>
	 * @return�@roles
	 */
	public List<String> getRoles() {
		return linkSession.getRoles();
	}

	/**
	 * ����Z�V�������A�u���E�U�̃^�u�P�ʂ�id���擾���܂��B<br/>
	 * @return�@bid
	 */
	public Integer getBid() {
		return linkSession.getBid();
	}

	/**
	 * websocket�ŒʐM�����ۂ����擾���܂��B<br/>
	 * false�̏ꍇ�Axhr�ʐM�Őڑ����Ă���B<br/>
	 * @return�@isWs
	 */
	public boolean isWs() {
		return linkSession.isWs();
	}
	
	/**
	 * browser����̃��b�Z�[�W���ۂ�<br/>
	 * server���̃A�v������publish���ꂽ�ꍇ�́Afalse��ԋp<br/>
	 * ���̏ꍇ�Apath,appSid,loginId,roles,bid,isWs�͎擾�ł��Ȃ��B
	 * @return
	 */
	public boolean isLinkSession(){
		return (linkSession!=null);
	}
	
	/* API */
	/**
	 * ���Y�[���Ƀ��b�Z�[�W�𑗐M���܂�<br/>
	 * �T�[�o���A�v�������message�̏ꍇ�́Alinklet��publish����</br>
	 * @param message ���M�f�[�^
	 */
	public void message(Object message){
		if(linkSession==null){
			linkManager.publish(qname, subname, message);
			return;
		}
		JSONObject json=new JSONObject();
		json.put(LinkSession.KEY_TYPE, LinkSession.TYPE_MESSAGE);
		json.put(LinkSession.KEY_MESSAGE, message);
		json.put(LinkSession.KEY_QNAME, qname);
		json.put(LinkSession.KEY_SUBNAME, subname);
		//subname�����͂����ł͌��߂��Ȃ�
		Envelope envelope=Envelope.pack(json);
		if(envelope.isBinary()){
			linkSession.sendBinary(envelope.createSendAsyncBuffer(null));
		}else{
			linkSession.sendJson(envelope.getSendJson(null));
		}
		envelope.unref(true);
	}
	
	/**
	 * ���Y�[���Ƀo�C�i�����b�Z�[�W�𑗐M���܂�<br/>
	 * �T�[�o���A�v�������message�̏ꍇ�́Alinklet��publish����</br>
	 * @param message ���M�f�[�^
	 */
	public void sendBinary(AsyncBuffer data){
		if(linkSession==null){
			linkManager.publish(qname, subname, data);
			return;
		}
		linkSession.sendBinary(data);
	}
	
	/* API */
	private long downloadSec=0;
	private synchronized String getDownloadKey(){
		downloadSec++;
		return "PP"+downloadSec;
	}
	
	/**
	 * blob�f�[�^���u���E�U�Ƀ_�E�����[�h�����܂�<br/>
	 * @param blob ���M�f�[�^
	 */
	public void download(Blob blob){
		JSONObject json=new JSONObject();
		json.put(LinkSession.KEY_TYPE, LinkSession.TYPE_DOWNLOAD);
		json.put(LinkSession.KEY_KEY, getDownloadKey());
		json.put(LinkSession.KEY_QNAME, qname);
		json.put(LinkSession.KEY_SUBNAME, subname);
		linkSession.download(json,blob);
	}
	
	/**
	 * blob�f�[�^���u���E�U�Ƀ_�E�����[�h�����܂�<br/>
	 * �A�v���P�[�V��������͗��p���Ȃ��B<br/>
	 * @param message
	 * @param blob
	 */
	public void download(JSONObject message,Blob blob){
		linkSession.download(message,blob);
	}
	
	/**
	 * JSONObject��[���ɑ��M���܂�<br/>
	 * @param data ���M�f�[�^
	 */
	public void sendJson(JSONObject data){
		linkSession.sendJson(data);
	}
	
	/**
	 * qname���擾���܂��B<br/>
	 * @return qname
	 */
	public String getQname() {
		return qname;
	}

	/**
	 * subname���擾���܂��B<br/>
	 * @return subname
	 */
	public String getSubname() {
		return subname;
	}

	/* API */
	/**
	 * �T�[�o�����炱��linkPeer�̒[����unsubscribe���܂��B<br/>
	 * @return unsubscribe�̉�
	 */
	public boolean unsubscribe(){
		return unsubscribe("api");
	}
	
	/**
	 * �T�[�o�����炱��linkPeer�̒[����unsubscribe���܂��B<br/>
	 * @param reason ����
	 * @return unsubscribe�̉�
	 */
	public boolean unsubscribe(String reason){
		/* client��unsubscribe(subscribe���s)��ʒm���� */
		if( linkSession!=null && linkSession.unsubscribeByPeer(this) ){
			/* linklet��onUnsubscribe��ʒm���� */
			LinkletWrapper linkletWrapper=linkManager.getLinkletWrapper(qname);
			return linkletWrapper.onUnubscribe(this,reason);
		}else{
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 0;//super.hashCode();
		result = prime * result	+ (int)linkSessionId;
		result = prime * result + ((qname == null) ? 0 : qname.hashCode());
		result = prime * result + ((subname == null) ? 0 : subname.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (getClass() != obj.getClass())
			return false;
		LinkPeer other = (LinkPeer) obj;
		if (linkSessionId != other.linkSessionId)
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
