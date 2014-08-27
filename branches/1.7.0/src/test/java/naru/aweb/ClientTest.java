package naru.aweb;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;


public class ClientTest {
	
	private void readInputStream(InputStream is) throws IOException{
		byte[] buffer=new byte[1024];
		while(true){
			int len=is.read(buffer);
			if(len<=0){
				return;
			}
			System.out.print(new String(buffer,0,len));
		}
	}
	
	@Test
	public void testClient() throws ClientProtocolException, IOException{
        HttpClient httpclient = new DefaultHttpClient();
        HttpHost proxy = new HttpHost("127.0.0.1", 1280, "http");
        HttpGet httpget = new HttpGet("http://a.b.c.d/pdn/"); 

        httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
        
        System.out.println("executing request " + httpget.getURI());
        HttpResponse response = httpclient.execute(httpget);
        HttpEntity entity = response.getEntity();

        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        if (entity != null) {
            System.out.println("Response content length: " + entity.getContentLength());
        }
        InputStream is=response.getEntity().getContent();
        readInputStream(is);
        
        System.out.println("----------------------------------------");

        // Do not feel like reading the response body
        // Call abort on the request object
        httpget.abort();
        
        // When HttpClient instance is no longer needed, 
        // shut down the connection manager to ensure
        // immediate deallocation of all system resources
        httpclient.getConnectionManager().shutdown();        
	}

}
