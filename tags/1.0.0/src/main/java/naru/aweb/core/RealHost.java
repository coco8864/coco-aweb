package naru.aweb.core;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import naru.async.ChannelHandler;
import naru.async.ChannelHandler.IpBlockType;
import naru.aweb.util.ServerParser;
/*
�ȉ��̂悤�Ȋ����Œ�`
realHosts=h1,h2
realHost.h1.bindHost=*
realHost.h1.bindPort=1280
realHost.h1.backlog=100
//realHost.h1.services=sslproxy,proxy,web,sslweb
realHost.h1.virtuslHosts=a.com,b.com,c.com

realHost.h2.bindHost=127.0.0.1
realHost.h2.bindPort=1281
realHost.h2.backlog=100
//realHost.h2.services=sslproxy
*/	
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;


public class RealHost {
	private static Logger logger=Logger.getLogger(RealHost.class);
	/*�ȉ��Q�͓������čX�V���錟�����A�������̂��߂ɂQ�d�Ǘ�����*/
	private static Map<String,RealHost> realHosts=new HashMap<String,RealHost>();
	private static List<RealHost> realHostsList=new ArrayList<RealHost>();
	
	private static Map<String,ChannelHandler> handlers=new HashMap<String,ChannelHandler>();
	private static Map<ServerParser, RealHost> serverRealHostMap = new HashMap<ServerParser, RealHost>();

	//json�Ή�
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(RealHost.class);
		jsonConfig.setExcludes(new String[]{"inetSocketAddress","servers"});
	}
	public static RealHost fromJson(String jsonString) throws UnknownHostException{
		JSON json=JSONObject.fromObject(jsonString);
		RealHost realHost=(RealHost)JSONSerializer.toJava(json,jsonConfig);
//		realHost.init();
		return realHost;
	}
	
	public static JSON toJsonAll(){
		return JSONSerializer.toJSON(realHostsList,jsonConfig);
	}
	
	public String toJson(){
		JSON json=JSONSerializer.toJSON(this,jsonConfig);
		return json.toString();
	}
	
	public static Set<RealHost> getRealHosts() {
		Set<RealHost> hosts=new HashSet<RealHost>(realHostsList);
		return hosts;
	}
	
	public static RealHost getRealHost(String name) {
		return realHosts.get(name);
	}

	//binding�Ή�
	public static RealHost getRealHostByServer(ServerParser server) {
		return serverRealHostMap.get(server);
	}
	
	public static void addRealHost(RealHost realHost){
//		synchronized(serverRealHostMap){
//			for(ServerParser server:realHost.servers){
//				serverRealHostMap.put(server,realHost);
//			}
//		}
		synchronized(realHosts){
			realHosts.put(realHost.getName(), realHost);
			realHostsList.add(realHost);
		}
	}
	
	public static RealHost delRealHost(String name){
		if(handlers.get(name)!=null){
			unbind(name);
		}
		RealHost realHost=null;
		synchronized(realHosts){
			realHost=realHosts.remove(name);
			if(realHost!=null){
				realHostsList.remove(realHost);
			}
		}
//		if(realHost!=null){
//			synchronized(serverRealHostMap){
//				for(ServerParser server:realHost.servers){
//					serverRealHostMap.remove(server);
//				}
//			}
//		}
		return realHost;
	}
	
	private InetSocketAddress prepareBind() throws UnknownHostException{
		InetAddress inetAdder=null;
		if(!"*".equals(bindHost)){
			inetAdder=InetAddress.getByName(bindHost);
			servers.add(new ServerParser(bindHost, bindPort));
			servers.add(new ServerParser(inetAdder.getHostAddress(), bindPort));
		}else{
			servers.add(new ServerParser(InetAddress.getLocalHost().getHostAddress(), bindPort));
			servers.add(new ServerParser("0.0.0.0", bindPort));//�K�v�Ȃ�
			servers.add(new ServerParser("127.0.0.1", bindPort));
		}
		for(String virtualHost:virtualHosts){
			this.servers.add(ServerParser.create(virtualHost, bindPort));
		}
		synchronized(serverRealHostMap){
			for(ServerParser server:servers){
				serverRealHostMap.put(server,this);
			}
		}
		return new InetSocketAddress(inetAdder, bindPort);
	}
	
	private void completeUnbind(){
		synchronized(serverRealHostMap){
			for(ServerParser server:servers){
				serverRealHostMap.remove(server);
			}
		}
	}
	
	public static boolean bind(String name){
		return bind(name,false);
	}
	
	private static Pattern createPattern(String pattern){
		if(pattern==null||"".equals(pattern)){
			return null;
		}
		return Pattern.compile(pattern);
	}
	
	public static boolean bind(String name,boolean isInit){
		ChannelHandler handler=handlers.get(name);
		if(handler!=null){
			logger.error("fail to bind.already registered:"+name);
			return false;
		}
		RealHost realHost=realHosts.get(name);
		if(realHost==null){
			logger.error("fail to bind.unknown name:"+name);
			return false;
		}
		if(isInit && realHost.isInitBind==false){
			return false;
		}
		InetSocketAddress address =null;
		try {
			address=realHost.prepareBind();
		} catch (UnknownHostException e) {
			logger.error("bindHost error.",e);
			return false;
		}
		int backlog=realHost.getBacklog();
		Pattern blackPattern=createPattern(realHost.getBlackPattern());
		Pattern whitePattern=createPattern(realHost.getWhitePattern());
		handler=ChannelHandler.accept(realHost,address, backlog, DispatchHandler.class,IpBlockType.whiteBlack,blackPattern,whitePattern);
		if(handler==null){//bind�Ɏ��s����
			realHost.completeUnbind();
			logger.warn("fail to accept."+name+":"+address);
			return false;
		}
		handler.ref();//����ɏI�����recycle�����̂�h�~
		handlers.put(name, handler);
		logger.info(name + " listen start address:"+address);
		System.out.println(name + " listen start address:"+address);
		realHost.setBinding(true);
		return true;
	}
	
	public static synchronized boolean unbind(String name){
		ChannelHandler handler=handlers.remove(name);
		if(handler==null){
			logger.error("fail to deleteHost.not registered:"+name);
			return false;
		}
		handler.asyncClose(name);
		handler.unref();
		logger.info(name + " listen stop");
		System.out.println(name + " listen stop");
		
		//���A���^�C�����Ɍ����邪��ڂɌ���
		RealHost realHost=realHosts.get(name);
		realHost.setBinding(false);
		realHost.completeUnbind();
		return true;
	}
	
	public static void unbindAll(){
		Object[] names=null;
		synchronized(realHosts){
			names=handlers.keySet().toArray();
		}
		for(int i=0;i<names.length;i++){
			unbind((String)names[i]);
		}
	}
	
	public static  void bindAll(boolean isInit){
		Object[] names=null;
		synchronized(realHosts){
			names=realHosts.keySet().toArray();
		}
		for(int i=0;i<names.length;i++){
			bind((String)names[i],isInit);
		}
	}
	
//	private Configuration configuration;
	private String name;//��`��
	private Set<ServerParser> servers=new HashSet<ServerParser>();
	private boolean isBinding;//����binding�����ۂ�
	private boolean isInitBind;//�N������bind���邩�ۂ�
	private String bindHost;
	private int bindPort;
	private int backlog;
//	private InetSocketAddress inetSocketAddress;
	private Set<String> virtualHosts=new HashSet<String>();
	private String blackPattern;
	private String whitePattern;
//	private boolean isProxy;
//	private boolean isSslproxy;
//	private boolean isWeb;
//	private boolean isSslweb;

	public String getName() {
		return name;
	}

	public Set<ServerParser> getServers() {
		return servers;
	}

	public int getBacklog() {
		return backlog;
	}

	/*
	public InetSocketAddress getInetSocketAddress() {
		InetAddress inetAdder=null;
		if(!"*".equals(bindHost)){
			try {
				inetAdder=InetAddress.getByName(bindHost);
			} catch (UnknownHostException e) {
				logger.warn("fail to getByName:"+bindHost,e);
				return null;
			}
		}
		return new InetSocketAddress(inetAdder, bindPort);
	}
	*/
	
	
/*
	public boolean isProxy() {
		return isProxy;
	}

	public boolean isSslproxy() {
		return isSslproxy;
	}

	public boolean isWeb() {
		return isWeb;
	}

	public boolean isSslweb() {
		return isSslweb;
	}
*/
	public RealHost(String name,String bindHost,int bindPort,int backlog) throws UnknownHostException{
		this(name, bindHost, bindPort, backlog, null);
	}
	
	public RealHost(){
	}
	
	public void setName(String name) {
		this.name = name;
	}

	public void setBindHost(String bindHost) {
		this.bindHost = bindHost;
	}

	public void setBindPort(int bindPort) {
		this.bindPort = bindPort;
	}

	public void setBacklog(int backlog) {
		this.backlog = backlog;
	}

	public void setVirtualHosts(Set<String> virtualHosts) {
		this.virtualHosts = virtualHosts;
	}
	
	public RealHost(String name,String bindHost,int bindPort,int backlog,String[] virtualHosts) throws UnknownHostException{
		this.isInitBind=true;
		this.name=name;
		this.bindHost=bindHost;
		this.bindPort=bindPort;
		this.backlog=backlog;
		if(virtualHosts!=null){
			for(int i=0;i<virtualHosts.length;i++){
				this.virtualHosts.add(virtualHosts[i]);
			}
		}
//		init();
	}

	public String getBindHost() {
		return bindHost;
	}
	public int getBindPort() {
		return bindPort;
	}
	public Set<String> getVirtualHosts() {
		return virtualHosts;
	}
	public boolean isBinding() {
		return isBinding;
	}
	public boolean isInitBind() {
		return isInitBind;
	}
	private void setBinding(boolean isBinding) {
		this.isBinding = isBinding;
	}
	public void setInitBind(boolean isInitBind) {
		this.isInitBind = isInitBind;
	}

	public String getBlackPattern() {
		return blackPattern;
	}

	public String getWhitePattern() {
		return whitePattern;
	}

	public void setBlackPattern(String blackPttern) {
		this.blackPattern = blackPttern;
	}

	public void setWhitePattern(String whitePattern) {
		this.whitePattern = whitePattern;
	}
}