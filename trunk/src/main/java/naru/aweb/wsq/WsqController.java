package naru.aweb.wsq;

import java.util.Collection;

public interface WsqController {
	/**
	 * 指定したchannel以外のchannelに送信
	 * echobackしないためにdnyPeersを利用
	 * @param message(JSON or String or BinaryMessage)
	 * @param dnyPeers 送信しないchannel id
	 * @return
	 */
	public int message(Object message,Collection<WsqPeer> peers, Collection<WsqPeer> dnyPeers);
	
	public int message(Object message,Collection<WsqPeer> peers, WsqPeer dnyPeer);
	
	/**
	 * 指定したchannelに送信
	 * @param message(JSON or String or BinaryMessage)
	 * @param chids　送信するchannel id群
	 * @return 送信しchannel数を返却
	 */
	public int message(Object message,Collection<WsqPeer> peers);

	public int message(Object message,WsqPeer peer);

	/**
	 * 接続しているchannel全部に送信
	 * @param message(JSON or String or BinaryMessage)
	 * @return 送信しchannel数を返却
	 */
	public int message(Object message);
	
	/**
	 *　指定したchannelの強制終了
	 * @param chid
	 * @return　chidの可否
	 */
	public boolean unsubscribe(WsqPeer peer);
	
	public long getLastAccess(WsqPeer peer);
	
	/**
	 * Queue処理終了
	 */
	public void endQueue();
}
