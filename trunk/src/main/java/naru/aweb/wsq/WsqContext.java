package naru.aweb.wsq;

import java.util.List;

public interface WsqContext {
	/**
	 * �ڑ����Ă���channel�S���ɑ��M
	 * @param message
	 * @return ���M��channel����ԋp
	 */
	public int publish(String message);
	
	/**
	 * �w�肵��channel�ɑ��M
	 * @param message
	 * @param chids�@���M����channel id�Q
	 * @return ���M��channel����ԋp
	 */
	public int publish(String message,List<String> chids);
	
	/**
	 * �w�肵��channel�ȊO��channel�ɑ��M
	 * echoback���Ȃ�����
	 * @param message
	 * @param dnyChid ���M���Ȃ�channel id
	 * @return
	 */
	public int publish(String message,String dnyChid);
	
	/**
	 *�@�w�肵��channel�̋����I��
	 * @param chid
	 * @return�@chid�̉�
	 */
	public boolean unsubscribe(String chid);
	
	public long getLastAccess(String chid);
	
	/**
	 * Queue�����I��
	 */
	public void endQueue();
}
