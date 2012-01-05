package naru.aweb.core;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.core.IOManager;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.queuelet.Queuelet;
import naru.queuelet.QueueletContext;
import naru.queuelet.watch.StartupInfo;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

public class Main implements Queuelet,Timer {
	static private Logger logger=Logger.getLogger(Main.class);
	private static Config config=Config.getConfig();
	private static StartupInfo startupInfo;//�Ď��v���Z�X���A���̃R���e�i���N���������̏��
	
	private static Main mainInstance;
	private boolean isTerminating=false;//�I��������

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
		//���Y�̃��N�G�X�g����ѓ��Y���N�G�X�g�̃��O�I�t�𑖍s�����邽�ߏI��������x����������
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
			responseStartupInfo.setJavaVmOptions(null);//javaVmOption�͕ύX�����Ȃ�
		}
		TimerManager.setTimeout(1000, mainInstance, responseStartupInfo);
	}
	
	private class PoolInfo{
		String type;//class,buffer,array
		String className;
		int bufferSize;
		int arraySize;
		int limit_100;//100�[���������limit
	}
	
	private List<PoolInfo> poolInfos=new ArrayList<PoolInfo>();
	private int bfferlimit_100;
	
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

	 *�@init���̎��s�͍ċN�������݂�
	 * @return
	 */
	public void init(QueueletContext context, Map param) {
		startupInfo=(StartupInfo)param.get(PARAM_KEY_STARTUPINFO);
		if(startupInfo!=null){
			startupInfo.setJavaVmOptions(null);//javaVmOption�͕ύX�����Ȃ�
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
				logger.error("config init return false.");
				context.finish(false,true,startupInfo);
				return;
			}
		}catch(Throwable t){
			logger.error("fail to config init.",t);
			context.finish(false,true,startupInfo);
			return;
		}
		//pool�̏�����
		
		
		
		PoolManager.createArrayPool(ByteBuffer.class, 1, 5120);
		SSLEngine sslEngine=config.getSslEngine(null);
		PoolManager.createBufferPool(sslEngine.getSession().getPacketBufferSize(),1024);
		
		mainInstance=this;
		Main.context=context;
		if( !RealHost.bindAll(true) ){
			/* ����port���g���Ă����ꍇ������ʂ� */
			logger.error("fail to bindAll.");
			context.finish(false,true,startupInfo);
		}
	}
	
	/**
	 * @param maxClients �z��[����
	 */
	public void setupPool(int maxClients,boolean isSsl){
		if( maxClients<=0 ){
			maxClients=100;
		}
		int client_100=((maxClients-1)/100)+1;
		for(PoolInfo poolInfo:poolInfos){
			if("class".equals(poolInfo.type)){
				PoolManager.setupClassPool(poolInfo.className, poolInfo.limit_100*client_100);
			}else if("array".equals(poolInfo.type)){
				PoolManager.setupArrayPool(poolInfo.className,poolInfo.arraySize,poolInfo.limit_100*client_100);
			}else if("buffer".equals(poolInfo.type)){
				PoolManager.setupBufferPool(poolInfo.bufferSize, poolInfo.limit_100*client_100);
			}
		}
		if(isSsl){
			SSLEngine sslEngine=config.getSslEngine(null);
			PoolManager.setupBufferPool(sslEngine.getSession().getPacketBufferSize(), bfferlimit_100*client_100);
		}else{
			int defaultBufferSize=PoolManager.getDefaultBufferSize();
			PoolManager.setupBufferPool(defaultBufferSize, bfferlimit_100*client_100);
		}
	}
	
	public boolean service(Object arg0) {
		return false;
	}

	public void term() {
		RealHost.unbindAll();
		config.term();
		System.gc();
	}
	
	//�I��������x����������
	public void onTimer(Object userContext) {
		StartupInfo responseStartupInfo=(StartupInfo)userContext;
		RealHost.unbindAll();
		IOManager.stop();
		config.term();
		if(responseStartupInfo!=null){//�ċN����
			context.finish(false,true,responseStartupInfo);
		}else{//�I����
			context.finish();
		}
	}
}
