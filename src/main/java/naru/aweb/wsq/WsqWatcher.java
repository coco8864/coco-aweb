package naru.aweb.wsq;

import java.util.List;

/**
 * wsq�ɓo�^����I�u�W�F�N�g
 * TODO:���ׂẴ��\�b�h���L�q����K�v�͂Ȃ��A�K�v�ȃ��\�b�h�݂̂ł悢�悤�ɂ�����
 * @author Owner
 *
 */
public interface WsqWatcher {
	public void onStartQueue(String wsqName,WsqContext context);
	public void onEndQueue();
	public void onMessage(String fromChid,String message);
	public void onSubscribe(String fromChid,String userName,List<String> roles);
	public void onUnsubscribe(String fromChid);
	
	
	/**
	 * ����Ď��̑O�ɖ���Ăяo�����
	 * @return�@�Ď��Ώۂɂ���ꍇtrue
	 */
	public boolean onWatch();
}
