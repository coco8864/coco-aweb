package naru.aweb.wsq;

import java.util.List;

import naru.async.Timer;
import naru.async.timer.TimerManager;

/**
 * ���ꓚ�A�񓯊����ʒʒm�pwatcher
 * @author Owner
 */
public class CmdWatcher implements WsqWatcher,Timer {
	public static WsqWatcher create(long timeout){
		CmdWatcher watcher=new CmdWatcher();
		if( WsqManager.createWsq(watcher)==false ){
			return null;
		}
		TimerManager.setTimeout(timeout, watcher, null);
		return watcher;
	}
	
	private WsqContext wsqContext;
	private String wsqName;
	
	public synchronized void doResult(Object result){
		if(wsqContext==null){
			return;
		}
		wsqContext.publish(result.toString());
		wsqContext.endQueue();
		wsqContext=null;
	}
	
	public String getWsqName(){
		return wsqName;
	}
	
	@Override
	public void onStartQueue(String wsqName, WsqContext wsqContext) {
		this.wsqName=wsqName;
		this.wsqContext=wsqContext;
	}

	/* timeout�o�߂��� */
	public synchronized void onTimer(Object userContext) {
		if(wsqContext==null){
			return;
		}
		wsqContext.endQueue();
		wsqContext=null;
	}
	
	@Override
	public void onEndQueue() {
	}

	@Override
	public void onMessage(String fromChid, String message) {
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
