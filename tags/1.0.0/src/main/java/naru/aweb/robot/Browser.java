package naru.aweb.robot;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.aweb.config.AccessLog;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebClientConnection;
import naru.aweb.http.WebClientHandler;
import naru.aweb.queue.QueueManager;
import naru.aweb.util.ServerParser;

public class Browser extends PoolBase {
	private static Logger logger = Logger.getLogger(Browser.class);
	private final int MAX_DOMAIN_CONNECTION=2;
	
	private Scenario scenario;
	private String name;
	private List<Caller> startCallers=new ArrayList<Caller>();
	private boolean isAsyncStop=true;//asyncStop�v�����󂯕t�������ۂ�?
	boolean isProcessing=false;//���������ۂ�?

	private Map<String,WebClientConnection> connections=new HashMap<String,WebClientConnection>();
	private Map<WebClientConnection,LinkedList<WebClientHandler>> webClientQueue=new HashMap<WebClientConnection,LinkedList<WebClientHandler>>();
	
	//���s�\��caller��
	private LinkedList<Caller> callerQueue=new LinkedList<Caller>();
	//��������caller��connection�̑g
	private Map<Caller,WebClientHandler> processingClientHandler=new HashMap<Caller,WebClientHandler>();
	
	
	public static Browser cleate(Scenario scenario,AccessLog[] accessLogs,
			boolean isCallerkeepAlive,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		Browser browser=(Browser)PoolManager.getInstance(Browser.class);
		browser.scenario=scenario;
		browser.setup(accessLogs, isCallerkeepAlive,isResponseHeaderTrace,isResponseBodyTrace);
		return browser;
	}
	
	/**
	 * �P�ꃊ�N�G�X�g�̃V�~�����[�g�������Ȃ�
	 * @param isHttps �ڑ���T�[�ossl��,requetHeader.isProxy��true�̏ꍇ���������(false����)
	 * @param requestHeader
	 * @param requestBodyBuffer
	 * @return
	 */
	public static Browser cleate(boolean isHttps,HeaderParser requestHeader,ByteBuffer[] requestBodyBuffer){
		Browser browser=(Browser)PoolManager.getInstance(Browser.class);
		browser.setup(isHttps, requestHeader, requestBodyBuffer);
		return browser;
	}
	
	@Override
	public void recycle() {
		scenario=null;
		startCallers.clear();
		connections.clear();
		processingClientHandler.clear();
		isAsyncStop=true;
		isProcessing=false;
	}
	
	public Browser dup(){
		Browser browser=(Browser) PoolManager.getInstance(Browser.class);
		browser.scenario=scenario;
		Iterator<String> domainItr=connections.keySet().iterator();
		while(domainItr.hasNext()){
			String domain=domainItr.next();
			WebClientConnection connection=connections.get(domain);
			connection.ref();
			browser.connections.put(domain, connection);
		}
		Iterator<WebClientConnection> itr=webClientQueue.keySet().iterator();
		while(itr.hasNext()){
			WebClientConnection connection=itr.next();
			browser.setupClient(connection);
		}
		for(Caller startCaller:startCallers){
			browser.startCallers.add(startCaller.dup(browser));
		}
		
		return browser;
	}
	
	private WebClientConnection setupConnection(boolean isHttps,String server,int port){
		String domain=Boolean.toString(isHttps)+server+port;
		WebClientConnection connection=connections.get(domain);
		if(connection==null){
			connection=WebClientConnection.create(isHttps,server,port);
			connections.put(domain,connection);
			setupClient(connection);
		}
		return connection;
	}
	
	private void cleanupConnection(){
		Iterator<WebClientConnection> itr=connections.values().iterator();
		while(itr.hasNext()){
			WebClientConnection connection=itr.next();
			connection.unref();
		}
		connections.clear();
	}
	
	private WebClientHandler popClient(WebClientConnection connection){
		LinkedList<WebClientHandler> clients=webClientQueue.get(connection);
		if(clients==null){
			return null;
		}
		synchronized(clients){
			if(clients.size()==0){
				return null;
			}
			return clients.removeLast();
		}
	}
	
	private void pushClient(WebClientConnection connection,WebClientHandler handler){
		LinkedList<WebClientHandler> clients=webClientQueue.get(connection);
		synchronized(clients){
			clients.addLast(handler);
		}
	}
	
	private void setupClient(WebClientConnection connection){
		LinkedList<WebClientHandler> clients=webClientQueue.get(connection);
		if(clients==null){
			clients=new LinkedList<WebClientHandler>();
			webClientQueue.put(connection, clients);
			for(int i=0;i<MAX_DOMAIN_CONNECTION;i++){
				WebClientHandler clientHandler=WebClientHandler.create(connection);
				clientHandler.ref();//�ؒf��ɉ�������̂�h��
				clients.add(clientHandler);
			}
		}
	}
	
	private void cleanupClients(){
		logger.debug("#cleanupClients");
		Iterator<LinkedList<WebClientHandler>> listItr=webClientQueue.values().iterator();
		while(listItr.hasNext()){
			LinkedList<WebClientHandler> clientHandlers=listItr.next();
			for(WebClientHandler clientHandler:clientHandlers){
				if(clientHandler.isConnect()){
					clientHandler.asyncClose(null);
				}
				clientHandler.setWebClientConnection(null);
				clientHandler.unref();
			}
		}
		webClientQueue.clear();
	}
	
	private void cleanupCaller(List<Caller> callers){
		if(callers==null){
			return;
		}
		for(Caller caller:callers){
			if(caller==null){
				continue;
			}
			List<Caller> nextCallers=caller.getNextCallers();
			cleanupCaller(nextCallers);
			caller.unref();
		}
	}
	
	public void cleanup(){
		logger.debug("#cleanup");
		cleanupConnection();
		cleanupCaller(startCallers);
		cleanupClients();
		startCallers.clear();
	}
	
	private Caller createCaller(URL url,WebClientConnection connection,Caller nextCaller,boolean isCallerkeepAlive){
		HeaderParser requestHeader=(HeaderParser) PoolManager.getInstance(HeaderParser.class);
		requestHeader.setMethod("GET");
		requestHeader.setPath(url.getFile());
		requestHeader.setQuery(url.getQuery());
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_11);
		requestHeader.setHeader("User-Agent", "Mozilla/4.0");
		String requestLine=connection.getRequestLine(requestHeader);
		ByteBuffer[] requestHeaderBuffer=connection.getRequestHeaderBuffer(requestLine,requestHeader, isCallerkeepAlive);
		requestHeader.unref(true);
		Caller caller=Caller.create(this, connection, nextCaller, requestHeaderBuffer, requestLine, null,null);
		return caller;
	}
	
	private Caller createCaller(AccessLog accessLog,WebClientConnection connection,Caller nextCaller,boolean isCallerkeepAlive){
		HeaderParser requestHeader=HeaderParser.createByStore(accessLog.getRequestHeaderDigest());
		if(requestHeader==null){
			return null;
		}
		if(HeaderParser.CONNECT_METHOD.equalsIgnoreCase(requestHeader.getMethod())){
			//CONNECT���\�b�h�̏ꍇ�́A�^�̃w�b�_��body�����Ɋi�[����Ă���
			ByteBuffer[] realHeader=requestHeader.getBodyBuffer();
			requestHeader.recycle();
			for(ByteBuffer buffer:realHeader){
				requestHeader.parse(buffer);
			}
			PoolManager.poolArrayInstance(realHeader);
			if(!requestHeader.isParseEnd()){
				return null;
			}
		}
		String requestLine=connection.getRequestLine(requestHeader);
		ByteBuffer[] requestHeaderBuffer=connection.getRequestHeaderBuffer(requestLine,requestHeader, isCallerkeepAlive);
		requestHeader.unref(true);
		ByteBuffer[] requestBodyBuffer=null;
		String responseBodyDigest=accessLog.getRequestBodyDigest();
		if(responseBodyDigest!=null){
			requestBodyBuffer=DataUtil.toByteBuffers(responseBodyDigest);
		}
		Caller caller=Caller.create(this, connection, nextCaller, requestHeaderBuffer, requestLine, requestBodyBuffer,accessLog);
		return caller;
	}

	private Caller createCaller(HeaderParser requestHeader,ByteBuffer[] requestBodyBuffer,WebClientConnection connection){
		String requestLine=connection.getRequestLine(requestHeader);
		ByteBuffer[] requestHeaderBuffer=connection.getRequestHeaderBuffer(requestLine,requestHeader, false);
		requestHeader.unref(true);
		Caller caller=Caller.create(this, connection, null, requestHeaderBuffer, requestLine, requestBodyBuffer,null);
		return caller;
	}
	
	/**
	 * @param urls
	 */
	public void setup(URL[] urls,boolean isCallerkeepAlive){
		Caller nextCaller=null;
		for(int i=urls.length-1;i>=0;i--){
			boolean isHttps="https".equals(urls[i].getProtocol());
			String server=urls[i].getHost();
			int port=urls[i].getPort();
			if(port<=0){
				if(isHttps){
					port=443;
				}else{
					port=80;
				}
			}
			WebClientConnection connection=setupConnection(isHttps,server,port);
			Caller caller=createCaller(urls[i], connection, nextCaller, isCallerkeepAlive);
			nextCaller=caller;
		}
		startCallers.add(nextCaller);
	}
	
	/**
	 * @param urls
	 */
	public void setup(AccessLog[] accessLogs,boolean isCallerkeepAlive,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		Caller nextCaller=null;
		for(int i=accessLogs.length-1;i>=0;i--){
			AccessLog accessLog=accessLogs[i];
			if(accessLog.getRequestHeaderDigest()==null || accessLog.getRequestLine()==null){
				throw new RuntimeException("no request header or requestLine");
			}
			String resolveOrigin=accessLog.getResolveOrigin();
			char destinationType = accessLog.getDestinationType();
			boolean isHttps;
			ServerParser server;
			switch(destinationType){
			case AccessLog.DESTINATION_TYPE_HTTP:
				server=ServerParser.parse(resolveOrigin,80);
				isHttps=false;
				break;
			case AccessLog.DESTINATION_TYPE_HTTPS:
				server=ServerParser.parse(resolveOrigin,443);
				isHttps=true;
				break;
			default:
				throw new RuntimeException("destinationType error."+destinationType);
			}
			WebClientConnection connection=setupConnection(isHttps,server.getHost(),server.getPort());
			server.unref(true);
			Caller caller=createCaller(accessLog, connection, nextCaller, isCallerkeepAlive);
			if(caller==null){
				throw new RuntimeException("fail to createCaller.");
			}
			caller.setResponseHeaderTrace(isResponseHeaderTrace);
			caller.setResponseBodyTrace(isResponseBodyTrace);
			nextCaller=caller;
		}
		startCallers.add(nextCaller);
	}
	
	/**
	 * @param urls
	 */
	public void setup(boolean isHttps,HeaderParser requestHeader,ByteBuffer[] requestBodyBuffer){
		ServerParser server=requestHeader.getServer();
		if(requestHeader.isProxy()){
			isHttps=false;
		}
		WebClientConnection connection=setupConnection(isHttps,server.getHost(),server.getPort());
		Caller caller=createCaller(requestHeader,requestBodyBuffer, connection);
		caller.setResponseHeaderTrace(true);
		caller.setResponseBodyTrace(true);
		String resolveDigest=AccessLog.calcResolveDigest(requestHeader.getMethod(),
				isHttps,
				server.toString(),
				requestHeader.getPath(),requestHeader.getQuery());
		caller.setResolveDigest(resolveDigest);
		
		startCallers.add(caller);
	}
	private String chId;//�I�����ɒʒm����Queue��channelId
	private long startTime;
	
	public void start(){
		start(null);
	}
	
	/**
	 * chId���ʒm�����̂́Atrace1�V���b�g���s�̏ꍇ�����B
	 * 1)caller���P�B
	 * 2)trace���w�肳��Ă���B
	 * ���̏ꍇ�A�񓯊��ʒm����AccessLog��id��ʒm����B
	 * 
	 * @param chId
	 */
	public void start(String chId){
		logger.debug("#start startCallers.size():"+startCallers.size());
		this.chId=chId;
		this.startTime=System.currentTimeMillis();
		synchronized(this){
			if(isAsyncStop==false || isProcessing){
				throw new RuntimeException("isAsyncStop:"+isAsyncStop + ":isProcessing:"+isProcessing);
			}
			isProcessing=true;
			isAsyncStop=false;
		}
		for(Caller caller:startCallers){
			callerQueue.add(caller);
		}
		dispatchs(startCallers.size());
	}
	
	public void asyncStop(){
		synchronized(this){
			if(isAsyncStop){
				return;
			}
			isAsyncStop=true;
		}
	}
	
	private void dispatchs(int n){
		for(int i=0;i<n;i++){
			dispatch();
		}
	}
	
	private void dispatch(){
		logger.debug("#dispatch");
		Caller caller=null;
		WebClientHandler clientHandler=null;
		synchronized(this){
			if(callerQueue.size()!=0){
				caller=callerQueue.removeLast();
			}
			if(!isAsyncStop && caller!=null){
				clientHandler=popClient(caller.getConnection());
				if(clientHandler!=null){
					if(!clientHandler.isKeepAlive()){
						clientHandler.unref();
						clientHandler=WebClientHandler.create(caller.getConnection());
						clientHandler.ref();
					}
//					int size1=processingClientHandler.size();
					processingClientHandler.put(caller, clientHandler);
//					int size2=processingClientHandler.size();
//					System.out.println("dispatch+"+this.hashCode()+":size1:"+size1+":size2:"+size2+":caller:"+caller+":clientHandler:"+clientHandler);
				}
			}
			if(clientHandler==null && caller!=null){
				//caller���擾���Ă͌�����client�������Ȃ��������߁A�ԋp
				callerQueue.addLast(caller);
				caller=null;
			}
			if(processingClientHandler.size()==0){//�����C�x���g����������]�n���Ȃ�
				callerQueue.clear();
				isProcessing=false;
				isAsyncStop=true;
			}
		}
		if(clientHandler!=null && caller!=null){
			caller.startRequest(clientHandler);
		}else if(!isProcessing){
			if(scenario!=null){
				logger.debug("call onBrowserEnd");
				scenario.onBrowserEnd(this);
			}
			if(chId!=null){//accessLog�ɐݒ肷��O�ɏI�����...�ُ탋�[�g
				QueueManager queueManager=QueueManager.getInstance();
				queueManager.publish(chId, "browser end.time:"+(System.currentTimeMillis()-startTime));
			}
		}
	}
	
	public void onRequestEnd(Caller caller,AccessLog accessLog){
		logger.debug("#onRequestEnd");
		if(scenario!=null){
			scenario.onRequest(accessLog);
		}else{
			//Scenari�Ȃ��ł�т����ꂽ�ꍇ�́AAccessLog���L�^����
			if(chId!=null){
				accessLog.setChId(chId);
				chId=null;
			}
			accessLog.setPersist(true);
			accessLog.decTrace();
		}
		
		List<Caller> nextCallers=caller.getNextCallers();
		synchronized(this){
			//clientHandler�̍ė��p
//			int size1=processingClientHandler.size();
			WebClientHandler clientHandler=processingClientHandler.remove(caller);
//			int size2=processingClientHandler.size();
//			System.out.println("onRequestEnd+"+this.hashCode()+":size1:"+size1+":size2:"+size2+":caller:"+caller+":clientHandler:"+clientHandler);
			WebClientConnection connection=caller.getConnection();
			pushClient(connection, clientHandler);
			if(nextCallers!=null){
				for(Caller nextCaller:nextCallers){
					callerQueue.addLast(nextCaller);
				}
			}
		}
		if(scenario==null){
			//scenario�Ȃ��̏ꍇ�́A�P���Ăяo���Ɍ��܂��Ă���
			cleanup();
			unref();
			return;
		}
		
		int dispatchCount=nextCallers.size();
		if(dispatchCount==0){
			dispatchCount++;
		}
		dispatchs(dispatchCount);
	}

	public boolean isProcessing() {
		return isProcessing;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Scenario getScenario() {
		return scenario;
	}

}