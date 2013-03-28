package naru.aweb.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.Page;

public class UnzipConverter extends PoolBase implements BufferGetter{
	private static IOException UNDERFLOW=new IOException("underFlow");
	
	private ZipInputStream zipInputStream;
	private InnerInputStream innerInputStream=new InnerInputStream();
	private BufferGetter getter;
	private Page page=new Page();
	private ZipEntry zipEntry;
	
	public static UnzipConverter create(BufferGetter getter){
		UnzipConverter converter=(UnzipConverter)PoolManager.getInstance(UnzipConverter.class);
		converter.init(getter);
		return converter;
	}
	
	public void init(BufferGetter getter){
		this.innerInputStream.recycle();
		this.zipInputStream=new ZipInputStream(innerInputStream);
		this.getter=getter;
		this.zipEntry=null;
		this.page.recycle();
		this.asyncBuffer=null;
	}
	
	private AsyncBuffer asyncBuffer;
	private long offset;
	
	@Override
	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		offset+=BuffersUtil.remaining(buffers);
		put(buffers);
		asyncBuffer.asyncBuffer(this, offset, null);
		return false;
	}

	@Override
	public void onBufferEnd(Object userContext) {
		end();
		if(asyncBuffer instanceof PoolBase){
			((PoolBase)asyncBuffer).unref();
		}
		asyncBuffer=null;
		unref();
	}

	@Override
	public void onBufferFailure(Object userContext, Throwable failure) {
		getter.onBufferFailure(userContext, failure);
		if(asyncBuffer instanceof PoolBase){
			((PoolBase)asyncBuffer).unref();
		}
		asyncBuffer=null;
		unref();
	}
	
	public synchronized void parse(AsyncBuffer asyncBuffer){
		if(this.asyncBuffer!=null){
			throw new RuntimeException("UnzipConverter parse");
		}
		this.asyncBuffer=asyncBuffer;
		this.offset=0;
		asyncBuffer.asyncBuffer(this, offset, null);
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
	
	private byte[] buf=new byte[1024];
	private synchronized void callback(){
		try {
			while(innerInputStream.isBuffer()){
				if(zipEntry==null){
					zipEntry=zipInputStream.getNextEntry();
					continue;
				}
				int len=zipInputStream.read(buf);
				if(len>0){
					page.putBytes(buf, 0, len);
				}else if(len<=0){
					ByteBuffer[] buffers=page.getBuffer();
					if(buffers!=null){
						getter.onBuffer(zipEntry, buffers);
					}
					zipEntry=zipInputStream.getNextEntry();
				}
			}
		} catch (IOException e) {
			if(e!=UNDERFLOW){
				getter.onBufferFailure(zipEntry, e);
				return;
			}
			ByteBuffer[] buffers=page.getBuffer();
			if(buffers!=null){
				getter.onBuffer(zipEntry, buffers);
			}
		}
	}
	
	private class InnerInputStream extends InputStream{
		private LinkedList<ByteBuffer> buffers=new LinkedList<ByteBuffer>();
		public void recycle(){
			for(ByteBuffer buffer:buffers){
				PoolManager.poolBufferInstance(buffer);
			}
			buffers.clear();
		}
		
		public void putBuffer(ByteBuffer buffer){
			this.buffers.add(buffer);
		}
		
		public void putBuffer(ByteBuffer[] buffers){
			for(ByteBuffer buffer:buffers){
				this.buffers.add(buffer);
			}
			PoolManager.poolArrayInstance(buffers);
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
