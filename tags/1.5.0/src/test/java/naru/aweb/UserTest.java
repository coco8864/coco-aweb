package naru.aweb;

import java.util.Collection;


import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import naru.aweb.config.User;
import naru.queuelet.test.TestBase;

public class UserTest extends TestBase {

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
	public void testCreateUpdate() throws Throwable {
		callTest("testCreateUpdate0");
	}
	public void testCreateUpdate0() {
		User user=new User();
		user.setLoginId("test");
		user.setFootSize(24);
		System.out.println(user.toJson());
		user.save();
		user.setFootSize(25);
		System.out.println(user.toJson());
	}
	
	
	@Test
	public void testSameId() throws Throwable {
		callTest("testSameId0");
	}
	public void testSameId0() {
		User user=new User();
		user.setLoginId("test");
		user.setFootSize(26);
		try{
			user.save();
//			fail();
		}catch(Exception e){
		}
		System.out.println(user.toJson());
	}

	@Test
	public void testQuery() throws Throwable {
		callTest("testQuery0");
	}
	public void testQuery0() {
		Collection<User> c=User.query(null, -1, 0, "id");
		System.out.println(User.collectionToJson(c));
		for(User user:c){
			User user2=User.getByLoginId(user.getLoginId());
			user2.setFootSize(24);
			user2.update();
		}
		
	}
	
}
