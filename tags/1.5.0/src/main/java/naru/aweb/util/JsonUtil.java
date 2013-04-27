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
			} else if(Character.isISOControl(ch)){
				sb.append(CH_ESCAPE).append('u');
				String hexStr=Integer.toHexString((int)ch);
				switch(hexStr.length()){
				case 1:
					sb.append('0');
				case 2:
					sb.append('0');
				case 3:
					sb.append('0').append(Integer.toHexString((int)ch));
					break;
				default:
					throw new RuntimeException("hexStr:"+hexStr);
				}
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
