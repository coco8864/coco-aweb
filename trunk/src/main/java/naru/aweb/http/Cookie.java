package naru.aweb.http;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.pool.PoolBase;

public class Cookie extends PoolBase{
//    private final static DateFormat cookieFormat=new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.US);
    private static DateFormat cookieFormat=null;
    private final static String ancientDate=formatCookieDate(new Date(10000));
    private static String formatCookieDate(Date date){
    	if(cookieFormat==null){
    		cookieFormat=new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.US);
    		cookieFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    	}
    	synchronized(cookieFormat){
    		return cookieFormat.format(date);
    	}
    }
    
	public static String getValue(List cookies,String key){
		return getValueAndEditCookies(cookies,key,null);
	}
	
	/* phSessionIdからsessionIdを取得するとともに、phSessionIdをcookieヘッダから削除する*/
	public static String getValueAndEditCookies(List cookies,String key,List<String> restCookies){
		if(cookies==null){
			return null;
		}
		Iterator itr=cookies.iterator();
		String value=null;
		StringBuilder sb=null;
		if(restCookies!=null){
			sb=new StringBuilder();
		}
		while(itr.hasNext()){
			String cookie=(String)itr.next();
			value=Cookie.parseHeader(cookie, key,sb);
			if(sb!=null && sb.length()!=0){//sessionIdを削った結果残りがなくなったらヘッダじゃない
				restCookies.add(sb.toString());
				sb.setLength(0);
			}
		}
		return value;
	}

    //Cookie: SC_Cut=89425559; ebNewBandWidth_.www.asahi.com=1891%3A1283087873183; __utma=261975709.1974198372570266000.1240874164.1292505393.1293265236.233; IMPASEG=S0%3D10001/S1%3D11136/S2%3D10417/S3%3D10319; __utma=123950411.1838177944.1258991685.1283072875.1293265245.45; __utmz=261975709.1279502743.185.3.utmcsr=127.0.0.1:1280|utmccn=(referral)|utmcmd=referral|utmcct=/pub/phPortal.html; ebNewBandWidth_.ph.www.asahi.com=3649%3A1277593240932; s_nr=1292049534791-New; __utmc=261975709; s_sq=%5B%5BB%5D%5D; s_cc=true; __utmb=261975709.1.10.1293265236; __utmb=123950411.1.10.1293265245; __utmc=123950411; __utmz=123950411.1293265245.45.1.utmcsr=(direct)|utmccn=(direct)|utmcmd=(none)
//	private static final Pattern KEY_VALUE_PATTERN=Pattern.compile("\\s*([^\\s=]*)\\s*=\\s*([^\\s;]*)\\s*[;|$]");
	private static final Pattern KEY_VALUE_PATTERN=Pattern.compile("\\s*([^\\s=]*)\\s*=\\s*([^\\s;]*)\\s*[;]?");
    public static String parseHeader(String cookie,String key,StringBuilder restOfCookie){
    	Matcher matcher=null;
    	synchronized(KEY_VALUE_PATTERN){
    		matcher=KEY_VALUE_PATTERN.matcher(cookie);
    	}
    	String value=null;
    	while(matcher.find()){
    		String k=matcher.group(1);
    		if(!key.equals(k)){
    			if(restOfCookie!=null){
    				restOfCookie.append(matcher.group(0));
    			}
    			continue;
    		}
    		if(value==null){//同一のキーがあった場合先頭のvalueを採用、cookie path最長一致仕様より
    			value=matcher.group(2);
    		}
    	}
    	return value;
    }
    
    public static String formatSetCookieHeader(String name,String value,String domain,String path){
    	return formatSetCookieHeader(name,value,domain,path,-1,false);
    }
    
    public static String formatSetCookieHeader(String name,String value,String domain,String path,int maxAge,boolean isSecure){
		StringBuilder buf=new StringBuilder();
		
		// this part is the same for all cookies
		buf.append(name);
		buf.append("=");
		buf.append(value);
		if (domain != null) {
			buf.append("; Domain=");
			buf.append(domain);
		}
		if (maxAge >= 0) {
			buf.append("; Expires=");
			// Wdy, DD-Mon-YY HH:MM:SS GMT ( Expires netscape format )
			if (maxAge == 0){
				buf.append(ancientDate);
			}else{
				Date expiredate=new Date(System.currentTimeMillis()+(maxAge * 1000L));
				buf.append(formatCookieDate(expiredate));
			}
		}

		// Path=path
		if (path != null) {
			buf.append("; Path=");
			buf.append(path);
		}
		// Secure
		if (isSecure) {
			buf.append("; Secure");
		}
		return buf.toString();
    }
    
	private String name;
	private String value;
	private String domain;
	private String path;
	private boolean isSecure=false;
	private int maxAge=-1;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isSecure() {
		return isSecure;
	}

	public void setSecure(boolean isSecure) {
		this.isSecure = isSecure;
	}

	public int getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(int maxAge) {
		this.maxAge = maxAge;
	}

	public String toSetCookieHeader() {
		return formatSetCookieHeader(name, value, domain, path, maxAge, isSecure);
	}

}
