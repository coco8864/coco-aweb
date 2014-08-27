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
 * http���N�G�X�g��proxy���N�G�X�g�ɑ΂��ă��X�|���X����handler�̊��N���X<br/>
 * http���N�G�X�g��proxy���N�G�X�g�ɑ΂��ă��X�|���X�������ꍇ�A�p�����ăA�v���P�[�V�����N���X���쐬���Ă��������B<br/>
 * Servlet�A�v���P�[�V������Servlet�N���X�����ł����Ahandler�I�u�W�F�N�g�̃X�R�[�v��request�ł��B<br/>
 * ���N�G�X�g���ɍ쐬����A�g�p������͍ė��p����܂��B�ė��p�����ۂɂ́Arecycle���\�b�h���Ăяo����܂��B<br/>
 * �I���������K�v�ȏꍇ�́Arecycle��override���ċL�q���Ă��������B<br/>
 * �A�v���P�[�V�����쐬�菇
 * <ol>
 * <li>WebServerHandler���p�����ăA�v���P�[�V�����N���X���쐬</li>
 * <li>onRequestHeader���\�b�h��override���āAparameter����header��񂩂�r�W�l�X���W�b�N���������{</li>
 * <li>completeResponse���\�b�h�Ń��X�|���X��ԋp</li>
 * <li>setXXX�n�̃��\�b�h��responseHeader��ݒ�</li>
 * <li>responseBody���\�b�h��responseBody��ݒ�</li>
 * <li>responseEnd���\�b�h�ŁA���X�|���X�̏I����ʒm</li>
 * <li>responseJSON���\�b�h��JSON�`���̃��X�|���X��ԋp</li>
 * <li>...</li>
 * <li>console��mapping�^�u�ŁAdestinationType:HANDLER�AdestinationServer:1.�ō쐬�����N���X�����w��
 * ��`���ꂽ���N�G�X�g�����������ꍇ�ɁA���̃N���X�����X�|�����X�ɗ��p����܂��B</li>
 * </ol>
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
	@Override
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
	 * ByteBuffer���Ń��X�|���X�p�w�b�_��g�ݗ��Ă܂��B<br/>
	 * �w�b�_��񂪃o�C�g��Ƃ��ĕۑ�����Ă���ꍇ�ɗ��p���܂�<br/>
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

	/**
	 * ���X�|���X�R�[�h��ݒ肵�܂��B
	 * @param statusCode ���X�|���X�R�[�h
	 */
	public void setStatusCode(String statusCode) {
		responseHeader.setStatusCode(statusCode);
	}
	/**
	 * ���X�|���X�R�[�h��reasonPhrase��ݒ肵�܂��B<br/>
	 * "HTTP/1.1 404 Not Found"�̏ꍇ�A"Not Found"��reasonPhrase
	 * @param statusCode ���X�|���X�R�[�h
	 * @param reasonPhrase ���R������
	 */
	public void setStatusCode(String statusCode,String reasonPhrase) {
		responseHeader.setStatusCode(statusCode,reasonPhrase);
	}

	/**
	 * ���X�|���X�R�[�h��ԋp���܂��B
	 * @return ���X�|���X�R�[�h
	 */
	public String getStatusCode() {
		return responseHeader.getStatusCode();
	}

	/**
	 * ���X�|���X��HTTP�o�[�W������ݒ肵�܂��B
	 * @param httpVersion HTTP�o�[�W����
	 */
	public void setHttpVersion(String httpVersion) {
		responseHeader.setResHttpVersion(httpVersion);
	}

	/**
	 * ���X�|���X�w�b�_��ݒ肵�܂��B
	 * @param name header��
	 * @param value header�l
	 */
	public void setHeader(String name, String value) {
		responseHeader.setHeader(name, value);
	}

	/**
	 * ���X�|���X�w�b�_���폜���܂��B
	 * @param name header��
	 */
	public void removeHeader(String name) {
		responseHeader.removeHeader(name);
	}
	
	/**
	 * ���X�|���X�̃R���e���c���w�b�_���폜���܂��B
	 */
	public void removeContentLength() {
		responseHeader.removeContentLength();
	}

	/**
	 * ���X�|���X�̃R���e���c���w�b�_��ݒ肵�܂��B
	 * @param contentLength �R���e���c��
	 */
	public void setContentLength(long contentLength) {
		responseHeader.setContentLength(contentLength);
	}

	/**
	 * ���X�|���X�̃R���e���c�^�C�v��ݒ肵�܂��B
	 * @param contentType �R���e���c�^�C�v
	 */
	public void setContentType(String contentType) {
		responseHeader.setContentType(contentType);
	}
	
	/**
	 * ���X�|���X�̃w�b�_���擾���܂��B
	 * @param name header��
	 * @return header�l
	 */
	public String getHeader(String name) {
		return responseHeader.getHeader(name);
	}

	/**
	 * �L���b�V���������w�b�_���܂Ƃ߂Đݒ肵�܂��B
	 */
	public void setNoCacheResponseHeaders() {
		responseHeader.setHeader("Pragma", "no-cache");
		responseHeader.setHeader("Cache-Control", "no-cache");
		responseHeader.setHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
	}

	/**
	 * ���X�|���X�{�f�B�̉�͏������J�n���܂��B onRequestHeader�Ăяo�����ɂ́Aread�v�����o���Ă��Ȃ��̂ŁA�ق��Ă����body�͓������Ȃ��B
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
		onReadPlain(body, null);
	}

	/**
	 * �Ǝ��Ƀ��X�|���X��ԋp�������A�v���P�[�V�����́A���̃��\�b�h���I�[�o���C�h����<br/>
	 * header�������ɒʒm����邽�߁Abody��������������ꍇ�ɗ��p����B
	 * ���̃��\�b�h�Ăяo�����_�ł́A�w�b�_��͎��ɓǂݍ���ł��܂���body������requestHeader�����Ɏc���Ă���_�ɒ���
	 * startParseBody���\�b�h�ł́A���̕����ɂ��Ė����I��onReadPlain���\�b�h���Ăяo���B
	 */
	public void onRequestHeader() {
		startParseRequestBody();
	}

	/**
	 * ���N�G�X�gbody�����������������A�v���P�[�V�����͂��̃��\�b�h���I�[�o���C�h���� �B<br/>
	 * ���̃��\�b�h�̏����́Aparameter�Ŏ�M�f�[�^����͂���<br/>
	 * @param buffers ���N�G�X�gbody
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
	 * �Ǝ��Ƀ��X�|���X��ԋp�������A�v���P�[�V�����́A���̃��\�b�h���I�[�o���C�h����<br/>
	 * body�������ɒʒm�����Bbody�́Aparameter�ɉ�͂��ꂽ��ԂŊi�[����Ă���B<br/>
	 * ��M�f�[�^�́AgetParameterParser�Ŏ擾<br/>
	 */
	public void onRequestBody() {
	}
	
	/**
	 * maping��destinationPath�z���ɂ���html��vsp,vsf���烌�X�|���X��g�ݗ��Ă�
	 * @param fileName forward��y�[�W
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
	 * ���_�C���N�g���X�|���X(302)��ԋp���܂��B
	 * @param location ���_�C���N�g��
	 */
	public void redirect(String location) {
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	
	/**
	 * �w��̃R�[�h�Ń��X�|���X��ԋp���܂��B
	 * @param statusCode �X�e�[�^�X�R�[�h
	 */
	public void completeResponse(String statusCode) {
		completeResponse(statusCode, (ByteBuffer) null);
	}

	/**
	 * �w��̃R�[�h,body�f�[�^�Ń��X�|���X��ԋp���܂��B
	 * @param statusCode �X�e�[�^�X�R�[�h
	 * @param body�@body�f�[�^
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
	 * �w��̃R�[�h,body�f�[�^�Ń��X�|���X��ԋp���܂��B
	 * @param statusCode �X�e�[�^�X�R�[�h
	 * @param body�@body�f�[�^
	 */
	public void completeResponse(String statusCode, byte[] body) {
		completeResponse(statusCode, ByteBuffer.wrap(body));
	}

	/**
	 * �w��̃R�[�h,body�f�[�^�Ń��X�|���X��ԋp���܂��B
	 * @param statusCode �X�e�[�^�X�R�[�h
	 * @param body�@body�f�[�^
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
	 * �R���e���c�������X�|���X������false�𕜋A�B
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

	/* WebHandler�p���N���X�����X�|���X�I����m�点�郁�\�b�h */
	// TODO keepAlive��foward������responseEnd���Ăяo����鎖������B
	// handler������������Ă���̂ŁA���肷����@���Ȃ��B
	/**
	 * ���X�|���X���I�������܂��B<br/>
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
	 * responseEnd���\�b�h���Ăяo���ꂽ���ۂ���ԋp���܂��B
	 * @return responseEnd���\�b�h���Ăяo����Ă����ꍇtrue
	 */
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
		boolean isReadWrite = false;
		/* gzip�r���̃f�[�^���t���b�V���A�K�v������΍ŏIchunk���o�� */
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

	// SSL Proxy�n�́A���ڃ��X�|���X���邪�A�N�Z�X���O�̃��X�|���X���\���̂��߁A���������Z����B
	protected void responseBodyLength(long length) {
		responseWriteBodyApl += length;
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
		if (asyncWrite(buffers, writeContext)) {
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
				if(headerBuffer==null){//SPDY�o�R�̏ꍇ�̓w�b�_�o�b�t�@�͂Ȃ�
					/* �w�b�_��񂩂�t�ϊ� */
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
		asyncWrite(headerBuffer, WRITE_CONTEXT_LAST_HEADER);
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
	
	/**
	 * ���X�|���X�{�f�B��ԋp���܂��B
	 * @param buffer ���X�|���X�{�f�B
	 */
	public void responseBody(ByteBuffer buffer) {
		responseBody(BuffersUtil.toByteBufferArray(buffer));
	}

	
	
	/* �Z�����N�G�X�g�̏ꍇ�ɂ́AcontentLength��ݒ肵�Ȃ�ׂ�KeepAlive���L���ɂȂ�悤�ɐ��� 
	 * ���̂��߂�firstBuffer�͑����ɑ��M���������ǎ����������� */
	/**
	 * ���X�|���X�{�f�B��ԋp���܂��B
	 * @param buffer ���X�|���X�{�f�B
	 */
	public void responseBody(ByteBuffer[] buffers) {
		// body�Ƃ���write�v���������������Z�Awrite�������������́ASSL�̏ꍇ������̂œ��
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
	 * �S�w�b�_���ǂݍ��܂�Ă���Ăяo�����̂ŁAbody�f�[�^������������ʉ߂���B<br/>
	 * �w�b�_��͎��ɓǂݍ���ł��܂���body�����́A�����I�ɌĂяo���Ă���B<br/>
	 * ���̃��N�G�X�g�{�f�B�������������ꍇ�́Aoverride����<br/>
	 * @param buffers ���̃��N�G�X�g�{�f�B
	 */
	public void onReadPlain(ByteBuffer[] buffers, Object userContext) {
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

	/**
	 * ��handler�ɏ����������p���܂��B<br/>
	 */
	public ChannelHandler forwardHandler(Class handlerClass) {
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

	/**
	 * ���Yhandler�ŏ��u���ɉ����������ꂽ�ꍇ�ɒʒm����܂��B<br/>
	 * override����ꍇ�́A�����\�b�h���Ăяo���Ă��������B<br/>
	 */
	public void onFinished() {
		logger.debug("#onFinished cid:" + getChannelId());
		responseEnd();// SSL proxy�nhandler�A����шُ�I���΍�
		KeepAliveContext keepAliveContext = getKeepAliveContext();
		if (keepAliveContext != null) {
			keepAliveContext.finishedOfServerHandler();
		}
		super.onFinished();
	}

	/**
	 * �������݂������������Ƃ�ʒm�B<br/>
	 * override���Ȃ�<br/>
	 */
	@Override
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
				/*
				if (isResponseEnd) {
					if (doneKeepAlive()) {
						return;// keepalive����
					}
				}
				*/
			}
		}
		super.onWrittenPlain(userContext);
	}

	/**
	 * �{�f�B�̏������݂������������Ƃ�ʒm�B<br/>
	 * responseBody���Ăяo������callback�����<br/>
	 * ��ʂ̃f�[�^�����X�|���X����ꍇ�A�P�o�b�t�@�Â��M���邱�ƂŎg�p���������팸����<br/>
	 */
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

	/**
	 * ���X�|���X���X�g���[���ŕԋp����ꍇ��OutputStream���擾
	 * @return ���X�|���X�X�g���[��
	 */
	public OutputStream getResponseBodyStream() {
		if (responseBodyStream != null) {
			return responseBodyStream;
		}
		responseBodyStream = new ResponseBodyStream();
		return responseBodyStream;
	}

	/**
	 * ���X�|���X��writer�ŕԋp����ꍇ��writer���擾
	 * @return ���X�|���Xwriter
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
	 * json����������X�|���X����
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
		KeepAliveContext keepAliveContext = spdySession.getKeepAliveContext();
		keepAliveContext.endOfResponse();
	}
}
