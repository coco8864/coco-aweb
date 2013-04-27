package naru.aweb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Properties;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.jdo.Transaction;

import naru.aweb.config.AccessLog;
import naru.aweb.config.Mapping;
import naru.aweb.util.JdoUtil;

import junit.framework.TestCase;

public class MappingTest extends TestCase {

	public MappingTest(String name) {
		super(name);
		JdoUtil.initPersistenceManagerFactory("testDatanucleus.properties");
	}

	public void xtestSave() {
		PersistenceManager pm=JdoUtil.currentPm();
		Transaction tx = pm.currentTransaction();
		try {
			tx.begin();
			System.out.println("Persisting products");
			Mapping mapping = new Mapping();
			mapping.setSourceType(Mapping.SourceType.WS);
			pm.makePersistent(mapping);

			tx.commit();
			System.out.println("Mapping have been persisted");
		} finally {
			if (tx.isActive()) {
				tx.rollback();
			}
			pm.close();
		}
	}

	public void testQuery() {
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		String query="select from naru.aweb.config.Mapping";
		Query q=pm.newQuery(query);
		q.setRange(0, 100);
		Collection col=(Collection)q.execute();
		for(Object o:col){
			Mapping m=(Mapping)o;
			System.out.println(m.toJson());
		}
	}
	
	public void testJson(){
		String jsonString="{\"destinationPath\":\"/\",\"destinationServer\":\"naru.aweb.admin.AdminHandler\",\"destinationType\":\"HANDLER\",\"enabled\":true,\"id\":5,\"notes\":\"admin web\",\"options\":\"{}\",\"realHostName\":\"\",\"secureType\":\"SSL\",\"sourcePath\":\"/admin\",\"sourceServer\":\"\",\"sourceType\":\"WEB\"}";
		Mapping m=Mapping.fromJson(jsonString);
		System.out.println(m.toJson());
		m.setId(6l);
		m.save();
		System.out.println("----");
		testQuery();
	}
	
	public void testGetById(){
		Mapping m=Mapping.getById(6l);
		System.out.println(m.toJson());
		m.delete();
		System.out.println("----");
		testQuery();
	}
	
	public void testInitProp() throws IOException{
		InputStream is=Mapping.class.getResourceAsStream("MappingIni.properties");
		Properties prop=new Properties();
		prop.load(is);
		System.out.println(prop.get("1"));
		Mapping m=Mapping.fromJson(prop.getProperty("1"));
		System.out.println(m.toJson());
	}
	
	public void testCleanup() throws IOException{
//		Mapping.cleanup();
		System.out.println("----");
		testQuery();
	}
	
	
	
	
	
}
