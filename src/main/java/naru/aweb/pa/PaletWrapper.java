package naru.aweb.pa;

import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import net.sf.json.JSON;

public class PaletWrapper extends PaletCtx{
	private static Logger logger=Logger.getLogger(PaSession.class);
	
	private Set<PaPeer> peers;
	private Map<String,Set<PaPeer>> subnamePeersMap;
	private Palet palet;
	
	void onSubscribe(PaPeer peer){
		palet.onSubscribe(peer);
	}
	
	void onUnubscribe(PaPeer peer){
		palet.onUnsubscribe(peer,null);
	}
	
	void onPublish(PaPeer peer,Object data){
		if(data instanceof String){
			palet.onPublishText(peer,(String)data);
		}else if(data instanceof JSON){
			palet.onPublishObj(peer,(JSON)data);
		}else if(data instanceof BlobMessage){
			palet.onPublishBlob(peer, (BlobMessage)data);
		}else{
			logger.error("onPublish data type" + data.getClass().getName());
		}
	}

}
