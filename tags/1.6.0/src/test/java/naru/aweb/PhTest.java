package naru.aweb;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.util.Iterator;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.async.store.StoreManager;
import naru.queuelet.test.TestBase;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class PhTest extends TestBase{
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
	
	private Long storeId;
	@Test
	public void test0() throws Throwable{
		callTest("qtest0");
	}
	public void qtest0() throws Throwable{
		System.out.println("I am here.");
	}
}
