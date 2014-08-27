package naru.aweb;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Iterator;

import org.junit.Test;

public class HashMapTest {

	@Test
	public void test() {
		HashMap map=new HashMap();
		map.put(1,1);
		map.put(2,2);
		map.put(3,3);
		map.put(4,4);
		
		System.out.println(map.size());
		Iterator itr=map.values().iterator();
		if(itr.hasNext()){
			Object o=itr.next();
			itr.remove();
		}
		
		System.out.println(map.size());
		
		
	}

}
