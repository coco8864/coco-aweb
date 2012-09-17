package naru.aweb.wsq;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.cache.CacheBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSONObject;

public class BlobEnvelope extends PoolBase implements AsyncBuffer,BufferGetter{
	private JSONObject header;
	private BlobMessage blobMessage;
	
	private ByteBuffer[] headerBuffer;
	private long blobOffset=0;
	private long bufferLength;
	
	private static String getString(ByteBuffer buf,int length){
		int pos=buf.position();
		if((pos+length)>buf.limit()){
			throw new UnsupportedOperationException("getString");
		}
		String result;
		try {
			result = new String(buf.array(),pos,length,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException("getString enc");
		}
		buf.position(pos+length);
		return result;
	}
	
	public static ByteBuffer headerBuffer(JSONObject header,BlobMessage message){
		header.element("dataType", "blobMessage");
		header.element("blobHeaderLength", message.getBlobHeaderLength());
		String headerString=header.toString();
		byte[] headerBytes=null;
		try {
			headerBytes = headerString.getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException("WsqHandler onMessage");
		}
		ByteBuffer headerBuffer=PoolManager.getBufferInstance(4+headerBytes.length);
		headerBuffer.order(ByteOrder.BIG_ENDIAN);
		headerBuffer.putInt(headerBytes.length);
		headerBuffer.put(headerBytes);
		headerBuffer.flip();
		return headerBuffer;
	}
	
	/* 受信データを解析する場合 */
	public static BlobEnvelope parse(CacheBuffer buffer){
		if(!buffer.isInTopBuffer()){
			buffer.unref();
			throw new UnsupportedOperationException("BlobEnvelope parse");
		}
		//TODO 先頭の1バッファにheader類が保持されている事に依存
		ByteBuffer[] topBufs=buffer.popTopBuffer();
		ByteBuffer topBuf=topBufs[0];
		topBuf.order(ByteOrder.BIG_ENDIAN);
		int headerLength=topBuf.getInt();
		int pos=topBuf.position();
		if((pos+headerLength)>topBuf.limit()){
			PoolManager.poolBufferInstance(topBufs);
			throw new UnsupportedOperationException("BlobEnvelope parse");
		}
		String headerString=getString(topBuf,headerLength);
		JSONObject header=JSONObject.fromObject(headerString);
		int blobHeaderLength=header.getInt("blobHeaderLength");
		
		String blobHeaderString=getString(topBuf,blobHeaderLength);
		JSONObject blobHeader=JSONObject.fromObject(blobHeaderString);
		BlobMessage blobMessage=BlobMessage.parse(blobHeader, 4+headerLength+blobHeaderLength, buffer);
		buffer.unref();//必要なBlobは参照をマーク、この処理は、これ以上bufferを予約する必要はない。
		PoolManager.poolBufferInstance(topBufs);//
		
		BlobEnvelope envelope=(BlobEnvelope)PoolManager.getInstance(BlobEnvelope.class);
		envelope.header=header;
		envelope.blobMessage=blobMessage;
		return envelope;
	}
	
	/* 送信用に新規に作る場合 */
	public static BlobEnvelope create(JSONObject header,BlobMessage blobMessage){
		BlobEnvelope envelope=(BlobEnvelope)PoolManager.getInstance(BlobEnvelope.class);
		envelope.init(header, blobMessage);
		return envelope;
	}
	
	private void init(JSONObject header,BlobMessage blobMessage){
		this.header=header;
		blobMessage.ref();
		blobMessage.flip();
		this.blobMessage=blobMessage;
		ByteBuffer buffer=headerBuffer(header, blobMessage);
		headerBuffer=BuffersUtil.toByteBufferArray(buffer);
		blobOffset=0;
		bufferLength=BuffersUtil.remaining(headerBuffer)+blobMessage.bufferLength();
	}
	
	@Override
	public void recycle() {
		if(headerBuffer!=null){
			PoolManager.poolBufferInstance(headerBuffer);
			headerBuffer=null;
		}
		if(blobMessage!=null){
			blobMessage.unref();
			blobMessage=null;
		}
		blobOffset=0;
		bufferLength=0;
	}
	
	public synchronized boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		if(headerBuffer!=null){
			bufferGetter.onBuffer(userContext, headerBuffer);
			headerBuffer=null;
		}else{
			Object[] ctx=new Object[]{bufferGetter,userContext};
			blobMessage.asyncBuffer(this, blobOffset, ctx);
		}
		return false;
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext){
		throw new UnsupportedOperationException("asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext)");
	}

	public long bufferLength() {
		return bufferLength;
	}

	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		blobOffset+=BuffersUtil.remaining(buffers);
		Object[] ctx=(Object [])userContext;
		return ((BufferGetter)ctx[0]).onBuffer(ctx[1], buffers);
	}

	public void onBufferEnd(Object userContext) {
		Object[] ctx=(Object [])userContext;
		((BufferGetter)ctx[0]).onBufferEnd(ctx[1]);
	}

	public void onBufferFailure(Object userContext, Throwable failure) {
		Object[] ctx=(Object [])userContext;
		((BufferGetter)ctx[0]).onBufferFailure(ctx[1],failure);
	}

	public JSONObject getHeader() {
		return header;
	}

	public BlobMessage getBlobMessage() {
		return blobMessage;
	}
}
