package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;

import naru.async.BufferGetter;
import naru.async.cache.AsyncBuffer;
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
public class Blob extends PoolBase implements BufferGetter{
	private String mimeType;
	private String jsType;
	private String name;
	private AsyncBuffer buffer;
	private long offset;
	private long length;
	private long endPosition;
	
	public static Blob create(File file){
		AsyncBuffer buffer=AsyncBuffer.open(file);
		return create(buffer);
	}
	
	public static Blob create(ByteBuffer[] byteBuffer){
		AsyncBuffer buffer=AsyncBuffer.open(byteBuffer);
		return create(buffer);
	}
	
	public static Blob create(AsyncBuffer buffer){
		return create(buffer,0,buffer.length());
	}
	
	/* buffer�́ABlob�I�����ɊJ������� */
	public static Blob create(AsyncBuffer buffer,long offset,long length){
		Blob blob=(Blob)PoolManager.getInstance(Blob.class);
		blob.buffer=buffer;
		blob.offset=offset;
		blob.length=length;
		blob.endPosition=offset+length;
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

	private static class GetContext{
		GetContext(BufferGetter getter,long position){
			this.getter=getter;
			this.position=position;
		}
		private BufferGetter getter;
		private long position;
	}
	
	/**
	 * ����Ăяo����ctx��null�ŌĂяo���B
	 * 2��ڈȍ~�́AonBuffer�ɒʒm����ctx���w�肵�A�p��get�ł��鎖��\������
	 * onBuffer�ɒʒm�����ctx��null�̏ꍇ�͍ŏIbuffer�ł��鎖��\��
	 */
	public boolean asyncGet(BufferGetter getter,Object ctx){
		GetContext context;
		if(ctx==null){
			context=new GetContext(getter,offset);
		}else{
			context=(GetContext)ctx;
		}
		return buffer.asyncGet(this, context.position, context);
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

	@Override
	public boolean onBuffer(Object ctx, ByteBuffer[] buffers) {
		GetContext context=(GetContext)ctx;
		long len=BuffersUtil.remaining(buffers);
		if( endPosition<=(context.position+len)){
			BuffersUtil.cut(buffers, endPosition-context.position);
			context.getter.onBuffer(null,buffers);
		}else{
			context.position+=len;
			context.getter.onBuffer(context,buffers);
		}
		return false;
	}

	@Override
	public void onBufferEnd(Object ctx) {
		GetContext context=(GetContext)ctx;
		context.getter.onBufferEnd(context);
	}

	@Override
	public void onBufferFailure(Object ctx, Throwable failure) {
		GetContext context=(GetContext)ctx;
		context.getter.onBufferFailure(context,failure);
	}
}
