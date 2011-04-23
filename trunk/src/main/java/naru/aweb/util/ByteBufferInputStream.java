package naru.aweb.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import naru.async.pool.PoolManager;

public class ByteBufferInputStream extends InputStream {
	private List<ByteBuffer> buffersList=new LinkedList<ByteBuffer>();
	
	public ByteBufferInputStream(ByteBuffer[] buffers){
		for(int i=0;i<buffers.length;i++){
			buffersList.add(buffers[i]);
		}
	}
	
	@Override
	public void close() throws IOException {
		while(true){
			if(buffersList.size()==0){
				return;
			}
			PoolManager.poolBufferInstance(buffersList.remove(0));
		}
	}

	private ByteBuffer getBuffer(){
		while(true){
			if(buffersList.size()==0){
				return null;
			}
			ByteBuffer buffer=buffersList.get(0);
			if(buffer.hasRemaining()){
				return buffer;
			}
			buffersList.remove(0);
			PoolManager.poolBufferInstance(buffer);
		}
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		ByteBuffer buffer=getBuffer();
		int remaining=buffer.remaining();
		if(remaining<len){
			len=remaining;
		}
		buffer.get(b,off,len);
		if(!buffer.hasRemaining()){
			PoolManager.poolBufferInstance(buffer);
			buffersList.remove(0);
		}
		return len;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b,0,b.length);
	}

	public int read() throws IOException {
		byte[] b=new byte[1];
		int len=read(b);
		if(len==0){
			return -1;
		}
		return (int)b[0];
	}
}
