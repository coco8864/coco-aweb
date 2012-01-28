package naru.aweb.admin;

import java.net.UnknownHostException;

import naru.aweb.config.Config;
import naru.aweb.core.RealHost;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class AdminRealHostHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminRealHostHandler.class);
	private static Config config=Config.getConfig();
	
	void doCommand(String command,ParameterParser parameter){
		if("realHostList".equals(command)){
			responseJson(RealHost.toJsonAll());
			return;
		}
		completeResponse("404");
	}
	void doObjCommand(String command,Object paramObj){
		if("realHostInsert".equals(command)){
			RealHost realHost;
			try {
				realHost = RealHost.fromJson(paramObj.toString());
				RealHost orgRealHost=RealHost.getRealHost(realHost.getName());
				if(orgRealHost==null){//êVãKçÏê¨
					config.addRealHost(realHost);
				}else{//èÓïÒçXêV
					orgRealHost.setInitBind(realHost.isInitBind());
					orgRealHost.setBindHost(realHost.getBindHost());
					orgRealHost.setBindPort(realHost.getBindPort());
					orgRealHost.setBacklog(realHost.getBacklog());
					orgRealHost.setBlackPattern(realHost.getBlackPattern());
					orgRealHost.setWhitePattern(realHost.getWhitePattern());
					config.updateRealHosts();
				}
			} catch (UnknownHostException e) {
				logger.warn("fail to create RealHost fromJson.json:"+paramObj.toString(),e);
			}
			responseJson(RealHost.toJsonAll());
			return;
		}else if("realHostDelete".equals(command)){
			JSONObject realHostJson=(JSONObject)paramObj;
			String name=realHostJson.optString("name");
			config.delRealHost(name);
			responseJson(RealHost.toJsonAll());
			return;
		}else if("realHostBind".equals(command)){
			JSONObject realHostJson=(JSONObject)paramObj;
			String name=realHostJson.optString("name");
			RealHost.bind(name);
			responseJson(RealHost.toJsonAll());
			return;
		}else if("realHostUnbind".equals(command)){
			JSONObject realHostJson=(JSONObject)paramObj;
			String name=realHostJson.optString("name");
			RealHost.unbind(name);
			responseJson(RealHost.toJsonAll());
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
