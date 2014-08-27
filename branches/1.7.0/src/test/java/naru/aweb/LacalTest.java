package naru.aweb;

import java.util.Locale;

public class LacalTest {

	private static void l(String l){
		Locale loc=new Locale(l);
		System.out.println("loc.toString():"+loc.toString());
		System.out.println("loc.getLanguage():"+loc.getLanguage());
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		l("a_b");
		l("a-b");
		l("ab");
		l("zh_CN");
		l("zh-CN");
		l("zh");
		
		String [] s="a_b_c_d".split("_", 3);
		System.out.println(s.length);
		System.out.println(s[0]);
		System.out.println(s[1]);
		System.out.println(s[2]);
	}

}
