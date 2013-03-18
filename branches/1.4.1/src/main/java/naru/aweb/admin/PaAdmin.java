package naru.aweb.admin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.log4j.Logger;

import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.ParameterParser;
import naru.aweb.pa.Blob;
import naru.aweb.pa.PaPeer;
import naru.aweb.pa.Palet;
import naru.aweb.pa.PaletCtx;
import net.sf.json.JSON;

public class PaAdmin implements Palet {
	private static Logger logger = Logger.getLogger(PaAdmin.class);
	private static Config config=Config.getConfig();
	
	private PaletCtx ctx;
	@Override
	public void init(PaletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(String reason) {
	}

	@Override
	public void onTimer() {
	}

	@Override
	public void onSubscribe(PaPeer peer) {
	}

	@Override
	public void onUnsubscribe(PaPeer peer, String reason) {
	}

	@Override
	public void onPublishText(PaPeer peer, String data) {
		// TODO Auto-generated method stub
	}
	
	private void chat(PaPeer peer,Map<String, ?> data){
		if(Boolean.TRUE.equals(data.get("echoback"))){
			ctx.message(data, "chat");
		}else{
			ctx.message(data, "chat", peer);
		}
	}
	
	private void setting(PaPeer peer,Map<String, ?> data){
		
		/*
		 * %proxy
		 * proxyPacUrl
		 * proxyServer
		 * sslProxyServer
		 * exceptProxyDomains
		 * 
		 * %keepALive
		 * isWebKeepAlive check
		 * isProxyKeepAlive check
		 * maxKeepAliveRequests num
		 * keepAliveTimeout num
		 * allowChunked check
		 * 
		 * %html5
		 * isUseHixie75 check
		 * isUseHixie76 check
		 * isUseHybi10 check
		 * webSocketMessageLimit
		 * webSocketPingInterval
		 * 
		 * 
		 * 
		 */
		
		
		peer.message("done");
		
	}
	
	private JSON listAccessLogJson(Map<String, ?> parameter){
		try{
			String query=(String)parameter.get("query");
			String fromString=(String)parameter.get("from");
			String toString=(String)parameter.get("to");
			String orderby=(String)parameter.get("orderby");
			int from=-1;
			int to=Integer.MAX_VALUE;
			if(fromString!=null){
				from=Integer.parseInt(fromString);
				if(toString!=null){
					to=Integer.parseInt(toString);
				}
			}
			Collection<AccessLog> accessLogs=AccessLog.query(query, from, to,orderby);
			return AccessLog.collectionToJson(accessLogs);
		}catch(Exception e){
			logger.error("accessLogJson error.",e);
		}
		return null;
	}
	
	private void importAccesslog(PaPeer peer,Map<String, ?> parameter) {
		Blob importsFile=(Blob)parameter.get("importsFile");
		config.getLogPersister().importAccessLog(importsFile,peer);
	}
	
	private Set<Long> list(Map<String, ?> parameter){
		String param=(String)parameter.get("list");
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
	private String query(Map<String, ?> parameter){
		String query=(String)parameter.get("query");
		if(query!=null&&!query.equals("")){
			query = LIST_QUERY_BASE + " WHERE " + query;
		}else{
			query=LIST_QUERY_BASE;
		}
		return query;
	}
	
	private void accessLog(PaPeer peer,Map<String, ?> parameter){
		String command=(String)parameter.get("command");
		if("list".equals(command)){
			JSON list=listAccessLogJson(parameter);
			peer.message(list);
			return;
		}else if("import".equals(command)){
			importAccesslog(peer,parameter);
			peer.message("done");
			return;
		}else if("exportIds".equals(command)){
			Collection<Long>ids=list(parameter);
			config.getLogPersister().exportAccessLog(ids, peer);
			return;
		}else if("exportQuery".equals(command)){
			config.getLogPersister().exportAccessLog(query(parameter), peer);
			return;
		}else if("deleteIds".equals(command)){
			Collection<Long>ids=list(parameter);
			config.getLogPersister().deleteAccessLog(ids, peer);
			return;
		}else if("deleteQuery".equals(command)){
			config.getLogPersister().deleteAccessLog(query(parameter), peer);
			return;
		/*
		}else if("downloadExportFile".equals(command)){
			String fileId=(String)parameter.get("fileId");
			File exportFile=config.getLogPersister().popExportBlob(fileId);
			Blob blob=Blob.create(exportFile);
			blob.setName("phatomProxyExport.zip");
			peer.download(blob);
			return;
		*/
		}
	}
	

	@Override
	public void onPublishObj(PaPeer peer, Map<String, ?> data) {
		String subname=peer.getSubname();
		if("chat".equals(subname)){
			chat(peer,data);
		}else if("sttics".equals(subname)){
			ctx.message(data, "sttics");
		}else if("setting".equals(subname)){
			setting(peer,data);
		}else if("accessLog".equals(subname)){
			accessLog(peer,data);
		}
	}

	@Override
	public void onPublishArray(PaPeer peer, List<?> data) {
		// TODO Auto-generated method stub
	}

}
