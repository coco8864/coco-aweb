package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BlobMessage implements AsyncBuffer,BufferGetter{
	private Object message;
	private JSONObject meta;
	private List<Blob> blobs=new ArrayList<Blob>();
	private long[] offsets=null;
	
	public static BlobMessage create(JSONObject header,naru.async.cache.CacheBuffer buffer){
		Object message=header.get("message");//message ‚ÍString or JSON
		BlobMessage result=new BlobMessage(message);
		result.setMeta(header);
		if(header.getInt("totalLength")!=0){
//			BlobFile blobFile=BlobFile.create(data,meta.getBoolean("isGz"));
			JSONArray metas=header.getJSONArray("metas");
			long offset=0;
			for(int i=0;i<metas.size();i++){
				JSONObject meta=metas.getJSONObject(i);
				long length=meta.getLong("length");
				Blob blob=Blob.create(buffer,offset,length);
				offset+=length;
				blob.setJsType(meta.optString("jsType"));
				blob.setName(meta.optString("name"));
				blob.setMimeType(meta.optString("mimeType"));
				result.addBlob(blob);
			}
		}
		return result;
	}
	
	/*
	public ByteBuffer[] toBuffer(){
		meta.element("message", message);
		Page page=new Page();
		ByteBuffer buf=PoolManager.getBufferInstance();
		byte[] metaBytes=null;
		try {
			metaBytes = message.toString().getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
		}
		int metaSize;
		buf.order(ByteOrder.LITTLE_ENDIAN);
		buf.putInt(metaBytes.length);
		buf.put(metaBytes);
		page.putBuffer(buf, true);
		int blobCount=blobCount();
		for(int i=0;i<blobCount;i++){
			Blob blob=getBlob(i);
			page.putBuffer(blob.read(), true);
		}
		ByteBuffer[] buffer=page.getBuffer();
		return buffer;
		
	}
	*/
	
	public BlobMessage(Object message){
		this.message=message;
	}
	
	public JSONObject getMeta() {
		return meta;
	}
	public void setMeta(JSONObject meta) {
		this.meta=meta;
	}
	
	public int blobCount(){
		return blobs.size();
	}
	
	public Blob getBlob(int index) {
		return blobs.get(index);
	}
	public void addBlob(Blob blob){
		blobs.add(blob);
	}
	public void addBlob(ByteBuffer[] buffer){
		blobs.add(Blob.create(buffer));
	}
	public void addBlob(File file){
		blobs.add(Blob.create(file));
	}
	public Object getMessage() {
		return message;
	}
	public void setMessage(Object message) {
		this.message = message;
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		throw new UnsupportedOperationException("asyncBuffer(BufferGetter bufferGetter, Object userContext)");
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext) {
		int blobNo=0;
		long blobOffset=0;
		for(;blobNo<offsets.length;blobNo++){
			if(offsets[blobNo]>offset){
				blobOffset=offsets[blobNo]-offset;
				break;
			}
		}
		if(blobNo==0){
		}else{
			Blob blob=getBlob(blobNo-1);
			blob.asyncBuffer(this,blobOffset,bufferGetter);
		}
		return false;
	}

	public long bufferLength() {
		return offsets[offsets.length-1];
	}

	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		BufferGetter bufferGetter=(BufferGetter)userContext;
		return bufferGetter.onBuffer(userContext, buffers);
	}

	public void onBufferEnd(Object userContext) {
		throw new IllegalStateException("BlobMessage not use onBufferEnd");
	}

	public void onBufferFailure(Object userContext, Throwable failure) {
		BufferGetter bufferGetter=(BufferGetter)userContext;
		bufferGetter.onBufferFailure(userContext, failure);
	}

}
