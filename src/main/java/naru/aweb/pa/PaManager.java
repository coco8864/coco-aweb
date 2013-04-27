package naru.aweb.pa;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import naru.aweb.config.Mapping;
import naru.aweb.pa.api.PaPeer;
import naru.aweb.pa.api.Palet;

public class PaManager {
	
	private static Map<String,PaManager> instances=new HashMap<String,PaManager>();
	public static synchronized PaManager getInstance(String path){
		PaManager manager=instances.get(path);
		if(manager==null){
			manager=new PaManager();
			instances.put(path, manager);
		}
		return manager;
	}
	
	private Map<String,PaletWrapper> paletWrappers=new HashMap<String,PaletWrapper>();//qname->palet
	public PaletWrapper deploy(String qname,String paletName){
		return deploy(qname, paletName,null);
	}
	
	/*
	 * nextHanderの意味
	 * 動的コンテンツをwebsocketで、静的コンテンツをFileHandler or VerocityHandlerで実施するのが基本
	 * Handlerで動的要素を使うのは、Pahtom Appplicationとしては推奨できないがAdminは既にHandlerで
	 * 実装してしまっていたため、このルートが必要だった。
	 * TODO adminの完全pa化
	 */
	private Class nextHandler=Mapping.FILE_SYSTEM_HANDLER;
	public void setNextHandler(Class nextHandler){
		this.nextHandler=nextHandler;
	}
	public Class getNextHandler(){
		return nextHandler;
	}
	
	public synchronized PaletWrapper deploy(String qname,String paletName,Map subscriberNames){
		try {
			if(paletWrappers.get(qname)!=null){
				return null;
			}
			Class clazz = Class.forName(paletName);
			Palet rootPalet = (Palet) clazz.newInstance();
			Map<String,Palet> subscrivers=null;
			if(subscriberNames!=null){
				subscrivers=new HashMap<String,Palet>();
				for(Object subname:subscriberNames.keySet()){
					String className=(String)subscriberNames.get((String)subname);
					clazz=Class.forName(className);
					Palet palet = (Palet) clazz.newInstance();
					subscrivers.put((String)subname, palet);
				}
			}
			PaletWrapper paletWrapper = new PaletWrapper(qname, rootPalet,subscrivers);
			paletWrappers.put(qname, paletWrapper);
			return paletWrapper;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public synchronized PaletWrapper undeploy(String qname){
		PaletWrapper paletWrapper=paletWrappers.get(qname);
		if(paletWrapper!=null){
			paletWrapper.terminate();
		}
		paletWrappers.remove(qname);
		return paletWrapper;
	}
	
	public PaletWrapper getPaletWrapper(String qname){
		return paletWrappers.get(qname);
	}
	
	public synchronized Set<String> qnames(){
		return paletWrappers.keySet();
	}
	
	public void publish(String qname,String subname,Object data){
		PaletWrapper palet=getPaletWrapper(qname);
		if(palet==null){
			return;
		}
		PaPeer peer=PaPeer.create(this,null, qname, subname);
		palet.onPublish(peer, data);
		peer.unref(true);
	}
}
