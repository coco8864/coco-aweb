package naru.aweb.pa;

import java.util.Map;

public interface Palet {
	void init(PaletCtx ctx);
	void term(PaletCtx ctx,String reason);
	void onTimer(PaletCtx ctx);
	
	void onSubscribe(PaPeer peer);
	void onUnsubscribe(PaPeer peer,String reason);
	void onPublishText(PaPeer peer,String data);
	void onPublishObj(PaPeer peer,Map<String,?> data);
}
