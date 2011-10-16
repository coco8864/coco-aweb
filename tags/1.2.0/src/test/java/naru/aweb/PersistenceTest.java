package naru.aweb;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import naru.aweb.config.AccessLog;
import naru.aweb.config.Mapping;
import naru.aweb.util.JdoUtil;
import naru.queuelet.test.TestBase;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class PersistenceTest extends TestBase{
	private static Logger logger=Logger.getLogger(PersistenceTest.class);
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		setupContainer(
				"testEnv.properties",
				"Phantom");
	}
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		stopContainer();
	}
	
//	@Test
	public void testAccessLog() throws Throwable{
		callTest("qtestAccessLog");
	}
	public void qtestAccessLog() throws Throwable{
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query q=pm.newQuery("select from naru.aweb.config.AccessLog");
		Collection<AccessLog> accessLogs=(Collection<AccessLog>)pm.detachCopyAll((Collection)q.execute());
		System.out.println(AccessLog.collectionToJson(accessLogs));
		
		for(AccessLog accessLog:accessLogs){
			System.out.println(accessLog.getRequestLine() +" " +accessLog.getStatusCode());
			System.out.println(accessLog.toJson());
		}
		System.out.println(AccessLog.countOfAccessLog(null));
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
		URL url=new URL("http://127.0.0.1:1280/admin/accessLog.json?from=0&to=2");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
		System.out.println("===");
	}
	@Test
	public void testAccessLogReq2() throws Throwable{
		System.out.println("===");
		URL url=new URL("http://127.0.0.1:1280/admin/accessLog.json?query=destinationType=='S'");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
		System.out.println("===");
	}
	
	@Test
	public void testAccessLogReq3() throws Throwable{
		System.out.println("===");
		URL url=new URL("http://127.0.0.1:1280/admin/accessLog.json?query=requestLine.startsWith(\"GET%20/\")");
		InputStream is=(InputStream)url.getContent();
		readInputStream(is);
		is.close();
		System.out.println("===");
	}
	
	
//	@Test
	public void testMapping() throws Throwable{
		callTest("qtestMapping");
	}
	public void qtestMapping() throws Throwable{
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query q=pm.newQuery("select from naru.aweb.config.Mapping");
		Collection<Mapping> mappings=(Collection<Mapping>)pm.detachCopyAll((Collection)q.execute());
		for(Mapping mapping:mappings){
			mapping.setup();
			System.out.println(mapping.getNotes());
			System.out.println(mapping.toJson());
		}
		System.out.println(AccessLog.countOfAccessLog(null));
	}
	
	
}
