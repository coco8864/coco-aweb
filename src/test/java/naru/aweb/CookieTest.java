package naru.aweb;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CookieTest {

	/**
	 * @param args
	 * @throws URISyntaxException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws URISyntaxException, IOException {
		CookieManager cm=new CookieManager();
		Map<String,List<String>> responseHeaders=new HashMap<String,List<String>>();
		List<String> values=new ArrayList<String>();
		values.add("aaa=bbb");
		values.add("phId1=623551a03e589ba80bb20c8100060338; Path=/admin; Secure");
		values.add("phId2=623551a03e589ba80bb20c8100060338; Path=/admin");
		values.add("phId3=623551a03e589ba80bb20c8100060338; expires=Mon, 11-Nov-2013 21:29:42 GMT; Path=/admin");
		responseHeaders.put("Set-Cookie", values);
		URI uri=new URI("https://a.com/admin");
		cm.put(uri, responseHeaders);
		responseHeaders.clear();
		Object o=cm.get(new URI("https://a.com/admin/xxx"), responseHeaders);
		System.out.println(o);
		o=cm.get(new URI("http://b.com"), responseHeaders);
		System.out.println(o);
	}

}
