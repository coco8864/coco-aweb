package naru.aweb.core;

import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.ChannelHandler;
import naru.async.core.IOManager;
import naru.aweb.config.Config;
import naru.queuelet.Queuelet;
import naru.queuelet.QueueletContext;

public class Main implements Queuelet {
	static private Logger logger=Logger.getLogger(Main.class);
	private static Config config=Config.getConfig();
	
	private static Main mainInstance;
	private boolean isTerminating=false;//èIóπèàóùíÜ

	private static QueueletContext context;
	public static void terminate(){
		synchronized(mainInstance){
			if(mainInstance.isTerminating){
				return;
			}
			mainInstance.isTerminating=true;
		}
		RealHost.unbindAll();
		IOManager.stop();
		config.term();
		try {
			Thread.sleep(1000);//TODO 1000msÇ¡ÇƒâΩÇë“Ç¡ÇƒÇ¢ÇÈÇÃÇ©?
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		context.finish();
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
		boolean isCleanup=false;
		String args[]=(String[])param.get("QueueletArgs");
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

}
