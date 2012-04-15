package naru.aweb.wsq;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import naru.async.cache.AsyncBuffer;
import naru.async.pool.PoolManager;
import naru.async.store.Page;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BlobMessage {
	private Object message;
	private JSONObject meta;
	private List<Blob> blobs=new ArrayList<Blob>();
	
	public static BlobMessage create(JSONObject header,AsyncBuffer buffer){
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
}
