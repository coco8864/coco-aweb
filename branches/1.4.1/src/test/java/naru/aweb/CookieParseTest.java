package naru.aweb;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.aweb.http.Cookie;

import org.junit.Test;


public class CookieParseTest {
	//Cookie: SC_Cut=89425559; ebNewBandWidth_.www.asahi.com=1891%3A1283087873183; __utma=261975709.1974198372570266000.1240874164.1292505393.1293265236.233; IMPASEG=S0%3D10001/S1%3D11136/S2%3D10417/S3%3D10319; __utma=123950411.1838177944.1258991685.1283072875.1293265245.45; __utmz=261975709.1279502743.185.3.utmcsr=127.0.0.1:1280|utmccn=(referral)|utmcmd=referral|utmcct=/pub/phPortal.html; ebNewBandWidth_.ph.www.asahi.com=3649%3A1277593240932; s_nr=1292049534791-New; __utmc=261975709; s_sq=%5B%5BB%5D%5D; s_cc=true; __utmb=261975709.1.10.1293265236; __utmb=123950411.1.10.1293265245; __utmc=123950411; __utmz=123950411.1293265245.45.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)

	@Test
	public void test1(){
		String trget=" SC_Cut=89425559; ebNewBandWidth_.www.asahi.com=1891%3A1283087873183; __utma=261975709.1974198372570266000.1240874164.1292505393.1293265236.233; IMPASEG=S0%3D10001/S1%3D11136/S2%3D10417/S3%3D10319; __utma=123950411.1838177944.1258991685.1283072875.1293265245.45; __utmz=261975709.1279502743.185.3.utmcsr=127.0.0.1:1280|utmccn=(referral)|utmcmd=referral|utmcct=/pub/phPortal.html; ebNewBandWidth_.ph.www.asahi.com=3649%3A1277593240932; s_nr=1292049534791-New; __utmc=261975709; s_sq=%5B%5BB%5D%5D; s_cc=true; __utmb=261975709.1.10.1293265236; __utmb=123950411.1.10.1293265245; __utmc=123950411; __utmz=123950411.1293265245.45.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)";
		StringBuilder sb=new StringBuilder();
		String value=Cookie.parseHeader(trget,"SC_Cut",sb);
		System.out.println("value:"+value);
		System.out.println("rest:"+sb.toString());
	}
	@Test
	public void test2(){
		String trget=" SC_Cut=89425559; ebNewBandWidth_.www.asahi.com=1891%3A1283087873183; __utma=261975709.1974198372570266000.1240874164.1292505393.1293265236.233; IMPASEG=S0%3D10001/S1%3D11136/S2%3D10417/S3%3D10319; __utma=123950411.1838177944.1258991685.1283072875.1293265245.45; __utmz=261975709.1279502743.185.3.utmcsr=127.0.0.1:1280|utmccn=(referral)|utmcmd=referral|utmcct=/pub/phPortal.html; ebNewBandWidth_.ph.www.asahi.com=3649%3A1277593240932; s_nr=1292049534791-New; __utmc=261975709; s_sq=%5B%5BB%5D%5D; s_cc=true; __utmb=261975709.1.10.1293265236; __utmb=123950411.1.10.1293265245; __utmc=123950411; __utmz=123950411.1293265245.45.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)";
		StringBuilder sb=new StringBuilder();
		String value=Cookie.parseHeader(trget,"ebNewBandWidth_.www.asahi.com",sb);
		System.out.println("value:"+value);
		System.out.println("rest:"+sb.toString());
	}
	@Test
	public void test3(){
		String trget=" SC_Cut=89425559; ebNewBandWidth_.www.asahi.com=1891%3A1283087873183; __utma=261975709.1974198372570266000.1240874164.1292505393.1293265236.233; IMPASEG=S0%3D10001/S1%3D11136/S2%3D10417/S3%3D10319; __utma=123950411.1838177944.1258991685.1283072875.1293265245.45; __utmz=261975709.1279502743.185.3.utmcsr=127.0.0.1:1280|utmccn=(referral)|utmcmd=referral|utmcct=/pub/phPortal.html; ebNewBandWidth_.ph.www.asahi.com=3649%3A1277593240932; s_nr=1292049534791-New; __utmc=261975709; s_sq=%5B%5BB%5D%5D; s_cc=true; __utmb=261975709.1.10.1293265236; __utmb=123950411.1.10.1293265245; __utmc=123950411; __utmz=123950411.1293265245.45.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)";
		StringBuilder sb=new StringBuilder();
		String value=Cookie.parseHeader(trget,"__utmz",sb);
		System.out.println("value:"+value);
		System.out.println("rest:"+sb.toString());
	}
	@Test
	public void test4(){
		String trget="phSessionId=e4aa11c0adfecc90282409041f64fa34";
		StringBuilder sb=new StringBuilder();
		String value=Cookie.parseHeader(trget,"phSessionId",sb);
		System.out.println("value:"+value);
		System.out.println("rest:"+sb.toString());
	}

}
