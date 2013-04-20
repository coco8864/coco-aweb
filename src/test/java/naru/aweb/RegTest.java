package naru.aweb;

import static org.junit.Assert.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class RegTest {
	private static String TYPE1="<?xml version=\"1.0\" encoding=\"EUC-JP\"?>";
	private static String TYPE2="<meta http-equiv=\"content-type\" content=\"text/html; charset=EUC-JP\" />";
	
	@Test
	public void test1() {
		String regex="encoding=\"([^\"\\s]*)\"";
		Pattern p = Pattern.compile(regex);
		Matcher m=p.matcher(TYPE1);
		if(m.find()){
			System.out.println(m.group(1));
		}else{
			System.out.println("not find");
		}
	}

	@Test
	public void test2() {
		String regex="charset=([^\"'\\s]*)";
		Pattern p = Pattern.compile(regex);
		Matcher m=p.matcher(TYPE2);
		if(m.find()){
			System.out.println(m.group(1));
		}else{
			System.out.println("not find");
		}
	}
	
	@Test
	public void test3() {
		String regex="(?:encoding=\"([^\"\\s]*)\")|(?:charset=([^\"'\\s]*))";
		Pattern p = Pattern.compile(regex);
		Matcher m=p.matcher(TYPE2);
		if(m.find()){
			for(int i=0;i<=m.groupCount();i++)
				System.out.println("i:" + i +":" +m.group(i));
		}else{
			System.out.println("not find");
		}
	}
	
	
}
