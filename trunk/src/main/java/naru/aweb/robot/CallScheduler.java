package naru.aweb.robot;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.timer.WriteScheduler;
import naru.aweb.http.WebClientHandler;

public class CallScheduler extends PoolBase{
	private static Logger logger = Logger.getLogger(CallScheduler.class);
	
	private WebClientHandler handler;//��U�ݒ肳�ꂽ��recycle�����܂ŕύX����Ȃ�
	//�ُ�̃V�~�����[�g,���Ԃ͐�Ύ��ԂŐݒ肷��
	//1)connect���đ��M�J�n����܂ł̎��� (0)
	//2)���M����requestLine�� 0-��requestLine��(��requestLine��)��������
	//3)requestLine���M��Aheader���M���J�n����܂ł̎���(0)
	//4)���M����header�� 0-��header��(��header��)
	//5)header���M��Abody���M���J�n����܂ł̎���(0)
	//6)���M����body���@0-��body��(��body��)
	//7)���X�|���X�҂����� 0-���X�|���X��������(readTimeout)
//	private long requestLineTime;
//	private long requestLineLength;
	private long headerTime;
	private long headerLength;
	private long bodyTime;
	private long bodyLength;
	private WriteScheduler headerScheduler;
	private WriteScheduler bodyScheduler;
	
	public static CallScheduler create(WebClientHandler handler,long headerTime,long headerLength,long bodyTime,long bodyLength){
		CallScheduler scheduler=(CallScheduler) PoolManager.getInstance(CallScheduler.class);
		scheduler.setup(handler, headerTime, headerLength, bodyTime, bodyLength);
		return scheduler;
	}
	
	private void setup(WebClientHandler handler,long headerTime,long headerLength,long bodyTime,long bodyLength){
		handler.setScheduler(this);
		this.handler=handler;
		this.headerTime=headerTime;
		this.headerLength=headerLength;
		this.bodyTime=bodyTime;
		this.bodyLength=bodyLength;
	}
	
	public void recycle() {
		handler=null;
		if(headerScheduler!=null){
			headerScheduler.unref();
			headerScheduler=null;
		}
		if(bodyScheduler!=null){
			bodyScheduler.unref();
			bodyScheduler=null;
		}
		super.recycle();
	}
	
	public void cancel(){
		if(headerScheduler!=null){
			headerScheduler.cancel();
		}
		if(bodyScheduler!=null){
			bodyScheduler.cancel();
		}
	}
	
	public void scheduleWrite(String userContext,ByteBuffer[] buffer){
		if(userContext==WebClientHandler.CONTEXT_HEADER){
			if(headerScheduler!=null){
				throw new UnsupportedOperationException("CallScheduler not support header multi write ");
			}
			headerScheduler=WriteScheduler.create(handler,userContext, buffer,headerTime, headerLength, null);
		}else if(userContext==WebClientHandler.CONTEXT_BODY){
			if(bodyScheduler!=null){
				throw new UnsupportedOperationException("CallScheduler not support body multi write ");
			}
			bodyScheduler=WriteScheduler.create(handler,userContext, buffer,bodyTime, bodyLength, headerScheduler);
		}else{
			throw new IllegalArgumentException("unknown userContext."+userContext);
		}
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
