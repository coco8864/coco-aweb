package naru.aweb.filter;

import static org.junit.Assert.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.aweb.http.HeaderParser;

import org.junit.Test;


public class Cookie {
	
	private Pattern makePatten(String name){
		 Pattern p = Pattern.compile(" ?"+name+" ?= ?([^\\s;]*);? ?");
		 return p;
	}
	@Test
	public void testCookie(){
		HeaderParser header=new HeaderParser();
		String cookieHeader="Website_common_01=k7.co.jp.63941208497096656; NIN=1; RIYOU=4x; OUSU=UgxIaMwUtKwIN3hwh8OiYYcrBNVyVpK3HlrKr15VacKsBFa_bDTDO3x40ronDNDJrMuu1v/uYVZ4n5eAg1in7tJJQhicQHsdDZzU3FB0gWDMyVd0cyW7pQ==; POTO=0x";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		
		String value=header.getAndRemoveCookieHeader("RIYOU");
		assertEquals("4x",value);
		assertEquals("Website_common_01=k7.co.jp.63941208497096656; NIN=1; OUSU=UgxIaMwUtKwIN3hwh8OiYYcrBNVyVpK3HlrKr15VacKsBFa_bDTDO3x40ronDNDJrMuu1v/uYVZ4n5eAg1in7tJJQhicQHsdDZzU3FB0gWDMyVd0cyW7pQ==; POTO=0x",
				header.getHeader(HeaderParser.COOKIE_HEADER));
	}

	@Test
	public void testCookie2(){
		HeaderParser header=new HeaderParser();
		String cookieHeader="Website_common_01=k7.co.jp.63941208497096656; NIN=1; RIYOU=4x; OUSU=UgxIaMwUtKwIN3hwh8OiYYcrBNVyVpK3HlrKr15VacKsBFa_bDTDO3x40ronDNDJrMuu1v/uYVZ4n5eAg1in7tJJQhicQHsdDZzU3FB0gWDMyVd0cyW7pQ==; POTO=0x";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		
		String value=header.getAndRemoveCookieHeader("POTO");
		assertEquals("0x",value);
		assertEquals("Website_common_01=k7.co.jp.63941208497096656; NIN=1; RIYOU=4x; OUSU=UgxIaMwUtKwIN3hwh8OiYYcrBNVyVpK3HlrKr15VacKsBFa_bDTDO3x40ronDNDJrMuu1v/uYVZ4n5eAg1in7tJJQhicQHsdDZzU3FB0gWDMyVd0cyW7pQ==;",
				header.getHeader(HeaderParser.COOKIE_HEADER));
	}
	
	@Test
	public void testCookie3(){
		HeaderParser header=new HeaderParser();
		String cookieHeader="Website_common_01=k7.co.jp.63941208497096656; NIN=1; RIYOU=4x; OUSU=UgxIaMwUtKwIN3hwh8OiYYcrBNVyVpK3HlrKr15VacKsBFa_bDTDO3x40ronDNDJrMuu1v/uYVZ4n5eAg1in7tJJQhicQHsdDZzU3FB0gWDMyVd0cyW7pQ==; POTO=0x";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		
		String value=header.getAndRemoveCookieHeader("Website_common_01");
		assertEquals("k7.co.jp.63941208497096656",value);
		assertEquals("NIN=1; RIYOU=4x; OUSU=UgxIaMwUtKwIN3hwh8OiYYcrBNVyVpK3HlrKr15VacKsBFa_bDTDO3x40ronDNDJrMuu1v/uYVZ4n5eAg1in7tJJQhicQHsdDZzU3FB0gWDMyVd0cyW7pQ==; POTO=0x",
				header.getHeader(HeaderParser.COOKIE_HEADER));
	}
	
	@Test
	public void testCookie4(){
		HeaderParser header=new HeaderParser();
		String cookieHeader="Website_common_01=k7.co.jp.63941208497096656";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		
		String value=header.getAndRemoveCookieHeader("Website_common_01");
		assertEquals("k7.co.jp.63941208497096656",value);
		assertEquals(null,
				header.getHeader(HeaderParser.COOKIE_HEADER));
	}
	@Test
	public void testCookie5(){
		HeaderParser header=new HeaderParser();
		String cookieHeader="Website_common_01=k7.co.jp.63941208497096656";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		cookieHeader="Website_common_01=k7.co.jp.63941208497096656";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		
		String value=header.getAndRemoveCookieHeader("Website_common_01");
		assertEquals("k7.co.jp.63941208497096656",value);
		assertEquals(null,
				header.getHeader(HeaderParser.COOKIE_HEADER));
	}
	@Test
	public void testCookie6(){
		HeaderParser header=new HeaderParser();
		String cookieHeader="Website_common_01=k7.co.jp.63941208497096656";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		cookieHeader="Website_common_02=k7.co.jp.63941208497096656";
		header.addHeader(HeaderParser.COOKIE_HEADER, cookieHeader);
		
		String value=header.getAndRemoveCookieHeader("Website_common_01");
		assertEquals("k7.co.jp.63941208497096656",value);
		assertEquals("Website_common_02=k7.co.jp.63941208497096656",
				header.getHeader(HeaderParser.COOKIE_HEADER));
	}
	
	
}
