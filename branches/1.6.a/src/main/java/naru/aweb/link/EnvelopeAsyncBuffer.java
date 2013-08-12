package naru.aweb.link;

import java.nio.ByteBuffer;
import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.link.api.Blob;

/*
 * レスポンス毎に１つづつ作成する
 */
public class EnvelopeAsyncBuffer extends PoolBase implements AsyncBuffer,BufferGetter{
	private ByteBuffer headerBuffer;
	private Blob[] blobs;
	private long bufferLength;
	private int curBlobIdx;
	private long curBlobOffset;
	
	/* headerBufferは消費されない */
	public static EnvelopeAsyncBuffer create(ByteBuffer headerBuffer,List<Blob>blobs){
		EnvelopeAsyncBuffer result=(EnvelopeAsyncBuffer)PoolManager.getInstance(EnvelopeAsyncBuffer.class);
		result.init(headerBuffer, blobs);
		return result;
	}
	
	private void init(ByteBuffer headerBuffer,List<Blob>blobs){
		this.headerBuffer=PoolManager.duplicateBuffer(headerBuffer);
		this.blobs=blobs.toArray(new Blob[blobs.size()]);
		bufferLength=headerBuffer.remaining();
		for(Blob blob:this.blobs){
			blob.ref();
			bufferLength+=blob.size();
		}
	}
	
	@Override
	public void recycle() {
		if(headerBuffer!=null){
			PoolManager.poolBufferInstance(headerBuffer);
			headerBuffer=null;
		}
		if(blobs!=null){
			for(Blob blob:this.blobs){
				blob.unref();
			}
			blobs=null;
		}
	}
	
	@Override
	public synchronized boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		if(headerBuffer!=null){
			bufferGetter.onBuffer(userContext, BuffersUtil.toByteBufferArray(headerBuffer));
			headerBuffer=null;
			curBlobIdx=0;
			curBlobOffset=0L;
		}else if(curBlobIdx<blobs.length){
			Blob blob=blobs[curBlobIdx];
			Object[] ctx=new Object[]{bufferGetter,userContext};
			blob.asyncBuffer(this, curBlobOffset, ctx);
		}else{
			bufferGetter.onBufferEnd(userContext);
		}
		return false;
	}

	@Override
	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext){
		throw new UnsupportedOperationException("asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext)");
	}

	@Override
	public long bufferLength() {
		return bufferLength;
	}
	
	/* BufferGetterインタフェース 
	 * 個々のBlobを読み込むために実装*/
	@Override
	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		curBlobOffset+=BuffersUtil.remaining(buffers);
		return bufferGetter.onBuffer(ctx[1], buffers);
	}

	@Override
	public void onBufferEnd(Object userContext) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		curBlobIdx++;
		curBlobOffset=0;
		if(curBlobIdx>=blobs.length){
			bufferGetter.onBufferEnd(ctx[1]);
			return;
		}
		Blob blob=blobs[curBlobIdx];
		blob.asyncBuffer(this, curBlobOffset, ctx);
	}

	@Override
	public void onBufferFailure(Object userContext, Throwable failure) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		bufferGetter.onBufferFailure(ctx[1], failure);
	}
}
