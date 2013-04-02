package naru.aweb.config;

import org.apache.log4j.Logger;

import naru.async.ChannelStastics;
import naru.async.Timer;
import naru.async.core.ChannelContext;
import naru.async.core.IOManager;
import naru.async.core.SelectorStastics;
import naru.async.pool.Pool;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.StoreManager;
import naru.async.store.StoreStastics;
import naru.async.timer.TimerManager;
import naru.aweb.admin.PaAdmin;
import naru.aweb.auth.AuthSession;
import naru.aweb.http.RequestContext;
import naru.aweb.pa.PaManager;
import net.sf.json.JSONObject;

public class Broadcaster implements Timer {
	private static Logger logger=Logger.getLogger(Broadcaster.class);	
	private static final long BROADCAST_INTERVAL=1000;
	private static final long LOG_WATCH_INTERVAL=300000;
	private PaManager paManager=PaManager.getInstance("/pa");
	private long timerId=TimerManager.INVALID_ID;
	private Config config;
	
	Broadcaster(Config config){
		this.config=config;
		long interval=config.getLong("broardcastInterval", BROADCAST_INTERVAL);
		Stastics stastics=new Stastics();
		config.setStasticsObject(stastics);
		timerId=TimerManager.setTimeout(interval, this, stastics);
	}
	
	public void term(){
		TimerManager.clearTimeout(timerId);
	}
	
	private Pool getPool(Class clazz){
		Pool pool=PoolManager.getClassPool(clazz);
		if(pool!=null){
			return pool;
		}
		PoolBase dummy=(PoolBase)PoolManager.getInstance(clazz);
		dummy.unref(true);
		return PoolManager.getClassPool(clazz);
	}
	
	public class Stastics{
		private Pool channelContextPool;
		private Pool authSessionPool;
		private Pool requestContextPool;
		
		/* json化する変数郡 */
		long counter=0;
		long time=0;
		JSONObject memory=new JSONObject();
		JSONObject channelContext=new JSONObject();
		JSONObject authSession=new JSONObject();
		JSONObject requestContext=new JSONObject();
		long[] storeStack;
		JSONObject[] selectorStasticss;
		ChannelStastics channelStastics;
		StoreStastics storeStastics;
		SelectorStastics[] selectorStasticses;
		Stastics(){
			storeStastics=StoreManager.getStoreStastics();
			channelStastics=ChannelContext.getTotalChannelStastics();
			channelContextPool=getPool(ChannelContext.class);
			authSessionPool=getPool(AuthSession.class);
			requestContextPool=getPool(RequestContext.class);
			selectorStasticses=IOManager.getSelectorStasticses();
			int stCount=storeStastics.getBufferFileCount();
			storeStack=new long[stCount];
		}
		
		private void updatePool(JSONObject jsonobj,Pool pool){
			jsonobj.element("total", pool.getSequence());
			jsonobj.element("instance", pool.getInstanceCount());
			jsonobj.element("poolBack",pool.getPoolBackCount());
			jsonobj.element("pool", pool.getPoolCount());
			jsonobj.element("gc", pool.getGcCount());
		}
		
		void update(){
			counter++;
			time=System.currentTimeMillis();
			Runtime runtime=Runtime.getRuntime();
			memory.element("free", runtime.freeMemory());
			memory.element("max", runtime.maxMemory());
			updatePool(channelContext,channelContextPool);
			updatePool(authSession,authSessionPool);
			updatePool(requestContext,requestContextPool);
			for(int fileId=0;fileId<storeStack.length;fileId++){
				storeStack[fileId]=storeStastics.getBufferFileSize(fileId);
			}
		}

		public long getCounter() {
			return counter;
		}
		
		public long getTime() {
			return time;
		}

		public JSONObject getMemory() {
			return memory;
		}

		public JSONObject getChannelContext() {
			return channelContext;
		}

		public JSONObject getAuthSession() {
			return authSession;
		}

		public long[] getStoreStack() {
			return storeStack;
		}

		public ChannelStastics getChannelStastics() {
			return channelStastics;
		}

		public StoreStastics getStoreStastics() {
			return storeStastics;
		}

		public JSONObject getRequestContext() {
			return requestContext;
		}
		public SelectorStastics[] getSelectorStasticses() {
			return selectorStasticses;
		}
		
		/* statis.vsfにjsonオブジェクトに展開するのに使用 */
		@Override
		public String toString() {
			return JSONObject.fromObject(this).toString();
		}
	}
	
	private long nextLogOutput=0;
	private void logWatch(Stastics stastics){
		long now=System.currentTimeMillis();
		if(now<nextLogOutput){
			return;
		}
		long interval=config.getLong("logWatchInterval", LOG_WATCH_INTERVAL);
		nextLogOutput=now+interval;
		logger.info(JSONObject.fromObject(stastics).toString());
	}

	public void onTimer(Object userContext) {
		timerId=TimerManager.INVALID_ID;
		Stastics stastics=(Stastics)userContext;
		stastics.update();
		logWatch(stastics);
		paManager.publish(PaAdmin.QNAME,PaAdmin.SUBNAME_STASTICS, JSONObject.fromObject(stastics));
		long interval=config.getLong("broardcastInterval", BROADCAST_INTERVAL);
		timerId=TimerManager.setTimeout(interval, this, stastics);
	}
	
}
