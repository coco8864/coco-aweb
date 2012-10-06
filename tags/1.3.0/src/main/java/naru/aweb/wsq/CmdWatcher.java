package naru.aweb.wsq;

/**
 * ���ꓚ�A�񓯊����ʒʒm�pwatcher
 * @author Owner
 */
public class CmdWatcher implements WsqWatcher {
	public static String create(long timeout){
		CmdWatcher watcher=new CmdWatcher();
		if( WsqManager.createWsq(watcher,null,timeout)==false ){
			return null;
		}
		return watcher.getWsqName();
	}
	
	private WsqContext wsqContext;
	private String wsqName;
	private String chid;
	private Object result;
	
	private void send(){
		if(wsqContext==null){
			return;
		}
		if(chid!=null && result!=null){
			wsqContext.publish(result.toString());
			wsqContext.endQueue();
			chid=null;
			result=null;
			wsqContext=null;
		}
	}
	
	public synchronized void doResult(Object result){
		this.result=result;
		send();
	}
	
	public String getWsqName(){
		return wsqName;
	}
	
	@Override
	public void onStartQueue(String wsqName, WsqContext wsqContext) {
		this.wsqName=wsqName;
		this.wsqContext=wsqContext;
		this.chid=null;
	}

	/* timeout�o�߂��� */
	@Override
	public synchronized boolean onWatch() {
		if(wsqContext==null){
			return false;
		}
		wsqContext.endQueue();
		wsqContext=null;
		return false;
	}
	
	@Override
	public synchronized void onSubscribe(WsqFrom from) {
		this.chid=from.getChid();
		send();
	}
	
	@Override
	public void onEndQueue() {
	}

	@Override
	public void onMessage(WsqFrom from, Object message) {
	}

	@Override
	public void onUnsubscribe(WsqFrom from) {
	}
}