package naru.aweb.pa;

import java.util.Map;

import net.sf.json.JSON;
import net.sf.json.JSONSerializer;

public class JsonBlob {
	private static final String BLOB_VALUE_NAME_PREFIX="_paBlobValue";
	/* Blob‚ğŠÜ‚Ş@JSON‚ğ siriarize */
	/* siriarize‚³‚ê‚½JSON‚ğBlob‚ğŠÜ‚ŞJSON‚É•ÏŠ· */
	private JSON mainObj;/* Blob‚ğŠÜ‚Ü‚È‚¢json */
	private Map<String,Blob> blobs;
	
	public static JsonBlob serialize(JSON json){
		return (JsonBlob)JSONSerializer.toJava(json);
	}
	
	public static JSON deserialize(JsonBlob jsonBlob){
		return JSONSerializer.toJSON(jsonBlob);
	}
	
	

}
