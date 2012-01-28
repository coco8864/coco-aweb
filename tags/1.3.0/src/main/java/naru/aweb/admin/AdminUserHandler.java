package naru.aweb.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import naru.aweb.auth.Authenticator;
import naru.aweb.config.Config;
import naru.aweb.config.User;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
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
	
	/*
	private String calcPass(String loginId,JSONObject jsonUser){
		String pass1=jsonUser.optString("pass1");
		String pass2=jsonUser.optString("pass2");
		if(pass1!=null&&pass1.length()!=0&&pass1.equals(pass2)){
			return authenticator.calcPass(loginId, pass1);
		}
		return null;
	}
	*/
	
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
//			user.setPass(pass);
			Date now=new Date();
			user.setCreateDate(now);
//			user.setChangePass(now);
			user.save();
//			authenticator.putUserCache(user);
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
			//passwordÇÃê›íË
			String pass1=jsonUser.optString("pass1");
			String pass2=jsonUser.optString("pass2");
			if(pass1!=null && pass1.equals(pass2)){
				user.changePassword(pass1, authenticator.getRealm());
				user.setChangePass(new Date());
			}
			user.setRoles(jsonUser.getString("roles"));
			user.setFirstName(jsonUser.getString("firstName"));
			user.setLastName(jsonUser.getString("lastName"));
			user.setFootSize(jsonUser.optInt("footSize",25));
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
			completeResponse("205");
			return;
		}
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
