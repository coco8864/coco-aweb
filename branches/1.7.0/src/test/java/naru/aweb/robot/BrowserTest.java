package naru.aweb.robot;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import naru.aweb.config.AccessLog;
import naru.aweb.mapping.Mapping;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.JdoUtil;
import naru.queuelet.test.TestBase;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class BrowserTest extends TestBase{
	private static Logger logger=Logger.getLogger(BrowserTest.class);
	
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
	
	@Test
	public void testBrowser() throws Throwable{
		callTest("qtestBrowser");
	}
	public void qtestBrowser() throws Throwable{
		Browser browser=new Browser();
		browser.setup(new URL[]{new URL("http://homepage2.nifty.com/naruh/"),
				new URL("https://login.yahoo.co.jp/config/login")},false);
		synchronized(browser){
			browser.start();
			while(browser.isProcessing()){
				browser.wait();
			}
		}
		browser.cleanup();
	}
	
	@Test
	public void testBrowserLoop() throws Throwable{
		callTest("qtestBrowserLoop");
	}
	public void qtestBrowserLoop() throws Throwable{
		Browser browser=new Browser();
		browser.setup(new URL[]{new URL("http://homepage2.nifty.com/naruh/"),
				new URL("https://login.yahoo.co.jp/config/login")},true);
		
		for(int i=0;i<10;i++){
			synchronized(browser){
				browser.start();
				while(browser.isProcessing()){
					browser.wait();
				}
			}
		}
		browser.cleanup();
	}
	
	
	//AccessLog id
	//15:https://login.yahoo.co.jp/config/login
	//3:http://homepage2.nifty.com/naruh/
	
	@Test
	public void testAccessLog() throws Throwable{
		callTest("qtestAccessLog");
	}
	public void qtestAccessLog() throws Throwable{
		AccessLog accessLog1=AccessLog.getById(722L);
		AccessLog accessLog2=AccessLog.getById(734L);
		Browser browser=new Browser();
		browser.setup(new AccessLog[]{accessLog1,accessLog2}, false,false,false);
		synchronized(browser){
			browser.start();
			while(browser.isProcessing()){
				browser.wait();
			}
		}
		browser.cleanup();
	}
	
	@Test
	public void testAccessLog2() throws Throwable{
		callTest("qtestAccessLog2");
	}
	public void qtestAccessLog2() throws Throwable{
		AccessLog accessLog1=AccessLog.getById(722L);
		AccessLog accessLog2=AccessLog.getById(734L);
		Browser browser=new Browser();
		browser.setup(new AccessLog[]{accessLog1,accessLog2}, false,true,true);
		
		for(int i=0;i<10;i++){
			synchronized(browser){
				browser.start();
				while(browser.isProcessing()){
					browser.wait();
				}
			}
		}
		
		browser.cleanup();
	}
	
	
}
