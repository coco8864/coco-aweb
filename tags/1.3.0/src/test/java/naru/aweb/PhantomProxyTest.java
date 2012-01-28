package naru.aweb;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import naru.queuelet.test.TestBase;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class PhantomProxyTest extends TestBase{
	private static Logger logger=Logger.getLogger(PhantomProxyTest.class);
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.setProperty("http.proxyHost","127.0.0.1");
		System.setProperty("http.proxyPort","1280");
		System.setProperty("https.proxyHost","127.0.0.1");
		System.setProperty("https.proxyPort","1280");
//		setupContainer(
//				"testEnv.properties",
//				"Phantom");
	}
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
//		stopContainer();
	}
	
	@Test
	public void test0() throws Throwable{
		callTest("qtest0");
	}
	public void qtest0() throws Throwable{
		System.out.println("ok?");
//		Main.terminate();
	}
	
	
	private void readInputStream(InputStream is) throws IOException{
		byte[] buffer=new byte[1024];
		long readLength=0;
		while(true){
			int len=is.read(buffer);
			if(len<=0){
				break;
			}
//			System.out.print(new String(buffer,0,len));
			readLength+=len;
		}
		System.out.println(readLength);
	}
	
	@Test
	public void testWeb() throws Throwable{
		URL url=new URL("http://ph.127.0.0.1:1280/pub/myProxy_080929.zip");
//		URL url=new URL("http://ph.127.0.0.1:1280/pub/test.txt");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
	}
	
	@Test
	public void testProxyPlain() throws Throwable{
		URL url=new URL("http://a.b.c.d:8888/ctrl/js/jquery-1.2.6.js");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
	}
	
	@Test
	public void testProxyPlainTrace() throws Throwable{
		URL url=new URL("http://ph.www.asahi.com/");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
	}
	
	@Test
	public void testProxySsl() throws Throwable{
		URL url=new URL("https://login.yahoo.co.jp/config/login");
		HttpsURLConnection httpsconnection=(HttpsURLConnection)url.openConnection();
		KeyManager[] km = null;
		TrustManager[] tm = { new X509TrustManager() {
		  public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		  }
		  public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		  }
		  public X509Certificate[] getAcceptedIssuers() {
		   return null;
		  }
		 }
		};
		SSLContext sslcontext= SSLContext.getInstance("SSL");
		sslcontext.init(km, tm, new SecureRandom());
		httpsconnection.setSSLSocketFactory(sslcontext.getSocketFactory());
		
		InputStream is=httpsconnection.getInputStream();
//		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
	}
	
	@Test
	public void testProxySslTrace() throws Throwable{
		URL url=new URL("https://ph.login.yahoo.co.jp/config/login");
		HttpsURLConnection httpsconnection=(HttpsURLConnection)url.openConnection();
		KeyManager[] km = null;
		TrustManager[] tm = { new X509TrustManager() {
		  public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		  }
		  public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
		  }
		  public X509Certificate[] getAcceptedIssuers() {
		   return null;
		  }
		 }
		};
		SSLContext sslcontext= SSLContext.getInstance("SSL");
		sslcontext.init(km, tm, new SecureRandom());
		httpsconnection.setSSLSocketFactory(sslcontext.getSocketFactory());
		
		InputStream is=httpsconnection.getInputStream();
//		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
	}
	
	@Test
	public void testDumpStore() throws Throwable{
		URL url=new URL("http://127.0.0.1:1280/admin?cmd=dumpStore");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
	}
	
}
