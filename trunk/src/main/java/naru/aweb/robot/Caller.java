package naru.aweb.robot;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.store.Store;
import naru.aweb.config.AccessLog;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientConnection;
import naru.aweb.http.WebClientHandler;

/**
 * ���x�ł��Ăяo����Web���N�G�X�^
 * 
 * @author naru
 */
public class Caller extends PoolBase implements WebClient/*,BufferGetter*/ {
	private static Logger logger = Logger.getLogger(Caller.class);
	private Browser browser;
	private List<Caller> nextCallers=new ArrayList<Caller>();
	
	/**
	 * http[s]://server:port�`���̕�����
	 */
	private WebClientConnection connection;
	
//	private HeaderParser requestHeader;//����Ăяo������requestHeaderBuffer�ɕϊ����Ďg��
	private ByteBuffer[] requestHeader;
	private String requestHeaderDigest;
	private String requestLine;
	private long requestContentLength;
	private ByteBuffer[] requestBody;
	private String requestBodyDigest;
	private MessageDigest messageDigest;
	private AccessLog orgAccessLog;//���ƂȂ���AccessLog
	private String resolveDigest;
	
//	private WebClientHandler webClientHandler;
	private AccessLog accessLog;
	private CallScheduler scheduler;
	private long responseLength;
	
	private boolean isResponseHeaderTrace=false;//TODO �O������炤�K�v����
	private boolean isResponseBodyTrace=false;//TODO �O������炤�K�v����
	
	private Store responseBodyStore=null;
	
	public void recycle() {
		if(requestHeader!=null){
			PoolManager.poolBufferInstance(requestHeader);
			requestHeader=null;
		}
		requestContentLength=0;
		if(requestBody!=null){
			PoolManager.poolBufferInstance(requestBody);
			requestBody=null;
		}
		if(messageDigest==null){
			try {
				messageDigest=MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				logger.error("MessageDigest.getInstance error.",e);
			}
		}else{
			messageDigest.reset();
		}
		setConnection(null);
		nextCallers.clear();
		resolveDigest=null;
		super.recycle();
	}
	
	public static Caller create(Browser browser,WebClientConnection connection,Caller nextCaller,
			ByteBuffer[] requestHeaderBuffer,String requestLine,
			ByteBuffer[] requestBody,AccessLog orgAccessLog){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		caller.setup(browser, connection, nextCaller,requestLine,requestHeaderBuffer,requestBody);
		caller.orgAccessLog=orgAccessLog;
		return caller;
	}
	
	public static Caller create(Browser browser,WebClientConnection connection,Caller[] nextCallers,
			ByteBuffer[] requestHeaderBuffer,String requestLine,ByteBuffer[] requestBody){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		caller.setup(browser, connection, nextCallers,requestLine,requestHeaderBuffer,requestBody);
		return caller;
	}
	
	public static Caller create(Browser browser,WebClientConnection connection,List<Caller> nextCallers,
			ByteBuffer[] requestHeaderBuffer,String requestLine,ByteBuffer[] requestBody){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		caller.setup(browser, connection, nextCallers,requestLine,requestHeaderBuffer,requestBody);
		return caller;
	}
	
	private void setup(Browser browser,WebClientConnection connection,Caller[] nextCallers,
			String requestLine,ByteBuffer[] requestHeaderBuffer,ByteBuffer[] requestBody){
		for(Caller nextCaller:nextCallers){
			this.nextCallers.add(nextCaller);
		}
		setupExceptNextCaller(browser, connection, requestLine, requestHeaderBuffer, requestBody);
	}
	
	private void setup(Browser browser,WebClientConnection connection,Caller nextCaller,
			String requestLine,ByteBuffer[] requestHeaderBuffer,ByteBuffer[] requestBody){
		if(nextCaller!=null){
			this.nextCallers.add(nextCaller);
		}
		setupExceptNextCaller(browser, connection, requestLine, requestHeaderBuffer, requestBody);
	}
	
	private void setup(Browser browser,WebClientConnection connection,List<Caller> nextCallers,
			String requestLine,ByteBuffer[] requestHeaderBuffer,ByteBuffer[] requestBody){
		this.nextCallers.addAll(nextCallers);
		setupExceptNextCaller(browser, connection, requestLine, requestHeaderBuffer, requestBody);
	}
	
	private String digest(ByteBuffer[] buffers){
		if(buffers==null){
			return null;
		}
		ByteBuffer[] dupBuffers=PoolManager.duplicateBuffers(buffers);
		Store store=Store.open(true);
		store.putBuffer(dupBuffers);
		store.close();
/*		
		BuffersUtil.mark(buffers);
		for(ByteBuffer buffer:buffers){
			messageDigest.update(buffer);
		}
		String digest=DataUtil.digest(messageDigest);
		BuffersUtil.reset(buffers);
		messageDigest.reset();
*/
		return store.getDigest();
	}
	
	/* nextCaller�ȊO�̏����� */
	private void setupExceptNextCaller(Browser browser,WebClientConnection connection,
			String requestLine,ByteBuffer[] requestHeader,ByteBuffer[] requestBody){
		this.browser=browser;
		setConnection(connection);
		this.requestHeader=requestHeader;
		this.requestLine=requestLine;
		this.requestBody=requestBody;
		this.requestContentLength=BuffersUtil.remaining(requestBody);
		this.requestHeaderDigest=digest(requestHeader);
		this.requestBodyDigest=digest(requestBody);
	}
	
	public Caller dup(Browser browser){
		Caller caller=(Caller)PoolManager.getInstance(Caller.class);
		ByteBuffer[] dupRequestHeader=PoolManager.duplicateBuffers(requestHeader);
		ByteBuffer[] dupRequestBody=null;
		if(requestBody!=null){
			dupRequestBody=PoolManager.duplicateBuffers(requestBody);
		}
		caller.setupExceptNextCaller(browser, connection, requestLine, dupRequestHeader, dupRequestBody);
		for(Caller nextCaller:nextCallers){
			caller.nextCallers.add(nextCaller.dup(browser));
		}
		//�Čv�Z���V���[�g�J�b�g
		caller.requestHeaderDigest=requestHeaderDigest;
		caller.requestBodyDigest=requestBodyDigest;
		caller.isResponseHeaderTrace=isResponseHeaderTrace;
		caller.isResponseBodyTrace=isResponseBodyTrace;
		caller.resolveDigest=resolveDigest;
		return caller;
	}
	
	//TODO �ُ�̃V�~�����[�g
	//1)connect���đ��M�J�n����܂ł̎��� (0)
	//2)���M����requestLine�� 0-��requestLine��(��requestLine��)
	//3)requestLine���M��Aheader���M���J�n����܂ł̎���(0)
	//4)���M����header�� 0-��header��(��header��)
	//5)header���M��Abody���M���J�n����܂ł̎���(0)
	//6)���M����body���@0-��body��(��body��)
	//7)���X�|���X�҂����� 0-���X�|���X��������(readTimeout)
	public void startRequest(WebClientHandler webClientHandler){
		logger.debug("#startRequest:"+browser.getName());
		//TODO
		/*
		scheduler=(CallScheduler)PoolManager.getInstance(CallScheduler.class);
		long now=System.currentTimeMillis();
		logger.debug("now:"+now);
		scheduler.setup(webClientHandler, now, 0, now, 0);
		webClientHandler.setScheduler(scheduler);
		*/
		
		accessLog=(AccessLog) PoolManager.getInstance(AccessLog.class);
		//simulate����̍쐬���ꂽ�����}�[�N
		accessLog.setIp(browser.getName());//browser���
		Scenario scenario=browser.getScenario();
		if(scenario!=null){
			accessLog.setRealHost(scenario.getName());//scenario���
		}
		if(orgAccessLog!=null){
			accessLog.setOriginalLogId(orgAccessLog.getId());
		}
		accessLog.setResolveOrigin(connection.getTargetServer()+":"+connection.getTargetPort());//�ڑ���T�[�o
		if(connection.isHttps()){
			accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_HTTPS);
		}else{
			accessLog.setDestinationType(AccessLog.DESTINATION_TYPE_HTTP);
		}
		accessLog.setSourceType(AccessLog.SOURCE_TYPE_SIMULATE);
		
		accessLog.setRequestLine(requestLine);
		accessLog.setRequestHeaderLength(BuffersUtil.remaining(requestHeader));
		accessLog.setStartTime(new Date(System.currentTimeMillis()));
		accessLog.setRequestHeaderDigest(requestHeaderDigest);
		accessLog.setRequestBodyDigest(requestBodyDigest);
		accessLog.setResolveDigest(resolveDigest);
		
		responseLength=0;
		if(isResponseHeaderTrace){
			Store responseHeaderStore=Store.open(true);
			webClientHandler.pushReadPeekStore(responseHeaderStore);
		}
		if( webClientHandler.startRequest(this, webClientHandler,3000,PoolManager.duplicateBuffers(requestHeader),requestContentLength, true, 15000)==false){
			logger.error("fail to webClientHandler.startRequest.scenario.getName:"+scenario.getName());
			//connect����ł��Ȃ��������߁A�C�x���g�ʒm�����҂ł��Ȃ��B���͂ŃC�x���g���s
			onRequestFailure(webClientHandler,0, new Exception("webClientHandler.startRequest faile to connect"));
			return;
		}
		if(requestBody!=null){
			webClientHandler.requestBody(PoolManager.duplicateBuffers(requestBody));
		}
	}
	
	public void cancel(){
		if(scheduler!=null){
			scheduler.cancel();
			scheduler.unref();
			scheduler=null;
		}
	}
	
	public void onWrittenRequestHeader(Object userContext) {
		logger.debug("#onWrittenRequestHeader:"+browser.getName());
		accessLog.setTimeCheckPint(AccessLog.TimePoint.requestHeader);
		if(requestBody==null){
			accessLog.setTimeCheckPint(AccessLog.TimePoint.requestBody);
		}
	}
	
	public void onWrittenRequestBody(Object userContext) {
		logger.debug("#onWrittenRequestBody:"+browser.getName());
		accessLog.setTimeCheckPint(AccessLog.TimePoint.requestBody);
	}
	
	public void onResponseHeader(Object userContext, HeaderParser responseHeader) {
		logger.debug("#onResponseHeader:"+browser.getName());
		WebClientHandler webClientHandler=(WebClientHandler)userContext;
//		accessLog.setChannelId(webClientHandler.getChannelId());
		Store responseHeaderStore=webClientHandler.popReadPeekStore();
		if(responseHeaderStore!=null){
			accessLog.incTrace();
			responseHeaderStore.close(accessLog,responseHeaderStore);
			accessLog.setResponseHeaderDigest(responseHeaderStore.getDigest());
		}
//		responseHeader.getHeaderBuffer();
		accessLog.setTimeCheckPint(AccessLog.TimePoint.responseHeader);
		accessLog.setStatusCode(responseHeader.getStatusCode());
		accessLog.setContentType(responseHeader.getContentType());
		accessLog.setContentEncoding(responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER));
		accessLog.setTransferEncoding(responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER));
		//chunk����Ă����Ƃ��Ă��AonResponseBody�ɂ̓f�R�[�h���Ēʒm�����B�L�^�����store��chunk����鎖�͂Ȃ��B
		//��L�͂����AWebClientHandler��isReadableCallback�Ƃ����I�v�V����������A���ꂪ�����Ă��Ȃ��ꍇ�A���f�[�^��callback�����
//		accessLog.setTransferEncoding(null);
	}

	public void onResponseBody(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onResponseBody:"+browser.getName());
		if(responseLength==0){
			accessLog.setTimeCheckPint(AccessLog.TimePoint.responseBody);
		}
		responseLength+=BuffersUtil.remaining(buffers);
		if(isResponseBodyTrace){
			if(responseBodyStore==null){
				responseBodyStore=Store.open(true);
			}
			responseBodyStore.putBuffer(buffers);
		}else{
			for(ByteBuffer buffer:buffers){
				messageDigest.update(buffer);
			}
			PoolManager.poolBufferInstance(buffers);
		}
	}
	
	public void onRequestEnd(Object userContext,int stat) {
		logger.debug("#onRequestEnd:"+browser.getName());
		if(accessLog.getStatusCode()==null){
			accessLog.setStatusCode("%" +Integer.toHexString(stat));
		}
		
		WebClientHandler webClientHandler=(WebClientHandler)userContext;
		accessLog.setChannelId(webClientHandler.getChannelId());
//		if(accessLog.getStatusCode()==null){
//			//connect�Ɏ��s�����ꍇ�Ahandshake�Ɏ��s�����ꍇ�A���̑�������؂ꂽ�ꍇ
//			logger.debug("Caller.onRequestEnd.no status code:"+webClientHandler.getChannelId()+":"+accessLog.getChannelId());
//		}
		if(scheduler!=null){
			scheduler.unref();
			scheduler=null;
		}
		accessLog.setResponseLength(responseLength);
		if(responseLength==0){
		}else if(responseBodyStore==null){
			accessLog.setResponseBodyDigest(DataUtil.digest(messageDigest));
		}else{
			accessLog.incTrace();
			responseBodyStore.close(accessLog,responseBodyStore);
			accessLog.setResponseBodyDigest(responseBodyStore.getDigest());
			responseBodyStore=null;
		}
		accessLog.setRequestHeaderLength(webClientHandler.getRequestHeaderLength());
		accessLog.setResponseHeaderLength(webClientHandler.getResponseHeaderLength());
		accessLog.endProcess();
		AccessLog wkAccessLog=accessLog;
		accessLog=null;
		browser.onRequestEnd(this,wkAccessLog);
	}

	public void onRequestFailure(Object userContext,int stat,Throwable t) {
		logger.warn("#onRequestFailure:"+browser.getName()+":"+stat,t);
		//stat��accessLog�ɋL�^
		String statusCode=accessLog.getStatusCode();
		if(statusCode!=null){
			//response�@header��M��ɃG���[�ɂȂ���,accesslog�ɂ�stat�������L�^
			logger.warn("fail after response header.statusCode:"+statusCode+":"+stat);
			accessLog.setStatusCode("#" +Integer.toHexString(stat));
		}else{
			accessLog.setStatusCode("$" +Integer.toHexString(stat));
		}
		onRequestEnd(userContext,stat);
	}
	
	public List<Caller> getNextCallers(){
		return nextCallers;
	}
	
	public void setConnection(WebClientConnection connection) {
		if(this.connection!=null){
			this.connection.unref();
		}
		if(connection!=null){
			connection.ref();
		}
		this.connection=connection;
	}

	public WebClientConnection getConnection() {
		return connection;
	}

	public boolean isResponseHeaderTrace() {
		return isResponseHeaderTrace;
	}

	public void setResponseHeaderTrace(boolean isResponseHeaderTrace) {
		this.isResponseHeaderTrace = isResponseHeaderTrace;
	}

	public boolean isResponseBodyTrace() {
		return isResponseBodyTrace;
	}

	public void setResponseBodyTrace(boolean isResponseBodyTrace) {
		this.isResponseBodyTrace = isResponseBodyTrace;
	}

	public void setResolveDigest(String resolveDigest) {
		this.resolveDigest = resolveDigest;
	}
}
