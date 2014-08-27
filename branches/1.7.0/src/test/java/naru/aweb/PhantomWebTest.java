package naru.aweb;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.sound.midi.Receiver;

import naru.async.ChannelHandler;
import naru.async.pool.BuffersUtil;
import naru.async.ssl.SslHandler;
import naru.aweb.core.Main;
import naru.queuelet.test.TestBase;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class PhantomWebTest extends TestBase{
	private static Logger logger=Logger.getLogger(PhantomWebTest.class);
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TestBase.setupContainer(
				"testEnv.properties",
				"Phantom");
	}
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestBase.stopContainer();
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
		URL url=new URL("http://127.0.0.1:1280/pub/myProxy_080929.zip");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
	}
	
//	@Test
//	public void testTerm() throws Throwable{
//		URL url=new URL("http://127.0.0.1:1280/admin?cmd=terminate");
//		InputStream is=(InputStream)url.getContent();
//		readInputStream(is);
//		is.close();
//	}
	
	
//	@Test
	public void testWebSSL() throws Throwable{
		URL url=new URL("https://phantom.naru.com:1280/pub");
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
}
