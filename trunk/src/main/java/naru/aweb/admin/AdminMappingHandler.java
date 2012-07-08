package naru.aweb.admin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.ServerParser;
import naru.aweb.util.StreamUtil;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.log4j.Logger;

public class AdminMappingHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminMappingHandler.class);
	private static Config config=Config.getConfig();
	
	private void save(Object mappingString){
		Mapping mapping=Mapping.fromJson(mappingString.toString());
		mapping.save();
	}
	
	void doCommand(String command,ParameterParser parameter){
		if("mappingList".equals(command)){
			String order=parameter.getParameter("order");
			Collection<Mapping> mappings=Mapping.query(null, -1, 0, order);
//			TreeSet<Mapping> sortMappings=new TreeSet<Mapping>(Mapping.mappingComparator);
//			sortMappings.addAll(mappings);
//			JSON mappingsJson=Mapping.collectionToJson(sortMappings);
			JSON mappingsJson=Mapping.collectionToJson(mappings);
			responseJson(mappingsJson);
			return;
		}else if("importsMappings".equals(command)){
			DiskFileItem item=parameter.getItem("importsFile");
			String s;
			try {
				s = item.getString("utf-8");
				JSON json=JSONArray.fromObject(s);
				if(json instanceof JSONArray ){
					JSONArray array=(JSONArray)json;
					for(int i=0;i<array.size();i++){
						save(array.get(i));
					}
				}else if(json instanceof JSONObject){
					save(json);
				}
			} catch (UnsupportedEncodingException e) {
				logger.debug("importsMappings",e);
			} catch (RuntimeException e) {
				logger.debug("importsMappings",e);
			}
			completeResponse("205");
			return;
		}else if("mappingInsert2".equals(command)){
			/* 簡易登録でリバースproxyとWebサイトを簡単に登録できるようにする */
			DiskFileItem item=parameter.getItem("importsFile");
			String mappingNotes=parameter.getParameter("mappingNotes");
			String mappingRealHost=parameter.getParameter("mappingRealHost");
			String mappingRoles=parameter.getParameter("mappingRoles");
			String mappingSecureType=parameter.getParameter("mappingSecureType");
			String mappingSourcePath=parameter.getParameter("mappingSourcePath");
			String mappingDestinationType=parameter.getParameter("mappingDestinationType");
			String destinationUrl=parameter.getParameter("destinationUrl");
			Collection<Mapping> mappings=Mapping.query("WHERE notes=='"+mappingNotes+"'", -1, 0, null);
			Mapping mapping;
			if(mappings.size()!=0){
				mapping=mappings.iterator().next();
			}else{
				mapping=new Mapping();
			}
			mapping.setEnabled(true);
			mapping.setNotes(mappingNotes);
			mapping.setRealHostName(mappingRealHost);
			mapping.setSourceType(Mapping.SourceType.WEB);
			mapping.setRoles(mappingRoles);
			if(mapping.getOptions()==null){
				mapping.setOptions("{}");
			}
			//uploadファイルがあれば、それでサイトを構築する
			if("FILE".equalsIgnoreCase(mappingDestinationType)){
				createWebsite(mapping,mappingSourcePath,mappingSecureType,item);
			}else{
				createReverseProxy(mapping,destinationUrl);
			}
			return;
		}else if("reloadMappings".equals(command)){
			config.getMapper().reloadMappings();
			completeResponse("205");
			return;
		}
		completeResponse("404");
	}
	
	/*　website */
	private void createWebsite(Mapping mapping,String mappingSourcePath,String mappingSecureType,DiskFileItem item){
		if(mappingSourcePath==null||mappingSourcePath.length()==0){
			mappingSourcePath="/";
		}
		String path="test";
		if("SSL".equalsIgnoreCase(mappingSecureType)){
			mapping.setSecureType(Mapping.SecureType.SSL);
		}else{
			mapping.setSecureType(Mapping.SecureType.PLAIN);
		}
		mapping.setDestinationType(Mapping.DestinationType.FILE);
		mapping.setSourcePath(mappingSourcePath);
		File appsDir=config.getAppsDocumentRoot();
		File deployDir=new File(appsDir,path);
		mapping.setDestinationPath(deployDir.getAbsolutePath());
		if(deployDir.exists()){
			File packupDir=new File(appsDir,path+System.currentTimeMillis());
			deployDir.renameTo(packupDir);
		}
		InputStream is;
		try {
			is = item.getInputStream();
			StreamUtil.unzip(deployDir, is);
			mapping.save();
		} catch (IOException e) {
			logger.warn("createWebsite",e);
		}
		completeResponse("205");
	}
	
	/* リバースproxy決め打ち*/
	private void createReverseProxy(Mapping mapping,String destinationUrl){
		StringBuilder schemeSb=new StringBuilder();
		StringBuilder pathSb=new StringBuilder();
		ServerParser server=ServerParser.parseUrl(destinationUrl,schemeSb,pathSb);
		if(server==null){
			completeResponse("205");
			return;
		}
		mapping.setDestinationServer(server.toServerString());
		server.unref(true);
		if(pathSb.length()==0){
			pathSb.append('/');
		}
		mapping.setSourcePath(pathSb.toString());
		mapping.setDestinationPath(pathSb.toString());
		String scheme=schemeSb.toString();
		if(scheme.equals("http")){
			mapping.setSecureType(Mapping.SecureType.PLAIN);
			mapping.setDestinationType(Mapping.DestinationType.HTTP);
		}else if(scheme.equals("https")){
			mapping.setSecureType(Mapping.SecureType.SSL);
			mapping.setDestinationType(Mapping.DestinationType.HTTPS);
		}else{
			completeResponse("205");
			return;
		}
		mapping.save();
		completeResponse("205");
	}
	
	void doObjCommand(String command,Object paramObj){
		if("mappingInsert".equals(command)){
			Mapping mapping=Mapping.fromJson(paramObj.toString());
			mapping.save();
			responseJson(mapping.toJson());
			return;
		}else if("mappingUpdate".equals(command)){
			Mapping mapping=Mapping.fromJson(paramObj.toString());
			mapping.update();
			responseJson(mapping.toJson());
			return;
		}else if("mappingDelete".equals(command)){
			JSONObject mappingJson=(JSONObject)paramObj;
			Long id=mappingJson.optLong("id");
			if(id==null){
				logger.warn("mappingDelete not found 'id'");
			}else{
				Mapping.deleteById(id);
			}
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
