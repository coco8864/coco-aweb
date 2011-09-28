package naru.aweb.admin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import naru.async.store.Store;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.queue.QueueManager;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.log4j.Logger;

public class AdminAccessLogHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminAccessLogHandler.class);
	private static Config config=Config.getConfig();
	private static QueueManager queueManger=QueueManager.getInstance();
	
	private void listAccessLogJson(ParameterParser parameter){
		JSON accessLogsJson=null;
		try{
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
			Collection<AccessLog> accessLogs=AccessLog.query(query, from, to,orderby);
			accessLogsJson=AccessLog.collectionToJson(accessLogs);
		}catch(Exception e){
			logger.error("accessLogJson error.",e);
			completeResponse("500");
			return;
		}
		responseJson(accessLogsJson,parameter.getParameter("callback"));
	}
	
	private void importAccesslog(ParameterParser parameter,String chId) {
		DiskFileItem fileItem=parameter.getItem("importsFile");
		File importFile=fileItem.getStoreLocation();
		importFile.deleteOnExit();
		File importTmpFile=null;
		try {
			importTmpFile = File.createTempFile("import", ".zip", config.getTmpDir());
			importTmpFile.delete();
		} catch (IOException e) {
			logger.error("importAccesslog error.",e);
			return;
		}
		importFile.renameTo(importTmpFile);
		config.getLogPersister().importAccessLog(importTmpFile,chId);
	}
	
	private Set<Long> list(ParameterParser parameter){
		String param=parameter.getParameter("list");
		Set<Long> accessLogIds=new HashSet<Long>();
		if(param!=null){
			String[] ids=param.split(",");
			for(int i=0;i<ids.length;i++){
				long accessLogId=Long.parseLong(ids[i]);
				accessLogIds.add(accessLogId);
			}
		}
		return accessLogIds;
	}
	
	private static String LIST_QUERY_BASE="SELECT id from " + AccessLog.class.getName();
	private String query(ParameterParser parameter){
		String query=parameter.getParameter("query");
		if(query!=null&&!query.equals("")){
			query = LIST_QUERY_BASE + " WHERE " + query;
		}else{
			query=LIST_QUERY_BASE;
		}
		return query;
	}
	
	void doCommand(String command,ParameterParser parameter){
		if("list".equals(command)){
			listAccessLogJson(parameter);
			return;
		}else if("import".equals(command)){
			String chId=queueManger.createQueue();
			importAccesslog(parameter,chId);
			responseJson(chId);
			return;
		}else if("exportIds".equals(command)){
			String chId=queueManger.createQueue();
			Collection<Long>ids=list(parameter);
			config.getLogPersister().exportAccessLog(ids, chId);
			responseJson(chId);
			return;
		}else if("exportQuery".equals(command)){
			String chId=queueManger.createQueue();
			config.getLogPersister().exportAccessLog(query(parameter), chId);
			responseJson(chId);
			return;
		}else if("deleteIds".equals(command)){
			String chId=queueManger.createQueue();
			Collection<Long>ids=list(parameter);
			config.getLogPersister().deleteAccessLog(ids, chId);
			responseJson(chId);
			return;
		}else if("deleteQuery".equals(command)){
			String chId=queueManger.createQueue();
			config.getLogPersister().deleteAccessLog(query(parameter), chId);
			responseJson(chId);
			return;
		}else if("downloadExportFile".equals(command)){
			String fileId=parameter.getParameter("fileId");
			File exportFile=config.getLogPersister().popExportFile(fileId);
			if(exportFile==null){
				completeResponse("404");
				return;
			}
			setRequestAttribute(ATTRIBUTE_RESPONSE_FILE, exportFile);
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION, "attachment; filename=\"phatomProxyExport.zip\"");
			forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
			return;
		}
		completeResponse("404");
	}
	void doObjCommand(String command,Object paramObj){
		if("mappingInsert".equals(command)){
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
