package naru.aweb.wsq;

import net.sf.json.JSON;

import org.apache.log4j.Logger;

public class SampleWsqlet implements Wsqlet {
	private static Logger logger=Logger.getLogger(SampleWsqlet.class);
	private WsqCtx ctx;
	private String qname;

	@Override
	public void onStartQueue(String qname, WsqCtx ctx) {
		this.qname=qname;
		this.ctx=ctx;
	}
	
	@Override
	public void onEndQueue() {
		logger.info("onEndQueue:");
	}

	@Override
	public void onPublishText(WsqPeer from, String message) {
		logger.info("onPublish:"+from);
		ctx.message(message);//来たメッセージをecho back
	}
	@Override
	public void onPublishObj(WsqPeer from, JSON message) {
		logger.info("onPublish:"+from);
		ctx.message(message);//来たメッセージをecho back
	}
	@Override
	public void onPublishBlob(WsqPeer from, BlobMessage message) {
		logger.info("onPublish:"+from);
		Blob blob=message.getBlob(0);
//		ByteBuffer[] buf=blob.read();
		ctx.message(message);//来たメッセージをecho back
	}

	@Override
	public void onSubscribe(WsqPeer from) {
		logger.info("onSubscribe:"+from);
	}

	@Override
	public void onUnsubscribe(WsqPeer from) {
		logger.info("onUnsubscribe:"+from);
	}

	@Override
	public long onWatch() {
		logger.info("onWatch:");
		return 0;
	}

//	@Override
//	public boolean isBlobMessageOnly() {
//		return true;
//	}

	@Override
	public boolean useBlob() {
		return true;
	}

}
