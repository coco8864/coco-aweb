package naru.aweb;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import naru.aweb.config.AccessLog;
import naru.aweb.mapping.Mapping;
import naru.aweb.util.JdoUtil;
import naru.queuelet.test.TestBase;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SimpleTest extends TestBase{
	private static Logger logger=Logger.getLogger(SimpleTest.class);
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.setProperty("http.proxyHost","127.0.0.1");
		System.setProperty("http.proxyPort","1280");
		System.setProperty("https.proxyHost","127.0.0.1");
		System.setProperty("https.proxyPort","1280");
		setupContainer(
				"testEnv.properties",
				"Phantom");
	}
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		stopContainer();
	}
	
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
	public void testAccessLogReq1() throws Throwable{
		System.out.println("===");
//		URL url=new URL("http://homepage2.nifty.com/naruh/");
		URL url=new URL("http://ph.homepage2.nifty.com/naruh/");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
		/*
		url=new URL("http://homepage2.nifty.com/naruh/");
		is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
		*/
		System.out.println("===");
	}
	
	@Test
	public void testAccessLogReq2() throws Throwable{
		System.out.println("===");
//		URL url=new URL("http://homepage2.nifty.com/naruh/");
		URL url=new URL("http://127.0.0.1:1280/admin/admin.vm");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
		/*
		url=new URL("http://homepage2.nifty.com/naruh/");
		is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
		*/
		System.out.println("===");
	}
	
}
