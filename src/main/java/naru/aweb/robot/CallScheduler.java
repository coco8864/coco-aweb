package naru.aweb.robot;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.WriteScheduler;
import naru.aweb.http.WebClientHandler;

public class CallScheduler extends PoolBase{
	private static Logger logger = Logger.getLogger(CallScheduler.class);
	
	private WebClientHandler handler;
	//異常のシミュレート,時間は絶対時間で設定する
	//1)connectして送信開始するまでの時間 (0)将来検討
	//2)送信するrequestLine長 0-実requestLine長(実requestLine長)将来検討
	//3)requestLine送信後、header送信を開始するまでの時間(0)
	//4)送信するheader長 0-実header長(実header長)
	//5)header送信後、body送信を開始するまでの時間(0)
	//6)送信するbody長　0-実body長(実body長)
	//7)レスポンス待ち時間 0-レスポンス到着時間(readTimeout)
//	private long requestLineTime;
//	private long requestLineLength;
	private long headerTime;
	private long headerLength;
	private long bodyTime;
	private long bodyLength;
	private WriteScheduler prevScheduler;
	
	public void setup(WebClientHandler handler,long headerTime,long headerLength,long bodyTime,long bodyLength){
		setHandler(handler);
		this.headerTime=headerTime;
		this.headerLength=headerLength;
		this.bodyTime=bodyTime;
		this.bodyLength=bodyLength;
	}

	private void setPrevScheduler(WriteScheduler prevScheduler){
		if(prevScheduler!=null){
			prevScheduler.ref();
		}
		if(this.prevScheduler!=null){
			this.prevScheduler.unref();
		}
		this.prevScheduler=prevScheduler;
	}
	
	private void setHandler(WebClientHandler handler){
		if(handler!=null){
			handler.ref();
		}
		if(this.handler!=null){
			this.handler.unref();
		}
		this.handler=handler;
	}
	
	public void recycle() {
		setHandler(null);
		setPrevScheduler(null);
		super.recycle();
	}
	
	public void scheduleWrite(String userContext,ByteBuffer[] buffer){
		long writeTime=0;
		long writeLength=0;
		if(userContext==WebClientHandler.CONTEXT_HEADER){
			writeTime=headerTime;
			writeLength=headerLength;
		}else if(userContext==WebClientHandler.CONTEXT_BODY){
			writeTime=bodyTime;
			writeLength=bodyLength;
		}else{
			throw new IllegalArgumentException("unknown userContext."+userContext);
		}
		WriteScheduler scheduler=(WriteScheduler) PoolManager.getInstance(WriteScheduler.class);
		scheduler.scheduleWrite(writeTime, handler, WebClientHandler.CONTEXT_HEADER, buffer, prevScheduler, writeLength);
		setPrevScheduler(scheduler);
	}
}
