package naru.aweb.wsq;

import net.sf.json.JSON;

/**
 * wsq�ɓo�^����I�u�W�F�N�g
 * TODO:���ׂẴ��\�b�h���L�q����K�v�͂Ȃ��A�K�v�ȃ��\�b�h�݂̂ł悢�悤�ɂ�����
 * @author Naru
 *
 */
public interface Wsqlet {
	public void onStartQueue(String wsqName,WsqCtx ctx);
	public void onEndQueue();
	
	/**
	 * �[������̃f�[�^���M��ʒm
	 * @param from ���M��
	 * @param message(String or BinaryMessage)
	 */
	public void onPublishText(WsqPeer from,String message);
	public void onPublishObj(WsqPeer from,JSON message);
	public void onPublishBlob(WsqPeer from,BlobMessage message);
	
	public void onSubscribe(WsqPeer from);
	public void onUnsubscribe(WsqPeer from);
	
	/**
	 * ���b�Z�[�W�����z�M����ꍇ�ɗ��p
	 * @return�@����Ăяo���܂ł̊Ԋu
	 */
	public long onWatch();
	
	/**
	 * ����wsqlet��BlobMessage�𗘗p���邩�ۂ�
	 * @return true BlobMessage���g��
	 */
	public boolean useBlob();
	
	/**
	 * useBlob��true�̏ꍇ�L��
	 * �u���E�U����Blob�����p�ł��Ȃ��ꍇ�̋��������߂�
	 * @return true BlobMessage�����e���Ȃ��u���E�U��message�݂̂𑗐M����
	 */
//	public boolean isBlobMessageOnly();
}
