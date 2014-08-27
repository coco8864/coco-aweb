package naru.aweb.robot;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.WriteScheduler;
import naru.aweb.http.WebClientHandler;

public class CallScheduler extends PoolBase{
	private static Logger logger = Logger.getLogger(CallScheduler.class);
	
	private WebClientHandler handler;//一旦設定されたらrecycleされるまで変更されない
	//異常のシミュレート,時間は絶対時間で設定する
	//1)connectして送信開始するまでの時間 (0)
	//2)送信するrequestLine長 0-実requestLine長(実requestLine長)将来検討
	//3)requestLine送信後、header送信を開始するまでの時間(0)
	//4)送信するheader長 0-実header長(実header長)
	//5)header送信後、body送信を開始するまでの時間(0)
	//6)送信するbody長　0-実body長(実body長)
	//7)レスポンス待ち時間 0-レスポンス到着時間(readTimeout)
//	private long requestLineTime;
//	private long requestLineLength;
	private long sslProxyTime;
	private long sslProxyLength;
	private long headerTime;
	private long headerLength;
	private long bodyTime;
	private long bodyLength;
	private WriteScheduler sslProxyScheduler;
	private WriteScheduler headerScheduler;
	private WriteScheduler bodyScheduler;
	
	public static CallScheduler create(WebClientHandler handler){
		CallScheduler scheduler=(CallScheduler) PoolManager.getInstance(CallScheduler.class);
		scheduler.handler=handler;
		handler.setScheduler(scheduler);
		return scheduler;
	}
	
	public void recycle() {
		handler=null;
		if(sslProxyScheduler!=null){
			sslProxyScheduler.unref();
			sslProxyScheduler=null;
		}
		if(headerScheduler!=null){
			headerScheduler.unref();
			headerScheduler=null;
		}
		if(bodyScheduler!=null){
			bodyScheduler.unref();
			bodyScheduler=null;
		}
		sslProxyTime=headerTime=bodyTime=0;
		sslProxyLength=headerLength=bodyLength=0;
		super.recycle();
	}
	
	public void setSslProxySchedule(long sslProxyTime,long sslProxyLength){
		this.sslProxyTime=sslProxyTime;
		this.sslProxyLength=sslProxyLength;
	}
	public void setHeaderSchedule(long headerTime,long headerLength){
		this.headerTime=headerTime;
		this.headerLength=headerLength;
	}
	public void setBodySchedule(long bodyTime,long bodyLength){
		this.bodyTime=bodyTime;
		this.bodyLength=bodyLength;
	}
	
	public void cancel(){
		if(sslProxyScheduler!=null){
			sslProxyScheduler.cancel();
		}
		if(headerScheduler!=null){
			headerScheduler.cancel();
		}
		if(bodyScheduler!=null){
			bodyScheduler.cancel();
		}
	}
	
	public void scheduleWrite(String userContext,ByteBuffer[] buffer){
		if(userContext==WebClientHandler.CONTEXT_SSL_PROXY_CONNECT){
			if(sslProxyScheduler!=null){
				throw new UnsupportedOperationException("CallScheduler not support ssl proxy multi write ");
			}
			sslProxyScheduler=WriteScheduler.create(handler,userContext, buffer,sslProxyTime, sslProxyLength, null);
		}else if(userContext==WebClientHandler.CONTEXT_HEADER){
			if(headerScheduler!=null){
				throw new UnsupportedOperationException("CallScheduler not support header multi write ");
			}
			headerScheduler=WriteScheduler.create(handler,userContext, buffer,headerTime, headerLength, sslProxyScheduler);
		}else if(userContext==WebClientHandler.CONTEXT_BODY){
			if(bodyScheduler!=null){
				throw new UnsupportedOperationException("CallScheduler not support body multi write ");
			}
			bodyScheduler=WriteScheduler.create(handler,userContext, buffer,bodyTime, bodyLength, headerScheduler);
		}else{
			throw new IllegalArgumentException("unknown userContext."+userContext);
		}
	}
	
	public long getSslProxyActualWriteTime(){
		if(sslProxyScheduler==null){
			return -1;
		}
		return sslProxyScheduler.getActualWriteTime();
	}
	public long getHeaderActualWriteTime(){
		if(headerScheduler==null){
			return -1;
		}
		return headerScheduler.getActualWriteTime();
	}
	public long getBodyActualWriteTime(){
		if(bodyScheduler==null){
			return -1;
		}
		return bodyScheduler.getActualWriteTime();
	}
}
