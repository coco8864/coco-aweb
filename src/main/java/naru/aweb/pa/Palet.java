package naru.aweb.pa;

import net.sf.json.JSON;

public interface Palet {
	void init(PaletCtx ctx);
	void term(PaletCtx ctx,String reason);
	void onTimer(PaletCtx ctx);
	
	void onSubscribe(PaPeer peer);
	void onUnsubscribe(PaPeer peer,String reason);
	void onPublishText(PaPeer peer,String data);
	void onPublishObj(PaPeer peer,JSON data);
}
