package naru.aweb.pa;

import java.util.Map;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;

public class JsonBlob {
	private static final String BLOB_VALUE_NAME_PREFIX="_paBlobValue";
	/* Blob���܂ށ@JSON�� siriarize */
	/* siriarize���ꂽJSON��Blob���܂�JSON�ɕϊ� */
	private JSON mainObj;/* Blob���܂܂Ȃ�json */
	private Map<String,Blob> blobs;
	
	public static JsonBlob serialize(JSON json){
		return (JsonBlob)JSONSerializer.toJava(json);
	}
	
	public static JSON deserialize(JsonBlob jsonBlob){
		return JSONSerializer.toJSON(jsonBlob);
	}
	
	

}
