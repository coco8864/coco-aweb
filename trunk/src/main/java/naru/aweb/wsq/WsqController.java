package naru.aweb.wsq;

import java.util.Collection;

public interface WsqController {
	/**
	 * �w�肵��channel�ȊO��channel�ɑ��M
	 * echoback���Ȃ�����
	 * @param message(JSON or String)
	 * @param dnyChid ���M���Ȃ�channel id
	 * @return
	 */
	public int message(Object message,Collection<WsqPeer> peers, Collection<WsqPeer> dnyPeers);
	
	public int message(Object message,Collection<WsqPeer> peers, WsqPeer dnyPeer);
	
	/**
	 * �w�肵��channel�ɑ��M
	 * @param message(JSON or String)
	 * @param chids�@���M����channel id�Q
	 * @return ���M��channel����ԋp
	 */
	public int message(Object message,Collection<WsqPeer> peers);

	public int message(Object message,WsqPeer peer);

	/**
	 * �ڑ����Ă���channel�S���ɑ��M
	 * @param message(JSON or String)
	 * @return ���M��channel����ԋp
	 */
	public int message(Object message);
	
	/**
	 *�@�w�肵��channel�̋����I��
	 * @param chid
	 * @return�@chid�̉�
	 */
	public boolean unsubscribe(WsqPeer peer);
	
	public long getLastAccess(WsqPeer peer);
	
	/**
	 * Queue�����I��
	 */
	public void endQueue();
}
