package naru.aweb.link.api;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

/**
 * ����M�p�̃��b�Z�[�W�I�u�W�F�N�g<br/>
 * JSONObject�Ɠ��lMap�`���Ńf�[�^��ێ�����<br/>
 * Blob�ADate�I�u�W�F�N�g��value�Ɋ܂߂邱�Ƃ��ł���<br/>
 * �������܂܂Ȃ��ꍇ�́AJSONObject�ő���M�f�[�^�Ƃ��ė��p�ł���<br/>
 * @author naru
 *
 */
public class LinkMsg extends PoolBase implements Map{
	private Map root;
	
	public static LinkMsg create(){
		return wrap(null);
	}
	public static LinkMsg wrap(Map root){
		LinkMsg paData=(LinkMsg)PoolManager.getInstance(LinkMsg.class);
		if(root==null){
			paData.root=new HashMap();
		}else{
			paData.root=new HashMap(root);
		}
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
	public Integer getInt(String key){
		Object value=root.get(key);
		if(value instanceof Long){
			return ((Long)value).intValue();
		}else if(value instanceof Integer){
			return (Integer)value;
		}
		return null;
	}
	@Override
	public void recycle() {
		if(root==null){
			return;
		}
		for(Object value:values()){
			if(value instanceof LinkMsg){
				((LinkMsg)value).unref();
			}
		}
		root=null;
	}

	public Long getLong(String key){
		Object value=root.get(key);
		if(value instanceof Long){
			return (Long)value;
		}else if(value instanceof Integer){
			return ((Integer)value).longValue();
		}
		return null;
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
	public LinkMsg getMap(String key){
		Object map=root.get(key);
		if(map instanceof LinkMsg){
			return (LinkMsg)map;
		}else if(map instanceof Map){
			LinkMsg value=wrap((Map)map);
			root.put(key, value);
			return value;
		}
		return null;
	}
	public List getList(String key){
		return (List)root.get(key); 
	}

}
