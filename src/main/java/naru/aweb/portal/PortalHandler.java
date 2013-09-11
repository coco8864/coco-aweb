package naru.aweb.portal;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jdo.PersistenceManager;

import naru.async.store.DataUtil;
import naru.aweb.auth.AuthSession;
import naru.aweb.config.CommissionAuth;
import naru.aweb.config.CommissionAuthEntry;
import naru.aweb.config.CommissionAuthRole;
import naru.aweb.config.CommissionAuthUrl;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.ParameterParser;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.JdoUtil;
import naru.aweb.util.ServerParser;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.log4j.Logger;

public class PortalHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(PortalHandler.class);
	private static Config config=Config.getConfig();
	
	private static Map<String,List<CommissionAuthUrl>> roleCommissionAuthUrlLists=new HashMap<String,List<CommissionAuthUrl>>();
	public static void clearRoleCash(){
		roleCommissionAuthUrlLists.clear();
	}
	
	private void commissionAuthIdList(String role,List<CommissionAuthUrl> authUrlList){
		List<CommissionAuthUrl> cache=roleCommissionAuthUrlLists.get(role);
		if(cache==null){
			cache=new ArrayList<CommissionAuthUrl>();
			Collection<CommissionAuthRole> carl=CommissionAuthRole.getByKey(role);
			for(CommissionAuthRole car:carl){
				cache.add(car.getAuthUrl());
			}
			roleCommissionAuthUrlLists.put(role, cache);
		}
		authUrlList.addAll(cache);
	}
	
	private void setAuthEntry(User user,long authUrlId,String authData){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.currentTransaction().begin();
		try{
			CommissionAuthUrl authUrl=CommissionAuthUrl.getById(authUrlId);
			if(authUrl==null){
				return;
			}
			CommissionAuthEntry authEntry=new CommissionAuthEntry(user,authUrl);
			authEntry.setAuthDataPlain(authData);
			authEntry.save();
			pm.currentTransaction().commit();
		}finally{
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
	}

	private static JSON NO_EXIST_RES=JSONSerializer.toJSON("{isEntry:false}");
	private static JSON EXIST_RES=JSONSerializer.toJSON("{isEntry:true}");
	/*
	private void removeAuthEntry(User user,ParameterParser parameter,PortalSession potalSession){
		long authUrlId=Long.parseLong(parameter.getParameter("authUrlId"));
		CommissionAuthUrl authUrl=CommissionAuthUrl.getById(authUrlId);
		if(authUrl==null){
			responseJson(NO_EXIST_RES);
			return ;
		}
		CommissionAuthEntry authEntry=CommissionAuthEntry.getByKey(user, authUrl);
		if(authEntry==null){
			responseJson(NO_EXIST_RES);
			return ;
		}
		String type=authUrl.getAuthType();
		if(!"basic".equals(type)){
			potalSession.removeBasicAuthHeader(authUrl.getAuthUrl());
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.currentTransaction().begin();
		try{
			authEntry.remove();
			pm.currentTransaction().commit();
		}finally{
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
		responseJson(EXIST_RES);
		return;
	}
	*/
	/*
	private void processBasicAuthEntry(User user,ParameterParser parameter,PortalSession potalSession){
		long authUrlId=Long.parseLong(parameter.getParameter("authUrlId"));
		CommissionAuthUrl authUrl=CommissionAuthUrl.getById(authUrlId);
		if(authUrl==null){
			responseJson(NO_EXIST_RES);
			return ;
		}
		String type=authUrl.getAuthType();
		if(!"basic".equals(type)){
			responseJson(NO_EXIST_RES);
			return ;
		}
		CommissionAuthEntry authEntry=CommissionAuthEntry.getByKey(user, authUrl);
		if(authEntry==null){
			responseJson(NO_EXIST_RES);
			return ;
		}
		potalSession.setBasicAuthHeader(authUrl.getAuthUrl(),authEntry.getAuthDataPlain());
		responseJson(EXIST_RES);
		return;
	}
	*/
	
	private void getFormAuthEntry(User user,ParameterParser parameter){
		long authUrlId=Long.parseLong(parameter.getParameter("authUrlId"));
		CommissionAuthUrl authUrl=CommissionAuthUrl.getById(authUrlId);
		if(authUrl==null){
			responseJson(NO_EXIST_RES);
			return ;
		}
		String type=authUrl.getAuthType();
		if(!"form".equals(type)){
			responseJson(NO_EXIST_RES);
			return;
		}
		CommissionAuthEntry authEntry=CommissionAuthEntry.getByKey(user, authUrl);
		if(authEntry==null){
			responseJson(NO_EXIST_RES);
			return;
		}
		JSONObject res=(JSONObject)JSONSerializer.toJSON("{isEntry:true}");
		res.put("authData", authEntry.getAuthDataPlain());
		responseJson(res);
		return;
	}
	
	private void setFormAuthEntry(User user,ParameterParser parameter){
		long authUrlId=Long.parseLong(parameter.getParameter("authUrlId"));
		String authData=parameter.getParameter("authData");
		setAuthEntry(user,authUrlId,authData);
		responseJson(EXIST_RES);
		return;
	}
	
	private void setBasicAuthEntry(User user,ParameterParser parameter){
		long authUrlId=Long.parseLong(parameter.getParameter("authUrlId"));
		String userId=parameter.getParameter("userId");
		String password=parameter.getParameter("password");
		String authInfo=userId+":"+password;
		String authHeader="Basic " +DataUtil.encodeBase64(authInfo);
		setAuthEntry(user,authUrlId,authHeader);
//		potalSession.setBasicAuthHeader(authUrl.getAuthUrl(),authHeader);
		responseJson(EXIST_RES);
		return;
	}
	
	private void removeCommissionAuths(User user,PortalSession portalSession,ParameterParser parameter){
		String ids=parameter.getParameter("ids");
		String[] authIds=ids.split(",");
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.currentTransaction().begin();
		try{
			for(int i=0;i<authIds.length;i++){
				CommissionAuth commissionAuth=CommissionAuth.getById(Long.parseLong(authIds[i]));
				//portalSessionに覚えているキャッシュを削除
				portalSession.removeBasicAuth(commissionAuth);
				commissionAuth.remove();
			}
			pm.currentTransaction().commit();
		}finally{
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
		responseJson(ids);
	}
	
	/*
	private void enableBasicAuth(User user,PortalSession portalSession,ParameterParser parameter){
		String id=parameter.getParameter("id");
		CommissionAuth commissionAuth=CommissionAuth.getById(Long.parseLong(id));
		portalSession.enabledBasicAuth(commissionAuth);
		commissionAuth.save(true);
		responseJson(commissionAuth.toJson());
	}
	*/

	
	//jsonpで呼び出される
	private void getWebAuth(User user,PortalSession portalSession,ParameterParser parameter){
		String authUrl=parameter.getParameter("authUrl");
		String actionUrl=parameter.getParameter("actionUrl");
		String callback=parameter.getParameter("callback");
		
		String realm=portalSession.getRealm(actionUrl);
		//portalSession.getBasicAuth(resolveUrl,authentication);
		CommissionAuth commissionAuth=portalSession.getBasicAuth(authUrl,realm);
//		CommissionAuth commissionAuth=portalSession.getWebAuth(authUrl);
		JSONObject auth=null;
		if(commissionAuth==null || !commissionAuth.isEnabled() || !commissionAuth.isData()){
			auth=new JSONObject();
//			String authentication=portalSession.getAuthentication(authUrl);
			if(realm!=null){
				auth.put("realm", realm);
			}
		}else{
			String authText=commissionAuth.getAuthDataPlain();
			auth=JSONObject.fromObject(authText);
			auth.put("id", commissionAuth.getId());
			auth.put("realm", commissionAuth.getRealm());
			auth.put("authUrl", authUrl);
		}
		responseJson(auth,callback);
	}
	
	private void getFormAuthData(User user,ParameterParser parameter){
		String id=parameter.getParameter("id");
		CommissionAuth commissionAuth=CommissionAuth.getById(Long.parseLong(id));
		responseJson(commissionAuth.getAuthDataPlain());
	}
	
	private void editAuth(User user,PortalSession portalSession,ParameterParser parameter){
		String id=parameter.getParameter("id");
		CommissionAuth commissionAuth=CommissionAuth.getById(Long.parseLong(id));
		String enabled=parameter.getParameter("enabled");
		String notes=parameter.getParameter("notes");
		String forcePost=parameter.getParameter("forcePost");
		String autoPost=parameter.getParameter("autoPost");
		commissionAuth.setEnabled("true".equalsIgnoreCase(enabled));
		commissionAuth.setForcePost("true".equalsIgnoreCase(forcePost));
		commissionAuth.setAutoPost("true".equalsIgnoreCase(autoPost));
		commissionAuth.setNotes(notes);
//		portalSession.editBasicAuth(commissionAuth);basic認証には編集可能な部分はないので必要なし
		String json=commissionAuth.toJson();
		commissionAuth.save(true);
		responseJson(json);
	}
	
	private void saveFormAuth(User user,ParameterParser parameter){
		String authUrl=parameter.getParameter("authUrl");
		CommissionAuth commissionAuth=new CommissionAuth(user,authUrl);
		String enabled=parameter.getParameter("enabled");
		String authTitle=parameter.getParameter("authTitle");
		String actionUrl=parameter.getParameter("actionUrl");
		String forcePost=parameter.getParameter("forcePost");
		String autoPost=parameter.getParameter("autoPost");
		commissionAuth.setAuthType(CommissionAuth.FORM);
		commissionAuth.setEnabled("true".equalsIgnoreCase(enabled));
		commissionAuth.setForcePost("true".equalsIgnoreCase(forcePost));
		commissionAuth.setAutoPost("true".equalsIgnoreCase(autoPost));
		String notes=parameter.getParameter("notes");
		
		commissionAuth.setRealm(authTitle);
		commissionAuth.setActionUrl(actionUrl);
		if(notes==null){
			notes=authTitle;
		}
		commissionAuth.setNotes(notes);
		
		if(commissionAuth.isEnabled()){
			String authData=parameter.getParameter("authData");
			commissionAuth.setAuthDataPlain(authData);
		}
		commissionAuth.save(true);
		responseJson(commissionAuth.getId());
	}
	
	private void saveWebAuth(User user,ParameterParser parameter){
		String authUrl=parameter.getParameter("authUrl");
		CommissionAuth commissionAuth=new CommissionAuth(user,authUrl);
		String enabled=parameter.getParameter("enabled");
		String realm=parameter.getParameter("realm");
		String actionUrl=parameter.getParameter("actionUrl");
		commissionAuth.setAuthType(CommissionAuth.BASIC);
		commissionAuth.setEnabled("true".equalsIgnoreCase(enabled));
		String notes=parameter.getParameter("notes");
		
		commissionAuth.setRealm(realm);
		commissionAuth.setActionUrl(actionUrl);
		if(notes==null){
			notes=realm;
		}
		commissionAuth.setNotes(notes);
		if(commissionAuth.isEnabled()){
			String authData=parameter.getParameter("authData");
			commissionAuth.setAuthDataPlain(authData);
		}
		commissionAuth.save(true);
		responseJson(commissionAuth.getId());
	}
	
	private void redirectPortal(){
		HeaderParser requestHeader=getRequestHeader();
		ServerParser selfServer=requestHeader.getServer();
		StringBuilder sb=new StringBuilder();
		if(isSsl()){
			sb.append("https://");
		}else{
			sb.append("http://");
		}
		sb.append(selfServer.toString());
		sb.append("/portal");//TODO
		sb.append("/portal.vsp");
		setHeader(HeaderParser.LOCATION_HEADER, sb.toString());
		completeResponse("302");
		return;
	}
	
	public void startResponseReqBody(){
		User user=(User)getRequestAttribute(ServerBaseHandler.ATTRIBUTE_USER);
		PortalSession portalSession=PortalSession.getPortalSession(this);
		MappingResult mapping=getRequestMapping();
		//portal画面からのリクエスト、画面
		ParameterParser parameter=getParameterParser();
		//自リクエストFile名をテンプレート名とする
		//実際に処理するpath
		HeaderParser requestHeader=getRequestHeader();
		String selfPath=requestHeader.getRequestUri();
		String path=mapping.getResolvePath();
		if("/".equals(path)){
			if(!selfPath.endsWith("/")){
				redirectPortal();
				return;
			}
			path="/portal.vsp";
			mapping.setResolvePath(path);
		}
		
		if( "/commissionAuth".equals(path)){
			String command=parameter.getParameter("command");
			if("processBasicAuthEntry".equals(command)){
//				processBasicAuthEntry(user, parameter, portalSession);
			}else if("getFormAuthEntry".equals(command)){
				getFormAuthEntry(user, parameter);
			}else if("setBasicAuthEntry".equals(command)){
				setBasicAuthEntry(user, parameter);
			}else if("setFormAuthEntry".equals(command)){
				setFormAuthEntry(user, parameter);
			}else if("removeAuthEntry".equals(command)){
//				removeAuthEntry(user, parameter, portalSession);
//			}else if("enableBasicAuth".equals(command)){
//				enableBasicAuth(user, portalSession,parameter);
			}else if("getFormAuthData".equals(command)){
				getFormAuthData(user, parameter);
			}else if("saveFormAuth".equals(command)){
				saveFormAuth(user, parameter);
			}else if("saveWebAuth".equals(command)){
				saveWebAuth(user, parameter);
			}else if("deleteCommissionAuths".equals(command)){
				removeCommissionAuths(user,portalSession,parameter);
			}else if("editAuth".equals(command)){
				editAuth(user,portalSession,parameter);
			}else if("getWebAuth".equals(command)){//jsonpで呼び出される
				getWebAuth(user,portalSession,parameter);
			}
			return;
		}else if(path.endsWith("portal.vsp")){
			/*
			String roles=user.getRole();
			String[] roleArray=null;
			if(roles!=null){
				roleArray=roles.split(",");
			}
			List<CommissionAuthUrl> authUrlList=new ArrayList<CommissionAuthUrl>();
			for(int i=0;i<roleArray.length;i++){
				commissionAuthIdList(roleArray[i],authUrlList);
			}
			setRequestAttribute("CommissionAuthUrls",authUrlList);
			*/
			setRequestAttribute("portalSession",portalSession);
		}
		//uriから、controllerUrlをとって、controllerRootの後ろにくっつける
		//でfileSystemに送る
		//既にmappingは設定されているのでFileSystemに送ればコンテンツがでる
		mapping.setDesitinationFile(config.getPortalDocumentRoot());
		forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
	}
}
