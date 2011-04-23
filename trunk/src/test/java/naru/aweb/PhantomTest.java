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

public class PhantomTest extends TestBase{
	private static Logger logger=Logger.getLogger(PhantomTest.class);
	
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
		System.out.println("startup");
		Thread.sleep(1000*60*60);
	}
	
}
