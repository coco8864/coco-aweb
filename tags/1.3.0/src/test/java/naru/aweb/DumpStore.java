package naru.aweb;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import naru.async.store.Store;
import naru.async.store.StoreManager;
import naru.async.store.StoreStream;
import naru.queuelet.test.TestBase;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DumpStore extends TestBase{
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
	
	private Set<Long> storeIds=new HashSet<Long>();
	
	@Test
	public void testSaveToFile() throws Throwable{
		callTest("qtestSaveToFile");
	}
	public void qtestSaveToFile() throws Throwable{
		Set ids=StoreManager.listPersistenceStoreId();
		Iterator<Long> itr=ids.iterator();
		while(itr.hasNext()){
			long storeId=itr.next();
			System.out.println("storeId:"+storeId +
			":"+StoreManager.getStoreLength(storeId)+
			":"+StoreManager.getStoreDigest(storeId)
			);
		}
		itr=ids.iterator();
		while(itr.hasNext()){
			long storeId=itr.next();
			String fileName="F:\\googlesvn\\naruaweb\\aweb\\store\\dump\\store"+ storeId + ".dat";
			OutputStream os=new FileOutputStream(fileName);
			StoreStream.storeToStream(storeId, os);
			os.close();
		}
	}
	
}
