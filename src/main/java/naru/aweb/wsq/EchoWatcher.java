package naru.aweb.wsq;

import java.util.List;

/**
 * ˆê–âˆê“šA”ñ“¯ŠúŒ‹‰Ê’Ê’m—pwatcher
 * @author Owner
 */
public class EchoWatcher implements Wsqlet {
	private WsqController wsqContext;
	private String wsqName;

	public String getWsqName(){
		return wsqName;
	}
	
	@Override
	public void onMessage(WsqPeer fromChid, Object message) {
		wsqContext.publish(message.toString(), fromChid.getChid());
	}
	
	@Override
	public void onEndQueue() {
	}

	@Override
	public void onStartQueue(String wsqName, WsqController wsqContext) {
	}

	@Override
	public void onSubscribe(WsqPeer from) {
	}

	@Override
	public void onUnsubscribe(WsqPeer from) {
	}

	@Override
	public boolean onWatch() {
		return false;
	}
}
