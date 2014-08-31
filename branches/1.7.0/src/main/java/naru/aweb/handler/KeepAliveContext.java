package naru.aweb.handler;

import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.Context;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.core.RealHost;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebClientHandler;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ServerParser;
/**
 * HTTP KeeyALiveに対する情報を管理する
 * 回線接続と同時に作成されるため、Socketに対する情報を管理する
 * @author Naru
 *
 */
public class KeepAliveContext extends Context {
	private static Logger logger = Logger.getLogger(KeepAliveContext.class);
	private static Config config=Config.getConfig();
	public static final String ATTRIBUTE_REQUEST_CONTEXT=RequestContext.class.getName();
	
	public static void setConnectionHandler(HeaderParser responseHeader,boolean isProxy,boolean isKeepAlive){
		String value;
		if(isKeepAlive){
			value=HeaderParser.CONNECION_KEEP_ALIVE;
		}else{
			value=HeaderParser.CONNECION_CLOSE;
		}
		if(isProxy){
			responseHeader.setHeader(HeaderParser.PROXY_CONNECTION_HEADER, value);
		}else{
			responseHeader.setHeader(HeaderParser.CONNECTION_HEADER, value);
		}
	}
	public static void setConnectionHandler(Map<String,String> responseHeader,boolean isProxy,boolean isKeepAlive){
		String value;
		if(isKeepAlive){
			value=HeaderParser.CONNECION_KEEP_ALIVE;
		}else{
			value=HeaderParser.CONNECION_CLOSE;
		}
		if(isProxy){
			responseHeader.put(HeaderParser.PROXY_CONNECTION_HEADER, value);
		}else{
			responseHeader.put(HeaderParser.CONNECTION_HEADER, value);
		}
	}
	
	private ServerParser acceptServer;
	private RealHost realHost;
	private ServerParser proxyTargetServer;//proxy対象サーバ
	private boolean isSelfProxy;//自サーバがproxy対象か？
	
	private boolean isKeepAlive;
	private boolean isChunked;
	private int requestsCount;
	private int maxKeepAliveRequests;
	private long keepAliveTimeout;
	private boolean isAllowChunked;//リクエストがchunkedをサポートするか否か
	private boolean isProxy;
	private boolean isSslProxy;
	private boolean isSendLastChunk;
	private boolean isCloseServerHandle=false;
	
	private WebClientHandler webClientHandler;//ここからポイントされる限り再利用されない
	
	public void recycle() {
		maxKeepAliveRequests=config.getMaxKeepAliveRequests();
		keepAliveTimeout=config.getKeepAliveTimeout();
		requestsCount=0;
		isSendLastChunk=isChunked=isProxy=isSslProxy=isKeepAlive=isAllowChunked=isSelfProxy=false;
		setWebClientHandler(null);
		isCloseServerHandle=false;
		setProxyTargetServer(null);
		if(acceptServer!=null){
			acceptServer.unref();
			acceptServer=null;
		}
		realHost=null;
		super.recycle();
	}
	
	public synchronized void setWebClientHandler(WebClientHandler webClientHandler){
		if(webClientHandler!=null){
			webClientHandler.ref();
		}
		if(this.webClientHandler!=null){
			this.webClientHandler.endRequest();
			if(this.webClientHandler.isConnect()){
				this.webClientHandler.asyncClose(null);
			}
			this.webClientHandler.unref();
		}
		this.webClientHandler=webClientHandler;
	}
	
	/**
	 * リクエストヘッダを見て、KeepAliveContextを返却する。
	 * 
	 * @param context　既存のkeepAliveContext
	 * @param requestHeader リクエストヘッダ
	 * @return
	 */
	public void startRequest(HeaderParser requestHeader){
		isSendLastChunk=isChunked=isKeepAlive=isAllowChunked=false;
//		isProxy=requestHeader.isProxy();
		
		/* 最大keepAlive回数を超えていればkeepAliveしない */
		if(requestsCount>=maxKeepAliveRequests){
			logger.debug("reach maxKeepAliveRequests."+maxKeepAliveRequests);
			isKeepAlive=false;
			return;
		}
		String connection=null;
		if(isProxy){//proxyサーバの場合
			if(!config.isProxyKeepAlive()){
				isKeepAlive=false;
				return;
			}
			/* 明示的にconnectionが切断が支持されている場合は、KeepAliveしない */
			connection=requestHeader.getHeader(HeaderParser.PROXY_CONNECTION_HEADER);
			if(connection!=null){
				connection.trim();
			}
			if(HeaderParser.CONNECION_CLOSE.equalsIgnoreCase(connection)){
				isKeepAlive=false;
				return;
			}
		}else{//webサーバの場合
			/* keepAlive設定がfalseの場合、KeepAliveしない */
			if(!config.isWebKeepAlive()){
				isKeepAlive=false;
				return;
			}
			/* 明示的にconnectionが切断が支持されている場合は、KeepAliveしない */
			connection=requestHeader.getHeader(HeaderParser.CONNECTION_HEADER);
			if(connection!=null){
				connection.trim();
			}
			if(HeaderParser.CONNECION_CLOSE.equalsIgnoreCase(connection)){
				isKeepAlive=false;
				return;
			}
		}
		/* HTTP1.0で明示されたKeepAlive指定があればKeepAlive可能　*/
		boolean isAllowChunked=config.isAllowChunked();
		String version=requestHeader.getReqHttpVersion();
		if(HeaderParser.HTTP_VESION_10.equalsIgnoreCase(version)){
			if(!HeaderParser.CONNECION_KEEP_ALIVE.equalsIgnoreCase(connection)){
				isKeepAlive=false;
				return;
			}
			isAllowChunked=false;/* HTTP1.0は、chunkedをサポートしない */
		}
		//if(context.isProxy){//なぜかproxy経由のコンテンツをchnkedするとブラウザが表示してくれない
		//	isAllowChunked=false;
		//}
		this.isKeepAlive=true;
		this.isAllowChunked=isAllowChunked;
		this.requestsCount++;
		return;
	}

	public WebClientHandler getWebClientHandler(boolean isHttps, String targetServer,int targetPort){
		if(webClientHandler!=null){
			if( webClientHandler.isSameConnection(isHttps,targetServer,targetPort) ){
				return webClientHandler;
			}
		}
		WebClientHandler newWebClientHandler=WebClientHandler.create(isHttps, targetServer, targetPort);
		setWebClientHandler(newWebClientHandler);
		return newWebClientHandler;
	}
	
	public boolean isKeepAlive() {
		return isKeepAlive;
	}
	
	public void setKeepAlive(boolean isKeepAlive){
		if(this.isKeepAlive){//trueを変更する事だけを許す
			this.isKeepAlive=isKeepAlive;
		}
	}
	
	public boolean isChunked() {
		return isChunked;
	}

	public long getKeepAliveTimeout() {
		return keepAliveTimeout;
	}
	
	private static final byte[] DATA_AND_LAST_CHUNK="\r\n0\r\n\r\n".getBytes();
	private static final byte[] LAST_CHUNK="0\r\n\r\n".getBytes();
	private static final byte[] CRLF = "\r\n".getBytes();
	
	/**
	 * 
	 * @param buffers　nullの場合レスポンス終端をあらわす
	 * @return
	 */
	public ByteBuffer[] chunkedIfNeed(boolean isLast,ByteBuffer[] buffers){
		if(!isChunked){
			return buffers;
		}
		ByteBuffer head=null;
		if(buffers==null){
			if(isLast && !isSendLastChunk){
				isSendLastChunk=true;
				return BuffersUtil.toByteBufferArray(ByteBuffer.wrap(LAST_CHUNK));
			}
			/* buffersがnullで最後のデータじゃないというのは異常 */
			throw new IllegalArgumentException("chunkedIfNeed");
		}else{
			long length=BuffersUtil.remaining(buffers);
			String headString=Long.toHexString(length)+"\r\n";
			head=ByteBuffer.wrap(headString.getBytes());
		}
		ByteBuffer tail=null;
		if(isLast){
			isSendLastChunk=true;
			tail=ByteBuffer.wrap(DATA_AND_LAST_CHUNK);
		}else{
			tail=ByteBuffer.wrap(CRLF);
		}
		ByteBuffer[] chunkedBuffer=BuffersUtil.concatenate(head, buffers, tail);
		return chunkedBuffer;
	}

	/* prepareResponseとendOfResponseは、対のメソッド 同一のhandlerから呼び出す事 */
	//private WebServerHandler setupedHandler;
	public boolean prepareResponse(WebServerHandler handler,HeaderParser responseHeader,long commitContentLength) {
		logger.debug("prepareResponse handler.cid:"+handler+ ":webClientHandler.cid:"+webClientHandler);
		//if(setupedHandler!=null){
		//	throw new IllegalStateException("fail to setup."+setupedHandler);
		//}
		//responseが確定していない。
		if(responseHeader.getStatusCode()==null){
			isKeepAlive=false;
		}
		//setupedHandler=handler;
		isChunked=false;
		if(!isKeepAlive){
			setConnectionHandler(responseHeader,isProxy,false);
			return false;
		}
		/* 上位アプリケーションがkeepAliveをやめることを指定した場合従う */
		/*
		String orgConnectionHeader;
		if(isProxy){
			orgConnectionHeader=responseHeader.getHeader(HeaderParser.PROXY_CONNECTION_HEADER);
		}else{
			orgConnectionHeader=responseHeader.getHeader(HeaderParser.CONNECTION_HEADER);
		}
		if(HeaderParser.CONNECION_CLOSE.equalsIgnoreCase(orgConnectionHeader)){
			isKeepAlive=false;
			setConnectionHandler(responseHeader,isProxy,false);
			return false;
		}
		*/
		String transferEncoding=responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		boolean isAreadyChunked=HeaderParser.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncoding);
		long contentLength=responseHeader.getContentLength();
		/*　keepAliveでかつ、コンテンツ長が確定していない場合、chunkedで返却する必要がある */
		if(isAreadyChunked){
			isChunked=false;//既にchunkedされているものを重ねてchunkedする必要なし
		}else if(contentLength<0&&commitContentLength<0){
			if(!isAllowChunked){
				//keepAliveしたくても、コンテンツ長が確定していない、かつchunkedができない場合、KeepAliveはできない
				isKeepAlive=false;
				setConnectionHandler(responseHeader,isProxy,false);
				return false;
			}
			isChunked=true;
		}
		
		setConnectionHandler(responseHeader,isProxy,true);
		/*Keep-Alive: timeout=15, max=100 な感じ*/
		responseHeader.setHeader(HeaderParser.KEEP_ALIVE_HEADER,"timeout=" +(keepAliveTimeout/1000) + ", max=" +(maxKeepAliveRequests-requestsCount));
		if(isChunked){
			responseHeader.removeHeader(HeaderParser.CONTENT_LENGTH_HEADER);
			responseHeader.setHeader(HeaderParser.TRANSFER_ENCODING_HEADER, HeaderParser.TRANSFER_ENCODING_CHUNKED);
			responseHeader.setResHttpVersion(HeaderParser.HTTP_VESION_11);
		}else if(contentLength<0 && commitContentLength>=0){
			responseHeader.setContentLength(commitContentLength);
		}
		return true;
	}
	
	/*
	 * KeepAliveContext１個につきServer handleを無効化するのは1回
	 */
	private void closeServerHandleOnce(WebServerHandler handler){
		logger.debug("closeServerHandleOnce.cid:"+handler.getChannelId()+":isCloseServerHandle:"+isCloseServerHandle);
		if(isCloseServerHandle==false){
			handler.asyncClose(null);
			isCloseServerHandle=true;
		}
	}
	
	/**
	 * レスポンスが終わったと考えられるタイミングで呼び出される
	 * １リクエストで呼び出しが１回だけとは限らない点に注意
	 * @param handler ブラウザとつながっているhandler
	 * @return
	 */
	public synchronized boolean commitResponse(WebServerHandler handler){
		logger.debug("commitResponse handler.cid:"+handler+ ":webClientHandler.cid:"+webClientHandler);
		/*
		if(setupedHandler==null){
			setWebClientHandler(null);
			logger.debug("commitResponse done end of keepAlive not prepareResponse.handler:"+handler);
			closeServerHandleOnce(handler);
			return false;
		}
		if(handler!=setupedHandler){
			throw new IllegalStateException("fail to endOfResponse.setupedHandler:"+setupedHandler);
		}
		*/
		if(!isKeepAlive/* || handler.isHandlerClosed()*/){
			//setupedHandler=null;
			setWebClientHandler(null);
			logger.debug("commitResponse done end of keepAlive.cid:"+handler.getChannelId());
			closeServerHandleOnce(handler);
			return false;
		}
		/*
		if(handler.orderCount()!=0){//未完了のorder(write)が残っている
			logger.debug("commitResponse.left order wait for done write");
			return false;
		}
		*/
		//setupedHandler=null;
		if(webClientHandler!=null){
			if(!webClientHandler.isKeepAlive()){
				setWebClientHandler(null);
			}
		}
		//logger.debug("commitResponse.handler.orderCount():"+handler.orderCount());
		logger.debug("commitResponse done keepAlive.handler:"+handler);
		logger.debug("commitResponse done keepAlive.webClientHandler:"+webClientHandler);
		isSendLastChunk=isChunked=isProxy=isKeepAlive=isAllowChunked=false;
		handler.setReadTimeout(keepAliveTimeout);
		handler.waitForNextRequest();
		return true;
	}
	
	public void finishedOfServerHandler(){
		setWebClientHandler(null);
	}
	
	public RequestContext getRequestContext(boolean isNew) {
		RequestContext requestContext=(RequestContext)getAttribute(ATTRIBUTE_REQUEST_CONTEXT);
		if(requestContext==null&&isNew){
			requestContext=(RequestContext) PoolManager.getInstance(RequestContext.class);
			endowAttribute(ATTRIBUTE_REQUEST_CONTEXT, requestContext);
		}
		return requestContext;
	}
	
	public void endOfResponse(){
		endowAttribute(ATTRIBUTE_REQUEST_CONTEXT, null);
	}

	//ssl proxy対象としたサーバ
	public ServerParser getProxyTargetServer() {
		return proxyTargetServer;
	}

	public void setProxyTargetServer(ServerParser proxyTargetServer) {
		isSelfProxy=false;
		if(proxyTargetServer!=null){
			isSelfProxy=(config.getRealHost(proxyTargetServer)!=null);
			proxyTargetServer.ref();
		}
		if(this.proxyTargetServer!=null){
			this.proxyTargetServer.unref();
		}
		this.proxyTargetServer = proxyTargetServer;
	}

	//自サーバの事
	public void setAcceptServer(ServerParser acceptServer){
		this.acceptServer=acceptServer;
		this.realHost=config.getRealHost(acceptServer);
		if(this.realHost!=null){
			return;
		}
		//realHostがnullでは困る,localhost等の名前でconnectしてきたものと考えられる
		this.realHost=config.getRealHostByBindPost(acceptServer.getPort());
		if(this.realHost==null){
			throw new IllegalAccessError("unkown server:"+acceptServer);
		}
	}
	
	/* spdyの場合の初期化 */
	public void setSpdyAcceptServer(ServerParser acceptServer,RealHost realHost,ServerParser proxyTargetServer){
		this.acceptServer=acceptServer;
		this.realHost=realHost;
		setProxyTargetServer(proxyTargetServer);
		
		//TODO SPDY接続の場合、ブラウザがproxyと思って接続しにきたのか?webサーバと思って接続に来たのか?確定する方法が不明
		//RealHost wkRealHost=config.getRealHost(proxyTargetServer);
		//if(!realHost.equals(wkRealHost)){
		//	setProxyTargetServer(proxyTargetServer);
		//}
	}
	
	public ServerParser getAcceptServer(){
		return acceptServer;
	}
	
	public RealHost getRealHost(){
		return realHost;
	}
	/*
	public AuthSession getAuthSession() {
		if(requestContext==null){
			logger.error("fail to getAuthSession.requestContext null,",new Exception());
			return null;
		}
		return requestContext.getAuthSession();
	}
	public void setAuthSession(AuthSession authSession) {
		if(requestContext==null){
			if(authSession!=null){
				logger.error("fail to setAuthSession.requestContext null,",new Exception());
				authSession.unref();
			}
			return;
		}
		requestContext.registerAuthSession(authSession);
	}
	*/
	public boolean isSelfProxy() {
		return isSelfProxy;
	}
	public boolean isProxy() {
		return isProxy;
	}
	public void setProxy(boolean isProxy) {
		this.isProxy = isProxy;
	}
	public boolean isSslProxy() {
		return isSslProxy;
	}
	public void setSslProxy(boolean isSslProxy) {
		this.isSslProxy = isSslProxy;
	}
}
