package naru.aweb.pa;

import java.util.List;
import java.util.Map;

public interface Palet {
	/**
	 * call when this object is loaded
	 * subnameÇ™nullÇÃèÍçáÇÕÅArootPalet
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
	 * this method call only rootPalet
	 * @see PaletCtx#setInterval
	 */
	void onTimer();
	
	/**
	 * call when subscribe this queue and subname
	 * @param peer
	 */
	void onSubscribe(PaPeer peer);
	void onUnsubscribe(PaPeer peer,String reason);
	void onPublishText(PaPeer peer,String data);
	void onPublishObj(PaPeer peer,Map<String,?> data);
	void onPublishArray(PaPeer peer,List<?> data);
}
