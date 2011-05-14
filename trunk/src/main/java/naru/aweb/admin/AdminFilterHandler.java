package naru.aweb.admin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jdo.PersistenceManager;

import naru.aweb.config.FilterCategory;
import naru.aweb.config.FilterEntry;
import naru.aweb.config.FilterRole;
import naru.aweb.filter.ListGetter;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.queue.QueueManager;
import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class AdminFilterHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminFilterHandler.class);
	private static QueueManager queueManger=QueueManager.getInstance();
	
	void doCommand(String command,ParameterParser parameter){
		if("listCategory".equals(command)){
			Collection<FilterCategory> categorys=FilterCategory.query(null, -1, 0,null);
			JSON categorysJson=FilterCategory.collectionToJson(categorys);
			responseJson(categorysJson);
			return;
		}else if("addEntry".equals(command)){
			String categoryId=parameter.getParameter("categoryId");
			PersistenceManager pm=JdoUtil.getPersistenceManager();
			try{
				pm.currentTransaction().begin();
				FilterCategory filterCategory=FilterCategory.getById(Long.parseLong(categoryId));
				String filter=parameter.getParameter("filter");
				FilterEntry f=new FilterEntry(filterCategory,filter);
				f.save();
				pm.currentTransaction().commit();
			}finally{
				if(pm.currentTransaction().isActive()){
					pm.currentTransaction().rollback();
				}
			}
			responseJson(categoryId);
			return;
		}else if("getRoleList".equals(command)){//roleÇ…ëŒÇ∑ÇÈblackList,whilteListÇéÊìæÇ∑ÇÈ
			String role=parameter.getParameter("role");
			boolean isBlack="true".equalsIgnoreCase(parameter.getParameter("isBlacklist"));
			Collection<FilterRole> roles=FilterRole.getByKey(role, isBlack);
			
			JSONArray catagoryIds=new JSONArray();
			for(FilterRole filterRole:roles){
				catagoryIds.add(filterRole.getCategory().getId());
			}
			responseJson(catagoryIds);
			return;
		}else if("setRoleList".equals(command)){//roleÇ…ëŒÇ∑ÇÈblackList,whilteListÇê›íËÇ∑ÇÈ
			String role=parameter.getParameter("role");
			boolean isBlack="true".equalsIgnoreCase(parameter.getParameter("isBlacklist"));
			Collection<FilterRole> roles=FilterRole.getByKey(role, isBlack);
			
			String[] categoryIds=parameter.getParameter("categoryIds").split(",");
			Set<Long> idsSet=new HashSet<Long>();
			for(String categoryId:categoryIds){
				idsSet.add(Long.parseLong(categoryId));
			}
			Iterator<FilterRole>itr=roles.iterator();
			while(itr.hasNext()){
				FilterRole filterRole=itr.next();
				if(idsSet.remove(filterRole.getId())){
					itr.remove();
				}
			}
			PersistenceManager pm=JdoUtil.getPersistenceManager();
			try{
				pm.currentTransaction().begin();
				itr=roles.iterator();
				while(itr.hasNext()){
					FilterRole filterRole=itr.next();
					filterRole.delete();
				}
				for(Long id:idsSet){
					FilterCategory filterCategory=FilterCategory.getById(id);
					if(filterCategory==null){
						logger.error("not found id."+id);
						continue;
					}
					FilterRole filterRole=new FilterRole(role,isBlack,filterCategory);
					filterRole.save();
				}
				pm.currentTransaction().commit();
			}finally{
				if(pm.currentTransaction().isActive()){
					pm.currentTransaction().rollback();
				}
			}
			responseJson(categoryIds);
			return;
		}else if("listUpdate".equals(command)){//categoryListÇnetÇ©ÇÁéÊìæÇ∑ÇÈ
			String chId=queueManger.createQueue();
			String source=parameter.getParameter("source");
			try {
				ListGetter listGetter=new ListGetter();
				listGetter.start(source, chId);
				responseJson(chId);
				return;
			} catch (MalformedURLException e) {
				logger.error("source error.source:"+source,e);
			} catch (IOException e) {
				logger.error("fail to listGetter.source:"+source,e);
			}
			queueManger.complete(chId, "fail to listGetter");
			return;
		}
		completeResponse("404");
	}
	
	void doObjCommand(String command,Object paramObj){
		completeResponse("404");
	}
	
	public void startResponseReqBody(){
		ParameterParser parameter=getParameterParser();
		String command=parameter.getParameter("command");
		if(command!=null){
			doCommand(command,parameter);
			return;
		}
		JSONObject json=(JSONObject)parameter.getJsonObject();
		if(json!=null){
			command=json.optString("command");
			Object paramObj=json.opt("param");
			doObjCommand(command,paramObj);
			return;
		}
		completeResponse("404");
	}
	
}
