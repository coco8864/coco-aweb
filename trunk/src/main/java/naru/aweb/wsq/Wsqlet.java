package naru.aweb.wsq;

import net.sf.json.JSON;

/**
 * form
 *  chid:brawser側で採番するid
 *  user:userName
 *  roles:userが持つroleリスト
 * 
 * wsqに登録するオブジェクト
 * TODO:すべてのメソッドを記述する必要はなく、必要なメソッドのみでよいようにしたい
 * @author Owner
 *
 */
public interface Wsqlet {
	public void onStartQueue(String wsqName,WsqController controller);
	public void onEndQueue();
	
	/**
	 * 端末からのデータ送信を通知
	 * @param from 送信元
	 * @param message(String or BinaryMessage)
	 */
	public void onPublish(WsqPeer from,String message);
	public void onPublish(WsqPeer from,JSON message);
	public void onPublish(WsqPeer from,BlobMessage message);
	
	public void onSubscribe(WsqPeer from);
	public void onUnsubscribe(WsqPeer from);
	
	/**
	 * 監視定義を行った場合、呼び出される
	 * @return　次回呼び出しまでの間隔
	 */
	public long onWatch();
}
