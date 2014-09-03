package naru.aweb.handler;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import naru.aweb.auth.User;
import naru.aweb.config.Config;
import naru.aweb.config.FilterCategory;
import naru.aweb.config.FilterEntry;
import naru.aweb.config.FilterRole;
import naru.aweb.handler.ServerBaseHandler.SCOPE;
import naru.aweb.http.Cookie;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.JdoUtil;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;
/**
 * @author naru
 *
 */
public class FilterHelper {
	private static Logger logger = Logger.getLogger(FilterHelper.class);
	private static Config config=Config.getConfig();
	private final static String filterCookieName=config.getString("filterCookieName", "PH_FILTER");
	
	//whilteList,blackListのキャッシュ
	private static Map<String,List<Long>> roleWhiteLists=new HashMap<String,List<Long>>();
	private static Map<String,List<Long>> roleBlackLists=new HashMap<String,List<Long>>();
	
	private FilterCategory matchCategory(List<Long> list,Collection<FilterEntry> entryList,String path){
		if(list==null){
			return null;
		}
		for(FilterEntry entry:entryList){
			FilterCategory category=entry.getCategory();
			if(category.isUrl()){
				if( entry.getFilter().indexOf(path)<0 ){//TODO
					continue;
				}
			}
			if(list.contains(category.getId())){
				return category;
			}
		}
		return null;
	}

	private List<Long> categoryIdList(String role,boolean isBlack){
		Map<String,List<Long>> cacheList;
		if(isBlack){
			cacheList=roleBlackLists;
		}else{
			cacheList=roleWhiteLists;
		}
		List<Long> cl=cacheList.get(role);
		if(cl==null){
			Collection<FilterRole> frl=FilterRole.getByKey(role, isBlack);
			cl=new ArrayList<Long>();
			for(FilterRole fr:frl){
				cl.add(fr.getCategory().getId());
			}
			cacheList.put(role, cl);
		}
		return cl;
	}
	
	//filterがipaddressならそのまま
	//fqdnならばらしてそれぞれに
	private Pattern ipPatten = Pattern.compile("^([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})$");
	
	private void domains(String filter,Set<String> result){
		Matcher matcher=null;
		synchronized(ipPatten){
			matcher=ipPatten.matcher(filter);
		}
		if(matcher.matches()){
			//ipadressの場合分解しない
			return;
		}
		String[] parts=filter.split("\\.");
		StringBuilder sb=new StringBuilder();
		for(int i=parts.length-1;i>=1;i--){
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
	
	private Collection<FilterEntry> matchFilter(String filter){
		//filterがipaddressならそのまま
		
		//SQLのOR条件はindexを使ってくれないので１つ１つqueryする。
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query;
		List<FilterEntry> result=new ArrayList<FilterEntry>();
		
		query = pm.newQuery("javax.jdo.query.SQL","SELECT id,category_id,filter FROM FILTER_ENTRY WHERE filter like '" + filter + "%'");
		query.setClass(FilterEntry.class);
//		query = pm.newQuery(FilterEntry.class, "filter.startsWith(:filter)");
		Collection<FilterEntry> list=(Collection<FilterEntry>)query.execute();
		result.addAll(list);
		//fqdnならばらしてそれぞれに
		Set<String> domains=new HashSet<String>();
		domains(filter,domains);
		Iterator itr=domains.iterator();
		while(itr.hasNext()){
			query = pm.newQuery(FilterEntry.class, "filter==:filter");
			Collection<FilterEntry> dlist=(Collection<FilterEntry>)query.execute(itr.next());
			result.addAll(dlist);
		}
		return result;
	}
	
	/**
	 */
	public boolean doFilter(ProxyHandler handler){
		if(logger.isDebugEnabled())logger.debug("#doFilter cid:"+handler.getChannelId());
		User user=(User)handler.getAttribute(SCOPE.REQUEST,ServerBaseHandler.ATTRIBUTE_USER);
		List<String> roles=user.getRolesList();
		if(roles==null){
			return false;
		}
		if(roles.contains("admin")){
			return true;//adminはblockしない
		}
		//cookieで解決できる場合は、cookieで解決する
		HeaderParser requestHeader=handler.getRequestHeader();
		//filter mark cookieを取得
		//filter mark cookieを削除して必要があれば再設定
		String filterCookie=requestHeader.getAndRemoveCookieHeader(filterCookieName);
		if(filterCookie!=null && filterCookie.startsWith("true/")){
			return true;
		}
		if(filterCookie!=null && filterCookie.startsWith("false/")){
			handler.completeResponse("200","phantom proxy filter blocked");
			return false;
		}
		
		//DBを参照しないと分からない
		//ユーザの属しているroleからblacklist,whitelistを作成
		List<Long> whiteList=null;
		List<Long> blackList=null;
//		String[] roleArray=roles.split(",");
		for(String role:roles){
			List<Long> bl=categoryIdList(role,true);
			if(bl.size()!=0){
				if(blackList==null){
					blackList=new ArrayList<Long>();
				}
				blackList.addAll(bl);
			}
			List<Long> wl=categoryIdList(role,false);
			if(wl.size()!=0){
				if(whiteList==null){
					whiteList=new ArrayList<Long>();
				}
				whiteList.addAll(wl);
			}
		}
		
		MappingResult mapping=handler.getRequestMapping();
		ServerParser server=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		long start=System.currentTimeMillis();
		Collection<FilterEntry> list=matchFilter(server.getHost());
		if(logger.isDebugEnabled())logger.debug("FilterEntry.matchFilter.time:"+(System.currentTimeMillis()-start));
		
		FilterCategory category=matchCategory(whiteList,list,path);
		if(category!=null){
			if(!category.isUrl()){
				path="/";
			}
			String okCookie=Cookie.formatSetCookieHeader(filterCookieName,"true/"+System.currentTimeMillis(),null,path);
			handler.addResponseHeader(HeaderParser.SET_COOKIE_HEADER,okCookie);
			return true;
		}
		category=matchCategory(blackList,list,path);
		if(category!=null){
			//NG cookieを設定
			if(!category.isUrl()){
				path="/";
			}
			String ngCookie=Cookie.formatSetCookieHeader(filterCookieName,"false/"+System.currentTimeMillis(),null,path);
			handler.setHeader(HeaderParser.SET_COOKIE_HEADER, ngCookie);
			handler.completeResponse("200","phantom proxy filter blocked");
			return false;
		}
		String okCookie=Cookie.formatSetCookieHeader(filterCookieName,"true/"+System.currentTimeMillis(),null,"/");
		handler.addResponseHeader(HeaderParser.SET_COOKIE_HEADER,okCookie);
		return true;
	}
}
