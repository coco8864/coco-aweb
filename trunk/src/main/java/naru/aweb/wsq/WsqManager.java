package naru.aweb.wsq;

public class WsqManager {
	
	/* command */
	public static boolean createWsq(Object wsqWatcher){
		return createWsq(wsqWatcher,null);
	}
	
	/* chat */
	public static boolean createWsq(Object wsqWatcher,String wsqName){
		return createWsq(wsqWatcher,wsqName,0);
	}
	
	/* stress,connection,����I�ɏ��𔭐M */
	public static boolean createWsq(Object wsqWatcher,String wsqName,long interval){
		return false;
	}
	
}
