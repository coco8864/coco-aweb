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

public class ScenarioTest extends TestBase{
	private static Logger logger=Logger.getLogger(ScenarioTest.class);
	
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
	public void testScenario() throws Throwable{
		callTest("qtestScenario");
	}
	public void qtestScenario() throws Throwable{
		Scenario scenario=new Scenario();
		AccessLog accessLog1=AccessLog.getById(722L);
		AccessLog accessLog2=AccessLog.getById(734L);
		scenario.setup(new AccessLog[]{accessLog1,accessLog2},"test",1,10,
				false,true,true,true);
		synchronized(scenario){
			scenario.start();
			while(scenario.getRunnningBrowserCount()>0){
				scenario.wait();
			}
		}
		System.out.println("end of test");
	}
	
	
	@Test
	public void testScenario2() throws Throwable{
		callTest("qtestScenario2");
	}
	public void qtestScenario2() throws Throwable{
		Scenario scenario=new Scenario();
		AccessLog accessLog1=AccessLog.getById(722L);
		AccessLog accessLog2=AccessLog.getById(734L);
		scenario.setup(new AccessLog[]{accessLog1,accessLog2},"test",2,4,
				false,true,true,true);
		synchronized(scenario){
			scenario.start();
			while(scenario.getRunnningBrowserCount()>0){
				scenario.wait();
			}
		}
		System.out.println("end of test");
	}
	
	
	@Test
	public void testScenario3() throws Throwable{
		callTest("qtestScenario3");
	}
	public void qtestScenario3() throws Throwable{
		Scenario scenario=new Scenario();
		AccessLog accessLog1=AccessLog.getById(3L);
//		AccessLog accessLog2=AccessLog.getById(734L);
		scenario.setup(new AccessLog[]{accessLog1},"test",8,100,
				true,true,true,true);
		long start;
		synchronized(scenario){
			start=System.currentTimeMillis();
			scenario.start();
			while(scenario.getRunnningBrowserCount()>0){
				scenario.wait();
			}
		}
		System.out.println("end of test.time:"+(System.currentTimeMillis()-start));
	}
	
	
}
