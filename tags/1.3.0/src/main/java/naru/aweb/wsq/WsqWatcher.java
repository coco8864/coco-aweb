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
public interface WsqWatcher {
	public void onStartQueue(String wsqName,WsqContext context);
	public void onEndQueue();
	
	/**
	 * 端末からのデータ送信を通知
	 * @param from 送信元
	 * @param message(JSON or String)
	 */
	public void onMessage(WsqFrom from,Object message);
	
	public void onSubscribe(WsqFrom from);
	public void onUnsubscribe(WsqFrom from);
	
	/**
	 * 定期監視の前に毎回呼び出される
	 * @return　監視対象にする場合true
	 */
	public boolean onWatch();
}
