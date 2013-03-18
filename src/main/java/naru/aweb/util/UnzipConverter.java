package naru.aweb.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import naru.async.BufferGetter;
import naru.async.pool.PoolManager;
import naru.async.store.Page;

public class UnzipConverter {
	private static UnderFlowException UNDERFLOW=new UnderFlowException();
	private static class UnderFlowException extends IOException{
	}
	
	private ZipInputStream zipInputStream;
	private InnerInputStream innerInputStream=new InnerInputStream();
	private BufferGetter getter;
	private Page page=new Page();
	private ZipEntry zipEntry;
	
	public void init(BufferGetter getter){
		innerInputStream.recycle();
		this.zipInputStream=new ZipInputStream(innerInputStream);
		this.getter=getter;
		this.zipEntry=null;
		this.page.recycle();
	}
	
	public void put(ByteBuffer buffer){
		innerInputStream.putBuffer(buffer);
		callback();
	}
	
	public void put(ByteBuffer[] buffers){
		innerInputStream.putBuffer(buffers);
		callback();
	}
	
	public void end(){
		callback();
		getter.onBufferEnd(null);
	}
	
	private void callback(){
		byte[] buf=null;
		try {
			while(innerInputStream.isBuffer()){
				if(zipEntry==null){
					zipEntry=zipInputStream.getNextEntry();
					continue;
				}
				int len=zipInputStream.read(buf);
				if(len>0){
					page.putBytes(buf, 0, len);
				}else if(len==0){
					page=null;
				}
			}
		} catch (IOException e) {
			if(e!=UNDERFLOW){
				getter.onBufferFailure(zipEntry, e);
			}
		}
		ByteBuffer[] buffers=page.getBuffer();
		if(buffers!=null){
			getter.onBuffer(zipEntry, buffers);
		}
	}
	
	private class InnerInputStream extends InputStream{
		private LinkedList<ByteBuffer> buffers=new LinkedList<ByteBuffer>();
		public void putBuffer(ByteBuffer buffer){
			this.buffers.add(buffer);
		}
		
		public void recycle(){
			for(ByteBuffer buffer:buffers){
				PoolManager.poolBufferInstance(buffer);
			}
			buffers.clear();
		}
		public void putBuffer(ByteBuffer[] buffers){
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
					throw UNDERFLOW;
				}
				ByteBuffer buffer=buffers.getFirst();
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
	}
}
