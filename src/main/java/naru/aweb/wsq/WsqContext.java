package naru.aweb.wsq;

import java.util.List;

public interface WsqContext {
	/**
	 * 接続しているchannel全部に送信
	 * @param message
	 * @return 送信しchannel数を返却
	 */
	public int publish(String message);
	
	/**
	 * 指定したchannelに送信
	 * @param message
	 * @param chids　送信するchannel id群
	 * @return 送信しchannel数を返却
	 */
	public int publish(String message,List<String> chids);
	
	/**
	 * 指定したchannel以外のchannelに送信
	 * echobackしないため
	 * @param message
	 * @param dnyChid 送信しないchannel id
	 * @return
	 */
	public int publish(String message,String dnyChid);
	
	/**
	 *　指定したchannelの強制終了
	 * @param chid
	 * @return　chidの可否
	 */
	public boolean unsubscribe(String chid);
	
	public long getLastAccess(String chid);
	
	/**
	 * Queue処理終了
	 */
	public void endQueue();
}
