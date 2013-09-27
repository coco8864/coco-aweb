package naru.aweb.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.Date;

import org.apache.log4j.Logger;

import naru.async.ChannelHandler;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.core.DispatchHandler;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.ChunkContext;
import naru.aweb.http.GzipContext;
import naru.aweb.mapping.MappingResult;
import naru.aweb.spdy.SpdySession;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;

/**
 * HTTPプロトコルを基本に、主にレスポンスをハンドリングする。 HTTPプロトコルのスキームに入らないプロトコルをハンドリングする場合には、
 * onReadメソッドをオーバーライドする
 * 
 * @author Naru
 * 
 */
public class WebServerHandler extends ServerBaseHandler {
	/* ここで指定した属性は、Velocityテンプレートから参照できる */
	private static final String ENCODE = "utf-8";
	private static final String WRITE_CONTEXT_BODY = "writeContextBody";
	private static final String WRITE_CONTEXT_BODY_INTERNAL = "writeContextBodyInternal";
	private static final String WRITE_CONTEXT_HEADER = "writeContextHeader";
	private static final String WRITE_CONTEXT_LAST_HEADER = "writeContextLastHeader";

	private static Logger logger = Logger.getLogger(WebServerHandler.class);
	private static Config config = Config.getConfig();
	private static final String NON_SERVER_HEADER="$$NON_SERVER_HEADER$$";
	private static String serverHeader=null;//config.getString("phantomServerHeader", null);
	private static String getServerHeader(){
		if(serverHeader==NON_SERVER_HEADER){
			return null;
		}else if(serverHeader!=null){
			return serverHeader;
		}
		serverHeader=config.getString("phantomServerHeader", null);
		if(serverHeader==null){
			serverHeader=NON_SERVER_HEADER;
			return null;
		}
		return serverHeader;
	}
	
	private HeaderParser responseHeader = new HeaderParser();
	private long requestContentLength;
	private long requestReadBody;

	private long responseHeaderLength;// レスポンスヘッダ長
	private long responseContentLengthApl;// アプリケーションが,content-lengthヘッダで指定したcontentLength
	private long responseWriteBodyApl;// 実際にaplからレスポンス依頼されたbody長
	private long responseWriteBody;// 実際にレスポンスしたbody長

	private boolean isFlushFirstResponse;
	private ByteBuffer[] firstBody;
	private boolean isResponseEnd;
	

	/**
	 * このオブジェクトを再利用する際に呼び出される。<br/>
	 * overrideした場合は、必ず元メソッドも呼び出してください。
	 */
	public void recycle() {
		logger.debug("#recycle :"+hashCode());
		requestContentLength = requestReadBody = 0;
		responseWriteBody = responseHeaderLength = responseWriteBodyApl = responseContentLengthApl = 0;
		responseHeader.recycle();
		responseBodyStream = null;
		responseBodyWriter = null;
		isFlushFirstResponse = false;
		isResponseEnd = false;// 微妙な動きをするのでpoolにあるうちはtrueにしたいが・・・
		firstBody = null;
		super.recycle();
	}

	/**
	 * 保存されていたbuffer情報でレスポンス用ヘッダを組み立てます。
	 * 
	 * @param buffers ヘッダ情報が格納されたByteBuffer配列
	 * @return　指定されたbuffersがヘッダとして完結していない場合false
	 */
	public boolean parseResponseHeader(ByteBuffer[] buffers) {
		for (int i = 0; i < buffers.length; i++) {
			responseHeader.parse(buffers[i]);
		}
		PoolManager.poolArrayInstance(buffers);
		return responseHeader.isParseEnd();
	}
	
	protected void setResponseHeader(HeaderParser header) {
		responseHeader.setStatusCode(header.getStatusCode(), header.getReasonPhrase());
		responseHeader.setResHttpVersion(header.getResHttpVersion());
		responseHeader.setAllHeaders(header);
	}

	/*
	public boolean isReponseParseError() {
		return responseHeader.isParseError();
	}
	*/

	public void setStatusCode(String statusCode) {
		responseHeader.setStatusCode(statusCode);
	}
	public void setStatusCode(String statusCode,String reasonPhrase) {
		responseHeader.setStatusCode(statusCode,reasonPhrase);
	}

	public String getStatusCode() {
		return responseHeader.getStatusCode();
	}

	public void setHttpVersion(String httpVersion) {
		responseHeader.setResHttpVersion(httpVersion);
	}

	public void setHeader(String name, String value) {
		responseHeader.setHeader(name, value);
	}

	public void removeHeader(String name) {
		responseHeader.removeHeader(name);
	}
	
	public void removeContentLength() {
		responseHeader.removeContentLength();
	}

	public void setContentLength(long contentLength) {
		responseHeader.setContentLength(contentLength);
	}

	public void setContentType(String contentType) {
		responseHeader.setContentType(contentType);
	}
	
	public String getHeader(String name) {
		return responseHeader.getHeader(name);
	}
	public String getResponseStatusCode() {
		return responseHeader.getStatusCode();
	}

	public void setNoCacheResponseHeaders() {
		responseHeader.setHeader("Pragma", "no-cache");
		responseHeader.setHeader("Cache-Control", "no-cache");
		responseHeader.setHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
	}

	/**
	 * ボディの解析処理を開始します。 doResponse呼び出し時には、read要求を出していないので、黙っているとbodyは到着しない。
	 */
	public void startParseRequestBody() {
		HeaderParser requestHeader = getRequestHeader();
		if(requestHeader==null){
			asyncClose(null);
			return;
		}
		requestReadBody = 0;
		requestContentLength = requestHeader.getContentLength();

		ParameterParser parameterParser = getParameterParser();
		parameterParser.init(requestHeader.getMethod(), requestHeader.getContentType(), requestContentLength);
		String query = requestHeader.getQuery();
		if (query != null && !"".equals(query)) {
			parameterParser.parseQuery(query);
		}
		if (requestContentLength <= 0) {// GETのような場合bodyはない
			//ここでNPE発生することあり
			AccessLog accessLog=getAccessLog();
			if(accessLog==null){
				logger.warn("accessLog is null",new Throwable());
				return;
			}
			accessLog.setTimeCheckPint(AccessLog.TimePoint.requestBody);
			onRequestBody();// パラメタ読み込み完了を通知
			return;
		}
		String transferEncoding=requestHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		ChunkContext requestChunkContext=getKeepAliveContext().getRequestContext().getRequestChunkContext();
		//spdyの場合は、transfer_encodingヘッダは無いはずだが念のため、chunkしない処理
		if(HeaderParser.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncoding)&&getSpdySession()==null){
			requestChunkContext.decodeInit(true, -1);
		}else{
			requestChunkContext.decodeInit(false, requestContentLength);
		}
		ByteBuffer[] body = requestHeader.getBodyBuffer();
		// bodyは、必ずこのオブジェクトのreadメソッドを通過させる。nullでもこの中からasyncReadが出るので必要
		onReadPlain(null, body);
	}

	/**
	 * 独自にレスポンスを返却したい人は、このメソッドをオーバライドする
	 * このメソッド呼び出し時点では、ヘッダ解析時に読み込んでしまったbody部分はrequestHeader内部に残っている点に注意
	 * startParseBodyメソッドでは、その部分について明示的にonReadPlainメソッドを呼び出す。
	 */
	public void onRequestHeader() {
		startParseRequestBody();
	}

	/**
	 * リクエストbodyを処理したい人はこのメソッドをオーバライドして使う デフォルトでは、parameterとして解析する処理
	 * 
	 * @param buffers
	 */
	public void requestBody(ByteBuffer[] buffers) {
		ParameterParser parameterParser = getParameterParser();
		try {
			for (int i = 0; i < buffers.length; i++) {
				parameterParser.parse(buffers[i]);
			}
		} catch (IOException e) {// リクエストに誤りがあった場合、IOExceptionが上がってくる
			logger.warn("fail to parse body", e);
			//リクエストを全部読んでいるとは限らないためkeepAliveは禁止
			getKeepAliveContext().setKeepAlive(false);
			completeResponse("500", "wrong body");
		}finally{
			PoolManager.poolArrayInstance(buffers);// 配列を返却
		}
	}

	/**
	 * リクエストデータを受信したことを通知
	 * 受信データは、getParameterParserで取得
	 * 
	 * @param buffers
	 */
	public void onRequestBody() {
	}
	
	//admin/auth配下のコンテンツを返却する。
	public void forwardPage(String fileName){
		MappingResult mapping=getRequestMapping();
		if(fileName.startsWith("/")){
			mapping.setResolvePath(fileName);
		}else{
			mapping.setResolvePath("/" +fileName);
		}
		forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
	}

	public void redirect(String location) {
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	
	/**
	 * このメソッドを呼ぶと必ずレスポンスを完結させる
	 * 
	 * @param statusCode
	 */
	public void completeResponse(String statusCode) {
		completeResponse(statusCode, (ByteBuffer) null);
	}

	/**
	 * このメソッドを呼ぶと必ずレスポンスを完結させる
	 * 
	 * @param statusCode
	 * @param body
	 */
	public void completeResponse(String statusCode, String body) {
		try {
			if(body==null){
				completeResponse(statusCode,(ByteBuffer)null);
			}else{
				completeResponse(statusCode, body.getBytes(ENCODE));
			}
		} catch (UnsupportedEncodingException e) {
			logger.error("fail to getBytes().", e);
		}
	}

	/**
	 * レスポンスを完結させる
	 * 
	 * @param requestParser
	 * @param body
	 */
	public void completeResponse(String statusCode, byte[] body) {
		completeResponse(statusCode, ByteBuffer.wrap(body));
	}

	/**
	 * レスポンスを完結させる
	 * 
	 * @param requestParser
	 * @param body
	 */
	public void completeResponse(String statusCode, ByteBuffer body) {
		if (statusCode != null) {
			setStatusCode(statusCode);
		}
		if (body != null) {
			responseContentLengthApl = (long) body.remaining();
			setContentLength(responseContentLengthApl);
			responseBody(body);
		}
		responseEnd();
	}

	/*
	public void responseHeaderAndRestBody() {
		// setupResponseHeader();//body長は確定していない
		// responseContentLengthApl=responseHeader.getContentLength();
		// body長を確定する。
		String statusCode = responseHeader.getStatusCode();
		if ("304".equals(statusCode) || "204".equals(statusCode)) {
			responseContentLengthApl = 0;
		} else {
			responseContentLengthApl = responseHeader.getContentLength();
		}
		ByteBuffer[] body = responseHeader.getBodyBuffer();
		if (body != null) {
			responseBody(body);
		}
	}
	*/

	/**
	 * コンテンツ長分レスポンスしたらfalseを復帰。
	 * 
	 * @return
	 */
	private boolean needMoreResponse() {
		// responseBody呼び出し後に有効に動作
		// その前に呼び出した場合、例外するのが親切
		if (responseContentLengthApl < 0) {
			return true;// content長の指定なし
		}
		if (responseContentLengthApl > responseWriteBodyApl) {
			return true;
		}
		return false;
	}

	/**
	 * レスポンスヘッダを確定してからじゃないとgzipは確定しない
	 * 
	 * @param isAllResponse
	 *            全レスポンスがメモリ上にあるか否か
	 */
	private void setupResponseHeader() {
		String httpVersion = responseHeader.getResHttpVersion();
		if (httpVersion == null) {// レスポンスバージョンが確定していない場合、myProxyがWebサーバ
			// myProxyがWebサーバなのでServerヘッダを追加
			responseHeader.setResHttpVersion(HeaderParser.HTTP_VESION_11);
			String serverHeader=getServerHeader();
			if(serverHeader!=null){//場合によってはServerヘッダを出力しない
				responseHeader.setHeader("Server", serverHeader);
			}
			responseHeader.setHeader("Date", HeaderParser
					.fomatDateHeader(new Date()));
		}
		String statusCode = responseHeader.getStatusCode();
		if ("304".equals(statusCode) || "204".equals(statusCode)) {
			responseContentLengthApl = 0;
		} else {
			responseContentLengthApl = responseHeader.getContentLength();
		}
		if (setupGzip()) {
			logger.debug("contents gzip response.id:" + getPoolId());
		}
		return;
	}

	/* レスポンスヘッダの変更が可能か否かを判断するメソッド */
	/*
	public boolean isCommitted() {
		return !isFlushFirstResponse;
	}
	*/

	/* WebHandler継承クラスがレスポンス終了を知らせるメソッド */
	// TODO keepAliveでfowardした後responseEndが呼び出される事がある。
	// handlerが初期化されているので、判定する方法がない。
	public void responseEnd() {
		logger.debug("#responseEnd isResponseEnd:"+isResponseEnd +":cid:"+getChannelId()+":"+hashCode());
		SpdySession spdySession=getSpdySession();
		synchronized (this) {
			if(isResponseEnd){
				return;
			}
			isResponseEnd=true;
			if(spdySession!=null){
				spdyResponseEnd(spdySession);
				return;
			}
			if (getChannelId() == -1) {
				return;
			}
			logger.debug("responseEnd called.handler:" + toString());
			if (isFlushFirstResponse == false) {
				flushFirstResponse(null);
				isFlushFirstResponse = true;
			}
			endOfResponse();
			// doneKeepAlive();
		}
	}
	
	public boolean isResponseEnd(){
		return isResponseEnd;
	}

	/* accessLogは、RequestContextからの参照がきれたタイミングで自動で出力される */
	private void doAccessLog(){
		AccessLog accessLog = getAccessLog();
		if(accessLog==null){
			return;
		}
		accessLog.endProcess();
		accessLog.setStatusCode(responseHeader.getStatusCode());
		accessLog.setResponseHeaderLength(responseHeaderLength);
		accessLog.setContentType(responseHeader.getContentType());
		accessLog.setTransferEncoding(responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER));
		accessLog.setPlainResponseLength(responseWriteBodyApl);
		accessLog.setResponseLength(responseWriteBodyApl);
		accessLog.setContentEncoding(responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER));
		MappingResult mapping=getRequestMapping();
		if(Boolean.TRUE.equals(mapping.getOption(Mapping.OPTION_SKIP_PH_LOG))){
			accessLog.setSkipPhlog(true);
		}
		
		SpdySession spdySession=getSpdySession();
		if(spdySession==null){
			//当該リクエストでの実read長、warite長(sslの場合を考慮)
			accessLog.setRawRead(getTotalReadLength());
			accessLog.setRawWrite(getTotalWriteLength());
		}else{
			accessLog.setSpdyInfo(spdySession.spdyInfo());
		}
		
		Store readPeek = popReadPeekStore();
		if (readPeek != null && readPeek.getPutLength() >= 0) {
			logger.debug("#endOfResponse"+readPeek.getStoreId());
			accessLog.incTrace();
			readPeek.close(accessLog,readPeek);//closeが完了したらaccessLogに知らせてね
			accessLog.setRequestBodyDigest(readPeek.getDigest());
		} else {
			if (readPeek != null) {
				readPeek.close();
			}
		}
		
		Store writePeek=null;
		if(spdySession==null){
			writePeek = popWritePeekStore();
		}else{
			writePeek=spdySession.popSesponseBodyStore();
		}
		if (writePeek != null && writePeek.getPutLength() > 0) {
			accessLog.incTrace();
			writePeek.close(accessLog,writePeek);//closeが完了したらaccessLogに知らせてね
			accessLog.setResponseBodyDigest(writePeek.getDigest());
		} else {
			if (writePeek != null) {
				writePeek.close();
			}
		}
		//logも別スレッドが出力するのが本来だがデバッグの場合はここで出力する
		if(logger.isDebugEnabled()){
			accessLog.log(true);// loggerに出力（常に）
		}
	}
	
	private void endOfResponse() {
//		boolean isGzip = false;
		boolean isReadWrite = false;
		// long gzipResponseLength=0;
		/* gzip途中のデータをフラッシュ、必要があれば最終chunkを出力 */
		GzipContext gzipContext = getGzipContext();
		if (gzipContext != null) {
			ByteBuffer[] zipdBuffer = gzipContext.getZipedBuffer(true);
			if (zipdBuffer != null && BuffersUtil.remaining(zipdBuffer) != 0) {
				isReadWrite = internalWriteBody(true, false, zipdBuffer);
			} else {
				isReadWrite = internalWriteBody(true, false, null);
			}
//			isGzip = true;
		} else {
			isReadWrite = internalWriteBody(true, false, null);
		}
		
		doAccessLog();
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		keepAliveContext.endOfResponse();
		
		// このメソッドで実asyncWriteが出なければ、
		// OnWrittenPlainが呼ばれない可能性がある。
		// そのため、doneKeepAliveが呼ばれず、Contextの再利用に失敗する。
		if (!isReadWrite) {
			doneKeepAlive();
		}
	}

	// SSL Proxy系は、直接レスポンスするがアクセスログのレスポンス長表示のため、長さを加算する。
	protected void responseBodyLength(long length) {
		responseWriteBodyApl += length;
	}

	public void responseBody(ByteBuffer buffer) {
		responseBody(BuffersUtil.toByteBufferArray(buffer));
	}

	/**
	 * keepAliveするか否かを判断
	 * 
	 * @return
	 */
	private void prepareKeepAlive(long commitContentLength) {
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		keepAliveContext.prepareResponse(this, responseHeader,
				commitContentLength);
	}

	protected boolean doneKeepAlive() {
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		if (keepAliveContext != null) {
			boolean done = keepAliveContext.commitResponse(this);
			return done;
		}
		return false;
	}

	/**
	 * gzip encodingが可能か判断し、可能な場合、gzipContextを用意する
	 * 
	 * @return
	 */
	private boolean setupGzip() {
		GzipContext gzipContext = getGzipContext();
		if (responseContentLengthApl == 0 || gzipContext != null) {
			return false;
		}
		// 設定がgzipになっていなければなにもしない(複数のencodingを許す場合は再考要)
		String settingContentEncoding = config.getContentEncoding();
		if (!HeaderParser.CONTENT_ENCODING_GZIP.equalsIgnoreCase(settingContentEncoding)) {
			return false;
		}
		
		// transferEencodingなコンテンツは圧縮できない
		String transferEncoding = responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		if (transferEncoding!=null){
			return false;
		}
		// レスポンスが既にencodeされていたら何もしない
		String contentEncoding = responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER);
		if (contentEncoding != null) {
			return false;
		}
		// なぜか不明だが"application/zip"なコンテンツをgzipでレスポンスすると、IE7でデータが壊れた
		String contentType = responseHeader.getHeader(HeaderParser.CONTENT_TYPE_HEADER);
		if (contentType != null && contentType.indexOf("zip") >= 0) {
			return false;
		}

		HeaderParser requestHeader = getRequestHeader();
		String acceptEncoding = requestHeader.getHeader(HeaderParser.ACCEPT_ENCODING_HEADER);
		if (acceptEncoding != null) {
			String[] entry = acceptEncoding.split(",");
			for (int i = 0; i < entry.length; i++) {
				if (HeaderParser.CONTENT_ENCODING_GZIP.equalsIgnoreCase(entry[i].trim())) {
					responseHeader.setHeader(
							HeaderParser.CONTENT_ENCODING_HEADER,
							HeaderParser.CONTENT_ENCODING_GZIP);
					responseHeader.removeContentLength();
					gzipContext = (GzipContext) PoolManager
							.getInstance(GzipContext.class);
					setGzipContext(gzipContext);
					return true;
				}
			}
		}
		return false;
	}

	private ByteBuffer[] zipedIfNeed(boolean isLast, ByteBuffer[] buffers) {
		GzipContext gzipContext = getGzipContext();
		if (gzipContext == null || buffers == null) {
			return buffers;
		}
		return gzipContext.getZipedBuffer(isLast, buffers);
	}

	/**
	 * @param isLast
	 *            最終データか否か
	 * @param neadCallback
	 *            onWriteBodyのcallbackが必要か否か
	 * @param buffers
	 *            送信データ
	 * @return 実writeしたか否か？
	 */
	private boolean internalWriteBody(boolean isLast, boolean needCallback,ByteBuffer[] buffers) {
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		/* 必要があればchunkedして出力する */
		buffers = keepAliveContext.chunkedIfNeed(isLast, buffers);
		if (buffers == null) {
			if (needCallback) {
				onWrittenBody();
			}
			return false;
		}
		String writeContext;
		if (needCallback) {
			writeContext = WRITE_CONTEXT_BODY;
		} else {
			writeContext = WRITE_CONTEXT_BODY_INTERNAL;
		}
		if (responseWriteBody == 0) {
			//ここでNPEになることあり
			/*
			 * java.lang.NullPointerException
	at naru.aweb.http.WebServerHandler.internalWriteBody(WebServerHandler.java:591)
	at naru.aweb.http.WebServerHandler.responseBody(WebServerHandler.java:841)
	at naru.aweb.http.WebServerHandler.responseBody(WebServerHandler.java:482)
	at naru.aweb.http.WebServerHandler.completeResponse(WebServerHandler.java:283)
	at naru.aweb.http.WebServerHandler.completeResponse(WebServerHandler.java:265)
	at naru.aweb.http.WebServerHandler.completeResponse(WebServerHandler.java:252)
	at naru.aweb.handler.ProxyHandler.onRequestEnd(ProxyHandler.java:347)
	at naru.aweb.http.WebClientHandler.onRequestEnd(WebClientHandler.java:625)
	at naru.aweb.http.WebClientHandler.onClosed(WebClientHandler.java:434)
	at naru.async.ChannelHandler.onCloseClosed(ChannelHandler.java:575)
	at naru.async.core.Order.callbackClosed(Order.java:192)
	at naru.async.core.Order.internalCallback(Order.java:205)
	at naru.async.core.Order.callback(Order.java:253)
	at naru.async.core.ChannelContext.callback(ChannelContext.java:388)
	at naru.async.core.DispatchManager.service(DispatchManager.java:38)
	at naru.queuelet.core.QueueletWrapper.service(QueueletWrapper.java:305)
	at naru.queuelet.core.Terminal.service(Terminal.java:553)
	at naru.queuelet.core.ServiceThread.run(ServiceThread.java:65)
	at java.lang.Thread.run(Thread.java:722)
			 */
			AccessLog accessLog=getAccessLog();
			if(accessLog==null){
				logger.warn("accessLog is null.",new Throwable());
				return false;
			}
			accessLog.setTimeCheckPint(AccessLog.TimePoint.responseBody);
		}
		// bodyWriteCount++;
		long length = BuffersUtil.remaining(buffers);
/*
 * IE10でVelocityを経由したときだけ以下のエラーがデバッグコンソールにでる。
 * HTML1405: 無効な文字: U+0000 NULL。NULL 文字は使用できません。
 * HTML1507: 予期しない文字: U+0000 NULL。NULL 文字は使用できません。 
 * リロードした時だけ,表示に問題はなさそう
 */
//		BuffersUtil.hexDump("WebServerHandler#internalWriteBody",buffers);
		if (asyncWrite(writeContext, buffers)) {
			responseWriteBody += length;
			return true;
		}
		return false;
	}
	
	/**
	 * WebSocket用にheaderを即座にflushするメソッド
	 */
	protected void flushHeaderForWebSocket(String spec,String subprotocol) {
		ByteBuffer[] headerBuffer = responseHeader.getHeaderBuffer();
		if (headerBuffer == null) {// ヘッダが確定していない..
			logger.warn("flushHeader fail to getHeaderBuffer.cid:"+ getChannelId());
			asyncClose(null);// 回線を切断
			return;// 何をしても無駄
		}
		AccessLog accessLog = getAccessLog();
		if(accessLog==null){
			return;// 何をしても無駄
		}
		accessLog.setTimeCheckPint(AccessLog.TimePoint.responseHeader);
		responseHeaderLength = BuffersUtil.remaining(headerBuffer);
		boolean isPersist=false;
		Store responsePeek = null;
		MappingResult mapping=getRequestMapping();
		if(mapping!=null){
			switch (mapping.getLogType()) {
			case RESPONSE_TRACE:
			case TRACE:
				responsePeek = Store.open(true);
				ByteBuffer[] headerDup = PoolManager.duplicateBuffers(headerBuffer);
				responsePeek.putBuffer(headerDup);
				logger.debug("#flushHeader"+responsePeek.getStoreId());
				accessLog.incTrace();
				responsePeek.close(accessLog,responsePeek);
				accessLog.setResponseHeaderDigest(responsePeek.getDigest());
//				responsePeek = Store.open(true);
			case REQUEST_TRACE:
			case ACCESS:
				isPersist=true;
			case NONE:
				if(isPersist==false&&!WsProtocol.isWebSocketLog()){
					break;
				}
				AccessLog wsAccessLog=accessLog.copyForWs();
				StringBuffer sb=new StringBuffer();
				switch(mapping.getDestinationType()){
				case WS:
					sb.append("ws://");
					sb.append(mapping.getResolveServer());
					sb.append(mapping.getResolvePath());
					break;
				case WSS:
					sb.append("wss://");
					sb.append(mapping.getResolveServer());
					sb.append(mapping.getResolvePath());
					break;
				case HANDLER:
					//TODO 必要なのか？スマートにならないか？
					if(isSsl()){
						sb.append("wss://");
					}else{
						sb.append("ws://");
					}
					sb.append(config.getSelfDomain());
					sb.append(':');
					sb.append(config.getProperty(Config.SELF_PORT));
					sb.append(mapping.getSourcePath());
					break;
				}
				sb.append('[');
				sb.append(spec);
				sb.append(':');
				if(subprotocol!=null){
					sb.append(subprotocol);
				}
				sb.append(':');
				sb.append(getChannelId());
				sb.append(']');
				wsAccessLog.setRequestLine(sb.toString());
				wsAccessLog.setStatusCode("B=S");
				wsAccessLog.endProcess();
				wsAccessLog.setSourceType(AccessLog.SOURCE_TYPE_WS_HANDSHAKE);
				wsAccessLog.setPersist(isPersist);
				wsAccessLog.decTrace();
			}
		}
		asyncWrite(WRITE_CONTEXT_HEADER, headerBuffer);
//		if (responsePeek != null) {
//			pushWritePeekStore(responsePeek);
//		}
		isFlushFirstResponse = true;
		if (firstBody != null) {
			logger.error("flushHeader use only websocket.");
			asyncClose(null);
		}
	}
	
	private void setupTraceBody(){
		Store responsePeek = null;
		MappingResult mapping=getRequestMapping();
		if(mapping!=null){
			switch (mapping.getLogType()) {
			case RESPONSE_TRACE:
			case TRACE:
				responsePeek = Store.open(true);
			}
		}
		if (responsePeek != null) {
			SpdySession spdySession=getSpdySession();
			if(spdySession==null){
				pushWritePeekStore(responsePeek);
			}else{
				spdySession.pushResponseBodyStore(responsePeek);
			}
		}
	}
	
	private void traceHeader(boolean isHeaderOnlyResponse,ByteBuffer[] headerBuffer){
		Store responsePeek = null;
		AccessLog accessLog = getAccessLog();
		if(accessLog==null){
			logger.warn("accessLog is null",new Throwable());
			return;
		}
		MappingResult mapping=getRequestMapping();
		if(mapping!=null){
			switch (mapping.getLogType()) {
			case RESPONSE_TRACE:
			case TRACE:
				if(headerBuffer==null){//SPDY経由の場合はヘッダバッファはない
					headerBuffer=responseHeader.getHeaderBuffer();
				}else{
					headerBuffer=PoolManager.duplicateBuffers(headerBuffer);
				}
				responseHeaderLength = BuffersUtil.remaining(headerBuffer);
				responsePeek = Store.open(true);
				ByteBuffer[] headerDup = headerBuffer;
				responsePeek.putBuffer(headerDup);
				logger.debug("#flushFirstResponse"+responsePeek.getStoreId());
				accessLog.incTrace();
				responsePeek.close(accessLog,responsePeek);
				accessLog.setResponseHeaderDigest(responsePeek.getDigest());
			}
		}
		accessLog.setTimeCheckPint(AccessLog.TimePoint.responseHeader);
		if (isHeaderOnlyResponse) {
			/* headerだけのレスポンス */
			accessLog.setTimeCheckPint(AccessLog.TimePoint.responseBody);
			return;
		}
		/* bodyが続くレスポンス */
		logger.debug("flushFirstResponse cid:" + getChannelId());
	}

	/**
	 * 1リクエストで1回、初回body実書き込み時に呼び出される
	 * 
	 * @param secondBody
	 *            nullの場合、レスポンス終了
	 */
	private void flushFirstResponse(ByteBuffer[] secondBody) {
		/* 基本headerの確定 */
		setupResponseHeader();

		/* bodyを確定する */
		ByteBuffer[] bodyBuffers = BuffersUtil.concatenate(firstBody,secondBody);
		boolean isHeaderOnlyResponse=false;
		if (firstBody == null && secondBody == null) {
			isHeaderOnlyResponse=true;
		}
		firstBody=null;
		
		long commitContentLength = -1;
		if (secondBody == null) {
			bodyBuffers = zipedIfNeed(true, bodyBuffers);
			/* 全レスポンスがメモリ上にあるのでchunkedにする必要はない,コンテンツ長確定 */
			commitContentLength = BuffersUtil.remaining(bodyBuffers);
			responseHeader.setContentLength(commitContentLength);
		} else {
			bodyBuffers = zipedIfNeed(false, bodyBuffers);
		}

		/* keepAlive関連headerの設定 */
		prepareKeepAlive(commitContentLength);
		ByteBuffer[] headerBuffer = responseHeader.getHeaderBuffer();
		if (headerBuffer == null) {// ヘッダが確定していない..
			logger.warn("flushFirstResponse fail to getHeaderBuffer.cid:"
					+ getChannelId());
			logger.warn("firstBody:" + firstBody + ":secondBody:" + secondBody);
			asyncClose(null);// 回線を切断
			return;// 何をしても無駄
		}
		traceHeader(isHeaderOnlyResponse,headerBuffer);
		asyncWrite(WRITE_CONTEXT_LAST_HEADER, headerBuffer);
		if(isHeaderOnlyResponse){
			return;
		}
		setupTraceBody();
		if (secondBody == null) {// 全レスポンスがある場合これで最後
			internalWriteBody(true, true, bodyBuffers);
		} else {
			internalWriteBody(false, true, bodyBuffers);
		}
	}
	
	
	/* 短いリクエストの場合には、contentLengthを設定しなるべくKeepAliveが有効になるように制御 
	 * そのためにfirstBufferは即座に送信せずいちど持ちこたえる */
	public void responseBody(ByteBuffer[] buffers) {
		// bodyとしてwrite要求した長さを加算、write完了した長さは、SSLの場合もあるので難しい
		responseWriteBodyApl += BuffersUtil.remaining(buffers);
		SpdySession spdySession=getSpdySession();
		if(spdySession!=null){
			spdySesponseBody(spdySession,buffers);
			return;
		}
		boolean isCallbackOnWrittenBody = false;
		synchronized (this) {
			if (getChannelId() == -1) {
				PoolManager.poolBufferInstance(buffers);
				return;// 既に切れている
			}
			if (isFlushFirstResponse == false && firstBody != null) {
				flushFirstResponse(buffers);
				isFlushFirstResponse = true;
				return;// TODO
			} else if (isFlushFirstResponse == false && firstBody == null) {
				firstBody = buffers;// すぐには出力せず持ちこたえる
				isCallbackOnWrittenBody = true;
			}
		}
		if (isCallbackOnWrittenBody) {
			onWrittenBody();
			return;// TODO
		}
		if (isFlushFirstResponse) {
			buffers = zipedIfNeed(false, buffers);
			if (buffers == null) {// 圧縮したらなくなった
				onWrittenBody();
			} else {
				internalWriteBody(false, true, buffers);
			}
		}
		if (needMoreResponse()) {
			return;
		}
		// レスポンス終了
		responseEnd();
	}
	
	/**
	 * このクラスは、全ヘッダが読み込まれてから呼び出されるので、bodyデータだけがここを通過する。
	 * ヘッダ解析時に読み込んでしまったbody部分は、明示的に呼び出している。
	 * 
	 * @param buffers
	 */
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onReadPlain cid:" + getChannelId());
		ChunkContext requestChunkContext=getRequestContext().getRequestChunkContext();
		if (requestChunkContext.isEndOfData()) {
			//if (requestReadBody >= requestContentLength) {
			PoolManager.poolBufferInstance(buffers);
			return;// コンテンツ長を超えるデータを受信しても無視する
		}
		if (buffers != null) {
			requestReadBody += BuffersUtil.remaining(buffers);
			requestBody(requestChunkContext.decodeChunk(buffers));
		}
		if (!requestChunkContext.isEndOfData()) {
			SpdySession spdySession=getSpdySession();
			if(spdySession==null){
				asyncRead(null);
			}else{
				spdySession.asyncRead(null);
			}
			return;
		}
		// リクエストbody終了
		AccessLog accessLog=getAccessLog();
		if(accessLog==null){
			return;
		}
		accessLog.setTimeCheckPint(AccessLog.TimePoint.requestBody);
		onRequestBody();
	}

	public ChannelHandler forwardHandler(Class handlerClass) {
//		return forwardHandler(handlerClass, true);
//	}
//
//	public ChannelHandler forwardHandler(Class handlerClass,boolean callStartMethod) {
		boolean callStartMethod=true;
		logger.debug("#forwardHandler cid:" + getChannelId() + ":"+ handlerClass.getName());
		WebServerHandler handler = (WebServerHandler)super.allocHandler(handlerClass);
		handler.responseHeader.setAllHeaders(responseHeader);
		// request bodyを全部読んでfowardしようとしているのか？読まずにfowardしようとしているかが問題
		handler.requestContentLength = requestContentLength;
		handler.requestReadBody = requestReadBody;
		super.forwardHandler(handler);
		// WebServerHandler handler=
		// (WebServerHandler)super.forwardHandler(handlerClass);
		if (callStartMethod) {
			if (handler.requestContentLength > 0
					&& handler.requestContentLength <= handler.requestReadBody) {
				handler.onRequestBody();
			} else {
				handler.onRequestHeader();
			}
		}
		return handler;
	}

	protected void waitForNextRequest() {
		logger.debug("#waitForNextRequest cid:" + getChannelId());
		DispatchHandler handler = (DispatchHandler) super.forwardHandler(DispatchHandler.class);
		if(handler==null){//既にcloseされていた
			logger.warn("fail to forward Dispatcher.Can't keepAlive.");
			return;
		}
		handler.onStartRequest();
	}

	public void onFinished() {
		logger.debug("#onFinished cid:" + getChannelId());
		responseEnd();// SSL proxy系handler、および異常終了対策
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		if (keepAliveContext != null) {
			keepAliveContext.finishedOfServerHandler();
		}
		super.onFinished();
	}

	public void onWrittenPlain(Object userContext) {
		logger.debug("#onWrittenPlain cid:" + getChannelId()+":userContext:"+userContext);
		if (userContext == WRITE_CONTEXT_BODY) {
			onWrittenBody();//この延長でresponseEndが呼び出される可能性がある、その場合contextがnullとなる
		}
		if (userContext == WRITE_CONTEXT_BODY
				|| userContext == WRITE_CONTEXT_BODY_INTERNAL
				|| userContext == WRITE_CONTEXT_LAST_HEADER) {
			synchronized (this) {
				//onWrittenBodyの延長でresponseEnd->doneKeepAliveが呼ばれた可能性がある。
				if(getChannelId()<0){
					return;
				}
				// logger.debug("onWrittenPlain.orderCount:"+orderCount());
				if (isResponseEnd) {
					if (doneKeepAlive()) {
						return;// keepaliveした
					}
				}
			}
		}
		super.onWrittenPlain(userContext);
	}

	/* responseBody完了時に通知される、 */
	public void onWrittenBody() {
		logger.debug("#onWrittenBody cid:" + getChannelId());
	}

	/* 出力をoutputStreamやwriter経由で指定するメソッド velocity等 */
	private OutputStream responseBodyStream;
	private Writer responseBodyWriter;

	private class ResponseBodyStream extends OutputStream {
		private ByteBuffer buffer;
		private int capacity;
		private int limit;

		ResponseBodyStream() {
			buffer = null;
		}

		public void close() throws IOException {
			responseBodyStream = null;
			responseBodyWriter = null;
			flush();
		}

		public void flush() throws IOException {
			if (buffer != null) {
				buffer.flip();
				responseBody(BuffersUtil.toByteBufferArray(buffer));
			}
			buffer = null;
		}

		public void write(byte[] src, int offset, int length)
				throws IOException {
			if (buffer != null && capacity < (limit + length)) {
				flush();
			}
			if (buffer == null) {
				buffer = PoolManager.getBufferInstance();
				capacity = buffer.capacity();
				limit = 0;
				// 用意されたバッファより大きいデータがwriteされてしまった。
				if (capacity < length) {
					PoolManager.poolBufferInstance(buffer);
					buffer = null;
					ByteBuffer[] buffers = BuffersUtil.buffers(src, offset,
							length);
					responseBody(buffers);
					return;
				}
			}
			buffer.put(src, offset, length);
			limit += length;
		}

		public void write(byte[] src) throws IOException {
			write(src, 0, src.length);
		}

		public void write(int src) throws IOException {
			write(new byte[] { (byte) src }, 0, 1);
		}
	}

	public OutputStream getResponseBodyStream() {
		if (responseBodyStream != null) {
			return responseBodyStream;
		}
		responseBodyStream = new ResponseBodyStream();
		return responseBodyStream;
	}

	public Writer getResponseBodyWriter(String enc)
			throws UnsupportedEncodingException {
		if (responseBodyWriter != null) {
			return responseBodyWriter;
		}
		responseBodyWriter = new OutputStreamWriter(getResponseBodyStream(),
				enc);
		return responseBodyWriter;
	}
	
	
	/**
	 * json文字列をレスポンスする
	 * 
	 * @param json　toStringがjsonとなるオブジェクト
	 */
	public void responseJson(Object json){
		responseJson(json,null);
	}
	
	/**
	 * json文字列をレスポンスする
	 * 
	 * @param json　toStringがjsonとなるオブジェクト
	 * @param callback callbackメソッド名
	 * @deprecated
	 */
	public void responseJson(Object json,String callback){
		setNoCacheResponseHeaders();//動的コンテンツなのでキャッシュさせない
		setContentType("text/javascript; charset=utf-8");
		setStatusCode("200");
		Writer out=null;
		try {
			out = getResponseBodyWriter("utf-8");//TODO writeTimeoutは聴かない
		} catch (UnsupportedEncodingException e) {
			completeResponse("500","fail to getWriter.");
			logger.error("fail to getWriter.",e);
			return;
		}
		try {
			if(callback!=null){
				out.write(callback);
				out.write("(");
			}
			if(json==null){
				out.write("null");
			}else{
				out.write(json.toString());
			}
			if(callback!=null){
				out.write(");");
			}
		} catch (IOException e) {
			logger.error("doJson IO error.",e);
		} catch (Throwable e) {
			logger.error("doJson IO error.!!",e);
		} finally {
			try {
				out.close();
			} catch (IOException ignore) {
			}
		}
		responseEnd();
	}
	
	/* spdy対応 */
	//headerをレスポンス
	private synchronized void spdyFlushResponseHeader(SpdySession spdySession,boolean isFin){
		setupResponseHeader();
		traceHeader(isFin,null);
		if(!isFin){
			setupTraceBody();
		}
		spdySession.responseHeader(isFin,responseHeader);
		isFlushFirstResponse = true;
	}
	
	/* spdyの場合、firstBody出力時にヘッダを出力、バッファ処理はしない */
	private void spdySesponseBody(SpdySession spdySession,ByteBuffer[] buffers){
		if (isFlushFirstResponse == false){
			if(firstBody == null) {
				firstBody=buffers;
				onWrittenBody();
				return;
			}else{
				buffers = BuffersUtil.concatenate(firstBody,buffers);
			}
			spdyFlushResponseHeader(spdySession,false);
			isFlushFirstResponse=true;
		}
		boolean	isFin=!needMoreResponse();
		buffers = zipedIfNeed(isFin, buffers);
		if(buffers==null){
			onWrittenBody();
			return;
		}
		//bodyをレスポンス
		spdySession.responseBody(isFin, buffers);
	}
	
	/* spdyの場合、firstBody出力時にヘッダを出力、バッファ処理はしない */
	private void spdyResponseEnd(SpdySession spdySession){
		/* 必要な場合ヘッダをflushする */
		if (isFlushFirstResponse == false){
			if(firstBody==null){
				//ヘッダだけのレスポンス205
				spdyFlushResponseHeader(spdySession,true);
			}else{
				//bodyが１バッファだけのレスポンス
				spdyFlushResponseHeader(spdySession,false);
				firstBody = zipedIfNeed(true, firstBody);
				//bodyをレスポンス
				spdySession.responseBody(true, firstBody);
				firstBody=null;
			}
		}else{
			GzipContext gzipContext = getGzipContext();
			ByteBuffer[] lastBuffer=null;
			if (gzipContext != null) {
				lastBuffer = gzipContext.getZipedBuffer(true);
			}
			//bodyをレスポンス,既にinFinで送っているかもしれないが、spdySession側で空振りする
			spdySession.responseBody(true, lastBuffer);
		}
		doAccessLog();
	}
}
