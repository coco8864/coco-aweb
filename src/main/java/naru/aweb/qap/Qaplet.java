package naru.aweb.qap;

import net.sf.json.JSON;

/**
 * wsq�ɓo�^����I�u�W�F�N�g
 * TODO:���ׂẴ��\�b�h���L�q����K�v�͂Ȃ��A�K�v�ȃ��\�b�h�݂̂ł悢�悤�ɂ�����
 * @author Naru
 *
 */
public interface Qaplet {
	public void onStartQueue(String wsqName,QapCtx ctx);
	public void onEndQueue();
	
	/**
	 * �[������̃f�[�^���M��ʒm
	 * @param from ���M��
	 * @param message(String or BinaryMessage)
	 */
	public void onPublishText(QapPeer from,String message);
	public void onPublishObj(QapPeer from,JSON message);
	public void onPublishBlob(QapPeer from,BlobMessage message);
	
	public void onSubscribe(QapPeer from);
	public void onUnsubscribe(QapPeer from);
	
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
}
