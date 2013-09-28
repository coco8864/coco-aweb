package naru.aweb.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import naru.aweb.config.Config;
import naru.aweb.core.RealHost;
import naru.aweb.mapping.Mapping;
import naru.aweb.mapping.MappingResult;
import naru.aweb.mapping.Mapping.SourceType;
import naru.aweb.util.HeaderParser;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

public class ProxyPacHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(ProxyPacHandler.class);
	private static Config config=Config.getConfig();
	
	/*
	 * {pacId:id1}
	 * 
	 * pacDef:{pacIds:['id1','id2'],excludeDomains:['a.*','b.*'],includeDomains:['b.*','c.*']}
	 * 
	 */
	private static final String PAC_TEXT="pacText";
	
	private void collectDomain(Mapping m,List pacIds,Map<String,String> plainDomains,Map<String,String> secureDomains){
		if(m.isEnabled()==false){
			return;
		}
		if(m.getSourceType()!=SourceType.PROXY&&m.getSourceType()!=SourceType.WS_PROXY){
			return;
		}
		String pacId=(String)m.getOption("pacId");
		if(!pacIds.contains(pacId)){
			return;
		}
		RealHost realHost=RealHost.getRealHost(m.getRealHostName());
		int port=realHost.getBindPort();
		switch(m.getSecureType()){
		case PLAIN:
			plainDomains.put(m.getSourceServer(), Integer.toString(port));
			break;
		case SSL:
			secureDomains.put(m.getSourceServer(), Integer.toString(port));
			break;
		}
	}
	
	/* ÉhÉÅÉCÉìñºÇÕç≈í∑àÍívèáÇ…ÇµÇΩÇ¢ÇÃÇ≈ÅAï∂éöóÒÇÃí∑Ç¢èáî‘Ç…ï¿Ç◊ÇÈ */
	private static class DomainComparator implements java.util.Comparator<String>{
		@Override
		public int compare(String o1, String o2) {
			if(o1==null && o2==null){
				return 0;
			}
			if(o1==null){
				return -1;
			}
			if(o2==null){
				return 1;
			}
			int l1=o1.length();
			int l2=o2.length();
			if(l1>l2){
				return -1;
			}else if(l2<l1){
				return 1;
			}
			return o1.hashCode()-o2.hashCode();
		}
	}
	private static DomainComparator domainComparator=new DomainComparator();
	
	private String createPac(MappingResult mapping,String selfHost){
		JSONObject pacDef=(JSONObject)mapping.getOption("pacDef");
		JSONArray pacIds=pacDef.getJSONArray("pacIds");
		JSONArray excludeDomains=pacDef.getJSONArray("excludeDomains");
		JSONArray includeDomains=pacDef.getJSONArray("includeDomains");
		Map<String,String> plainDomains=(Map<String,String>)new TreeMap(domainComparator);
		Map<String,String> secureDomains=(Map<String,String>)new TreeMap(domainComparator);
		Iterator<Mapping> itr=config.getMapper().mappingIterator();
		while(itr.hasNext()){
			Mapping m=itr.next();
			collectDomain(m,pacIds,plainDomains,secureDomains);
		}
		Map param=new HashMap();
		param.put("selfHost", selfHost);
		param.put("excludeDomains", excludeDomains);
		param.put("includeDomains", includeDomains);
		param.put("plainDomains", plainDomains);
		param.put("secureDomains", secureDomains);
		String pac=config.getProxyPac(param);
		return pac;
	}

	public void onRequestBody(){
		HeaderParser requestHeader=getRequestHeader();
		String selfHost=requestHeader.getHeader(HeaderParser.HOST_HEADER);
		int pos=selfHost.indexOf(":");
		if(pos>0){
			selfHost=selfHost.substring(0, pos);
		}
		MappingResult mapping=getRequestMapping();
		String mapKey=PAC_TEXT+selfHost;
		String pac=(String)getAttribute(SCOPE.MAPPING, mapKey);
//		String pac=(String)mapping.getMapping().getOption(mapKey);
		if(pac==null){
			pac=createPac(mapping,selfHost);
			setAttribute(SCOPE.MAPPING, mapKey,pac);
//			mapping.getMapping().setOption(mapKey,pac);
		}
		setContentType("application/x-ns-proxy-autoconfig");
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
