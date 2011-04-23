package naru.aweb.filter;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.aweb.http.HeaderParser;

import org.junit.Test;


public class Filter {
	private Pattern ipPatten = Pattern.compile("^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$");
	private Set<String> specialDomain=new HashSet<String>();
	public Filter(){
//		"COM","EDU", "NET", "ORG", "GOV", "MIL", "INT"
		specialDomain.add("com");
		specialDomain.add("edu");
		specialDomain.add("net");
		specialDomain.add("org");
		specialDomain.add("gov");
		specialDomain.add("mil");
		specialDomain.add("int");
	}
	
	private void filters(String filter,Set<String> result){
		Matcher matcher=null;
		synchronized(ipPatten){
			matcher=ipPatten.matcher(filter);
		}
		if(matcher.matches()){
			//ipadress‚Ìê‡•ª‰ð‚µ‚È‚¢
			result.add(filter);
			return;
		}
		String[] parts=filter.split("\\.");
		StringBuilder sb=new StringBuilder();
		for(int i=parts.length-1;i>=0;i--){
			sb.insert(0,parts[i]);
			result.add(sb.toString());
			sb.insert(0,".");
		}
		result.remove("com");
		result.remove("edu");
		result.remove("net");
		result.remove("org");
		result.remove("gov");
		result.remove("mil");
		result.remove("int");
	}
	
	@Test
	public void testFilter(){
		Matcher matcher=ipPatten.matcher("123.23.23.34");
		System.out.println(matcher.matches());
	}

	@Test
	public void testFilter2(){
		HashSet result=new HashSet();
		filters("a.b.com",result);
		System.out.println(result);
	}
	
	
}
