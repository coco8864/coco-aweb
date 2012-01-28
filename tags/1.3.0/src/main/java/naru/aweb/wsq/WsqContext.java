package naru.aweb.wsq;

import java.util.List;

public interface WsqContext {
	/**
	 * �ڑ����Ă���channel�S���ɑ��M
	 * @param message(JSON or String)
	 * @return ���M��channel����ԋp
	 */
	public int publish(Object message);
	
	/**
	 * �w�肵��channel�ɑ��M
	 * @param message(JSON or String)
	 * @param chids�@���M����channel id�Q
	 * @return ���M��channel����ԋp
	 */
	public int publish(Object message,List<String> chids);
	
	/**
	 * �w�肵��channel�ȊO��channel�ɑ��M
	 * echoback���Ȃ�����
	 * @param message(JSON or String)
	 * @param dnyChid ���M���Ȃ�channel id
	 * @return
	 */
	public int publish(Object message,String dnyChid);
	
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
