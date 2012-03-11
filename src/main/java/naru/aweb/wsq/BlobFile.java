package naru.aweb.wsq;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.http.GzipContext;

public class BlobFile {
	private static Config config=Config.getConfig();
	private File file;
	/* ì‡óeÇï€éùÇ∑ÇÈcahceÅ@*/
	private ByteBuffer[] cache;
	private List<Blob> refBlob=new ArrayList<Blob>();/* Ç±ÇÃBlobFileÇéQè∆ÇµÇƒÇ¢ÇÈBlob */
	
	public static BlobFile create(ByteBuffer[] buffer,boolean isGz){
		if(isGz){
			GzipContext gc=(GzipContext)PoolManager.getInstance(GzipContext.class);
			gc.putZipedBuffer(buffer);
			buffer=gc.getPlainBuffer();
			PoolManager.poolInstance(gc);
		}
		RandomAccessFile raf=null;
		BlobFile result=null;
		try {
			File file=File.createTempFile("wsqblob", "tmp", config.getTmpDir());
			result=new BlobFile(file);
			raf = new RandomAccessFile(file,"rwd");
			FileChannel writeChannel=raf.getChannel();
			BuffersUtil.mark(buffer);
			writeChannel.write(buffer);
			BuffersUtil.reset(buffer);
			raf.close();
			result.cache=buffer;
		} catch (IOException e) {
		}finally{
			if(raf!=null){
				try {
					raf.close();
				} catch (IOException ignore) {
				}
			}
		}
		return result;
	}
	
	public BlobFile(File file){
		this.file=file;
	}
	public File getFile() {
		return file;
	}
	public List<Blob> getRefBlob() {
		return refBlob;
	}
	
	private void readFile(){
		RandomAccessFile raf=null;
		try {
			long length=file.length();
			cache=BuffersUtil.prepareBuffers(length);
			raf = new RandomAccessFile(file,"rd");
			FileChannel readChannel=raf.getChannel();
			if( readChannel.read(cache)!=length){
			}
			raf.close();
		} catch (IOException e) {
		}finally{
			if(raf!=null){
				try {
					raf.close();
				} catch (IOException ignore) {
				}
			}
		}
	}
	
	public void addRef(Blob blob){
		refBlob.add(blob);
	}
	
	public ByteBuffer[] read(long offset,long length){
		if(cache==null){
			readFile();
		}
		BuffersUtil.mark(cache);
		BuffersUtil.slice(cache, offset, length);
		ByteBuffer[] result=BuffersUtil.dupBuffers(cache);
		BuffersUtil.reset(cache);
		return result;
	}
	
	public void cacheClean(){
		PoolManager.poolBufferInstance(cache);
		cache=null;
	}
}
