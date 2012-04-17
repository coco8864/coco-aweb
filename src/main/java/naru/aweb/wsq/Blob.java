package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.cache.CacheBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

/**
 * binaryMessageのインタフェースに利用
 * 送信時には、データの持ち方を判断する
 * 1)connectしていないsubId毎にbufferで持つとメモリがパンクする
 * 2)subId毎にFileをreadするのは非効率
 * 
 * データの持ち方は以下の3つ
 * 1)buffer
 * 2)file
 * 3)blobFile offset
 * 
 * 最初の実装は2)を許容する
 * @author Naru
 */
public class Blob extends PoolBase implements AsyncBuffer{
	private String mimeType;
	private String jsType;
	private String name;
	private CacheBuffer buffer;
	private long offset;
	private long length;
	
	public static Blob create(File file){
		CacheBuffer buffer=CacheBuffer.open(file);
		return create(buffer);
	}
	
	public static Blob create(ByteBuffer[] byteBuffer){
		CacheBuffer buffer=CacheBuffer.open(byteBuffer);
		return create(buffer);
	}
	
	public static Blob create(CacheBuffer buffer){
		return create(buffer,0,buffer.bufferLength());
	}
	
	/* bufferは、Blob終了時に開放される */
	public static Blob create(CacheBuffer buffer,long offset,long length){
		Blob blob=(Blob)PoolManager.getInstance(Blob.class);
		blob.buffer=buffer;
		blob.offset=offset;
		blob.length=length;
//		blob.endPosition=offset+length;
		return blob;
	}
	
	public long length(){
		return length;
	}
	
	public String getMimeType() {
		return mimeType;
	}

	public String getJsType() {
		return jsType;
	}

	public String getName() {
		return name;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public void setJsType(String jsType) {
		this.jsType = jsType;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public void recycle() {
		if(buffer!=null){
			buffer.unref();
			buffer=null;
		}
		mimeType=jsType=name=null;
		length=offset=0;
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		return buffer.asyncBuffer(bufferGetter, userContext);
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext) {
		return buffer.asyncBuffer(bufferGetter, this.offset+offset,userContext);
	}

	public long bufferLength() {
		return length;
	}
}
