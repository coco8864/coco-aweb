package naru.aweb.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.jdo.PersistenceManager;

import naru.aweb.config.CommissionAuthRole;
import naru.aweb.config.CommissionAuthUrl;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.portal.PortalHandler;
import naru.aweb.queue.QueueManager;
import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class AdminCommissionAuthHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminCommissionAuthHandler.class);
	private static QueueManager queueManger=QueueManager.getInstance();
	
	void doCommand(String command,ParameterParser parameter){
		if("listCommissionUrl".equals(command)){
			Collection<CommissionAuthUrl> urls=CommissionAuthUrl.query(null, -1, 0,null);
			JSON urlsJson=CommissionAuthUrl.collectionToJson(urls);
			responseJson(urlsJson);
			return;
		}else if("addCommissionUrl".equals(command)){
			String notes=parameter.getParameter("notes");
			String realm=parameter.getParameter("realm");
			String authType=parameter.getParameter("authType");
			String authMethod=parameter.getParameter("authMethod");
			String authUrl=parameter.getParameter("authUrl");
			String authBody=parameter.getParameter("authBody");
//			String actionMethod=parameter.getParameter("actionMethod");
//			String actionUrl=parameter.getParameter("actionUrl");
			PersistenceManager pm=JdoUtil.getPersistenceManager();
			CommissionAuthUrl commissionAuthUrl=new CommissionAuthUrl(notes,realm,authType,authMethod,authUrl,authBody);
			try{
				pm.currentTransaction().begin();
				commissionAuthUrl.save();
				pm.currentTransaction().commit();
			}finally{
				if(pm.currentTransaction().isActive()){
					pm.currentTransaction().rollback();
				}
			}
			responseJson(commissionAuthUrl.toJson());
			return;
		}else if("getRoleList".equals(command)){//roleに対するblackList,whilteListを取得する
			String role=parameter.getParameter("role");
			Collection<CommissionAuthRole> roles=CommissionAuthRole.getByKey(role);
			
			JSONArray urlIds=new JSONArray();
			for(CommissionAuthRole commissionAuthRole:roles){
				urlIds.add(commissionAuthRole.getAuthUrl().getId());
			}
			responseJson(urlIds);
			return;
		}else if("setRoleList".equals(command)){//roleに対するblackList,whilteListを設定する
			String role=parameter.getParameter("role");
			Collection<CommissionAuthRole> roles=CommissionAuthRole.getByKey(role);
			String[] urlIds=parameter.getParameter("urlIds").split(",");
			Set<Long> idsSet=new HashSet<Long>();
			for(String urlId:urlIds){
				idsSet.add(Long.parseLong(urlId));
			}
			Iterator<CommissionAuthRole>itr=roles.iterator();
			List<CommissionAuthRole>removeRoles=new ArrayList<CommissionAuthRole>();
			while(itr.hasNext()){
				CommissionAuthRole commissionAuthRole=itr.next();
				if(!idsSet.remove(commissionAuthRole.getId())){
					removeRoles.add(commissionAuthRole);
				}
			}
			PersistenceManager pm=JdoUtil.getPersistenceManager();
			try{
				pm.currentTransaction().begin();
				itr=removeRoles.iterator();
				while(itr.hasNext()){
					CommissionAuthRole commissionAuthRole=itr.next();
					commissionAuthRole.delete();
				}
				for(Long id:idsSet){
					CommissionAuthUrl authUrl=CommissionAuthUrl.getById(id);
					if(authUrl==null){
						logger.error("not found id."+id);
						continue;
					}
					CommissionAuthRole commisionAuthRole=new CommissionAuthRole(role,authUrl);
					commisionAuthRole.save();
				}
				pm.currentTransaction().commit();
			}finally{
				if(pm.currentTransaction().isActive()){
					pm.currentTransaction().rollback();
				}
			}
			//role情報が変更されたため,PortalHandlerが保持するキャッシュをクリアする。
			PortalHandler.clearRoleCash();
			responseJson(urlIds);
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
