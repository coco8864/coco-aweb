package naru.aweb.wsq;

/**
 * form
 *  chid:brawser���ō̔Ԃ���id
 *  user:userName
 *  roles:user������role���X�g
 * 
 * wsq�ɓo�^����I�u�W�F�N�g
 * TODO:���ׂẴ��\�b�h���L�q����K�v�͂Ȃ��A�K�v�ȃ��\�b�h�݂̂ł悢�悤�ɂ�����
 * @author Owner
 *
 */
public interface Wsqlet {
	public void onStartQueue(String wsqName,WsqController controller);
	public void onEndQueue();
	
	/**
	 * �[������̃f�[�^���M��ʒm
	 * @param from ���M��
	 * @param message(String or BinaryMessage)
	 */
	public void onPublish(WsqPeer from,Object message);
	public void onSubscribe(WsqPeer from);
	public void onUnsubscribe(WsqPeer from);
	
	/**
	 * �Ď���`���s�����ꍇ�A�Ăяo�����
	 * @return�@����Ăяo���܂ł̊Ԋu
	 */
	public long onWatch();
}
