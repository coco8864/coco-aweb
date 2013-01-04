package naru.aweb.pa;

import java.util.Map;
import java.util.Set;

import net.sf.json.JSON;

public class PaletWrapper extends PaletCtx{
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
		}else if(data instanceof BlobData){
			palet.onPublishBlob(peer, (BlobData)data);
		}else{
		}
		
	}

}
