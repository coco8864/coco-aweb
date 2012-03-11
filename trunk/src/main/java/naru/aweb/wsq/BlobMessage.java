package naru.aweb.wsq;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class BlobMessage {
	private Object message;
	private JSONObject meta;
	private List<Blob> blobs=new ArrayList<Blob>();
	
	public static BlobMessage create(JSONObject meta,ByteBuffer[] msgs){
		Object message=meta.get("message");//message ‚ÍString or JSON
		BlobMessage result=new BlobMessage(message);
		result.setMeta(meta);
		if(meta.getInt("totalLength")!=0){
			BlobFile blobFile=BlobFile.create(msgs,meta.getBoolean("isGz"));
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

	public Object getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
