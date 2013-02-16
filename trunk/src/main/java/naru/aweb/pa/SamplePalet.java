package naru.aweb.pa;

import java.util.Map;

import org.apache.log4j.Logger;

public class SamplePalet implements Palet{
	private static Logger logger=Logger.getLogger(SamplePalet.class);
	private PaletCtx ctx;

	@Override
	public void init(PaletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(PaletCtx ctx, String reason) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onTimer(PaletCtx ctx) {
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
	public void onPublishText(PaPeer peer, String data) {
		logger.info("onPublishText.peer:"+peer+":"+data);
		ctx.message(data);
	}

	@Override
	public void onPublishObj(PaPeer peer, Map data) {
		logger.info("onPublishObj.peer:"+peer+":"+data);
		ctx.message(data);
	}

}
