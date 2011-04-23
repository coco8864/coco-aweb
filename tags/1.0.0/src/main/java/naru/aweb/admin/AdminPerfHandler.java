package naru.aweb.admin;

import java.util.Collection;
import java.util.Iterator;

import naru.aweb.config.Performance;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class AdminPerfHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminPerfHandler.class);
	
	void doCommand(String command,ParameterParser parameter){
		if("list".equals(command)){
			String query=parameter.getParameter("query");
			String fromString=parameter.getParameter("from");
			String toString=parameter.getParameter("to");
			String orderby=parameter.getParameter("orderby");
			int from=-1;
			int to=Integer.MAX_VALUE;
			if(fromString!=null){
				from=Integer.parseInt(fromString);
				if(toString!=null){
					to=Integer.parseInt(toString);
				}
			}
			Collection<Performance> perfs=Performance.query(query, from, to,orderby);
			JSON perfsJson=Performance.collectionToJson(perfs);
			responseJson(perfsJson);
			return;
		}else if("delete".equals(command)){
			String query=parameter.getParameter("query");
			long deleteCount=Performance.delete(query);
			responseJson(deleteCount);
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
