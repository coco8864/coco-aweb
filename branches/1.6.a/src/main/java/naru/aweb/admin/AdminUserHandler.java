package naru.aweb.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import naru.aweb.auth.Authenticator;
import naru.aweb.config.Config;
import naru.aweb.config.User;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.util.ParameterParser;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class AdminUserHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminUserHandler.class);
	private static Config config=Config.getConfig();
	private static Authenticator authenticator=config.getAuthenticator();
	
	void doCommand(String command,ParameterParser parameter){
		if("userList".equals(command)){
			Collection<User> users=User.query(null, -1, 0, "id");
			Iterator<User> itr=users.iterator();
			List<User> updateUsers=new ArrayList<User>();
			while(itr.hasNext()){
				User user=itr.next();
				User cacheUser=authenticator.getUserFromCache(user.getLoginId());
				if(cacheUser!=null){
					updateUsers.add(cacheUser);
				}else{
					updateUsers.add(user);
				}
			}
			JSON mappingsJson=User.collectionToJson(updateUsers);
			responseJson(mappingsJson);
			return;
		}
		completeResponse("404");
	}
	
	void doObjCommand(String command,Object paramObj){
		if("userInsert".equals(command)){
			JSONObject jsonUser=(JSONObject)paramObj;
			User user=User.fromJson(jsonUser.toString());
			String pass1=jsonUser.optString("pass1");
			String pass2=jsonUser.optString("pass2");
			if(pass1==null || !pass1.equals(pass2)){
				logger.error("fail to get pass");
				responseJson("fail to userInsert");
				return;
			}
			user.changePassword(pass1, authenticator.getRealm());
			
			pass1=jsonUser.optString("offlinePass1");
			pass2=jsonUser.optString("offlinePass2");
			if(pass1==null || !pass1.equals(pass2)){
				logger.error("fail to get offline pass");
				responseJson("fail to userInsert");
				return;
			}
			if(!"".equals(pass1)&&!"$".equals(pass1)){
				user.changeOfflinePassword(pass1);
			}
			Date now=new Date();
			user.setCreateDate(now);
			user.save();
			responseJson(user.toJson());
			return;
		}else if("userUpdate".equals(command)){
			JSONObject jsonUser=(JSONObject)paramObj;
			String loginId=jsonUser.getString("loginId");
			User user=authenticator.getUserFromCache(loginId);
			boolean isInDb=false;
			if(user==null){
				isInDb=true;
				user=User.getByLoginId(loginId);
			}
			long id=jsonUser.getLong("id");
			if(!user.getId().equals(id)){
				logger.error("fail to get pass");
				responseJson("fail to userInsert");
				return;
			}
			//passwordの設定
			String pass1=jsonUser.optString("pass1");
			String pass2=jsonUser.optString("pass2");
			if(pass1!=null &&!"".equals(pass1)&& pass1.equals(pass2)){
				user.changePassword(pass1, authenticator.getRealm());
			}
			//offlinePasswordの設定
			pass1=jsonUser.optString("offlinePass1");
			pass2=jsonUser.optString("offlinePass2");
			if(pass1!=null&&!"".equals(pass1)&& pass1.equals(pass2)){
				if("$".equals(pass1)){//offfLinePasswordに"$"を指定すると設定を消去
					user.setOfflinePassHash(null);
				}else{
					user.changeOfflinePassword(pass1);
				}
			}
			user.setRoles(jsonUser.getString("roles"));
			user.setNickname(jsonUser.getString("nickname"));
			if(isInDb){
				user.save();
			}
			responseJson(user.toJson());
			return;
		}else if("userDelete".equals(command)){
			JSONObject mappingJson=(JSONObject)paramObj;
			User user=User.fromJson(paramObj.toString());
			Long id=mappingJson.optLong("id");
			if(id==null){
				logger.warn("mappingDelete not found 'id'");
			}else{
				User.deleteById(id);
			}
			authenticator.removeUserCache(user.getLoginId());
			completeResponse("204");
			return;
		}
		completeResponse("404");
	}
	
	public void onRequestBody(){
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
