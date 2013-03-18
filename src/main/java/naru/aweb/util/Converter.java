package naru.aweb.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

public class Converter extends PoolBase implements BufferGetter{
	private boolean inAsyncBuffer=false;
	private BufferGetter getter;//イベント通知先
	private List bufferQueue=new ArrayList();
	private boolean isError=false;
	
	private InputStream outerInputStream;
	private InnerInputStream innerInputStream=new InnerInputStream();

	private class InnerInputStream extends InputStream{
		private ArrayList<ByteBuffer> buffers=new ArrayList<ByteBuffer>();
		public void putBuffers(ByteBuffer[] buffers){
			for(int i=0;i<buffers.length;i++)
				this.buffers.add(buffers[i]);
		}
		public boolean isBuffer(){
			return (buffers.size()!=0);
		}
		
		public void close() throws IOException {
		}
		public void flush() throws IOException {
		}
		public int read() throws IOException {
			byte[] b=new byte[1];
			if( read(b)<=0 ){
				return -1;
			}
			return (int)(b[0]&0xff);
		}
		public int read(byte[] b) throws IOException {
			return read(b,0,b.length);
		}
		public int read(byte[] b, int off, int len) throws IOException {
			synchronized(buffers){
				if(buffers.size()==0){
					return -1;
				}
				ByteBuffer buffer=buffers.get(0);
				int remaining=buffer.remaining();
				int length=remaining;
				if(len<length){
					length=len;
				}
				int pos=buffer.position();
				System.arraycopy(buffer.array(), pos, b, off, length);
				buffer.position(pos+length);
				if(buffer.remaining()==0){
					PoolManager.poolBufferInstance(buffer);
					buffers.remove(0);
					buffer=null;
				}
				return length;
			}
	}
	
	private void callback(){
		byte[] buf;
		outerInputStream.read(buf,0,buf.length);
		
		
	}
	
	
	
	
	public synchronized void putBuffer(ByteBuffer[] buffers){
		for(ByteBuffer buffer:buffers){
			putBuffer(buffer);
		}
		PoolManager.poolArrayInstance(buffers);
	}
	
	public synchronized void putBuffer(ByteBuffer buffer){
		if(inAsyncBuffer){
			bufferQueue.add(buffer);
			return;
		}
		//TODO *1
	}
	
	public synchronized void putBuffer(AsyncBuffer asyncBuffer){
		if(inAsyncBuffer){
			bufferQueue.add(asyncBuffer);
			return;
		}
		inAsyncBuffer=true;
		asyncBuffer.asyncBuffer((BufferGetter) this,0);
	}
	
	public synchronized void end(){
		if(inAsyncBuffer){
			bufferQueue.add(this);
			return;
		}
		//TODO
	}
	
	public boolean isError(){
		return isError;
	}
	
	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		putBuffer(buffers);
		return true;
	}
	
	public synchronized void onBufferEnd(Object userContext) {
		inAsyncBuffer=false;
		if(bufferQueue.size()==0){
			//TODO *2
		}
		Object buf=bufferQueue.remove(0);
		if(buf==this){
			//TODO end
		}
		if(buf instanceof ByteBuffer){
			putBuffer((ByteBuffer)buf);
		}else if(buf instanceof AsyncBuffer){
			putBuffer((AsyncBuffer)buf);
		}
	}
	
	public synchronized void onBufferFailure(Object userContext, Throwable failure) {
		inAsyncBuffer=false;
		isError=true;
	}
}
