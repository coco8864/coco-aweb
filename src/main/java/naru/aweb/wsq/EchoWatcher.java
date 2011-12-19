package naru.aweb.wsq;

import java.util.List;

/**
 * ˆê–âˆê“šA”ñ“¯ŠúŒ‹‰Ê’Ê’m—pwatcher
 * @author Owner
 */
public class EchoWatcher implements WsqWatcher {
	private WsqContext wsqContext;
	private String wsqName;

	public String getWsqName(){
		return wsqName;
	}
	
	@Override
	public void onMessage(String fromChid, String message) {
		wsqContext.publish(message, fromChid);
	}
	
	@Override
	public void onEndQueue() {
	}

	@Override
	public void onStartQueue(String wsqName, WsqContext wsqContext) {
	}

	@Override
	public void onSubscribe(String fromChid, String userName, List<String> roles) {
	}

	@Override
	public void onUnsubscribe(String fromChid) {
	}

	@Override
	public boolean onWatch() {
		return false;
	}
}
