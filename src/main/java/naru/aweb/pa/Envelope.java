package naru.aweb.pa;

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
	
	/* protocol data -> user obj
	 */
	public static Object unpack(JSONObject meta,JSONObject main){
		Envelope jsonBlob=(Envelope)PoolManager.getInstance(Envelope.class);
		
		Object o=jsonBlob.deserialize(main);
		jsonBlob.unref();
		return o;
	}
	
	/* バイナリで受信した場合 */
	/* 通信データからユーザオブジェクトを生成する */
	public static Object unpack(CacheBuffer buffer){
		Envelope ev=null;
		return ev.unpack(null,null);
	}
}
