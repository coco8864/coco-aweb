package naru.aweb.pa;

import java.util.List;
import java.util.Map;

public interface Palet {
	void init(PaletCtx ctx);
	void term(String reason);
	void onTimer();
	
	void onSubscribe(PaPeer peer);
	void onUnsubscribe(PaPeer peer,String reason);
	void onPublishText(PaPeer peer,String data);
	void onPublishObj(PaPeer peer,Map<String,?> data);
	void onPublishArray(PaPeer peer,List<?> data);
}
