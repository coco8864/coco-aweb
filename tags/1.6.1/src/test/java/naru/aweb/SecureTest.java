package naru.aweb;

import java.io.IOException;
import java.io.InputStream;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import javax.net.ssl.SSLContext;

import naru.aweb.config.AccessLog;
import naru.aweb.secure.SslContextPool;

import junit.framework.TestCase;

public class SecureTest extends TestCase {

	public SecureTest(String name) {
		super(name);
	}
	
	public void test0() {
		SslContextPool pool=new SslContextPool(null);
		SSLContext sslContext=pool.getSSLContext("c.com");
		assertNotNull(sslContext);
	}

}
