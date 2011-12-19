package naru.aweb.wsq;

import java.util.List;

/**
 * wsqに登録するオブジェクト
 * TODO:すべてのメソッドを記述する必要はなく、必要なメソッドのみでよいようにしたい
 * @author Owner
 *
 */
public interface WsqWatcher {
	public void onStartQueue(String wsqName,WsqContext context);
	public void onEndQueue();
	public void onMessage(String fromChid,String message);
	public void onSubscribe(String fromChid,String userName,List<String> roles);
	public void onUnsubscribe(String fromChid);
	
	
	/**
	 * 定期監視の前に毎回呼び出される
	 * @return　監視対象にする場合true
	 */
	public boolean onWatch();
}
