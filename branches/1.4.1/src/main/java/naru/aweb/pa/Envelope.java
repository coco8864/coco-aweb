package naru.aweb.pa;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import naru.async.AsyncBuffer;
import naru.async.cache.CacheBuffer;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/*
 * 転送用の中間オブジェクト
 * blobを含む場合、binaryとしてしか送付できない
 */
public class Envelope extends PoolBase{
	private static final String BLOB_VALUE_NAME_PREFIX="_paBlobValue";
	private static final int BLOB_VALUE_NAME_PREFIX_LEN=BLOB_VALUE_NAME_PREFIX.length();
	private static final String DATE_VALUE_NAME_PREFIX="_paDateValue";
	private static final int DATE_VALUE_NAME_PREFIX_LEN=DATE_VALUE_NAME_PREFIX.length();

	/* Blobを含む　JSONを siriarize */
	/* siriarizeされたJSONをBlobを含むJSONに変換 */
	private JSONObject mainObj;/* Blobを含まないjson */
	private List<Blob> blobs=new ArrayList<Blob>();
	private List<Long> dates=new ArrayList<Long>();
	
	/* レスポンオブジェクト作成用のメンバ変数 
	 * subname毎にキャッシュして無駄なオブジェクトを作らない*/
	private Map<String,JSONObject> sendJsonCache=new HashMap<String,JSONObject>();
	private Map<String,ByteBuffer> headerBufferCache=new HashMap<String,ByteBuffer>();
	private ByteBuffer mainObjBuffer=null;
	
	@Override
	public void recycle(){
		for(Blob blob:blobs){
			blob.unref();
		}
		blobs.clear();
		dates.clear();
		mainObj=null;
		if(mainObjBuffer!=null){
			PoolManager.poolBufferInstance(mainObjBuffer);
			mainObjBuffer=null;
		}
		sendJsonCache.clear();
		for(ByteBuffer headerBuffer:headerBufferCache.values()){
			PoolManager.poolBufferInstance(headerBuffer);
		}
		headerBufferCache.clear();
	}
	
	public JSONObject meta(){
		JSONObject meta=new JSONObject();
		meta.element("dates", dates);
		int size=blobs.size();
		JSONArray blobsJson=new JSONArray();
		for(int i=0;i<size;i++){
			Blob blob=blobs.get(i);
			blobsJson.add(blob.meta());
		}
		meta.element("blobs", blobsJson);
		return meta;
	}
	
	public JSONObject getMainObj(){
		return mainObj;
	}
	
	public boolean isBinary(){
		return blobs.size()>0;
	}
	
	public JSONObject getSendJson(String subname){
		if(subname==null){
			return mainObj;
		}
		JSONObject sendJson=sendJsonCache.get(subname);
		if(sendJson!=null){
			return sendJson;
		}
		sendJson=new JSONObject();
		sendJson.putAll(mainObj);
		sendJson.put(PaSession.KEY_SUBNAME, subname);
		sendJsonCache.put(subname, sendJson);
		return sendJson;
	}
	
	private ByteBuffer getHeaderBuffer(JSONObject data){
		ByteBuffer headerBuffer=null;
		String sendJsonText=data.toString();
		headerBuffer=PoolManager.getBufferInstance();
		try {
			byte[] headerBytes=sendJsonText.getBytes("utf-8");
			headerBuffer.order(ByteOrder.BIG_ENDIAN);
			headerBuffer.putInt(headerBytes.length);
			headerBuffer.put(headerBytes);
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException("getBytes utf-8");
		}
		headerBuffer.flip();
		return headerBuffer;
	}
	
	/*
	 * bynaryデータは、streamを含むので1送信に1つづつAsyncBufferが必要
	 */
	public AsyncBuffer createSendAsyncBuffer(String subname){
		if(subname==null){
			if(mainObjBuffer==null){
				mainObjBuffer=getHeaderBuffer(mainObj);
			}
			return EnvelopeAsyncBuffer.create(mainObjBuffer, blobs);
		}
		ByteBuffer headerBuffer=headerBufferCache.get(subname);
		if(headerBuffer==null){
			mainObj.put(PaSession.KEY_SUBNAME, subname);
			headerBuffer=getHeaderBuffer(mainObj);
			headerBufferCache.put(subname, headerBuffer);
		}
		return EnvelopeAsyncBuffer.create(headerBuffer, blobs);
	}
	
	public Object serialize(Object obj){
		if(obj instanceof Blob){
			int idx=blobs.size();
			String key=BLOB_VALUE_NAME_PREFIX + idx;
			blobs.add((Blob)obj);
			return 	key;
		}else if(obj instanceof Date){
			int idx=dates.size();
			String key=DATE_VALUE_NAME_PREFIX + idx;
			dates.add(((Date)obj).getTime());
			return 	key;
		}else if(obj instanceof Map){
			JSONObject clone=new JSONObject();
			Map json=(Map)obj;
			for(Object key:json.keySet()){
				clone.put((String)key, serialize(json.get(key)));
			}
			return clone;
		}else if(obj instanceof List){
			JSONArray clone=new JSONArray();
			List array=(List)obj;
			int size=array.size();
			for(int i=0;i<size;i++){
				clone.add(serialize(array.get(i)));
			}
			return clone;
		}
		return obj;
	}
	
	public Object deserialize(Object obj){
		if(obj instanceof JSONObject){
			Map clone=new HashMap();
			JSONObject json=(JSONObject)obj;
			for(Object key:json.keySet()){
				clone.put((String)key, deserialize(json.get(key)));
			}
			return clone;
		}else if(obj instanceof JSONArray){
			List clone=new ArrayList();
			JSONArray array=(JSONArray)obj;
			int size=array.size();
			for(int i=0;i<size;i++){
				clone.add(deserialize(array.get(i)));
			}
			return clone;
		}else if(obj instanceof String){
			if(((String)obj).startsWith(BLOB_VALUE_NAME_PREFIX)){
				int idx=Integer.parseInt(((String)obj).substring(BLOB_VALUE_NAME_PREFIX_LEN));
				Blob blob=blobs.get(idx);
				blob.ref();
				return blob;
			}else if(((String)obj).startsWith(DATE_VALUE_NAME_PREFIX)){
				int idx=Integer.parseInt(((String)obj).substring(DATE_VALUE_NAME_PREFIX_LEN));
				return new Date(dates.get(idx));
			}
		}
		return obj;
	}
	
	/* user obj -> protocol data
	 * Blobオブジェクトを含むjsonをjsonBlobに変換
	 */
	public static Envelope pack(Map message){
		Envelope envelope=(Envelope)PoolManager.getInstance(Envelope.class);
		envelope.mainObj=(JSONObject)envelope.serialize(message);
		envelope.mainObj.accumulate("meta", envelope.meta());
		return envelope;
	}
	
	private static String getStringFromBuffer(ByteBuffer buf,int length){
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
	public static Map unpack(CacheBuffer prot){
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
		String headerString=getStringFromBuffer(topBuf,headerLength);
		offset+=headerLength;
		JSONObject header=JSONObject.fromObject(headerString);
		JSONObject meta=header.getJSONObject("meta");
		JSONArray blobs=meta.getJSONArray("blobs");
		int size=blobs.size();
		List<Blob> blobsList=new ArrayList<Blob>();		
		for(int i=0;i<size;i++){
			JSONObject blobMeta=blobs.getJSONObject(i);
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
			blobsList.add(blob);
		}
		prot.unref();//Blobに必要な参照は、Blob.create時に加算されている
		return unpack(header,blobsList);
	}
	
	public static Map unpack(JSONObject header,List<Blob> blobs){
		JSONObject meta=header.getJSONObject("meta");
		JSONArray dates=meta.getJSONArray("dates");
		Envelope envelop=(Envelope)PoolManager.getInstance(Envelope.class);
		if(blobs!=null){
			envelop.blobs.addAll(blobs);
		}
		int size=dates.size();
		for(int i=0;i<size;i++){
			envelop.dates.add(dates.getLong(i));
		}
		Map result=(Map)envelop.deserialize(header);
		envelop.unref();
		return result;
	}
	
}
