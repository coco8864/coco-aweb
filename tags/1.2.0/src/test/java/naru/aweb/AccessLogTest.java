package naru.aweb;

import java.util.Collection;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

import naru.async.store.StoreManager;
import naru.aweb.config.AccessLog;

import junit.framework.TestCase;

public class AccessLogTest extends TestCase {

	public AccessLogTest(String name) {
		super(name);
	}

	public void xtestSave() {
		PersistenceManagerFactory pmf = JDOHelper
				.getPersistenceManagerFactory("datanucleus.properties");
		PersistenceManager pm = pmf.getPersistenceManager();
		Transaction tx = pm.currentTransaction();
		try {
			tx.begin();
			System.out.println("Persisting products");
			AccessLog accessLog = new AccessLog();
			pm.makePersistent(accessLog);

			tx.commit();
			System.out.println("AccessLog have been persisted");
		} finally {
			if (tx.isActive()) {
				tx.rollback();
			}
			pm.close();
		}
	}

	public void testCount() {
		System.out.println(AccessLog.countOfAccessLog(null));
	}

	public void testQuery() {
		System.out.println(AccessLog.query(
				"select from naru.aweb.config.AccessLog", 0, 1));
	}

	public void testQuery2() {
		System.out.println(AccessLog.query(
				"select from naru.aweb.config.AccessLog", 0, 5));
	}

	public void testQuery3() {
		System.out.println(AccessLog.query(
				"select from naru.aweb.config.AccessLog", 2, 5));
	}
	
	public void testQueryx() {
		Collection<AccessLog> list=(Collection<AccessLog>)AccessLog.query(
				"select from naru.aweb.config.AccessLog", 0, 5);
		for(AccessLog accesslog:list){
			System.out.println(
					":requestHeader:"+accesslog.getRequestHeaderDigest()+
					":requestBody:"+accesslog.getRequestBodyDigest()+
					":responseHeader:"+accesslog.getResponseHeaderDigest()+
					":responseBody:"+accesslog.getResponseBodyDigest());
			System.out.println(
					":requestHeader:"+StoreManager.getStoreLength(accesslog.getRequestHeaderDigest())+
					":requestBody:"+StoreManager.getStoreLength(accesslog.getRequestBodyDigest())+
					":responseHeader:"+StoreManager.getStoreLength(accesslog.getResponseHeaderDigest())+
					":responseBody:"+StoreManager.getStoreLength(accesslog.getResponseBodyDigest()));
		}
	}
	
}
