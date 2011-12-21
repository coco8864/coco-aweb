package naru.aweb.wsq;


import net.sf.json.JSON;

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
public interface WsqWatcher {
	public void onStartQueue(String wsqName,WsqContext context);
	public void onEndQueue();
	
	/**
	 * �[������̃f�[�^���M��ʒm
	 * @param from ���M��
	 * @param message(JSON or String)
	 */
	public void onMessage(WsqFrom from,Object message);
	
	public void onSubscribe(WsqFrom from);
	public void onUnsubscribe(WsqFrom from);
	
	/**
	 * ����Ď��̑O�ɖ���Ăяo�����
	 * @return�@�Ď��Ώۂɂ���ꍇtrue
	 */
	public boolean onWatch();
}
