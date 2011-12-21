package naru.aweb.wsq;

import java.util.List;

/**
 * 一問一答、非同期結果通知用watcher
 * @author Owner
 */
public class EchoWatcher implements WsqWatcher {
	private WsqContext wsqContext;
	private String wsqName;

	public String getWsqName(){
		return wsqName;
	}
	
	@Override
	public void onMessage(WsqFrom fromChid, Object message) {
		wsqContext.publish(message.toString(), fromChid.getChid());
	}
	
	@Override
	public void onEndQueue() {
	}

	@Override
	public void onStartQueue(String wsqName, WsqContext wsqContext) {
	}

	@Override
	public void onSubscribe(WsqFrom from) {
	}

	@Override
	public void onUnsubscribe(WsqFrom from) {
	}

	@Override
	public boolean onWatch() {
		return false;
	}
}
