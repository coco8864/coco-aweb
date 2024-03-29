package naru.aweb.wsq;

import java.util.Collection;
import java.util.Set;

public interface WsqCtx {
	/**
	 * 指定したpeer以外のpeerに送信
	 * echobackしないためにdnyPeersを利用
	 * @param message(JSON or String or BlobMessage)
	 * @param peers 送信するpeer群,nullの場合,subscribe中の全peer
	 * @param dnyPeers 送信しないpeer群,nullの場合
	 * @return
	 */
	public int message(Object message,Collection<WsqPeer> peers, Collection<WsqPeer> dnyPeers);
	
	public int message(Object message,Collection<WsqPeer> peers, WsqPeer dnyPeer);
	
	/**
	 * 指定したpeerに送信
	 * @param message(JSON or String or BlobMessage)
	 * @param peers　送信するpeer群
	 * @return 送信しchannel数を返却
	 */
	public int message(Object message,Collection<WsqPeer> peers);

	public int message(Object message,WsqPeer peer);

	/**
	 * 接続しているpeer全部に送信
	 * @param message(JSON or String or BlobMessage)
	 * @return 送信しpeer数を返却
	 */
	public int message(Object message);
	
	/**
	 *　指定したpeerを強制退去
	 * @param peer
	 * @return　peerがなかった場合false
	 */
	public boolean unsubscribe(WsqPeer peer);
	
	/**
	 *　指定したpeerの最終アクセス時刻
	 * @param peer
	 * @return　peerがなかった場合-1
	 */
	public long getLastAccess(WsqPeer peer);
	
	/**
	 * 接続中の全peerを返却
	 * @return　接続中の全peer
	 */
	public Set<WsqPeer> getSubscribePeers();
	
	/**
	 * Queue処理終了
	 */
	public void endQueue();
}
