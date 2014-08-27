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
import naru.aweb.core.DispatchHandler;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.ChunkContext;
import naru.aweb.http.GzipContext;
import naru.aweb.mapping.Mapping;
import naru.aweb.mapping.MappingResult;
import naru.aweb.spdy.SpdySession;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;

/**
 * httpリクエストやproxyリクエストに対してレスポンスするhandlerの基底クラス<br/>
 * httpリクエストやproxyリクエストに対してレスポンスしたい場合、継承してアプリケーションクラスを作成してください。<br/>
 * ServletアプリケーションのServletクラス相当ですが、handlerオブジェクトのスコープはrequestです。<br/>
 * リクエスト毎に作成され、使用完了後は再利用されます。再利用される際には、recycleメソッドが呼び出されます。<br/>
 * 終了処理が必要な場合は、recycleをoverrideして記述してください。<br/>
 * アプリケーション作成手順
 * <ol>
 * <li>WebServerHandlerを継承してアプリケーションクラスを作成</li>
 * <li>onRequestHeaderメソッドをoverrideして、parameter情報やheader情報からビジネスロジック処理を実施</li>
 * <li>completeResponseメソッドでレスポンスを返却</li>
 * <li>setXXX系のメソッドでresponseHeaderを設定</li>
 * <li>responseBodyメソッドでresponseBodyを設定</li>
 * <li>responseEndメソッドで、レスポンスの終了を通知</li>
 * <li>responseJSONメソッドでJSON形式のレスポンスを返却</li>
 * <li>...</li>
 * <li>consoleのmappingタブで、destinationType:HANDLER、destinationServer:1.で作成したクラス名を指定
 * 定義されたリクエストが到着した場合に、このクラスがレスポンンスに利用されます。</li>
 * </ol>
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
	@Override
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
	 * ByteBuffer情報でレスポンス用ヘッダを組み立てます。<br/>
	 * ヘッダ情報がバイト列として保存されている場合に利用します<br/>
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

	/**
	 * レスポンスコードを設定します。
	 * @param statusCode レスポンスコード
	 */
	public void setStatusCode(String statusCode) {
		responseHeader.setStatusCode(statusCode);
	}
	/**
	 * レスポンスコードとreasonPhraseを設定します。<br/>
	 * "HTTP/1.1 404 Not Found"の場合、"Not Found"がreasonPhrase
	 * @param statusCode レスポンスコード
	 * @param reasonPhrase 理由文字列
	 */
	public void setStatusCode(String statusCode,String reasonPhrase) {
		responseHeader.setStatusCode(statusCode,reasonPhrase);
	}

	/**
	 * レスポンスコードを返却します。
	 * @return レスポンスコード
	 */
	public String getStatusCode() {
		return responseHeader.getStatusCode();
	}

	/**
	 * レスポンスのHTTPバージョンを設定します。
	 * @param httpVersion HTTPバージョン
	 */
	public void setHttpVersion(String httpVersion) {
		responseHeader.setResHttpVersion(httpVersion);
	}

	/**
	 * レスポンスヘッダを設定します。
	 * @param name header名
	 * @param value header値
	 */
	public void setHeader(String name, String value) {
		responseHeader.setHeader(name, value);
	}

	/**
	 * レスポンスヘッダを削除します。
	 * @param name header名
	 */
	public void removeHeader(String name) {
		responseHeader.removeHeader(name);
	}
	
	/**
	 * レスポンスのコンテンツ長ヘッダを削除します。
	 */
	public void removeContentLength() {
		responseHeader.removeContentLength();
	}

	/**
	 * レスポンスのコンテンツ長ヘッダを設定します。
	 * @param contentLength コンテンツ長
	 */
	public void setContentLength(long contentLength) {
		responseHeader.setContentLength(contentLength);
	}

	/**
	 * レスポンスのコンテンツタイプを設定します。
	 * @param contentType コンテンツタイプ
	 */
	public void setContentType(String contentType) {
		responseHeader.setContentType(contentType);
	}
	
	/**
	 * レスポンスのヘッダを取得します。
	 * @param name header名
	 * @return header値
	 */
	public String getHeader(String name) {
		return responseHeader.getHeader(name);
	}

	/**
	 * キャッシュ無効化ヘッダをまとめて設定します。
	 */
	public void setNoCacheResponseHeaders() {
		responseHeader.setHeader("Pragma", "no-cache");
		responseHeader.setHeader("Cache-Control", "no-cache");
		responseHeader.setHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
	}

	/**
	 * レスポンスボディの解析処理を開始します。 onRequestHeader呼び出し時には、read要求を出していないので、黙っているとbodyは到着しない。
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
		onReadPlain(body, null);
	}

	/**
	 * 独自にレスポンスを返却したいアプリケーションは、このメソッドをオーバライドする<br/>
	 * header到着時に通知されるため、bodyを順次処理する場合に利用する。
	 * このメソッド呼び出し時点では、ヘッダ解析時に読み込んでしまったbody部分はrequestHeader内部に残っている点に注意
	 * startParseBodyメソッドでは、その部分について明示的にonReadPlainメソッドを呼び出す。
	 */
	public void onRequestHeader() {
		startParseRequestBody();
	}

	/**
	 * リクエストbodyを順次処理したいアプリケーションはこのメソッドをオーバライドする 。<br/>
	 * このメソッドの処理は、parameterで受信データを解析する<br/>
	 * @param buffers リクエストbody
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
	 * 独自にレスポンスを返却したいアプリケーションは、このメソッドをオーバライドする<br/>
	 * body到着時に通知される。bodyは、parameterに解析された状態で格納されている。<br/>
	 * 受信データは、getParameterParserで取得<br/>
	 */
	public void onRequestBody() {
	}
	
	/**
	 * mapingのdestinationPath配下にあるhtmlやvsp,vsfからレスポンスを組み立てる
	 * @param fileName forward先ページ
	 */
	public void forwardPage(String fileName){
		MappingResult mapping=getRequestMapping();
		if(fileName.startsWith("/")){
			mapping.setResolvePath(fileName);
		}else{
			mapping.setResolvePath("/" +fileName);
		}
		forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
	}

	/**
	 * リダイレクトレスポンス(302)を返却します。
	 * @param location リダイレクト先
	 */
	public void redirect(String location) {
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	
	/**
	 * 指定のコードでレスポンスを返却します。
	 * @param statusCode ステータスコード
	 */
	public void completeResponse(String statusCode) {
		completeResponse(statusCode, (ByteBuffer) null);
	}

	/**
	 * 指定のコード,bodyデータでレスポンスを返却します。
	 * @param statusCode ステータスコード
	 * @param body　bodyデータ
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
	 * 指定のコード,bodyデータでレスポンスを返却します。
	 * @param statusCode ステータスコード
	 * @param body　bodyデータ
	 */
	public void completeResponse(String statusCode, byte[] body) {
		completeResponse(statusCode, ByteBuffer.wrap(body));
	}

	/**
	 * 指定のコード,bodyデータでレスポンスを返却します。
	 * @param statusCode ステータスコード
	 * @param body　bodyデータ
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

	/**
	 * コンテンツ長分レスポンスしたらfalseを復帰。
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

	/* WebHandler継承クラスがレスポンス終了を知らせるメソッド */
	// TODO keepAliveでfowardした後responseEndが呼び出される事がある。
	// handlerが初期化されているので、判定する方法がない。
	/**
	 * レスポンスを終了させます。<br/>
	 */
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
	
	/**
	 * responseEndメソッドが呼び出されたか否かを返却します。
	 * @return responseEndメソッドが呼び出されていた場合true
	 */
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
		boolean isReadWrite = false;
		/* gzip途中のデータをフラッシュ、必要があれば最終chunkを出力 */
		GzipContext gzipContext = getGzipContext();
		if (gzipContext != null) {
			ByteBuffer[] zipdBuffer = gzipContext.getZipedBuffer(true);
			if (zipdBuffer != null && BuffersUtil.remaining(zipdBuffer) != 0) {
				isReadWrite = internalWriteBody(true, false, zipdBuffer);
			} else {
				isReadWrite = internalWriteBody(true, false, null);
			}
		} else {
			isReadWrite = internalWriteBody(true, false, null);
		}
		
		doAccessLog();
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		keepAliveContext.endOfResponse();
		doneKeepAlive();
	}

	// SSL Proxy系は、直接レスポンスするがアクセスログのレスポンス長表示のため、長さを加算する。
	protected void responseBodyLength(long length) {
		responseWriteBodyApl += length;
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
		if (asyncWrite(buffers, writeContext)) {
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
		asyncWrite(headerBuffer, WRITE_CONTEXT_HEADER);
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
	
	private ByteBuffer NO_HEADER=ByteBuffer.wrap("no header".getBytes());
	
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
					/* ヘッダ情報から逆変換 */
					headerBuffer=responseHeader.getHeaderBuffer();
					if(headerBuffer==null){
						headerBuffer=BuffersUtil.toByteBufferArray(NO_HEADER.duplicate());
					}
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
		asyncWrite(headerBuffer, WRITE_CONTEXT_LAST_HEADER);
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
	
	/**
	 * レスポンスボディを返却します。
	 * @param buffer レスポンスボディ
	 */
	public void responseBody(ByteBuffer buffer) {
		responseBody(BuffersUtil.toByteBufferArray(buffer));
	}

	
	
	/* 短いリクエストの場合には、contentLengthを設定しなるべくKeepAliveが有効になるように制御 
	 * そのためにfirstBufferは即座に送信せずいちど持ちこたえる */
	/**
	 * レスポンスボディを返却します。
	 * @param buffer レスポンスボディ
	 */
	public void responseBody(ByteBuffer[] buffers) {
		// bodyとしてwrite要求した長さを加算、write完了した長さは、SSLの場合もあるので難しい
		responseWriteBodyApl += BuffersUtil.remaining(buffers);
		SpdySession spdySession=getSpdySession();
		if(spdySession!=null){
			spdyResponseBody(spdySession,buffers);
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
	 * 全ヘッダが読み込まれてから呼び出されるので、bodyデータだけがここを通過する。<br/>
	 * ヘッダ解析時に読み込んでしまったbody部分は、明示的に呼び出している。<br/>
	 * 生のリクエストボディを処理したい場合は、overrideする<br/>
	 * @param buffers 生のリクエストボディ
	 */
	public void onReadPlain(ByteBuffer[] buffers, Object userContext) {
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

	/**
	 * 他handlerに処理を引き継ぎます。<br/>
	 */
	public ChannelHandler forwardHandler(Class handlerClass) {
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

	/**
	 * 当該handlerで処置中に回線が回収された場合に通知されます。<br/>
	 * overrideする場合は、元メソッドも呼び出してください。<br/>
	 */
	public void onFinished() {
		logger.debug("#onFinished cid:" + getChannelId());
		responseEnd();// SSL proxy系handler、および異常終了対策
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		if (keepAliveContext != null) {
			keepAliveContext.finishedOfServerHandler();
		}
		super.onFinished();
	}

	/**
	 * 書き込みが完了したことを通知。<br/>
	 * overrideしない<br/>
	 */
	@Override
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
				/*
				if (isResponseEnd) {
					if (doneKeepAlive()) {
						return;// keepaliveした
					}
				}
				*/
			}
		}
		super.onWrittenPlain(userContext);
	}

	/**
	 * ボディの書き込みが完了したことを通知。<br/>
	 * responseBodyを呼び出す毎にcallbackされる<br/>
	 * 大量のデータをレスポンスする場合、１バッファづつ送信することで使用メモリを削減する<br/>
	 */
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

	/**
	 * レスポンスをストリームで返却する場合のOutputStreamを取得
	 * @return レスポンスストリーム
	 */
	public OutputStream getResponseBodyStream() {
		if (responseBodyStream != null) {
			return responseBodyStream;
		}
		responseBodyStream = new ResponseBodyStream();
		return responseBodyStream;
	}

	/**
	 * レスポンスをwriterで返却する場合のwriterを取得
	 * @return レスポンスwriter
	 */
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
	private void spdyResponseBody(SpdySession spdySession,ByteBuffer[] buffers){
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
		KeepAliveContext keepAliveContext = spdySession.getKeepAliveContext();
		keepAliveContext.endOfResponse();
	}
}
