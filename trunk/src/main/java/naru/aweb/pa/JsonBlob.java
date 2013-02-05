package naru.aweb.pa;

import java.util.Map;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;

public class JsonBlob {
	private static final String BLOB_VALUE_NAME_PREFIX="_paBlobValue";
	/* Blobを含む　JSONを siriarize */
	/* siriarizeされたJSONをBlobを含むJSONに変換 */
	private JSON mainObj;/* Blobを含まないjson */
	private Map<String,Blob> blobs;
	
	public static JsonBlob serialize(JSON json){
		return (JsonBlob)JSONSerializer.toJava(json);
	}
	
	public static JSON deserialize(JsonBlob jsonBlob){
		return JSONSerializer.toJSON(jsonBlob);
	}
	
	

}
