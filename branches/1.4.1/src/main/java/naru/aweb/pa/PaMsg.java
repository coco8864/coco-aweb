package naru.aweb.pa;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

public class PaMsg extends PoolBase implements Map{
	private Map root;
	
	public static PaMsg create(){
		return wrap(new HashMap());
	}
	
	public static PaMsg wrap(Map root){
		PaMsg paData=(PaMsg)PoolManager.getInstance(PaMsg.class);
		paData.root=root;
		return paData;
	}

	public void clear() {
		root.clear();
	}

	public boolean containsKey(Object key) {
		return root.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return root.containsValue(value);
	}

	public Set entrySet() {
		return root.entrySet();
	}

	public boolean equals(Object o) {
		return root.equals(o);
	}

	public Object get(Object key) {
		return root.get(key);
	}

	public boolean isEmpty() {
		return root.isEmpty();
	}

	public Set keySet() {
		return root.keySet();
	}

	public Object put(Object key, Object value) {
		return root.put(key, value);
	}

	public void putAll(Map m) {
		root.putAll(m);
	}

	public Object remove(Object key) {
		return root.remove(key);
	}

	public int size() {
		return root.size();
	}

	public Collection values() {
		return root.values();
	}
	
	public boolean getBoolean(String key){
		Object value=root.get(key);
		if(value==null){
			return false;
		}
		return (Boolean)value; 
	}
	public int getInt(String key){
		Object value=root.get(key);
		if(value instanceof Long){
			return ((Long)value).intValue();
		}else if(value instanceof Integer){
			return (Integer)value;
		}
		throw new RuntimeException("fail to getLong");
	}
	@Override
	public void recycle() {
		if(root==null){
			return;
		}
		for(Object value:values()){
			if(value instanceof PaMsg){
				((PaMsg)value).unref();
			}
		}
		root=null;
	}

	public long getLong(String key){
		Object value=root.get(key);
		if(value instanceof Long){
			return (Long)value;
		}else if(value instanceof Integer){
			return (Integer)value;
		}
		throw new RuntimeException("fail to getLong");
	}
	public double getDouble(String key){
		return (Double)root.get(key); 
	}
	public String getString(String key){
		return (String)root.get(key); 
	}
	public String optString(String key,String def){
		String value=getString(key);
		if(value!=null){
			return value;
		}
		return def;
	}
	
	public Blob getBlob(String key){
		return (Blob)root.get(key); 
	}
	public Date getDate(String key){
		return (Date)root.get(key); 
	}
	public PaMsg getMap(String key){
		Map map=(Map)root.get(key);
		if(map instanceof PaMsg){
			return (PaMsg)map;
		}else{
			PaMsg value=wrap(map);
			root.put(key, value);
			return value;
		}
	}
	public List getList(String key){
		return (List)root.get(key); 
	}

}
