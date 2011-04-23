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
	//�ُ�̃V�~�����[�g,���Ԃ͐�Ύ��ԂŐݒ肷��
	//1)connect���đ��M�J�n����܂ł̎��� (0)��������
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
