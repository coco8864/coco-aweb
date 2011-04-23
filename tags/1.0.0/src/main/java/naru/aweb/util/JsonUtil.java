package naru.aweb.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;
import net.sf.json.processors.JsonBeanProcessor;

public class JsonUtil {
	// エスケープ文字
	private static final char CH_ESCAPE = '\\';
	private static final char CH_SQUOT = '\'';
	private static final char CH_DQUOT = '\"';
	private static final char CH_BS = '\b';    // 0x08
	private static final char CH_HT = '\t';    // 0x09
	private static final char CH_LF = '\n';    // 0x0A
	private static final char CH_FF = '\f';    // 0x0C
	private static final char CH_CR = '\r';    // 0x0D
	private static final String STR_BS = "\\b";
	private static final String STR_HT = "\\t";
	private static final String STR_LF = "\\n";
	private static final String STR_FF = "\\f";
	private static final String STR_CR = "\\r";

    public static String escape(Object obj) {
		if (obj == null) {
			return null;
		}
		String string =null;
		if(obj instanceof String){
			string=(String)obj;
		}else{
			string = obj.toString();
		}
		if (string == null || string.length() == 0) {
			return string;
		}
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < string.length(); i++) {
			char ch = string.charAt(i);
			if (ch == CH_ESCAPE) {
				sb.append(CH_ESCAPE).append(CH_ESCAPE);
			} else if (ch == CH_SQUOT) {
				sb.append(CH_ESCAPE).append(CH_SQUOT);
			} else if (ch == CH_DQUOT) {
				sb.append(CH_ESCAPE).append(CH_DQUOT);
			} else if (ch == CH_BS) {
				sb.append(STR_BS);
			} else if (ch == CH_HT) {
				sb.append(STR_HT);
			} else if (ch == CH_LF) {
				sb.append(STR_LF);
			} else if (ch == CH_FF) {
				sb.append(STR_FF);
			} else if (ch == CH_CR) {
				sb.append(STR_CR);
			} else {
				sb.append(ch);
			}
		}
		if(sb.length()==string.length()){
			//エスケープ文字が含まれなかった場合そのまま返却
			return string;
		}
		return sb.toString();
	}
    
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.registerJsonBeanProcessor(Date.class, new DateBeanProcesser());
	}
	
	public static class DateBeanProcesser implements JsonBeanProcessor{
		public JSONObject processBean(Object obj, JsonConfig arg1) {
			Date date=(Date)obj;
			JSONObject m=new JSONObject();
			m.put("$Date$", date.getTime());
			return m;
		}
	}
    
    public static String toJsonString(Object o){
		JSON json=JSONSerializer.toJSON(o,jsonConfig);
		return json.toString();
    }
    
    public static Object toBean(Object o){
		if(o instanceof JSONObject){
			o=toBean((JSONObject)o);
		}else if(o instanceof JSONArray){
			o=toBean((JSONArray)o);
		}
		return o;
	}
	
	public static Object toBean(JSONArray ja){
		int n=ja.size();
		List a=new ArrayList();
		for(int i=0;i<n;i++){
			Object o=ja.get(i);
			o=toBean(o);
			a.set(i, o);
		}
		return a;
	}
	
	public static Object toBean(JSONObject jo){
		Object d=jo.get("$Date$");
		if(d!=null){
			return new Date((Long)d);
		}
		Map m=new HashMap();
		Iterator itr=jo.keys();
		while(itr.hasNext()){
			Object key=itr.next();
			Object o=jo.get(key);
			o=toBean(o);
			m.put(key, o);
		}
		return m;
	}
}
