package naru.aweb.wadm;

import org.apache.log4j.Logger;

import naru.async.ChannelStastics;
import naru.async.core.ChannelContext;
import naru.async.core.IOManager;
import naru.async.core.SelectorStastics;
import naru.async.pool.Pool;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.StoreManager;
import naru.async.store.StoreStastics;
import naru.aweb.auth.AuthSession;
import naru.aweb.http.RequestContext;
import naru.aweb.wsq.BlobMessage;
import naru.aweb.wsq.WsqCtx;
import naru.aweb.wsq.WsqPeer;
import naru.aweb.wsq.Wsqlet;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

public class StasticsWsqlet implements Wsqlet {
	private static Logger logger=Logger.getLogger(StasticsWsqlet.class);
	private WsqCtx ctx;
	private String qname;
	
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
	public StasticsWsqlet(){
		storeStastics=StoreManager.getStoreStastics();
		channelStastics=ChannelContext.getTotalChannelStastics();
		channelContextPool=getPool(ChannelContext.class);
		authSessionPool=getPool(AuthSession.class);
		requestContextPool=getPool(RequestContext.class);
		selectorStasticses=IOManager.getSelectorStasticses();
		int stCount=storeStastics.getBufferFileCount();
		storeStack=new long[stCount];
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
	

	@Override
	public void onEndQueue() {
	}

	@Override
	public void onPublishBlob(WsqPeer from, BlobMessage message) {
	}

	@Override
	public void onPublishObj(WsqPeer from, JSON message) {
	}

	@Override
	public void onPublishText(WsqPeer from, String message) {
	}

	@Override
	public void onStartQueue(String wsqName, WsqCtx ctx) {
		this.qname=qname;
		this.ctx=ctx;
	}

	@Override
	public void onSubscribe(WsqPeer from) {
	}

	@Override
	public void onUnsubscribe(WsqPeer from) {
	}

	@Override
	public long onWatch() {
		if(!ctx.getSubscribePeers().isEmpty()){
			ctx.message(this);
		}
		return 1000;
	}

	@Override
	public boolean useBlob() {
		return false;
	}
}
