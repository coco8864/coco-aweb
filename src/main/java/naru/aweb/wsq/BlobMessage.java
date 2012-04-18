package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.cache.CacheBuffer;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/* BlobMessageの形
 * header長(4byte,BigEndigan)
 * header=jsonデータ
 * {
 * type:'publish',qname:'qname',dataCount:データ数,totalLength:総データ長,(isGz:gz圧縮の有無),message:任意
 * metas:[
 *  {length:1番目datalen,以降任意,jsType:'ArrayBuffer'|string|Blob|object,name:,mimeType: },
 *  {length:2番目datalen,以降任意,},
 *  {length:3番目datalen,以降任意,}
 *  ]
 * }
 * 1番目のデータ
 * 2番目のデータ
 * 3番目のデータ
 */

public class BlobMessage implements AsyncBuffer,BufferGetter{
	private Object message;
	private JSONObject header;
	private List<Blob> blobs=new ArrayList<Blob>();
	private long[] offsets=null;
	
	public static BlobMessage create(JSONObject header,CacheBuffer buffer){
		Object message=header.get("message");//message はString or JSON
		BlobMessage result=new BlobMessage(message);
		result.setHeader(header);
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
	public JSONObject getHeader() {
		return header;
	}
	public void setHeader(JSONObject header) {
		this.header=header;
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

	/* 子Blob読み込み用 */
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
