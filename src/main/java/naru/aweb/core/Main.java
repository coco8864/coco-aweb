package naru.aweb.core;

import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.core.IOManager;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.queuelet.Queuelet;
import naru.queuelet.QueueletContext;
import naru.queuelet.watch.StartupInfo;

public class Main implements Queuelet,Timer {
	static private Logger logger=Logger.getLogger(Main.class);
	private static Config config=Config.getConfig();
	private static StartupInfo startupInfo;//監視プロセスが、このコンテナを起動した時の情報
	
	private static Main mainInstance;
	private boolean isTerminating=false;//終了処理中

	private static QueueletContext context;
	public static void terminate(){
		terminate(false,false,-1);
	}
	
	public static void terminate(boolean isRestart,boolean isCleanup,int javaHeapSize){
		synchronized(mainInstance){
			if(mainInstance.isTerminating){
				return;
			}
			mainInstance.isTerminating=true;
		}
		//当該のリクエストおよび当該リクエストのログオフを走行させるため終了処理を遅延処理する
		StartupInfo responseStartupInfo=null;
		if(isRestart&&startupInfo!=null){
			responseStartupInfo=startupInfo;
			if(javaHeapSize>=0){
				responseStartupInfo.setJavaHeapSize(javaHeapSize);
			}
			if(isCleanup){
				String[] args={"cleanup"};
				responseStartupInfo.setArgs(args);
			}else{
				String[] args={};
				responseStartupInfo.setArgs(args);
			}
			responseStartupInfo.setJavaVmOptions(null);//javaVmOptionは変更させない
		}
		TimerManager.setTimeout(1000, mainInstance, responseStartupInfo);
	}
	
	
	/**
	 * datanucleus.properties
javax.jdo.PersistenceManagerFactoryClass=org.datanucleus.jdo.JDOPersistenceManagerFactory

javax.jdo.option.ConnectionDriverName=org.hsqldb.jdbcDriver
javax.jdo.option.ConnectionURL=jdbc:hsqldb:file:db/ph
javax.jdo.option.ConnectionUserName=sa
javax.jdo.option.ConnectionPassword=
javax.jdo.option.Mapping=hsql

datanucleus.metadata.validate=false
datanucleus.autoCreateSchema=true
datanucleus.validateTables=false
datanucleus.validateConstraints=false

	 * @return
	 */
	
	public void init(QueueletContext context, Map param) {
		startupInfo=(StartupInfo)param.get(PARAM_KEY_STARTUPINFO);
		if(startupInfo!=null){
			startupInfo.setJavaVmOptions(null);//javaVmOptionは変更させない
			config.setJavaHeapSize(startupInfo.getJavaHeapSize());
			config.setRestartCount(startupInfo.getRestartCount());
		}else{
			config.setJavaHeapSize(-1);
			config.setRestartCount(-1);
		}
	
		boolean isCleanup=false;
		String args[]=(String[])param.get(PARAM_KEY_ARGS);
		for(String arg:args){
			if("cleanup".equalsIgnoreCase(arg)){
				System.out.println("Main recive cleanup");
				isCleanup=true;
			}
		}
		try{
			if( !config.init(context,isCleanup) ){
				logger.error("fail to config init.");
				context.finish();
				return;
			}
		}catch(Throwable t){
			t.printStackTrace();
			logger.error("fail to config init.",t);
			context.finish();
			return;
		}
		mainInstance=this;
		Main.context=context;
		RealHost.bindAll(true);
	}

	public boolean service(Object arg0) {
		return false;
	}

	public void term() {
		RealHost.unbindAll();
		config.term();
		System.gc();
	}
	
	//終了処理を遅延処理する
	public void onTimer(Object userContext) {
		StartupInfo responseStartupInfo=(StartupInfo)userContext;
		RealHost.unbindAll();
		IOManager.stop();
		config.term();
		if(responseStartupInfo!=null){//再起動時
			context.finish(false,true,responseStartupInfo);
		}else{//終了時
			context.finish();
		}
	}
}
