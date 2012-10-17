package naru.aweb.qap;

import net.sf.json.JSON;

/**
 * wsqに登録するオブジェクト
 * TODO:すべてのメソッドを記述する必要はなく、必要なメソッドのみでよいようにしたい
 * @author Naru
 *
 */
public interface Qaplet {
	public void onStartQueue(String wsqName,QapCtx ctx);
	public void onEndQueue();
	
	/**
	 * 端末からのデータ送信を通知
	 * @param from 送信元
	 * @param message(String or BinaryMessage)
	 */
	public void onPublishText(QapPeer from,String message);
	public void onPublishObj(QapPeer from,JSON message);
	public void onPublishBlob(QapPeer from,BlobMessage message);
	
	public void onSubscribe(QapPeer from);
	public void onUnsubscribe(QapPeer from);
	
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
}
