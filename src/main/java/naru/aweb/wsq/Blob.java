package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.cache.CacheBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

/**
 * binaryMessage�̃C���^�t�F�[�X�ɗ��p
 * ���M���ɂ́A�f�[�^�̎������𔻒f����
 * 1)connect���Ă��Ȃ�subId����buffer�Ŏ��ƃ��������p���N����
 * 2)subId����File��read����͔̂����
 * 
 * �f�[�^�̎������͈ȉ���3��
 * 1)buffer
 * 2)file
 * 3)blobFile offset
 * 
 * �ŏ��̎�����2)�����e����
 * @author Naru
 */
public class Blob extends PoolBase implements AsyncBuffer,BufferGetter{
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
	
	/* buffer�́ABlob�I�����ɊJ������� */
	public static Blob create(CacheBuffer buffer,long offset,long length){
		Blob blob=(Blob)PoolManager.getInstance(Blob.class);
		buffer.ref();
		blob.buffer=buffer;
		blob.offset=offset;
		blob.length=length;
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
		throw new UnsupportedOperationException("asyncBuffer(BufferGetter bufferGetter, Object userContext)");
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext) {
		long maxLength=length-offset;
		Object[] ctx={bufferGetter,userContext,maxLength};
		return buffer.asyncBuffer(this, this.offset+offset,ctx);
	}

	public long bufferLength() {
		return length;
	}

	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		long len=BuffersUtil.remaining(buffers);
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		Long maxLength=(Long)ctx[2];
		if(len>maxLength){
			BuffersUtil.cut(buffers, maxLength);
		}
		bufferGetter.onBuffer(orgUserContext, buffers);
		return false;
	}

	public void onBufferEnd(Object userContext) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		bufferGetter.onBufferEnd(orgUserContext);
	}

	public void onBufferFailure(Object userContext, Throwable failure) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		bufferGetter.onBufferFailure(orgUserContext,failure);
	}
}
