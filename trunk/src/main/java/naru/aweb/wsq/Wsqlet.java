package naru.aweb.wsq;

import net.sf.json.JSON;

/**
 * wsqに登録するオブジェクト
 * TODO:すべてのメソッドを記述する必要はなく、必要なメソッドのみでよいようにしたい
 * @author Naru
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
	 * メッセージを定期配信する場合に利用
	 * @return　次回呼び出しまでの間隔
	 */
	public long onWatch();
	
	/**
	 * このwsqletでBlobMessageを利用するか否か
	 * @return true BlobMessageを使う
	 */
	public boolean useBlob();
	
	/**
	 * useBlobがtrueの場合有効
	 * ブラウザ側がBlobが利用できない場合の挙動を決める
	 * @return true BlobMessageを許容しないブラウザにmessageのみを送信する
	 */
	public boolean isBlobMessageOnly();
}
