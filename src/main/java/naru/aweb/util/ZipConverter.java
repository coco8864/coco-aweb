package naru.aweb.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import naru.async.BufferGetter;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.Page;

public class ZipConverter extends PoolBase{
	private ZipOutputStream zipOutputStream;
	private InnerOutputStream innerOutputStream=new InnerOutputStream();
	private BufferGetter getter;
	private ZipEntry zipEntry;
	
	public static ZipConverter create(BufferGetter getter){
		ZipConverter converter=(ZipConverter)PoolManager.getInstance(ZipConverter.class);
		converter.init(getter);
		return converter;
	}
	
	public void init(BufferGetter getter){
		this.zipOutputStream=new ZipOutputStream(innerOutputStream);
		this.getter=getter;
		this.zipEntry=null;
		this.innerOutputStream.recycle();
	}
	
	private void writeBuffer(ByteBuffer buffer) throws IOException{
		int remaining=buffer.remaining();
		int pos=buffer.position();
		byte[] array=buffer.array();
		zipOutputStream.write(array,pos,remaining);
		PoolManager.poolBufferInstance(buffer);
	}
	
	public void putEntry(String name){
		try {
			if(zipEntry!=null){
				zipOutputStream.closeEntry();
			}
			zipEntry=new ZipEntry(name);
			zipOutputStream.putNextEntry(zipEntry);
		} catch (IOException e) {
			getter.onBufferFailure(e, "ZipConveter putEntry error");
		}
	}
	
	public void put(ByteBuffer buffer){
		try {
			writeBuffer(buffer);
			zipOutputStream.flush();
			callback();
		} catch (IOException e) {
			getter.onBufferFailure(e, "ZipConveter put error");
		}
	}
	
	public void put(ByteBuffer[] buffers){
		try {
			for(ByteBuffer buffer:buffers){
				writeBuffer(buffer);
			}
			PoolManager.poolArrayInstance(buffers);
			zipOutputStream.flush();
			callback();
		} catch (IOException e) {
			getter.onBufferFailure(e, "ZipConveter puts error");
		}
	}
	
	public void end(){
		try {
			zipOutputStream.close();
			callback();
			getter.onBufferEnd(null);
		} catch (IOException e) {
			getter.onBufferFailure(e, "ZipConveter end error");
		}
	}
	
	private synchronized void callback() throws IOException{
		ByteBuffer[] buffers=innerOutputStream.getBuffer();
		if(buffers!=null){
			getter.onBuffer(buffers, zipEntry);
		}
	}
	
	private class InnerOutputStream extends OutputStream{
		private Page page=new Page();
		public void recycle(){
			page.recycle();
		}
		
		public ByteBuffer[] getBuffer(){
			return page.getBuffer();
		}
		
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
}
