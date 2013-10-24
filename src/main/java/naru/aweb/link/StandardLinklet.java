package naru.aweb.link;

import naru.aweb.link.api.LinkMsg;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.link.api.LinkletCtx;

import org.apache.log4j.Logger;

public class StandardLinklet implements Linklet{
	private static Logger logger=Logger.getLogger(StandardLinklet.class);
	private LinkletCtx ctx;

	@Override
	public void init(String qname,String subname,LinkletCtx ctx) {
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
	public void onSubscribe(LinkPeer peer) {
		logger.info("onSubscribe.peer:"+peer);
	}

	@Override
	public void onUnsubscribe(LinkPeer peer, String reason) {
		logger.info("onUnsubscribe.peer:"+peer+":"+reason);
	}

	@Override
	public void onPublish(LinkPeer peer, LinkMsg data) {
		logger.info("onPublish(LinkMsg).peer:"+peer+":"+data);
		ctx.message(data,peer.getSubname(),peer);
	}
	
	@Override
	public void onPublish(LinkPeer peer, String data) {
		logger.info("onPublish(String).peer:"+peer+":"+data);
		ctx.message(data,peer.getSubname(),peer);
	}
}
