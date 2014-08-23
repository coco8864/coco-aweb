package naru.aweb;

import static org.junit.Assert.*;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;
import net.sf.json.util.PropertyFilter;

import org.junit.Test;

public class JsonLibTest {

	private static class XXX implements PropertyFilter{
		@Override
		public boolean apply(Object arg0, String arg1, Object arg2) {
			System.out.println("o:"+arg0);
			System.out.println("key:"+arg1);
			System.out.println("value:"+arg2);
			return true;
//			return false;
		}
	}
	
	@Test
	public void test1() {
		JSONObject o=(JSONObject)JSONSerializer.toJSON("{aaa:'bbb',bbb:{ccc:'ddd'}}");
		JsonConfig c=new JsonConfig();
		c.setRootClass(JSONObject.class);
		c.setJavaPropertyFilter(new XXX());
		Object x=JSONSerializer.toJava(o,c);
		System.out.println(x);
	}

}
