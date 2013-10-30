package naru.aweb.filter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import javax.jdo.PersistenceManager;
import javax.jdo.Transaction;

import naru.aweb.config.FilterCategory;
import naru.aweb.config.FilterEntry;
import naru.aweb.util.JdoUtil;

public class Data {
	public static void insert() throws IOException{
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		try{
			tx.begin();
			FilterCategory category=new FilterCategory("source1","category2",false);
			pm.makePersistent(category);
			for(int i=0;i<10;i++){
				FilterEntry entry=new FilterEntry(category,"domain"+i);
				pm.makePersistent(entry);
			}
			FilterEntry entry=FilterEntry.getByKey(category, "domain"+0);
			System.out.println(entry);
			tx.commit();
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
	}
	public static void list() throws IOException{
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		Iterator<FilterCategory> itr=pm.getExtent(FilterCategory.class).iterator();
		while(itr.hasNext()){
			FilterCategory category=itr.next();
			System.out.println(category.getCategory());
		}
		Iterator<FilterEntry> entryItr=pm.getExtent(FilterEntry.class).iterator();
		while(entryItr.hasNext()){
			FilterEntry entry=entryItr.next();
			System.out.println(entry.getFilter());
			System.out.println(entry.getCategory().getCategory());
		}
	}
	
	
	public static void main(String[] args) throws MalformedURLException, IOException{
		Maintenance.addCategorys(new URL("http://test"), "blacklists.tgz");
//		insert();
//		list();
		
	}


}
