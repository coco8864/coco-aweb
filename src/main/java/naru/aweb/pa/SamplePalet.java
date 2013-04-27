package naru.aweb.pa;

import naru.aweb.pa.api.PaMsg;
import naru.aweb.pa.api.PaPeer;
import naru.aweb.pa.api.Palet;
import naru.aweb.pa.api.PaletCtx;

import org.apache.log4j.Logger;

public class SamplePalet implements Palet{
	private static Logger logger=Logger.getLogger(SamplePalet.class);
	private PaletCtx ctx;

	@Override
	public void init(String qname,String subname,PaletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(String reason) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onTimer() {
		// TODO Auto-generated method stub
	}

	@Override
	public void onSubscribe(PaPeer peer) {
		logger.info("onSubscribe.peer:"+peer);
	}

	@Override
	public void onUnsubscribe(PaPeer peer, String reason) {
		logger.info("onUnsubscribe.peer:"+peer+":"+reason);
	}


	@Override
	public void onPublish(PaPeer peer, PaMsg data) {
		logger.info("onPublishObj.peer:"+peer+":"+data);
		ctx.message(data);
	}

	/*
	@Override
	public void onPublishArray(PaPeer peer, List<?> data) {
		logger.info("onPublishArray.peer:"+peer+":"+data);
		Map f=(Map)data.get(0);
//		peer.download((Blob)f.get("file"));
		ctx.download((Blob)f.get("file"), ctx.getPeers(), null);
//		ctx.message(data);
	}
	*/
}
