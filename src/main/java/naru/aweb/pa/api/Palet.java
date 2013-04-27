package naru.aweb.pa.api;

public interface Palet {
	/**
	 * call when this object is loaded
	 * subnameがnullの場合は、rootPalet
	 * @param qname
	 * @param subname
	 * @param ctx
	 */
	void init(String qname,String subname,PaletCtx ctx);
	
	/**
	 * call when this object is unloaded
	 */
	void term(String reason);
	
	/**
	 * call when time interval
	 * @see PaletCtx#setInterval
	 */
	void onTimer();
	
	/**
	 * call when subscribe this queue and subname
	 * @param peer
	 */
	void onSubscribe(PaPeer peer);
	void onUnsubscribe(PaPeer peer,String reason);
	
	/**
	 * Connection Deffedにpublishした場合は、peerにsubnameが設定されない。
	 * この場合、peerに直接messageすることができない
	 * サーバ側からmessageした場合は、PaSessionが設定されない。
	 * この場合、peerにmessageするとqname,subnameに合致するpeerに広報される
	 * dataは、BlobやDateを含む場合、HashMap,それ以外の場合、JSONObjectで通知
	 * @param peer
	 * @param data
	 */
	void onPublish(PaPeer peer,PaMsg data);
}
