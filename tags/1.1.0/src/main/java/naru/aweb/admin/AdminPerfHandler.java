package naru.aweb.admin;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Collection;

import naru.aweb.config.AccessLog;
import naru.aweb.config.Performance;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.queue.QueueManager;
import naru.aweb.robot.Scenario;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.log4j.Logger;

public class AdminPerfHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminPerfHandler.class);
	
	private String doStress(AccessLog[] accessLogs,String name,int browserCount,int callCount,
			boolean isCallerKeepAlive,long thinkingTime,
			boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		QueueManager queueManager=QueueManager.getInstance();
		String chId=queueManager.createQueue(true);
		if( Scenario.run(accessLogs, name, browserCount, callCount, isCallerKeepAlive, thinkingTime, isAccessLog, isResponseHeaderTrace, isResponseBodyTrace,chId)){
			return chId;
		}
		return null;
	}

	private String doStressFile(AccessLog[] accessLogs,JSONArray stressJson){
		QueueManager queueManager=QueueManager.getInstance();
		String chId=queueManager.createQueue(true);
		if( Scenario.run(accessLogs, stressJson,chId)){
			return chId;
		}
		return null;
	}
	
	private AccessLog[] listToAccessLogs(String list){
		String[] ids=list.split(",");
		AccessLog[] accessLogs=new AccessLog[ids.length];
		for(int i=0;i<ids.length;i++){
			long accessLogId=Long.parseLong(ids[i]);
			accessLogs[i]=AccessLog.getById(accessLogId);
		}
		return accessLogs;
	}
	
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
		}else if("csv".equals(command)){
			String query=parameter.getParameter("query");
			Collection<Performance> perfs=Performance.query(query, -1, -1,null);
			try {
				setHeader(HeaderParser.CONTENT_DISPOSITION_HEADER, "attachment; filename=\"stress.csv\"");
				setContentType("text/csv");
				setStatusCode("200");
				Writer writer=getResponseBodyWriter("utf8");
				writer.write(Performance.csvTitle());
				for(Performance perf:perfs){
					writer.write(perf.toCsv());
				}
				try {
					writer.close();
				} catch (IOException ignore) {
				}
				responseEnd();
				return;
			} catch (UnsupportedEncodingException e) {
				logger.error("fail to Performance csv",e);
			} catch (IOException e) {
				logger.error("fail to Performance csv",e);
			}
			completeResponse("500");
			return;
		}else if("stress".equals(command)){
			String list=parameter.getParameter("list");
			AccessLog[] accessLogs=listToAccessLogs(list);
//			Set<Long> accessLogIds=new HashSet<Long>();
			String name=parameter.getParameter("name");
			String browserCount=parameter.getParameter("browserCount");
			String call=parameter.getParameter("loopCount");
			String time=parameter.getParameter("time");
//			String trace=parameter.getParameter("trace");
			String keepAlive=parameter.getParameter("keepAlive");
			String accessLog=parameter.getParameter("accessLog");
			String tesponseHeaderTrace=parameter.getParameter("tesponseHeaderTrace");
			String tesponseBodyTrace=parameter.getParameter("tesponseBodyTrace");
			String thinkingTime=parameter.getParameter("thinkingTime");
			String chId=doStress(accessLogs,name,Integer.parseInt(browserCount),
					Integer.parseInt(call),
			"true".equalsIgnoreCase(keepAlive),
			Long.parseLong(thinkingTime),
			"true".equalsIgnoreCase(accessLog),
			"true".equalsIgnoreCase(tesponseHeaderTrace),
			"true".equalsIgnoreCase(tesponseBodyTrace));
			responseJson(chId);
			return;
		}else if("stressFile".equals(command)){
			String list=parameter.getParameter("list");
			AccessLog[] accessLogs=listToAccessLogs(list);
			DiskFileItem item=parameter.getItem("stressFile");
			try {
				String s = item.getString("utf-8");
				JSONArray json=JSONArray.fromObject(s);
				String chId=doStressFile(accessLogs,json);
				setContentType("text/html");
				completeResponse("200",//TODO vsfにfowardするのがスマート
					"<script>parent.streeCommandCb('"+chId+"');</script>");
				return;
			} catch (UnsupportedEncodingException e) {
			} catch (RuntimeException e) {
			}
			setContentType("text/html");
			completeResponse("200",
				"<script>alert('fail to stressFile.fileName:"+ item.getName()+"');</script>");
			return;
		}
		completeResponse("404");
	}
	
	void doObjCommand(String command,Object paramObj){
		completeResponse("404");
	}
	
	public void startResponseReqBody(){
		try{
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
		}catch(RuntimeException e){
			logger.warn("fail to AdminPerfHandler.",e);
			completeResponse("500");
			return;
		}
		completeResponse("404");
	}
	
}
