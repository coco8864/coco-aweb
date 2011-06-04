package naru.aweb.admin;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
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
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
			completeResponse("205");
			return;
		}else if("reloadMappings".equals(command)){
			config.getMapper().reloadMappings();
			completeResponse("205");
			return;
		}
		completeResponse("404");
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
