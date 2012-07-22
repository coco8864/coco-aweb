package naru.aweb.http;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.Page;

public class GzipContext extends PoolBase{
	private static Logger logger = Logger.getLogger(GzipContext.class);
	private GZIPOutputStream gzipOutputStream;
	private GZIPInputStream gzipInputStream;
	private ZipedOutputStream zipedOutputStream=new ZipedOutputStream();
	private ZipedInputStream zipedInputStream=new ZipedInputStream();
	//ファイルを使わないStore
	private Page page=new Page();
	private long inputLength;
	private long outputLength;
	
	public void recycle() {
		if(gzipOutputStream!=null){
			try {
				gzipOutputStream.close();
			} catch (IOException ignore) {
			}
			gzipOutputStream=null;
		}
		if(gzipInputStream!=null){
			try {
				logger.debug("##close gzipInputStream.gzipInputStream:"+gzipInputStream);
				gzipInputStream.close();
			} catch (IOException ignore) {
			}
			gzipInputStream=null;
		}
		page.recycle();
		zipedInputStream.buffers.clear();
		inputLength=outputLength=0;
		super.recycle();
	}
	
	public void putPlainBuffer(ByteBuffer[] buffers){
		for(int i=0;i<buffers.length;i++){
			putPlainBuffer(buffers[i]);
		}
	}
	
	public void putPlainBuffer(ByteBuffer buffer){
		try {
			inputLength+=buffer.remaining();
			if(gzipOutputStream==null){
				gzipOutputStream=new GZIPOutputStream(zipedOutputStream);
			}
			synchronized(gzipOutputStream){//closeとバッティングすると無限ループとなる
				byte[] writeBytes=new byte[buffer.remaining()];
				buffer.get(writeBytes);
				gzipOutputStream.write(writeBytes);
			}
			PoolManager.poolBufferInstance(buffer);
		} catch (IOException e) {
			throw new IllegalStateException("fail to gzipOutputStream.write",e);
		}
	}
	
	public ByteBuffer[] getZipedBuffer(boolean isLast){
		if(gzipOutputStream==null){
			return null;
		}
		try {
			synchronized(gzipOutputStream){//writeとバッティングすると無限ループとなる
				if(isLast){
					gzipOutputStream.close();
				}else{
					gzipOutputStream.finish();
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("fail to gzipOutputStream.getZipedBuffer",e);
		}
		ByteBuffer[] buffers=page.getBuffer();
		if(buffers!=null){
			outputLength+=BuffersUtil.remaining(buffers);
		}
		return buffers;
	}
	
	public ByteBuffer[] getZipedBuffer(boolean isLast,ByteBuffer[] src){
		try {
			if(gzipOutputStream==null){
				gzipOutputStream=new GZIPOutputStream(zipedOutputStream);
			}
			synchronized(gzipOutputStream){//writeとバッティングすると無限ループとなる
				putPlainBuffer(src);
				if(isLast){
					gzipOutputStream.close();
				}else{
					gzipOutputStream.flush();
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("fail to gzipOutputStream.getZipedBuffer",e);
		}
		ByteBuffer[] buffers=page.getBuffer();
		if(buffers!=null){
			outputLength+=BuffersUtil.remaining(buffers);
		}
		return buffers;
	}
	
	public void putZipedBuffer(ByteBuffer[] buffers){
		inputLength+=BuffersUtil.remaining(buffers);
		if(buffers==null){
			return;
		}
		page.putBuffer(buffers,false);
	}
	
	public void putZipedBuffer(ByteBuffer buffer){
		inputLength+=buffer.remaining();
		page.putBuffer(buffer,false);
	}
	
	private boolean fillBuffer(ByteBuffer buffer) throws IOException{
		while(buffer.hasRemaining()){
			//store側にbufferがあるのを確認してからread
			//if(zipedInputStream.isBuffer()==false){
			//	buffer.flip();
			//	return false;//バッファ不足
			//}
			synchronized(gzipInputStream){
				int pos=buffer.position();
				int len;
				try {
					len = gzipInputStream.read(buffer.array(),pos,buffer.remaining());
				} catch (EOFException e) {//bufferが途中で切れてしまった。怪しい方法だがうまく動いた
					len=0;
				}
				if(len<0){
					if(zipedInputStream.isBuffer()==false){
						len=0;
					}else{
						logger.debug("##error gzipInputStream.gzipInputStream:"+gzipInputStream);
						throw new IOException("zipedInputStream.isBuffer():"+zipedInputStream.isBuffer());
					}
				}
				buffer.position(pos+len);
				if(len==0&&zipedInputStream.isBuffer()==false){
					buffer.flip();
					return false;//バッファ不足
				}
			}
		}
		buffer.flip();
		return true;//
	}
	
	public ByteBuffer[] getPlainBuffer(){
		ArrayList<ByteBuffer> bufs=new ArrayList<ByteBuffer>();
		try {
			ByteBuffer[] ziped=page.getBuffer();
//			BuffersUtil.peekBuffer(ziped);
			if(ziped==null){
				return null;
			}
			zipedInputStream.putBuffers(ziped);
			if(gzipInputStream==null){
//				logger.debug("##new 1gzipInputStream.ziped[0]:"+ziped[0]);
				gzipInputStream=new GZIPInputStream(zipedInputStream);
//				logger.debug("##new 2gzipInputStream.ziped[0]:"+ziped[0]);
//				logger.debug("##new 3gzipInputStream.gzipInputStream:"+gzipInputStream);
			}
			while(true){
				ByteBuffer buffer=PoolManager.getBufferInstance();
				if( fillBuffer(buffer)==false ){
					if(buffer.hasRemaining()){
						bufs.add(buffer);
					}else{
						PoolManager.poolBufferInstance(buffer);
					}
					break;
				}
				bufs.add(buffer);
			}
		} catch (IOException e) {
			throw new IllegalStateException("fail to gzipOutputStream.write",e);
		}
		if(bufs.size()==0){
			return null;
		}
		ByteBuffer[] buffers=BuffersUtil.newByteBufferArray(bufs.size());
		buffers=bufs.toArray(buffers);
		outputLength+=BuffersUtil.remaining(buffers);
		return buffers;
	}
	
	private class ZipedOutputStream extends OutputStream{
		public void close() throws IOException {
		}
		public void flush() throws IOException {
		}
		public void write(byte[] src, int offset, int length) throws IOException {
			page.putBytes(src, offset, length);
		}
		public void write(byte[] src) throws IOException {
			write(src,0,src.length);
		}
		public void write(int src) throws IOException {
			write(new byte[]{(byte)src},0,1);
		}
	}
	
	private class ZipedInputStream extends InputStream{
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
//				for(int i=0;i<length;i++){
//					logger.debug("###"+(int)b[off+i]);
//				}
				return length;
			}
		}
		@Override
		public int available() throws IOException {
			// TODO Auto-generated method stub
			return super.available();
		}
	}

	public long getInputLength() {
		return inputLength;
	}

	public long getOutputLength() {
		return outputLength;
	}
	
}
