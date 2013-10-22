package naru.aweb.link;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.mapping.Mapping;

public class LinkManager {
	
	private static Map<String,LinkManager> instances=new HashMap<String,LinkManager>();
	public static synchronized LinkManager getInstance(String path){
		LinkManager manager=instances.get(path);
		if(manager==null){
			manager=new LinkManager();
			instances.put(path, manager);
		}
		return manager;
	}
	
	private Map<String,LinkletWrapper> linkletWrappers=new HashMap<String,LinkletWrapper>();//qname->palet
	public LinkletWrapper deploy(String qname,String paletName){
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
	
	public synchronized LinkletWrapper deploy(String qname,String linkletName,Map subscriberNames){
		try {
			if(linkletWrappers.get(qname)!=null){
				return null;
			}
			Class clazz=null;
			if(linkletName==null){
				clazz=StandardLinklet.class;
			}else{
				clazz = Class.forName(linkletName);
			}
			Linklet rootPalet = (Linklet) clazz.newInstance();
			Map<String,Linklet> subscrivers=null;
			if(subscriberNames!=null){
				subscrivers=new HashMap<String,Linklet>();
				for(Object subname:subscriberNames.keySet()){
					String className=(String)subscriberNames.get((String)subname);
					clazz=Class.forName(className);
					Linklet palet = (Linklet) clazz.newInstance();
					subscrivers.put((String)subname, palet);
				}
			}
			LinkletWrapper paletWrapper = new LinkletWrapper(qname, rootPalet,subscrivers);
			linkletWrappers.put(qname, paletWrapper);
			return paletWrapper;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public synchronized LinkletWrapper undeploy(String qname){
		LinkletWrapper linkletWrapper=linkletWrappers.get(qname);
		if(linkletWrapper!=null){
			linkletWrapper.terminate();
		}
		linkletWrappers.remove(qname);
		return linkletWrapper;
	}
	
	public LinkletWrapper getLinkletWrapper(String qname){
		return linkletWrappers.get(qname);
	}
	
	public synchronized Set<String> qnames(){
		return linkletWrappers.keySet();
	}
	
	public void publish(String qname,String subname,Object data){
		LinkletWrapper linklet=getLinkletWrapper(qname);
		if(linklet==null){
			return;
		}
		LinkPeer peer=LinkPeer.create(this,null, qname, subname);
		linklet.onPublish(peer, data);
		peer.unref(true);
	}
}
