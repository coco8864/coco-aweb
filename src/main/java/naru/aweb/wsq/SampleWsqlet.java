package naru.aweb.wsq;

import net.sf.json.JSON;

import org.apache.log4j.Logger;

public class SampleWsqlet implements Wsqlet {
	private static Logger logger=Logger.getLogger(SampleWsqlet.class);
	private WsqController controller;
	private String qname;

	@Override
	public void onStartQueue(String qname, WsqController controller) {
		this.qname=qname;
		this.controller=controller;
	}
	
	@Override
	public void onEndQueue() {
		logger.info("onEndQueue:");
	}

	@Override
	public void onPublish(WsqPeer from, String message) {
		logger.info("onPublish:"+from);
		controller.message(message);//来たメッセージをecho back
	}
	@Override
	public void onPublish(WsqPeer from, JSON message) {
		logger.info("onPublish:"+from);
		controller.message(message);//来たメッセージをecho back
	}
	@Override
	public void onPublish(WsqPeer from, BlobMessage message) {
		logger.info("onPublish:"+from);
		controller.message(message);//来たメッセージをecho back
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

}
