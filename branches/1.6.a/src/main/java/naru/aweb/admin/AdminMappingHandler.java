package naru.aweb.admin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.ParameterParser;
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
//			List<String>userRoles=getAuthSession().getUser().getRolesList();
//			List<Mapping> allowMappings=config.getMapper().getRoleMappings(userRoles);
			String order=parameter.getParameter("order");
			Collection<Mapping> mappings=Mapping.query(null, -1, 0, order);
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
			setContentType("text/html");
			completeResponse("200",//TODO vsfにfowardするのがスマート
				"<script>parent.mappingTableRefresh();</script>");
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
			String trace=parameter.getParameter("trace");
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
//			if(mapping.getOptions()==null){
//				mapping.setOptions("{}");
//			}
			if("true".equalsIgnoreCase(trace)){
				mapping.setOption("logType", "trace");
			}else{
				mapping.setOption("logType", "none");
			}
			String script=null;
			//uploadファイルがあれば、それでサイトを構築する
			if("FILE".equalsIgnoreCase(mappingDestinationType)){
				script=createWebsite(mapping,mappingSourcePath,mappingSecureType,item);
			}else{
				script=createReverseProxy(mapping,destinationUrl);
			}
			config.getMapper().reloadMappings();
			completeResponse("200",script);
			return;
		}else if("reloadMappings".equals(command)){
			config.getMapper().reloadMappings();
			completeResponse("204");
			return;
		}
		completeResponse("404");
	}
	
	private static String SCRIPT_MAPPING_REFRESH="<script>parent.mappingTableRefresh();</script>";
	
	/* システムが使うディレクトリには配備させない */
	private static String RESERVED_APP_NAMES="admin,auth,docroot";
	
	/*　website */
	private String createWebsite(Mapping mapping,String mappingSourcePath,String mappingSecureType,DiskFileItem item){
		if(mappingSourcePath==null||mappingSourcePath.length()==0){
			mappingSourcePath="/";
		}
		String path=item.getName();
		if(RESERVED_APP_NAMES.indexOf(path)>=0){
			return "<script>alert('reserved path name error');</script>";
		}
		int pos=path.lastIndexOf(".");
		if(pos>=0){
			path=path.substring(0,pos);
		}
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
			int count=StreamUtil.unzip(deployDir, is);
			if(count==0){
				return "<script>alert('upload file error');</script>";
			}
			mapping.save();
		} catch (IOException e) {
			logger.warn("createWebsite",e);
		}
		return SCRIPT_MAPPING_REFRESH;
	}
	
	/* リバースproxy決め打ち*/
	private String createReverseProxy(Mapping mapping,String destinationUrl){
		StringBuilder schemeSb=new StringBuilder();
		StringBuilder pathSb=new StringBuilder();
		ServerParser server=ServerParser.parseUrl(destinationUrl,schemeSb,pathSb);
		if(server==null){
			return "<script>alert('url fomat error');</script>";
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
			return "<script>alert('url scheme error');</script>";
		}
		mapping.save();
		return SCRIPT_MAPPING_REFRESH;
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
			completeResponse("204");
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
