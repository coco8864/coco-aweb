package naru.aweb.link.api;

/**
 * linkアプリのサーバ側<br/>
 * このインスタンスをimplementしてサーバ側のlinkアプリを記述する<br/>
 * pub/subモデルのトピックに位置する<br/>
 * qname/subname単位でインスタンスが作られる。servletのライフサイクルに似ている。<br/>
 * ブラウザからのアクションがonXXメソッドにイベントとして通知される。<br/>
 * @author naru
 *
 */
public interface Linklet {
	/**
	 * サービス開始時に呼び出される。<br/>
	 * @param qname 担当するqname
	 * @param subname 担当するsubname,nullの場合はrootLinklet
	 * @param ctx ブラウザにアクションするためのハンドラー
	 */
	void init(String qname,String subname,LinkletCtx ctx);
	
	/**
	 * サービス終了時に呼び出される。<br/>
	 * @param reason 終了理由
	 */
	void term(String reason);
	
	/**
	 * 定期的に呼び出されるイベント通知<br/>
	 * LinkletCtx#setIntervalで呼び出し間隔を指定する。<br/>
	 * @see LinkletCtx#setInterval
	 */
	void onTimer();
	
	/**
	 * ブラウザのsubscribe時に呼び出されるイベント通知<br/>
	 * @param peer subscribeした端末情報
	 */
	void onSubscribe(LinkPeer peer);
	
	/**
	 * ブラウザがunsubscribe時に呼び出されるイベント通知<br/>
	 * @param peer unsubscribeされた端末情報
	 * @param reason 原因
	 */
	void onUnsubscribe(LinkPeer peer,String reason);
	
	/**
	 * ブラウザがpublishした際に呼び出されるイベント通知<br/>
	 * @param peer　送信した端末情報、サーバ側からmessageした場合は、LinkSessionが設定されない。
	 * @param data　送信されたデータ、BlobやDateを含む場合、HashMap,それ以外の場合、JSONObject
	 */
	void onPublish(LinkPeer peer,LinkMsg data);
}
