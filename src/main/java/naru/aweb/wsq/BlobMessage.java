package naru.aweb.wsq;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import naru.async.pool.PoolManager;
import naru.async.store.Page;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BlobMessage {
	private Object message;
	private JSONObject meta;
	private List<Blob> blobs=new ArrayList<Blob>();
	
	public static BlobMessage create(JSONObject meta,ByteBuffer[] data){
		Object message=meta.get("message");//message ‚ÍString or JSON
		BlobMessage result=new BlobMessage(message);
		result.setMeta(meta);
		if(meta.getInt("totalLength")!=0){
			BlobFile blobFile=BlobFile.create(data,meta.getBoolean("isGz"));
			JSONArray dataMetas=meta.getJSONArray("dataMetas");
			long offset=0;
			for(int i=0;i<dataMetas.size();i++){
				JSONObject dataMeta=dataMetas.getJSONObject(i);
				long length=dataMeta.getLong("length");
				Blob blob=new Blob(blobFile,offset,length);
				offset+=length;
				blob.setJsType(dataMeta.optString("jsType"));
				blob.setName(dataMeta.optString("name"));
				blob.setMimeType(dataMeta.optString("mimeType"));
				result.addBlob(blob);
			}
		}
		return result;
	}
	
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
		blobs.add(new Blob(buffer));
	}
	public void addBlob(File file){
		blobs.add(new Blob(file));
	}

	public Object getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
