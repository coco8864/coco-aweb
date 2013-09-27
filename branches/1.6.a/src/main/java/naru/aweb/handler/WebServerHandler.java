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
 * HTTP�v���g�R������{�ɁA��Ƀ��X�|���X���n���h�����O����B HTTP�v���g�R���̃X�L�[���ɓ���Ȃ��v���g�R�����n���h�����O����ꍇ�ɂ́A
 * onRead���\�b�h���I�[�o�[���C�h����
 * 
 * @author Naru
 * 
 */
public class WebServerHandler extends ServerBaseHandler {
	/* �����Ŏw�肵�������́AVelocity�e���v���[�g����Q�Ƃł��� */
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

	private long responseHeaderLength;// ���X�|���X�w�b�_��
	private long responseContentLengthApl;// �A�v���P�[�V������,content-length�w�b�_�Ŏw�肵��contentLength
	private long responseWriteBodyApl;// ���ۂ�apl���烌�X�|���X�˗����ꂽbody��
	private long responseWriteBody;// ���ۂɃ��X�|���X����body��

	private boolean isFlushFirstResponse;
	private ByteBuffer[] firstBody;
	private boolean isResponseEnd;
	

	/**
	 * ���̃I�u�W�F�N�g���ė��p����ۂɌĂяo�����B<br/>
	 * override�����ꍇ�́A�K�������\�b�h���Ăяo���Ă��������B
	 */
	public void recycle() {
		logger.debug("#recycle :"+hashCode());
		requestContentLength = requestReadBody = 0;
		responseWriteBody = responseHeaderLength = responseWriteBodyApl = responseContentLengthApl = 0;
		responseHeader.recycle();
		responseBodyStream = null;
		responseBodyWriter = null;
		isFlushFirstResponse = false;
		isResponseEnd = false;// �����ȓ���������̂�pool�ɂ��邤����true�ɂ��������E�E�E
		firstBody = null;
		super.recycle();
	}

	/**
	 * �ۑ�����Ă���buffer���Ń��X�|���X�p�w�b�_��g�ݗ��Ă܂��B
	 * 
	 * @param buffers �w�b�_��񂪊i�[���ꂽByteBuffer�z��
	 * @return�@�w�肳�ꂽbuffers���w�b�_�Ƃ��Ċ������Ă��Ȃ��ꍇfalse
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
	 * �{�f�B�̉�͏������J�n���܂��B doResponse�Ăяo�����ɂ́Aread�v�����o���Ă��Ȃ��̂ŁA�ق��Ă����body�͓������Ȃ��B
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
		if (requestContentLength <= 0) {// GET�̂悤�ȏꍇbody�͂Ȃ�
			//������NPE�������邱�Ƃ���
			AccessLog accessLog=getAccessLog();
			if(accessLog==null){
				logger.warn("accessLog is null",new Throwable());
				return;
			}
			accessLog.setTimeCheckPint(AccessLog.TimePoint.requestBody);
			onRequestBody();// �p�����^�ǂݍ��݊�����ʒm
			return;
		}
		String transferEncoding=requestHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		ChunkContext requestChunkContext=getKeepAliveContext().getRequestContext().getRequestChunkContext();
		//spdy�̏ꍇ�́Atransfer_encoding�w�b�_�͖����͂������O�̂��߁Achunk���Ȃ�����
		if(HeaderParser.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncoding)&&getSpdySession()==null){
			requestChunkContext.decodeInit(true, -1);
		}else{
			requestChunkContext.decodeInit(false, requestContentLength);
		}
		ByteBuffer[] body = requestHeader.getBodyBuffer();
		// body�́A�K�����̃I�u�W�F�N�g��read���\�b�h��ʉ߂�����Bnull�ł����̒�����asyncRead���o��̂ŕK�v
		onReadPlain(null, body);
	}

	/**
	 * �Ǝ��Ƀ��X�|���X��ԋp�������l�́A���̃��\�b�h���I�[�o���C�h����
	 * ���̃��\�b�h�Ăяo�����_�ł́A�w�b�_��͎��ɓǂݍ���ł��܂���body������requestHeader�����Ɏc���Ă���_�ɒ���
	 * startParseBody���\�b�h�ł́A���̕����ɂ��Ė����I��onReadPlain���\�b�h���Ăяo���B
	 */
	public void onRequestHeader() {
		startParseRequestBody();
	}

	/**
	 * ���N�G�X�gbody�������������l�͂��̃��\�b�h���I�[�o���C�h���Ďg�� �f�t�H���g�ł́Aparameter�Ƃ��ĉ�͂��鏈��
	 * 
	 * @param buffers
	 */
	public void requestBody(ByteBuffer[] buffers) {
		ParameterParser parameterParser = getParameterParser();
		try {
			for (int i = 0; i < buffers.length; i++) {
				parameterParser.parse(buffers[i]);
			}
		} catch (IOException e) {// ���N�G�X�g�Ɍ�肪�������ꍇ�AIOException���オ���Ă���
			logger.warn("fail to parse body", e);
			//���N�G�X�g��S���ǂ�ł���Ƃ͌���Ȃ�����keepAlive�͋֎~
			getKeepAliveContext().setKeepAlive(false);
			completeResponse("500", "wrong body");
		}finally{
			PoolManager.poolArrayInstance(buffers);// �z���ԋp
		}
	}

	/**
	 * ���N�G�X�g�f�[�^����M�������Ƃ�ʒm
	 * ��M�f�[�^�́AgetParameterParser�Ŏ擾
	 * 
	 * @param buffers
	 */
	public void onRequestBody() {
	}
	
	//admin/auth�z���̃R���e���c��ԋp����B
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
	 * ���̃��\�b�h���ĂԂƕK�����X�|���X������������
	 * 
	 * @param statusCode
	 */
	public void completeResponse(String statusCode) {
		completeResponse(statusCode, (ByteBuffer) null);
	}

	/**
	 * ���̃��\�b�h���ĂԂƕK�����X�|���X������������
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
	 * ���X�|���X������������
	 * 
	 * @param requestParser
	 * @param body
	 */
	public void completeResponse(String statusCode, byte[] body) {
		completeResponse(statusCode, ByteBuffer.wrap(body));
	}

	/**
	 * ���X�|���X������������
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
		// setupResponseHeader();//body���͊m�肵�Ă��Ȃ�
		// responseContentLengthApl=responseHeader.getContentLength();
		// body�����m�肷��B
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
	 * �R���e���c�������X�|���X������false�𕜋A�B
	 * 
	 * @return
	 */
	private boolean needMoreResponse() {
		// responseBody�Ăяo����ɗL���ɓ���
		// ���̑O�ɌĂяo�����ꍇ�A��O����̂��e��
		if (responseContentLengthApl < 0) {
			return true;// content���̎w��Ȃ�
		}
		if (responseContentLengthApl > responseWriteBodyApl) {
			return true;
		}
		return false;
	}

	/**
	 * ���X�|���X�w�b�_���m�肵�Ă��炶��Ȃ���gzip�͊m�肵�Ȃ�
	 * 
	 * @param isAllResponse
	 *            �S���X�|���X����������ɂ��邩�ۂ�
	 */
	private void setupResponseHeader() {
		String httpVersion = responseHeader.getResHttpVersion();
		if (httpVersion == null) {// ���X�|���X�o�[�W�������m�肵�Ă��Ȃ��ꍇ�AmyProxy��Web�T�[�o
			// myProxy��Web�T�[�o�Ȃ̂�Server�w�b�_��ǉ�
			responseHeader.setResHttpVersion(HeaderParser.HTTP_VESION_11);
			String serverHeader=getServerHeader();
			if(serverHeader!=null){//�ꍇ�ɂ���Ă�Server�w�b�_���o�͂��Ȃ�
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

	/* ���X�|���X�w�b�_�̕ύX���\���ۂ��𔻒f���郁�\�b�h */
	/*
	public boolean isCommitted() {
		return !isFlushFirstResponse;
	}
	*/

	/* WebHandler�p���N���X�����X�|���X�I����m�点�郁�\�b�h */
	// TODO keepAlive��foward������responseEnd���Ăяo����鎖������B
	// handler������������Ă���̂ŁA���肷����@���Ȃ��B
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

	/* accessLog�́ARequestContext����̎Q�Ƃ����ꂽ�^�C�~���O�Ŏ����ŏo�͂���� */
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
			//���Y���N�G�X�g�ł̎�read���Awarite��(ssl�̏ꍇ���l��)
			accessLog.setRawRead(getTotalReadLength());
			accessLog.setRawWrite(getTotalWriteLength());
		}else{
			accessLog.setSpdyInfo(spdySession.spdyInfo());
		}
		
		Store readPeek = popReadPeekStore();
		if (readPeek != null && readPeek.getPutLength() >= 0) {
			logger.debug("#endOfResponse"+readPeek.getStoreId());
			accessLog.incTrace();
			readPeek.close(accessLog,readPeek);//close������������accessLog�ɒm�点�Ă�
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
			writePeek.close(accessLog,writePeek);//close������������accessLog�ɒm�点�Ă�
			accessLog.setResponseBodyDigest(writePeek.getDigest());
		} else {
			if (writePeek != null) {
				writePeek.close();
			}
		}
		//log���ʃX���b�h���o�͂���̂��{�������f�o�b�O�̏ꍇ�͂����ŏo�͂���
		if(logger.isDebugEnabled()){
			accessLog.log(true);// logger�ɏo�́i��Ɂj
		}
	}
	
	private void endOfResponse() {
//		boolean isGzip = false;
		boolean isReadWrite = false;
		// long gzipResponseLength=0;
		/* gzip�r���̃f�[�^���t���b�V���A�K�v������΍ŏIchunk���o�� */
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
		
		// ���̃��\�b�h�Ŏ�asyncWrite���o�Ȃ���΁A
		// OnWrittenPlain���Ă΂�Ȃ��\��������B
		// ���̂��߁AdoneKeepAlive���Ă΂ꂸ�AContext�̍ė��p�Ɏ��s����B
		if (!isReadWrite) {
			doneKeepAlive();
		}
	}

	// SSL Proxy�n�́A���ڃ��X�|���X���邪�A�N�Z�X���O�̃��X�|���X���\���̂��߁A���������Z����B
	protected void responseBodyLength(long length) {
		responseWriteBodyApl += length;
	}

	public void responseBody(ByteBuffer buffer) {
		responseBody(BuffersUtil.toByteBufferArray(buffer));
	}

	/**
	 * keepAlive���邩�ۂ��𔻒f
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
	 * gzip encoding���\�����f���A�\�ȏꍇ�AgzipContext��p�ӂ���
	 * 
	 * @return
	 */
	private boolean setupGzip() {
		GzipContext gzipContext = getGzipContext();
		if (responseContentLengthApl == 0 || gzipContext != null) {
			return false;
		}
		// �ݒ肪gzip�ɂȂ��Ă��Ȃ���΂Ȃɂ����Ȃ�(������encoding�������ꍇ�͍čl�v)
		String settingContentEncoding = config.getContentEncoding();
		if (!HeaderParser.CONTENT_ENCODING_GZIP.equalsIgnoreCase(settingContentEncoding)) {
			return false;
		}
		
		// transferEencoding�ȃR���e���c�͈��k�ł��Ȃ�
		String transferEncoding = responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		if (transferEncoding!=null){
			return false;
		}
		// ���X�|���X������encode����Ă����牽�����Ȃ�
		String contentEncoding = responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER);
		if (contentEncoding != null) {
			return false;
		}
		// �Ȃ����s������"application/zip"�ȃR���e���c��gzip�Ń��X�|���X����ƁAIE7�Ńf�[�^����ꂽ
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
	 *            �ŏI�f�[�^���ۂ�
	 * @param neadCallback
	 *            onWriteBody��callback���K�v���ۂ�
	 * @param buffers
	 *            ���M�f�[�^
	 * @return ��write�������ۂ��H
	 */
	private boolean internalWriteBody(boolean isLast, boolean needCallback,ByteBuffer[] buffers) {
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		/* �K�v�������chunked���ďo�͂��� */
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
			//������NPE�ɂȂ邱�Ƃ���
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
 * IE10��Velocity���o�R�����Ƃ������ȉ��̃G���[���f�o�b�O�R���\�[���ɂł�B
 * HTML1405: �����ȕ���: U+0000 NULL�BNULL �����͎g�p�ł��܂���B
 * HTML1507: �\�����Ȃ�����: U+0000 NULL�BNULL �����͎g�p�ł��܂���B 
 * �����[�h����������,�\���ɖ��͂Ȃ�����
 */
//		BuffersUtil.hexDump("WebServerHandler#internalWriteBody",buffers);
		if (asyncWrite(writeContext, buffers)) {
			responseWriteBody += length;
			return true;
		}
		return false;
	}
	
	/**
	 * WebSocket�p��header�𑦍���flush���郁�\�b�h
	 */
	protected void flushHeaderForWebSocket(String spec,String subprotocol) {
		ByteBuffer[] headerBuffer = responseHeader.getHeaderBuffer();
		if (headerBuffer == null) {// �w�b�_���m�肵�Ă��Ȃ�..
			logger.warn("flushHeader fail to getHeaderBuffer.cid:"+ getChannelId());
			asyncClose(null);// �����ؒf
			return;// �������Ă�����
		}
		AccessLog accessLog = getAccessLog();
		if(accessLog==null){
			return;// �������Ă�����
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
					//TODO �K�v�Ȃ̂��H�X�}�[�g�ɂȂ�Ȃ����H
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
				if(headerBuffer==null){//SPDY�o�R�̏ꍇ�̓w�b�_�o�b�t�@�͂Ȃ�
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
			/* header�����̃��X�|���X */
			accessLog.setTimeCheckPint(AccessLog.TimePoint.responseBody);
			return;
		}
		/* body���������X�|���X */
		logger.debug("flushFirstResponse cid:" + getChannelId());
	}

	/**
	 * 1���N�G�X�g��1��A����body���������ݎ��ɌĂяo�����
	 * 
	 * @param secondBody
	 *            null�̏ꍇ�A���X�|���X�I��
	 */
	private void flushFirstResponse(ByteBuffer[] secondBody) {
		/* ��{header�̊m�� */
		setupResponseHeader();

		/* body���m�肷�� */
		ByteBuffer[] bodyBuffers = BuffersUtil.concatenate(firstBody,secondBody);
		boolean isHeaderOnlyResponse=false;
		if (firstBody == null && secondBody == null) {
			isHeaderOnlyResponse=true;
		}
		firstBody=null;
		
		long commitContentLength = -1;
		if (secondBody == null) {
			bodyBuffers = zipedIfNeed(true, bodyBuffers);
			/* �S���X�|���X����������ɂ���̂�chunked�ɂ���K�v�͂Ȃ�,�R���e���c���m�� */
			commitContentLength = BuffersUtil.remaining(bodyBuffers);
			responseHeader.setContentLength(commitContentLength);
		} else {
			bodyBuffers = zipedIfNeed(false, bodyBuffers);
		}

		/* keepAlive�֘Aheader�̐ݒ� */
		prepareKeepAlive(commitContentLength);
		ByteBuffer[] headerBuffer = responseHeader.getHeaderBuffer();
		if (headerBuffer == null) {// �w�b�_���m�肵�Ă��Ȃ�..
			logger.warn("flushFirstResponse fail to getHeaderBuffer.cid:"
					+ getChannelId());
			logger.warn("firstBody:" + firstBody + ":secondBody:" + secondBody);
			asyncClose(null);// �����ؒf
			return;// �������Ă�����
		}
		traceHeader(isHeaderOnlyResponse,headerBuffer);
		asyncWrite(WRITE_CONTEXT_LAST_HEADER, headerBuffer);
		if(isHeaderOnlyResponse){
			return;
		}
		setupTraceBody();
		if (secondBody == null) {// �S���X�|���X������ꍇ����ōŌ�
			internalWriteBody(true, true, bodyBuffers);
		} else {
			internalWriteBody(false, true, bodyBuffers);
		}
	}
	
	
	/* �Z�����N�G�X�g�̏ꍇ�ɂ́AcontentLength��ݒ肵�Ȃ�ׂ�KeepAlive���L���ɂȂ�悤�ɐ��� 
	 * ���̂��߂�firstBuffer�͑����ɑ��M���������ǎ����������� */
	public void responseBody(ByteBuffer[] buffers) {
		// body�Ƃ���write�v���������������Z�Awrite�������������́ASSL�̏ꍇ������̂œ��
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
				return;// ���ɐ؂�Ă���
			}
			if (isFlushFirstResponse == false && firstBody != null) {
				flushFirstResponse(buffers);
				isFlushFirstResponse = true;
				return;// TODO
			} else if (isFlushFirstResponse == false && firstBody == null) {
				firstBody = buffers;// �����ɂ͏o�͂���������������
				isCallbackOnWrittenBody = true;
			}
		}
		if (isCallbackOnWrittenBody) {
			onWrittenBody();
			return;// TODO
		}
		if (isFlushFirstResponse) {
			buffers = zipedIfNeed(false, buffers);
			if (buffers == null) {// ���k������Ȃ��Ȃ���
				onWrittenBody();
			} else {
				internalWriteBody(false, true, buffers);
			}
		}
		if (needMoreResponse()) {
			return;
		}
		// ���X�|���X�I��
		responseEnd();
	}
	
	/**
	 * ���̃N���X�́A�S�w�b�_���ǂݍ��܂�Ă���Ăяo�����̂ŁAbody�f�[�^������������ʉ߂���B
	 * �w�b�_��͎��ɓǂݍ���ł��܂���body�����́A�����I�ɌĂяo���Ă���B
	 * 
	 * @param buffers
	 */
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#onReadPlain cid:" + getChannelId());
		ChunkContext requestChunkContext=getRequestContext().getRequestChunkContext();
		if (requestChunkContext.isEndOfData()) {
			//if (requestReadBody >= requestContentLength) {
			PoolManager.poolBufferInstance(buffers);
			return;// �R���e���c���𒴂���f�[�^����M���Ă���������
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
		// ���N�G�X�gbody�I��
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
		// request body��S���ǂ��foward���悤�Ƃ��Ă���̂��H�ǂ܂���foward���悤�Ƃ��Ă��邩�����
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
		if(handler==null){//����close����Ă���
			logger.warn("fail to forward Dispatcher.Can't keepAlive.");
			return;
		}
		handler.onStartRequest();
	}

	public void onFinished() {
		logger.debug("#onFinished cid:" + getChannelId());
		responseEnd();// SSL proxy�nhandler�A����шُ�I���΍�
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		if (keepAliveContext != null) {
			keepAliveContext.finishedOfServerHandler();
		}
		super.onFinished();
	}

	public void onWrittenPlain(Object userContext) {
		logger.debug("#onWrittenPlain cid:" + getChannelId()+":userContext:"+userContext);
		if (userContext == WRITE_CONTEXT_BODY) {
			onWrittenBody();//���̉�����responseEnd���Ăяo�����\��������A���̏ꍇcontext��null�ƂȂ�
		}
		if (userContext == WRITE_CONTEXT_BODY
				|| userContext == WRITE_CONTEXT_BODY_INTERNAL
				|| userContext == WRITE_CONTEXT_LAST_HEADER) {
			synchronized (this) {
				//onWrittenBody�̉�����responseEnd->doneKeepAlive���Ă΂ꂽ�\��������B
				if(getChannelId()<0){
					return;
				}
				// logger.debug("onWrittenPlain.orderCount:"+orderCount());
				if (isResponseEnd) {
					if (doneKeepAlive()) {
						return;// keepalive����
					}
				}
			}
		}
		super.onWrittenPlain(userContext);
	}

	/* responseBody�������ɒʒm�����A */
	public void onWrittenBody() {
		logger.debug("#onWrittenBody cid:" + getChannelId());
	}

	/* �o�͂�outputStream��writer�o�R�Ŏw�肷�郁�\�b�h velocity�� */
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
				// �p�ӂ��ꂽ�o�b�t�@���傫���f�[�^��write����Ă��܂����B
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
	 * json����������X�|���X����
	 * 
	 * @param json�@toString��json�ƂȂ�I�u�W�F�N�g
	 */
	public void responseJson(Object json){
		responseJson(json,null);
	}
	
	/**
	 * json����������X�|���X����
	 * 
	 * @param json�@toString��json�ƂȂ�I�u�W�F�N�g
	 * @param callback callback���\�b�h��
	 * @deprecated
	 */
	public void responseJson(Object json,String callback){
		setNoCacheResponseHeaders();//���I�R���e���c�Ȃ̂ŃL���b�V�������Ȃ�
		setContentType("text/javascript; charset=utf-8");
		setStatusCode("200");
		Writer out=null;
		try {
			out = getResponseBodyWriter("utf-8");//TODO writeTimeout�͒����Ȃ�
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
	
	/* spdy�Ή� */
	//header�����X�|���X
	private synchronized void spdyFlushResponseHeader(SpdySession spdySession,boolean isFin){
		setupResponseHeader();
		traceHeader(isFin,null);
		if(!isFin){
			setupTraceBody();
		}
		spdySession.responseHeader(isFin,responseHeader);
		isFlushFirstResponse = true;
	}
	
	/* spdy�̏ꍇ�AfirstBody�o�͎��Ƀw�b�_���o�́A�o�b�t�@�����͂��Ȃ� */
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
		//body�����X�|���X
		spdySession.responseBody(isFin, buffers);
	}
	
	/* spdy�̏ꍇ�AfirstBody�o�͎��Ƀw�b�_���o�́A�o�b�t�@�����͂��Ȃ� */
	private void spdyResponseEnd(SpdySession spdySession){
		/* �K�v�ȏꍇ�w�b�_��flush���� */
		if (isFlushFirstResponse == false){
			if(firstBody==null){
				//�w�b�_�����̃��X�|���X205
				spdyFlushResponseHeader(spdySession,true);
			}else{
				//body���P�o�b�t�@�����̃��X�|���X
				spdyFlushResponseHeader(spdySession,false);
				firstBody = zipedIfNeed(true, firstBody);
				//body�����X�|���X
				spdySession.responseBody(true, firstBody);
				firstBody=null;
			}
		}else{
			GzipContext gzipContext = getGzipContext();
			ByteBuffer[] lastBuffer=null;
			if (gzipContext != null) {
				lastBuffer = gzipContext.getZipedBuffer(true);
			}
			//body�����X�|���X,����inFin�ő����Ă��邩������Ȃ����AspdySession���ŋ�U�肷��
			spdySession.responseBody(true, lastBuffer);
		}
		doAccessLog();
	}
}
