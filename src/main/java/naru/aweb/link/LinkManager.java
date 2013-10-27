package naru.aweb.link;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import naru.aweb.handler.FileSystemHandler;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.mapping.Mapping;
import net.sf.json.JSONObject;

/**
 * option��`�͈ȉ��̂悤
link:{
 @LinkName:'link1'
 @MaxSubscribe:16
 @NextHandler:'naru.aweb.handler.fileSystemHandler'
 qname:{'@root':'@StdLinklet'}
 qname1:{'@root':'linklet1',subscribers:{sub1:'linklet2',sub2:'linklet3'}
 qname2:{main:'linklet1',subscribers:{sub1:'linklet2',sub2:'linklet3'}
}
 * @author naru
 *
 */
public class LinkManager {
	private static final String LINK_NAME="@LinkName";
	private static final String MAX_SUBSCRIBES="@MaxSubscribes";//���Y��qname��subscribe�ł�����,16
	private static final String NEXT_HANDLER_CLASS="@NextHandler";//linklet���N�G�X�g�ɓ��Y���Ȃ������ꍇ�̎��̃n���h��,FileSystemHandler
	private static final String ROOT_LINKLET="@Root";
	private static final String STANDARD_LINKLET="@StdLinklet";//StandardLinklet.class�̎�
	
	private static Map<String,LinkManager> instances=new HashMap<String,LinkManager>();
	public static synchronized LinkManager getInstance(JSONObject link){
		String linkName=link.getString(LINK_NAME);
		LinkManager manager=instances.get(linkName);
		if(manager==null){
			manager=new LinkManager();
			instances.put(linkName,manager);
		}
		int deployCount=0;
		for(Object q:link.keySet()){
			String qname=(String)q;
			if(qname.startsWith("@")){
				continue;
			}
			JSONObject subscribers=link.getJSONObject(qname);
			manager.deploy(qname, subscribers);
			deployCount++;
		}
		if(deployCount==0){
			/* subscribe������Ȃ���`�́A�Q�Ƃ��Ă��邾���@*/
			return manager;
		}
		int maxSubscribe=link.optInt(MAX_SUBSCRIBES, 16);
		String nextHandler=link.optString(NEXT_HANDLER_CLASS, FileSystemHandler.class.getName());
		Class nextHandlerClass;
		try {
			nextHandlerClass = Class.forName(nextHandler);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		manager.maxSubscribe=maxSubscribe;
		manager.nextHandler=nextHandlerClass;
		return manager;
	}
	
	private Map<String,LinkletWrapper> linkletWrappers=new HashMap<String,LinkletWrapper>();//qname->palet
	
	/*
	 * nextHander�̈Ӗ�
	 * ���I�R���e���c��websocket�ŁA�ÓI�R���e���c��FileHandler or VerocityHandler�Ŏ��{����̂���{
	 * Handler�œ��I�v�f���g���̂́APahtom Appplication�Ƃ��Ă͐����ł��Ȃ���Admin�͊���Handler��
	 * �������Ă��܂��Ă������߁A���̃��[�g���K�v�������B
	 * TODO admin�̊��Spa��
	 */
	//public void setNextHandler(Class nextHandler){
	//	this.nextHandler=nextHandler;
	//}
	public Class getNextHandler(){
		return nextHandler;
	}
	
	public int getMaxSubscribe(){
		return maxSubscribe;
	}
	
	private int maxSubscribe;
	private Class nextHandler;
	
	/*
	private LinkManager(int maxSubscribe,Class nextHandler){
		this.maxSubscribe=maxSubscribe;
		this.nextHandler=nextHandler;
	}
	*/
	
	/*
	 * subscriberNames= {'@root':'linklet1',sub1:'linklet2',sub2:'linklet3'}
	 */
	public synchronized LinkletWrapper deploy(String qname,JSONObject subscriberNames){
		try {
			if(linkletWrappers.get(qname)!=null){
				return null;
			}
			Linklet rootPalet=null;
			Map<String,Linklet> subscrivers=new HashMap<String,Linklet>();
			for(Object subname:subscriberNames.keySet()){
				String className=(String)subscriberNames.get((String)subname);
				Class clazz=null;
				if(STANDARD_LINKLET.equals(className)){
					clazz=StandardLinklet.class;
				}else{
					clazz=Class.forName(className);
				}
				Linklet palet = (Linklet) clazz.newInstance();
				if(ROOT_LINKLET.equals(subname)){
					rootPalet=palet;
				}else{
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
