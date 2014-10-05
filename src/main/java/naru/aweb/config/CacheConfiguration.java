package naru.aweb.config;

import java.util.HashMap;
import java.util.WeakHashMap;

import javax.sql.DataSource;

import org.apache.commons.configuration.DatabaseConfiguration;
import org.apache.log4j.Logger;

public class CacheConfiguration extends DatabaseConfiguration {
	private static Logger logger = Logger.getLogger(CacheConfiguration.class);
//    private WeakHashMap<String,Object> cache = new WeakHashMap<String,Object>();
    private HashMap<String,Object> cache = new HashMap<String,Object>();
	public CacheConfiguration(DataSource datasource, String table,
			String keyColumn, String valueColumn) {
		super(datasource, table, keyColumn, valueColumn);
	}
	
	@Override
	public Object getProperty(String key) {
		Object value = cache.get(key);
		if (value != null) {
			return value;
		}
		value = super.getProperty(key);
		cache.put(key, value);
		return value;
	}
	
	@Override
	public void setProperty(String key,Object value){
		if(logger.isDebugEnabled()){logger.debug("key:"+key+":value:"+value);};
		if(value!=null){
			value=value.toString();
		}
		super.setProperty(key, value);
		cache.put(key, value);
	}
}
