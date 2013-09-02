package naru.aweb.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.Mapping.SourceType;
import naru.aweb.core.RealHost;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class ProxyPacHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(ProxyPacHandler.class);
	private static Config config=Config.getConfig();
	
	/*
	 * {pac:id1}
	 * 
	 * pacDef:{ids:['id1','id2'],exceptDomains:['*.xx','xx']}
	 * 
	 */
	private static final String PAC_TEXT="pacText";
	
	private void collectDomain(Mapping m,List ids,Map<String,Set<String>> domains){
		if(m.isEnabled()==false){
			return;
		}
		if(m.getSourceType()!=SourceType.PROXY&&m.getSourceType()!=SourceType.WS_PROXY){
			return;
		}
		String pacId=(String)m.getOption("pac");
		if(!ids.contains(pacId)){
			return;
		}
		String realHost=m.getRealHostName();
		Set<String> ds=domains.get(realHost);
		if(ds==null){
			ds=new HashSet();
			domains.put(realHost, ds);
		}
		ds.add(m.getSourceServer());
	}
	private String createPac(MappingResult mapping){
		JSONObject pacDef=(JSONObject)mapping.getOption("pacDef");
		JSONArray ids=pacDef.getJSONArray("ids");
		JSONArray exceptDomains=pacDef.getJSONArray("exceptDomains");
		Map<String,Set<String>> domains=(Map<String,Set<String>>)new HashMap();;
		
		Iterator<Mapping> itr=config.getMapper().mappingIterator();
		while(itr.hasNext()){
			Mapping m=itr.next();
			collectDomain(m,ids,domains);
//			RealHost realHost=RealHost.getRealHost(m.getRealHostName());
		}
		return null;
	}

	public void startResponseReqBody(){
		if(false){
		MappingResult mapping=getRequestMapping();
		String pac=(String)mapping.getOption(PAC_TEXT);
		if(pac==null){
			pac=createPac(mapping);
			mapping.setOption(PAC_TEXT, pac);
		}
		}
		HeaderParser requestHeader=getRequestHeader();
		String localHost=requestHeader.getHeader(HeaderParser.HOST_HEADER);
		String pac=config.getProxyPac(localHost);
		setContentType("text/plain");
		completeResponse("200", pac);
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		asyncClose(userContext);
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:" +getChannelId());
		super.onFinished();
	}
	
}
