/**
 * 
 */
package naru.aweb.handler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import naru.async.pool.PoolManager;
import naru.async.store.Page;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientHandler;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.spdy.SpdySession;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

/**
 * reversProxyとして動作
 * 1)reverseパスと一致した場合
 * 2)sslProxyでpeekする場合
 * @author Naru
 *
 */
public class ProxyHandler extends  WebServerHandler implements WebClient{
	private static Logger logger=Logger.getLogger(ProxyHandler.class);
	private static Config config=Config.getConfig();
	private boolean isReplay=false;
	private boolean isTryAgain=false;//レスポンスヘッダを見てもう一度リクエストしなおしたいと判断した場合
	private boolean isReplace=false;//リクエストヘッダを見てコンテンツを置き換えると判断した場合,置き換えコンテンツは、injectコンテンツとして挿入
//	private Object injectContext=null;
//	private PortalSession portalSession=null;
	private ProxyInjector injector;
	private Page bodyPage=new Page();//replay時,AccessLog DBにヒットしなかった場合に使用
	private WebClientHandler webClientHandler;
	
	//response時のヘッダ編集対象
	private boolean isRequestEnd=false;//isRequestEndは、一回以上呼ばれないようにする
	private boolean isResponseHeader=false;//レスポンスを受信していなければ独自でエラーを返却する
	private List<String> removeResponseHeaders=new ArrayList<String>();
	private Map<String,String> addResponseHeaders=new HashMap<String,String>();
	
	public void recycle() {
		isResponseHeader=isRequestEnd=isTryAgain=isReplay=isReplace=false;
		if(webClientHandler!=null){
			webClientHandler.endRequest();
		}
		webClientHandler=null;
		bodyPage.recycle();
		if(injector!=null){
			injector.term();
			injector=null;
		}
		addResponseHeaders.clear();
		removeResponseHeaders.clear();
		super.recycle();
	}

	//responseヘッダ編集メソッド
	public void removeResponseHeader(String name){
		removeResponseHeaders.add(name);
	}
	public void addResponseHeader(String name,String value){
		addResponseHeaders.put(name, value);
	}
	
	private WebClientHandler getWebClientHandler(KeepAliveContext keepAliveContext,HeaderParser requestHeader){
		MappingResult mapping=getRequestMapping();
		ServerParser targetHostServer=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		requestHeader.setPath(path);
		return keepAliveContext.getWebClientHandler(mapping.isResolvedHttps(), targetHostServer.getHost(), targetHostServer.getPort());
	}
	
	private void calcResolveDigest(HeaderParser requestHeader){
		MappingResult mapping=getRequestMapping();
		Mapping.LogType logType=mapping.getLogType();
		if (logType!=Mapping.LogType.RESPONSE_TRACE&&logType!=Mapping.LogType.TRACE){
			return;
		}
		ServerParser targetHostServer=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		String resolveDigest=AccessLog.calcResolveDigest(requestHeader.getMethod(),mapping.isResolvedHttps(),targetHostServer.toString(),path,requestHeader.getQuery());
		AccessLog accessLog=getAccessLog();
		if(accessLog!=null){
			accessLog.setResolveDigest(resolveDigest);
		}else{
			logger.warn("accessLog is null",new Throwable());
		}
	}
	
	private void editRequestHeader(HeaderParser requestHeader) {
		MappingResult mapping=getRequestMapping();
		Mapping.LogType logType=mapping.getLogType();
		/*
		 * 将来検討機能 2013/2/24
		if (logType==Mapping.LogType.RESPONSE_TRACE||logType==Mapping.LogType.TRACE){
			//TODO confから削除すべきヘッダ名をもらう
			// レスポンスのトレースを採取する場合は、ブラウザキャッシュを使わせない
			requestHeader.removeHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
			requestHeader.removeHeader(HeaderParser.IF_NONE_MATCH);
		}
		*/
		//reverse proxyの場合refererを書き換える
		//if(mapping.getMapping().getSourceType()!=Mapping.SourceType.WEB){
		//	return;
		//}
		String referer=requestHeader.getHeader(HeaderParser.REFERER);
		if(referer==null){
			return;
		}
		referer=mapping.reverseReferer(referer);
		requestHeader.setHeader(HeaderParser.REFERER,referer);
	}
	
	private boolean doProxy(){
		logger.debug("#startResponse.cid:"+getChannelId());
		HeaderParser requestHeader=getRequestHeader();
		
		boolean isCallerKeepAlive=false;//バックはkeepAliveしない
		long keepAliveTimeout=0;
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		if(keepAliveContext!=null){
			isCallerKeepAlive=keepAliveContext.isKeepAlive();
			keepAliveTimeout=keepAliveContext.getKeepAliveTimeout();
		}
		//resolveDigestの計算
		calcResolveDigest(requestHeader);
		webClientHandler=getWebClientHandler(keepAliveContext,requestHeader);
		//認証ヘッダやIF_MODIFIED_SINCE_HEADERの調整
		editRequestHeader(requestHeader);
		
		long connectTimeout=config.getConnectTimeout();
		boolean rc=webClientHandler.startRequest(this, null,connectTimeout,requestHeader, isCallerKeepAlive, keepAliveTimeout);
		if(rc==false){
			completeResponse("500","fail to request backserver");
			webClientHandler=null;
			return false;
		}
		logger.debug("client cid:"+getChannelId() +" server cid:"+webClientHandler.getChannelId());
		webClientHandler.setReadTimeout(getReadTimeout());
		webClientHandler.setWriteTimeout(getWriteTimeout());
		return true;
	}
	
	//ブラウザからのリクエストヘッダは受信しきった、さあレスポンしてください...ってメソッド
	public void startResponse(){
		MappingResult mapping=getRequestMapping();
		if(mapping.getBooleanOption(Mapping.OPTION_FILTER)){
			FilterHelper helper=config.getFilterHelper();
			if(helper.doFilter(this)==false){
				logger.debug("filter blocked");
				return;
			}
		}
		
		InjectionHelper helper=config.getInjectionHelper();
		injector=helper.getInjector((String)mapping.getOption(Mapping.OPTION_INJECTOR));
		if(injector!=null){
			injector.init(this);
			injector.onRequestHeader(getRequestHeader());
		}
//		if(Boolean.TRUE.equals(mapping.getOption("portal"))){
//			portalSession=PortalSession.getPortalSession(this);
//			portalSession.onRequestHeader(this);
//		}
		if(mapping.getBooleanOption(Mapping.OPTION_REPLAY)){
			isReplay=true;
		}else{
			if(doProxy()==false){
				return;//proxyに失敗した
			}
		}
		startParseRequestBody();
	}
	
	public void requestBody(ByteBuffer[] buffers) {
		if(isReplay){
			bodyPage.putBuffer(PoolManager.duplicateBuffers(buffers), true);
			super.requestBody(buffers);//必要ない？
			return;
		}
		webClientHandler.requestBody(buffers);
	}
	
	/**
	 * bodyをすべて読み込んでから呼び出される,
	 * replayの場合、処理を開始する
	 * 
	 */
	public void startResponseReqBody(){
		if(!isReplay){
			return;
		}
		ReplayHelper helper=config.getReplayHelper();
		ByteBuffer[] body=bodyPage.getBuffer();
		if(helper.doReplay(this,body)){
			return;//replayできた,bodyは消費されている
		}
		if(doProxy()==false){
			return;//proxy失敗
		}
		if(body!=null){
			webClientHandler.requestBody(body);
		}
	}
	
	public void onTimeout(Object userContext){
		logger.debug("#timeout.cid:"+getChannelId());
		asyncClose(userContext);
		responseEnd();
	}
	
	public void onFailure(Object userContext,Throwable t){
		logger.debug("#failure.cid:"+getChannelId(),t);
		asyncClose(userContext);
		responseEnd();
	}

	public void onClosed(Object userContext) {
		logger.debug("#closed.cid:"+getChannelId());
		super.onClosed(userContext);
	}
	
	public void onFinished(){
		logger.debug("#finished.cid:"+getChannelId());
		responseEnd();
		super.onFinished();
	}

	public void onResponseBody(Object userContext,ByteBuffer[] buffer) {
		logger.debug("#responseBody.cid:"+getChannelId());
		if(isRequestEnd){
			return;
		}
		if(isTryAgain||isReplace){
			PoolManager.poolBufferInstance(buffer);
			return;//再実行時レスポンスbodyは捨てる
		}
		if(injector!=null){
			logger.debug("inject add contents cid:"+getChannelId());
			buffer=injector.onResponseBody(buffer);
		}
//		if(injectContext!=null && buffer!=null){
//			logger.debug("inject add contents cid:"+getChannelId());
//			InjectionHelper helper=config.getInjectionHelper();
//			helper.doInject(injectContext, this, buffer);
//			return;
//		}
		responseBody(buffer);
	}
	
	private void rewriteLocation(HeaderParser responseHeader){
		String location=responseHeader.getHeader(HeaderParser.LOCATION_HEADER);
		if(location==null){
			return;
		}
		MappingResult mapping=getRequestMapping();
		location=mapping.reverseResolve(location);
		responseHeader.setHeader(HeaderParser.LOCATION_HEADER, location);
	}
	
	public void onResponseHeader(Object userContext,HeaderParser responseHeader) {
		if(isRequestEnd){
			return;
		}
		isResponseHeader=true;
		logger.debug("#responseHeader.cid:"+getChannelId());
		long injectLength=0;
		boolean isInject=false;
		if(injector!=null){
			injector.onResponseHeader(responseHeader);
			isInject=injector.isInject();
			if(isInject){
				//chunk、gzipを解除してbodyを通知するようにwebClientHandlerに依頼（通信上の動作は関係しない）
				webClientHandler.setReadableCallback(true);
				injectLength=injector.getInjectLength();
				logger.debug("inject cid:"+getChannelId()+":injectLength:"+injectLength+":ContentsLength:"+responseHeader.getContentLength());
			}
		}
		/*
		if(portalSession!=null){//Portal処理をしている場合は、注入コンテンツの処理は、PortalSessionに任せる
			injectContext=portalSession.onResponseHeaderAndInject(this,responseHeader);
		}else if(injectionFile!=null){//mappingから指定されている場合は、ProxyHandlerから
			injectContext=helper.getInjectContext(injectionFile,responseHeader);
		}
//		long injectContentsLength=0;
		if(injectContext!=null){
			//chunk、gzipを解除してbodyを通知するようにwebClientHandlerに依頼（通信上の動作は関係しない）
			webClientHandler.setReadableCallback(true);
			injectContentsLength=helper.getInjectContentsLength(injectContext);
			logger.debug("inject cid:"+getChannelId()+":injectContentsLength:"+injectContentsLength+":ContentsLength:"+responseHeader.getContentLength());
		}
		*/
		//リダイレクトの場合、自分に帰ってくるようにlocationヘッダを設定
		String statusCode=responseHeader.getStatusCode();
		if("301".equals(statusCode)||"302".equals(statusCode)||"303".equals(statusCode)){
			rewriteLocation(responseHeader);
		}
		//set-cookieヘッダのdomein,secureも書き換えた方がよい
		setResponseHeader(responseHeader);
		
		//ヘッダ削除処理
		for(String removeHeader:removeResponseHeaders){
			removeHeader(removeHeader);
		}
		//ヘッダ追加処理
		for(String name:addResponseHeaders.keySet()){
			String value=addResponseHeaders.get(name);
			setHeader(name, value);
		}
		if(isInject){
			String contentEncoding=responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER);
			long contentLength=0;
			if(!isReplace){
				contentLength=responseHeader.getContentLength();
			}
			if(contentEncoding!=null){
				removeHeader(HeaderParser.CONTENT_ENCODING_HEADER);
				//圧縮後のcontentLengthが設定されている
				removeContentLength();
			}else if(contentLength>=0){
				setContentLength(contentLength+injectLength);
				logger.debug("inject change contentLength cid:"+getChannelId()+":contentLength:"+(contentLength+injectLength));
			}
			//onResponseBodyには、chunkがデコードされて通知されるため
			removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		}
	}
	
	private boolean requestEnd(){
		if(isRequestEnd){
			return false;
		}
		isRequestEnd=true;
		webClientHandler=null;
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		keepAliveContext.setWebClientHandler(null);
		return true;
	}
	
	public void onRequestEnd(Object userContext,int stat) {
		logger.debug("#webClientEnd.cid:"+getChannelId());
		if(requestEnd()==false){
			return;
		}
		if(isTryAgain){
			isTryAgain=false;
			startResponse();
			return;//再実行時レスポンスbodyは捨てる
		}
		//contents追加処理
//		ByteBuffer[] addContents=addResponseContents.getBuffer();
		if(injector!=null){
			ByteBuffer[] buffers=injector.onResponseBody(null);
			if(buffers!=null){
				logger.debug("inject add contents last cid:"+getChannelId());
				responseBody(buffers);
			}
		}
//		if(injectContext!=null){
//			logger.debug("inject add contents cid:"+getChannelId());
//			InjectionHelper helper=config.getInjectionHelper();
//			helper.doInject(injectContext, this, null);
//		}
		if(isResponseHeader){//レスポンスヘッダが確定していない
			responseEnd();
		}else{
			completeResponse("500","no response");
		}
	}

	public void onRequestFailure(Object userContext,int stat,Throwable t) {
		logger.debug("#webClientFailure.cid:"+getChannelId()+":"+stat,t);
		if(requestEnd()==false){
			return;
		}
		if(isResponseHeader){
			responseEnd();
		}else{
			//headerをレスポンスする事なく異常となった
			completeResponse("500", "proxyHandler error.stat:"+stat);
		}
	}
	
	public void onWrittenRequestHeader(Object userContext) {
		//backServerへのrequestHeader書き込み通知、特に使わない
	}
	public void onWrittenRequestBody(Object userContext) {
		//backServerへのrequestBody書き込み通知、特に使わない
	}

	public void setTryAgain(boolean isTryAgain) {
		this.isTryAgain = isTryAgain;
	}
	public void setReplace(boolean isReplace) {
		this.isReplace = isReplace;
	}
	
	public void onWebConnected(Object userContext){
	}
	public void onWebHandshaked(Object userContext){
	}
	public void onWebProxyConnected(Object userContext) {
	}

}
