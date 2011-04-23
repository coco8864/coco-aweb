package naru.aweb.util;

import java.util.Map;

import javax.jdo.Extent;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.jdo.Transaction;

import org.apache.log4j.Logger;

public class JdoUtil {
	private static Logger logger = Logger.getLogger(JdoUtil.class);
	private static PersistenceManagerFactory pmf;
	private static ThreadLocal<Transaction> transactions=new ThreadLocal<Transaction>();
	private static ThreadLocal<PersistenceManager> persistenceManagers=new ThreadLocal<PersistenceManager>();
	
	public static void initPersistenceManagerFactory(Map properties){
        pmf = JDOHelper.getPersistenceManagerFactory(properties);
	}
	
	public static void initPersistenceManagerFactory(String resource){
        pmf = JDOHelper.getPersistenceManagerFactory(resource);
	}
	
	private static PersistenceManagerFactory getPersistenceManagerFactory(){
		if(pmf==null){
	        pmf = JDOHelper.getPersistenceManagerFactory("datanucleus.properties");
		}
		return pmf;
	}
	
	public static PersistenceManager getPersistenceManager(){
		PersistenceManager pm=persistenceManagers.get();
		if(pm!=null){
			return pm;
		}
		PersistenceManagerFactory pmf=getPersistenceManagerFactory();
		pm=pmf.getPersistenceManager();
        persistenceManagers.set(pm);
		return pm;
	}
	
	public static void resetPersistenceManager(){
        persistenceManagers.set(null);
	}
	
	public static void commitCurrentPm(){
		PersistenceManager pm=persistenceManagers.get();
		if(pm==null){
			return;
		}
		Transaction tx=pm.currentTransaction();
		try{
			tx.commit();
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
	}
	
	public static void rollbackIfNeedCurrentPm(){
		PersistenceManager pm=persistenceManagers.get();
		if(pm==null){
			return;
		}
		Transaction tx=pm.currentTransaction();
		if(tx.isActive()){
			tx.rollback();
		}
	}
	
	
	public static void save(Object obj){
        PersistenceManagerFactory pmf = getPersistenceManagerFactory();
		PersistenceManager pm = pmf.getPersistenceManager();
        try {
			pm.currentTransaction().begin();
			pm.makePersistent(obj);
			pm.currentTransaction().commit();
			pm.makeTransient(obj);
		} finally {
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
			pm.close();
		}
	}
	
	public static PersistenceManager begin(){
        PersistenceManagerFactory pmf = getPersistenceManagerFactory();
		PersistenceManager pm=pmf.getPersistenceManager();
        persistenceManagers.set(pm);
        Transaction tx=pm.currentTransaction();
        tx.begin();
        transactions.set(tx);
        return pm;
	}
	
	public static void cleanup(Class clazz){
		PersistenceManager pm=getPersistenceManager();
		try {
			pm.currentTransaction().begin();
			Query query=pm.newQuery(clazz);
			query.deletePersistentAll();
			pm.currentTransaction().commit();
		} finally{
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
	}
	
	public static PersistenceManager currentPm(){
		return currentPm(true);
	}
	
	public static PersistenceManager currentPm(boolean isForce){
		PersistenceManager pm=persistenceManagers.get();
		if(pm!=null){
			return pm;
		}else if(isForce){
			return begin();
		}
		return null;
	}
	
	public static void commit(){
        Transaction tx=transactions.get();
        if(tx==null){
        	return;//気にしない
//        	throw new RuntimeException("fail to commit no transaction");
        }
        tx.commit();
        transactions.set(null);
        close();
	}
	
	public static void close(){
        Transaction tx=transactions.get();
        if (tx!=null && tx.isActive()){
            tx.rollback();
        }
        transactions.set(null);
        PersistenceManager pm=persistenceManagers.get();
        if(pm!=null){
            pm.close();
            persistenceManagers.set(null);
        }
	}
	
	public static void update(Object obj){
		PersistenceManager pm=getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		try{
			tx.begin();
			pm.makePersistent(obj);
			tx.commit();
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
	}
	
	
	public static void insert(Object obj){
		PersistenceManager pm=getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		try{
			tx.begin();
			pm.makePersistent(obj);
			tx.commit();
//			pm.makeTransient(obj);//再利用するために必要?
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
	}

	public static long delete(String queryString){
		PersistenceManager pm=getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		long count=0;
		try{
			tx.begin();
			Query q=pm.newQuery(queryString);
			count=q.deletePersistentAll();
			tx.commit();
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
		return count;
	}
	
	
}
