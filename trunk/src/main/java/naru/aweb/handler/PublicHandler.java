package naru.aweb.handler;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;

import org.apache.log4j.Logger;

public class PublicHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(PublicHandler.class);
	private static Config config=Config.getConfig();
//	private static Authenticator authenticator=Setting.getAuthenticator();
	
	//[空白かも]key[空白かも]=[空白かも]value[空白かも](;もしくは文末)　形式の正規表現
	//'"'で括られる可能性もあるらしいが正規表現では難しい
	private static String PH_JS="/js/ph.js";
	private static String PAC_PAGE="/proxy.pac";
	private static String COOKIE_KEY="PHANTOM_PAC";
	private static Pattern COOKIE_PATTERN=Pattern.compile(" *"+ COOKIE_KEY +" *= *(\\S*)(;|\\z)");
	
    private String stripQuote( String value ){
    	if (((value.startsWith("\"")) && (value.endsWith("\""))) ||
    			((value.startsWith("'") && (value.endsWith("'"))))) {
    		try {
    			return value.substring(1,value.length()-1);
    		} catch (Exception ex) { 
    		}
    	}
    	return value;
    }  
	
	//Cookie: Website_common_01=k7.co.jp.63941208497096656; NIN=1; RIYOU=4; OUSU=UgxIaMwUtKwIN3hwh8OiYYcrBNVyVpK3HlrKr15VacKsBFa_bDTDO3x40ronDNDJrMuu1v/uYVZ4n5eAg1in7tJJQhicQHsdDZzU3FB0gWDMyVd0cyW7pQ==; POTO=0
	//Set-Cookie: JSESSIONID=54KAU74VEVVUM8AVE7NJKNIIJ08114SFEH6VTFT3IH12CJU17R7RMRDEIKDG20001G000000.Naru001_001
	private String getCookieAuth(HeaderParser requestHeader) {
		String cookieHeader=requestHeader.getHeader(HeaderParser.COOKIE_HEADER);
		if(cookieHeader==null){
			return null;
		}
		Matcher matcher;
		synchronized(COOKIE_PATTERN){
			matcher = COOKIE_PATTERN.matcher(cookieHeader);
		}
		if(matcher.find()==false){
			return null;
		}
		String cookieAuth=matcher.group(1);
		//"や'で括られている場合は、削除する
		return stripQuote(cookieAuth);
	}
	
	//１年間有効とする
	private static long MAX_AGE=(365l*24l*60l*60l*1000l);
	//param2=GHIJKL; expires=Mon, 31-Dec-2001 23:59:59 GMT; path=/
	private String createSetCookieHeader(String loginId,String path){
		StringBuilder sb=new StringBuilder();
		sb.append(COOKIE_KEY);
		sb.append("=");
		sb.append(loginId);
		sb.append("; expires=");
		String expires=HeaderParser.fomatDateHeader(new Date(System.currentTimeMillis()+MAX_AGE));
		sb.append(expires);
		sb.append("; path=");
		sb.append(path);
		return sb.toString();
	}
	
	private void setCookie(String loginId,String path){
		String setCookieHeader=createSetCookieHeader(loginId,path);
		setHeader(HeaderParser.SET_COOKIE_HEADER,setCookieHeader);
	}
	
	private void responseProxyPac(){
		HeaderParser requestHeader=getRequestHeader();
		String cookieUserId=getCookieAuth(requestHeader);
		/*
		if(cookieUserId!=null){//Cookieにユーザ情報があれば
			if(user==authenticator.getAnonymousUser()){
				//認証ユーザがanonymouseの場合は、cookieユーザを信じる
				setUser(authenticator.getUserByLoginId(cookieUserId));
			}else if(!cookieUserId.equals(loginId)){
				//cookieのユーザと認証ユーザが異なる...ログインし直した
				setCookie(loginId,requestHeader.getPath());
			}
		}else{//cookieユーザがない
			if(user==authenticator.getAnonymousUser()){
				//認証ユーザがanonymouseの場合は、adminとする
				User adminUser=authenticator.getAdminUser();
				setUser(adminUser);
			}else{
				//認証ユーザがある場合...新たなログイン
				setCookie(loginId,requestHeader.getPath());
			}
		}
		*/
		MappingResult mapping=getRequestMapping();
		mapping.setResolvePath(PAC_PAGE);
		mapping.setDesitinationFile(config.getAdminDocumentRoot());
		forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
	}
/*
	private void responsePhJs(){
		MappingResult mapping=getMapping();
		setRequestAttribute("jsCreateTypes", JsonConverter.jsCreateTypes());
		mapping.setResolvePath(PH_JS);
		mapping.setDesitinationFile(adminSetting.getAdminDocumentRoot());
		forwardHandler(MappingEntry.VELOCITY_PAGE_HANDLER);
	}
*/
	
	public void startResponseReqBody(){
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		if(PAC_PAGE.equals(path)){
			responseProxyPac();
			return;
		}
		/*
		if(PH_JS.equals(path)){
			responsePhJs();
			return;
		}
		*/
		mapping.setDesitinationFile(config.getPublicDocumentRoot());
		WebServerHandler response=(WebServerHandler) forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
		logger.debug("responseObject:cid:"+getChannelId()+":"+response+":"+this);
	}
}
