package naru.aweb.qap;

import java.util.Collection;
import java.util.Set;

public interface QapCtx {
	/**
	 * �w�肵��peer�ȊO��peer�ɑ��M,echoback���Ȃ����߂�dnyPeers�𗘗p
	 * @param message(JSON or String or BlobMessage)
	 * @param peers ���M����peer�Q,null�̏ꍇ,subscribe���̑Speer
	 * @param dnyPeers ���M���Ȃ�peer�Q,null�̏ꍇ
	 * @return
	 */
	public int message(Object message,Collection<QapPeer> peers, Collection<QapPeer> dnyPeers);
	
	public int message(Object message,Collection<QapPeer> peers, QapPeer dnyPeer);
	
	/**
	 * �w�肵��peer�ɑ��M
	 * @param message(JSON or String or BlobMessage)
	 * @param peers�@���M����peer�Q
	 * @return ���M��channel����ԋp
	 */
	public int message(Object message,Collection<QapPeer> peers);

	public int message(Object message,QapPeer peer);

	/**
	 * �ڑ����Ă���peer�S���ɑ��M
	 * @param message(JSON or String or BlobMessage)
	 * @return ���M��peer����ԋp
	 */
	public int message(Object message);
	
	/**
	 *�@�w�肵��peer�������ދ�
	 * @param peer
	 * @return�@peer���Ȃ������ꍇfalse
	 */
	public boolean unsubscribe(QapPeer peer);
	
	/**
	 *�@�w�肵��peer�̍ŏI�A�N�Z�X����
	 * @param peer
	 * @return�@peer���Ȃ������ꍇ-1
	 */
	public long getLastAccess(QapPeer peer);
	
	/**
	 * �ڑ����̑Speer��ԋp
	 * @return�@�ڑ����̑Speer
	 */
	public Set<QapPeer> getSubscribePeers();
	
	/**
	 * Queue�����I��
	 */
	public void endQueue();
}
