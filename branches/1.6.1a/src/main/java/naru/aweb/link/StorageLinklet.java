package naru.aweb.link;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import naru.aweb.link.api.LinkMsg;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.link.api.LinkletCtx;

import org.apache.log4j.Logger;

public class StorageLinklet implements Linklet{
	private static final String SCOPE_APL_GLOBAL="aplGlobal";
	private static final String SCOPE_APL_USER="aplUser";
	private static final String TYPE_GET_ITEM="getItem";
	private static final String TYPE_SET_ITEM="setItem";
	private static final String TYPE_REMOVE_ITEM="removeItem";
	private static final String TYPE_KEYS="keys";
	private static final String TYPE_CHANGE_ITEM="changeItem";
	
	private static Logger logger=Logger.getLogger(StorageLinklet.class);
	private LinkletCtx ctx;
	
	private static class StorageInfo{
		private StorageInfo(String loginId,String storName){
			this.loginId=loginId;
			this.storName=storName;
		}
		private Set<LinkPeer> peers=new HashSet<LinkPeer>();
		private Map storage=new HashMap();
		private String loginId;
		private String storName;
		
		private void addPeer(LinkPeer peer){
			synchronized(peers){
				peers.add(peer);
			}
		}
		private void removePeer(LinkPeer peer){
			synchronized(peers){
				peers.remove(peer);
			}
		}
		
		private synchronized Object getItem(String key){
			return storage.get(key);
		}
		private synchronized Object setItem(String key,Object value){
			return storage.put(key, value);
		}
		private synchronized Object removeItem(String key){
			return storage.remove(key);
		}
		private synchronized Set<String> keys(){
			return storage.keySet();
		}
		private void publish(LinkletCtx ctx,Object data,LinkPeer peer){
			ctx.message(data, peers,peer);
		}
	}
	
	private Map<String,StorageInfo> globalStorage=new HashMap<String,StorageInfo>();
	private Map<String,Map<String,StorageInfo>> userStorage=new HashMap<String,Map<String,StorageInfo>>();
	
	private StorageInfo getGlobalStorageInfo(String storName){
		StorageInfo info=globalStorage.get(storName);
		if(info==null){
			info=new StorageInfo(null,storName);
			globalStorage.put(storName, info);
		}
		return info;
	}
	
	private StorageInfo getUserStorageInfo(String loginId,String storName){
		Map<String,StorageInfo> storages=userStorage.get(loginId);
		if(storages==null){
			storages=new HashMap<String,StorageInfo>();
			userStorage.put(loginId,storages);
		}
		StorageInfo info=storages.get(storName);
		if(info==null){
			info=new StorageInfo(loginId,storName);
			storages.put(storName, info);
		}
		return info;
	}
	
	private StorageInfo getStorageInfo(LinkPeer peer){
		StorageInfo info=(StorageInfo)peer.getAttribute("StorageInfo");
		if(info!=null){
			return info;
		}
		String subname=peer.getSubname();
		if(subname.startsWith(SCOPE_APL_GLOBAL)){
			String storName=subname.substring(SCOPE_APL_GLOBAL.length());
			info=getGlobalStorageInfo(storName);
		}else if(subname.startsWith(SCOPE_APL_USER)){
			String storName=subname.substring(SCOPE_APL_USER.length());
			String loginId=peer.getLoginId();
			info=getUserStorageInfo(loginId,storName);
		}else{
			throw new IllegalArgumentException(subname);
		}
		peer.setAttribute("StorageInfo", info);
		return info;
	}

	@Override
	public void init(String qname,String subname,LinkletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(String reason) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onTimer() {
		// TODO Auto-generated method stub
	}

	@Override
	public void onSubscribe(LinkPeer peer) {
		StorageInfo info=getStorageInfo(peer);
		info.peers.add(peer);
	}

	@Override
	public void onUnsubscribe(LinkPeer peer, String reason) {
		StorageInfo info=getStorageInfo(peer);
		info.peers.remove(peer);
	}

	@Override
	public void onPublish(LinkPeer peer, LinkMsg data) {
		StorageInfo info=getStorageInfo(peer);
		String type=data.getString("type");
		String key=data.getString("key");
		if(TYPE_GET_ITEM.equals(type)){
			Object value=info.getItem(key);
			data.put("value", value);
			peer.message(data);
		}else if(TYPE_SET_ITEM.equals(type)){
			Object oldValue=info.getItem(key);
			Object value=data.get("value");
			info.setItem(key, value);
			//{type:'changeItem',key:key,oldValue:oldValue,value:value}
			data.put("type", TYPE_CHANGE_ITEM);
			data.put("oldValue", oldValue);
			info.publish(ctx, data,peer);
		}else if(TYPE_REMOVE_ITEM.equals(type)){
			Object oldValue=info.getItem(key);
			info.removeItem(key);
			//{type:'changeItem',key:key,oldValue:oldValue}
			data.put("type", TYPE_CHANGE_ITEM);
			data.put("oldValue", oldValue);
			info.publish(ctx, data,peer);
		}else if(TYPE_KEYS.equals(type)){
			Set<String> keys=info.keys();
			data.put("keys", keys);
			peer.message(data);
		}
	}
	
	@Override
	public void onPublish(LinkPeer peer, String data) {
		throw new UnsupportedOperationException("onPublish String");
	}
}
