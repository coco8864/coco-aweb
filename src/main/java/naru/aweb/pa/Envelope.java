package naru.aweb.pa;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import naru.async.cache.CacheBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/*
 * 転送用の中間オブジェクト
 * blobを含む場合、binaryとしてしか送付できない
 */
public class Envelope extends PoolBase{
	private static final String BLOB_VALUE_NAME_PREFIX="_paBlobValue";
	private static final String DATE_VALUE_NAME_PREFIX="_paDateValue";
	/* Blobを含む　JSONを siriarize */
	/* siriarizeされたJSONをBlobを含むJSONに変換 */
	private JSON mainObj;/* Blobを含まないjson */
	private Map<String,Blob> blobs=new HashMap<String,Blob>();
	private Map<String,Long> dates=new HashMap<String,Long>();
	
	@Override
	public void recycle(){
		blobs.clear();
		dates.clear();
	}
	
	public boolean isBin(){
		return blobs.size()>0;
	}
	public JSONObject getMeta(){
		JSONObject meta=new JSONObject();
		int size=dates.size();
		for(int i=0;i<size;i++){
			Long date=dates.get(DATE_VALUE_NAME_PREFIX+i);
			meta.accumulate("date",date);
		}
		size=blobs.size();
		for(int i=0;i<size;i++){
			Blob blob=blobs.get(BLOB_VALUE_NAME_PREFIX+i);
			meta.accumulate("blobLength",blob.size());
		}
		return meta;
	}
	
	public Object serialize(Object obj){
		if(obj instanceof Blob){
			String key=BLOB_VALUE_NAME_PREFIX + blobs.size();
			blobs.put(key,(Blob)obj);
			return 	key;
		}else if(obj instanceof Date){
			String key=DATE_VALUE_NAME_PREFIX + dates.size();
			dates.put(key,((Date)obj).getTime());
			return 	key;
		}else if(obj instanceof JSONObject){
			JSONObject clone=new JSONObject();
			JSONObject json=(JSONObject)obj;
			for(Object key:json.keySet()){
				clone.put((String)key, serialize(json.get(key)));
			}
			return clone;
		}else if(obj instanceof JSONArray){
			JSONArray clone=new JSONArray();
			JSONArray array=(JSONArray)obj;
			int size=array.size();
			for(int i=0;i<size;i++){
				clone.add(serialize(array.get(i)));
			}
		}
		return obj;
	}
	
	public Object deserialize(Object obj){
		if(obj instanceof JSONObject){
			JSONObject clone=new JSONObject();
			JSONObject json=(JSONObject)obj;
			for(Object key:json.keySet()){
				clone.put((String)key, deserialize(json.get(key)));
			}
			return clone;
		}else if(obj instanceof JSONArray){
			JSONArray clone=new JSONArray();
			JSONArray array=(JSONArray)obj;
			int size=array.size();
			for(int i=0;i<size;i++){
				clone.add(deserialize(array.get(i)));
			}
			return clone;
		}else if(obj instanceof String){
			if(((String)obj).startsWith(BLOB_VALUE_NAME_PREFIX)){
				return blobs.get(obj);
			}else if(((String)obj).startsWith(DATE_VALUE_NAME_PREFIX)){
				return dates.get(obj);
			}
		}
		return obj;
	}
	
	/* user obj -> protocol data
	 * Blobオブジェクトを含むjsonをjsonBlobに変換
	 */
	public static Envelope pack(JSON json){
		Envelope envelope=(Envelope)PoolManager.getInstance(Envelope.class);
		envelope.mainObj=(JSON)envelope.serialize(json);
		return envelope;
	}
	
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
	
	/* protocol data -> user obj
	 */
	public static JSONObject unpack(CacheBuffer prot){
		if(!prot.isInTopBuffer()){
			prot.unref();
			throw new UnsupportedOperationException("Envelope parse");
		}
		//TODO 先頭の1バッファにheader類が保持されている事に依存
		ByteBuffer[] topBufs=prot.popTopBuffer();
		ByteBuffer topBuf=topBufs[0];
		topBuf.order(ByteOrder.BIG_ENDIAN);
		long offset=0;
		int headerLength=topBuf.getInt();
		offset+=4;
		int pos=topBuf.position();
		if((pos+headerLength)>topBuf.limit()){
			PoolManager.poolBufferInstance(topBufs);
			throw new UnsupportedOperationException("Envelope parse");
		}
		String headerString=getString(topBuf,headerLength);
		offset+=headerLength;
		JSONObject header=JSONObject.fromObject(headerString);
		Envelope envelop=(Envelope)PoolManager.getInstance(Envelope.class);
		JSONObject meta=header.getJSONObject("meta");
		JSONArray dates=meta.getJSONArray("dates");
		int size=dates.size();
		for(int i=0;i<size;i++){
			envelop.dates.put(DATE_VALUE_NAME_PREFIX+i, dates.getLong(i));
		}
		JSONArray blobs=meta.getJSONArray("blobs");
		size=blobs.size();
		for(int i=0;i<size;i++){
			JSONObject blobMeta=blobs.getJSONObject(i);
			//TODO protの先頭bufferに残りがあると困る
			long length=blobMeta.getLong("size");
			Blob blob=Blob.create(prot,offset,length);
			offset+=length;
			blob.setJsType(blobMeta.getString("jsType"));
			blob.setType(blobMeta.optString("type",null));
			blob.setName(blobMeta.optString("name",null));
			long lastModifiedDate=blobMeta.optLong("lastModifiedDate", -1L);
			if(lastModifiedDate>0){
				blob.setLastModifiedDate(lastModifiedDate);
			}
			envelop.blobs.put(BLOB_VALUE_NAME_PREFIX+i,blob);
		}
		JSONObject result=(JSONObject)envelop.deserialize(header);
		envelop.unref();
		return result;
	}
}
