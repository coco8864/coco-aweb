package naru.aweb.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.store.Page;
import naru.async.store.Store;
import naru.aweb.core.RealHost;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

public class HeaderParser extends PoolBase {
	private static Logger logger = Logger.getLogger(HeaderParser.class);
	private static Properties resonPhraseProperties = new Properties();
	static {
		InputStream is = null;
		try {
			is = HeaderParser.class
					.getResourceAsStream("resonPhrase.properties");
			resonPhraseProperties.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ignore) {
				}
			}
		}
	}
	public static final String HTTP_VESION_10 = "HTTP/1.0";
	public static final String HTTP_VESION_11 = "HTTP/1.1";
	public static final String HOST_HEADER = "Host";
	public static final String CONNECT_METHOD = "CONNECT";
	public static final String GET_METHOD = "GET";
	public static final String POST_METHOD = "POST";
	public static final String HEAD_METHOD = "HEAD";
	public static final String CONNECION_KEEP_ALIVE = "Keep-Alive";
	public static final String CONNECION_CLOSE = "Close";
	public static final String TRANSFER_ENCODING_CHUNKED = "chunked";
	public static final String CONTENT_LENGTH_HEADER = "Content-length";
	public static final String CONTENT_TYPE_HEADER = "Content-type";
	public static final String CONTENT_DISPOSITION_HEADER = "Content-Disposition";
	public static final String IF_MODIFIED_SINCE_HEADER = "If-Modified-Since";
	public static final String IF_NONE_MATCH = "If-None-Match";
	public static final String LAST_MODIFIED_HEADER = "Last-Modified";
	public static final String LOCATION_HEADER = "Location";
	public static final String COOKIE_HEADER = "Cookie";// ���N�G�X�g�w�b�_
	public static final String SET_COOKIE_HEADER = "Set-Cookie";// ���X�|���X�w�b�_
	public static final String PROXY_AUTHORIZATION_HEADER = "Proxy-Authorization";// ���N�G�X�g�w�b�_
	public static final String PROXY_AUTHENTICATE_HEADER = "Proxy-Authenticate";// ���X�|���X�w�b�_
	public static final String WWW_AUTHORIZATION_HEADER = "Authorization";
	public static final String WWW_AUTHENTICATE_HEADER = "WWW-Authenticate";
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN="Access-Control-Allow-Origin";
	
	public static final String CONNECTION_HEADER = "Connection";
	public static final String PROXY_CONNECTION_HEADER = "Proxy-Connection";
	public static final String KEEP_ALIVE_HEADER = "Keep-Alive";
	public static final String UPGRADE_HEADER = "Upgrade";
	public static final String WEB_SOCKET = "WebSocket";
	public static final String WEB_SOCKET_CONNECTION = "Upgrade";
	// ���N�G�X�g�Ɏw�肳���A�u���E�U�����߂ł��鈳�k�^�C�v,��jAccept-Encoding: gzip, deflate
	public static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
	// ���X�|���X�Ɏw�肳���A�R���e���c�̈��k�^�C�v,��jContent-Encoding: gzip
	public static final String CONTENT_ENCODING_HEADER = "Content-Encoding";
	public static final String CONTENT_ENCODING_GZIP = "gzip";
	// chunked���ۂ�,��jTransfer-encoding: chunked
	public static final String TRANSFER_ENCODING_HEADER = "Transfer-encoding";
	public static final String HEADER_ENCODE = "ISO8859_1";
	public static final byte[] HEADER_SEP = ": ".getBytes();
	public static final byte[] CRLF = "\r\n".getBytes();
	public static final byte[] SPACE = " ".getBytes();

	private static SimpleDateFormat headerDateFormat = null;
	static {
		headerDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",Locale.US);
		headerDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	public static Date parseDateHeader(String header) {
		if (header == null) {
			return null;
		}
		String fields[] = header.split(";");
		try {
			synchronized (headerDateFormat) {
				return headerDateFormat.parse(fields[0]);
			}
		} catch (ParseException e) {
			logger.warn("fail to parse date header." + header);
			return null;
		}
	}

	public static String fomatDateHeader(Date date) {
		synchronized (headerDateFormat) {
			return headerDateFormat.format(date);
		}
	}

	public static HeaderParser createByStore(Store store) {
		if (store == null) {
			return null;
		}
		HeaderParser headerParser = (HeaderParser) PoolManager
				.getInstance(HeaderParser.class);
		ByteBuffer[] buffers = DataUtil.toByteBuffers(store);
		for (ByteBuffer buffer : buffers) {
			headerParser.parse(buffer);
		}
		// ���g��parse�ŏ�����̂Ŕz����������
		PoolManager.poolArrayInstance(buffers);
		if (headerParser.isParseOk()) {
			return headerParser;
		}
		headerParser.unref(true);
		return null;
	}

	public static HeaderParser createByStore(long storeId) {
		Store store = Store.open(storeId);
		return createByStore(store);
	}

	public static HeaderParser createByStore(String digest) {
		Store store = Store.open(digest);
		return createByStore(store);
	}

	// ���Y�w�b�_��M�O��CONNECT���\�b�h�Ŏw�肳��Ă������
	// private String sslPeekHost;
	// private int sslPeekPort;
	private String firstLine;
	private String firstLineToken1;
	private String firstLineToken2;
	private String firstLineToken3;

	// ���̃N���X��parse�����ꍇ�̃w�b�_����ێ�����B
	private long headerLength;
	// private List rawHeaders;
	private Map<String, String> headerNames;// �w�b�_�Ƃ��ẮA�啶���������̋�ʂ��Ȃ��A�Ή��\������
	private Map<String, List<String>> headers;
	private String contentType;
	private long contentLength;
	private String addressBar;
	// private byte[] buffer;
	private List<ByteBuffer> bodyBuffers = new ArrayList<ByteBuffer>();

	private void setFirstLine(String firstLine) {
		this.firstLine = firstLine;
		if (firstLine.length() == 0) {
			return;
		}
		StringTokenizer st = new StringTokenizer(firstLine, " ");
		firstLineToken1 = st.nextToken();
		if (!st.hasMoreTokens()) {
			return;
		}
		firstLineToken2 = st.nextToken();
		if (!st.hasMoreTokens()) {
			return;
		}
		// resonPhrase�̒��ɂ́A�X�y�[�X���܂ނ��̂�����
		firstLineToken3 = st.nextToken("");
		firstLineToken3 = firstLineToken3.trim();

		if (firstLine.startsWith("HTTP")) {
			// ���X�|���X�̃X�e�[�^�X���C��"HTTP/1.1 200 OK"
			isRequest = false;
		} else {
			// ���N�G�X�g�̃��N�G�X�g���C��"GET / HTTP/1.0"
			isRequest = true;
		}
	}

	public static String stringfier(byte[] header, int pos, int end) {
		try {
			// TODO���������œK���ł���
			String result = new String(header, pos, end - pos, HEADER_ENCODE);
			return result.trim();
		} catch (UnsupportedEncodingException e) {
			logger.error("new String error.", e);
		}
		return null;
	}

	public void addRawHeader(String header) {
		int pos = header.indexOf(":");
		String headerName;
		String headerValue;
		if (pos > 0) {
			headerName = header.substring(0, pos).trim();
			headerValue = header.substring(pos + 1).trim();
		} else {
			headerName = header;
			headerValue = "";
		}
		addHeader(headerName, headerValue);
	}

	public void addRawHeader(byte[] header, int pos, int end) {
		int sep = indexOfByte(header, pos, end, ':');
		String headerName;
		String headerValue;
		if (sep > 0) {
			headerName = stringfier(header, pos, sep);
			headerValue = stringfier(header, (sep + 1), end);
		} else {
			headerName = stringfier(header, pos, end);
			headerValue = "";
		}
		addHeader(headerName, headerValue);
	}

	public void addLine(byte[] header, int pos, int end) {
		if (isFirstLine) {
			setFirstLine(stringfier(header, pos, end));
			isFirstLine = false;
		} else {
			addRawHeader(header, pos, end);
		}
	}

	public HeaderParser() {
		headers = new HashMap<String, List<String>>();
		headerNames = new HashMap<String, String>();
	}

	public void recycle() {
		headers.clear();
		headerNames.clear();
		firstLine = firstLineToken1 = firstLineToken2 = firstLineToken3 = null;
		headerLength = 0;
		contentLength = -1;
		contentType=null;
		// phase=PHASE_FIRST_LINE;
//		PoolManager.poolBufferInstance(bodyBuffers);
		bodyBuffers.clear();
		isRequest = false;
		isProxy = false;
		isSslProxy = false;
		isWs = false;
		if (server != null) {
			server.unref();
			server = null;
		}
		path = query = null;
		isFirstLine = true;
		parseSt = ST.INIT;
		lestOfHeader = null;
		addressBar = null;
	}

	// private int phase=PHASE_FIRST_LINE;
	// private int buffIndex = 0;
	private enum ST {
		INIT, LEST, R, RN, RNR, OK, ERROR,
	}

	private ST parseSt = ST.INIT;
	private boolean isFirstLine = true;
	private byte[] lestOfHeader = null;

	private void concatLestOfHeader(byte[] buf, int pos, int end) {
		int length = 0;
		if (lestOfHeader == null) {
			length = end - pos;
		} else {
			length = lestOfHeader.length + (end - pos);
		}
		byte[] lest = new byte[length];
		if (lestOfHeader != null) {
			System.arraycopy(lestOfHeader, 0, lest, 0, lestOfHeader.length);
			System.arraycopy(buf, pos, lest, lestOfHeader.length, (end - pos));
		} else {
			System.arraycopy(buf, pos, lest, 0, (end - pos));
		}
		lestOfHeader = lest;
	}

	private void parseLestOfHeader() {
		if (lestOfHeader != null) {
			addLine(lestOfHeader, 0, lestOfHeader.length);
			lestOfHeader = null;
		}
	}

	public static int indexOfByte(byte bytes[], int off, int end, char qq) {
		// Works only for UTF
		while (off < end) {
			byte b = bytes[off];
			if (b == qq)
				return off;
			off++;
		}
		return -1;
	}

	public static int lastIndexOfByte(byte bytes[], int off, int end, char qq) {
		// Works only for UTF
		end--;
		while (off < end) {
			byte b = bytes[end];
			if (b == qq)
				return end;
			end--;
		}
		return -1;
	}

	public boolean isParseEnd() {
		return parseSt == ST.OK || parseSt == ST.ERROR;
	}

	public boolean isParseError() {
		return parseSt == ST.ERROR;
	}

	public boolean isParseOk() {
		return parseSt == ST.OK;
	}

	// key1: value1\r\n
	// key2: value2\r\n
	// key3: value3\r\n\r\n
	// X[st0]\r[st1]\n[st2]\r[st3]\n[st4]
	// X[st0]\r[st1]\n[st2]X[st0]
	private int parseHeader(byte[] headers, int begin, int end) {
		int posR = indexOfByte(headers, begin, end, '\r');
		if (posR >= 0) {
			addLine(headers, begin, posR);
			parseSt = ST.R;
			return posR + 1;
		} else {
			concatLestOfHeader(headers, begin, end);
			parseSt = ST.LEST;
			return end;
		}
	}

	private int parseLine(byte[] headers, int position, int end) {
		int posR;
		switch (parseSt) {
		case INIT:
			return parseHeader(headers, position, end);
		case LEST:
			posR = indexOfByte(headers, position, end, '\r');
			if (posR >= 0) {
				concatLestOfHeader(headers, position, posR);
				addLine(lestOfHeader, 0, lestOfHeader.length);
				lestOfHeader = null;
				parseSt = ST.R;
				return posR + 1;
			} else {
				concatLestOfHeader(headers, position, end);
				parseSt = ST.LEST;
				return end;
			}
		case R:
			if (headers[position] == '\n') {
				parseSt = ST.RN;
				return position + 1;
			} else if (headers[position] == '\r') {
				logger.warn("end of header \\r\\r");
				parseSt = ST.RNR;// \r\r�͏I�[�Ɖ���
				return position + 1;
			} else {
				return parseHeader(headers, position, end);
			}
		case RN:
			if (headers[position] == '\r') {
				parseSt = ST.RNR;
				return position + 1;
			} else if (headers[position] == '\n') {
				parseSt = ST.ERROR;
				return position + 1;
			} else {
				return parseHeader(headers, position, end);
			}
		case RNR:
			if (headers[position] == '\n') {
				parseSt = ST.OK;
				return position + 1;
			} else if (headers[position] == '\r') {
				parseSt = ST.ERROR;
				return position + 1;
			} else {
				logger.warn("end of header \\r\\n\\r");
				parseSt = ST.OK;
				return position;
			}
		default:
			throw new IllegalStateException();
		}
	}

	public boolean parse(ByteBuffer buffer) {
		if (isParseEnd()) {
			addBodyBuffer(buffer);
			return true;
		}
		int bufferLength = buffer.remaining();
		byte[] headers = buffer.array();
		int position = buffer.position();
		int end = position + bufferLength;
		while (true) {
			int newPosition = parseLine(headers, position, end);
			headerLength += (newPosition - position);
			position = newPosition;
			if (isParseOk()) {
				parseFinish();
				buffer.position(position);
				if (buffer.hasRemaining()) {
					addBodyBuffer(buffer);
				} else {
					PoolManager.poolBufferInstance(buffer);
				}
				return true;
			} else if (isParseError()) {
				PoolManager.poolBufferInstance(buffer);
				return true;
			} else if (position == end) {
				PoolManager.poolBufferInstance(buffer);
				return false;
			}
		}
	}

	private void addBodyBuffer(ByteBuffer buffer) {
		bodyBuffers.add(buffer);
	}

	public ByteBuffer[] peekBodyBuffer() {
		if (bodyBuffers.size() == 0) {
			return null;
		}
		ByteBuffer[] result = (ByteBuffer[]) bodyBuffers.toArray(BuffersUtil
				.newByteBufferArray(bodyBuffers.size()));
		return PoolManager.duplicateBuffers(result);
	}

	public ByteBuffer[] getBodyBuffer() {
		if (bodyBuffers.size() == 0) {
			return null;
		}
		ByteBuffer[] result = (ByteBuffer[]) bodyBuffers.toArray(BuffersUtil
				.newByteBufferArray(bodyBuffers.size()));
		bodyBuffers.clear();// �������܂��ēǂ񂶂��������������S�~����̂��ߑ��߂ɊJ������B
		return result;
	}

	private boolean isRequest;// ���N�G�X�g�w�b�_���H,���X�|���X�w�b�_���H
	// �ȍ~���N�G�X�g�w�b�_�̏ꍇ�ɗL��
	private boolean isProxy;
	private boolean isSslProxy;
	private boolean isWs;// webSockets���N�G�X�g���ۂ�?
	private ServerParser server;
	private String path;
	private String query;
	private static Pattern httpServerPattern = Pattern
			.compile("(?:http://([^/\\?\\s]*))?(/[^\\?\\s]*)?(?:\\?(\\S*))?");

	private void parseFinish() {
		contentType = getHeader(CONTENT_TYPE_HEADER);
		contentLength = getLongHeader(CONTENT_LENGTH_HEADER);
		if (!isRequest) {
			return;
		}
		String uri = getRequestUri();
		if (CONNECT_METHOD.equalsIgnoreCase(getMethod())) {
			isProxy = true;// SSL proxy
			isSslProxy = true;
			server = ServerParser.parse(uri, 443);
			return;
		}
		Matcher matcher = null;
		synchronized (httpServerPattern) {
			matcher = httpServerPattern.matcher(uri);
		}
		/*
		 * java.lang.IllegalArgumentException: fail to match.uri:version="1.0"
		 * at naru.aweb.http.HeaderParser.parseFinish(HeaderParser.java:512) at
		 * naru.aweb.http.HeaderParser.parse(HeaderParser.java:442) at
		 * naru.aweb.http.WebClientHandler.onReadPlain(WebClientHandler.java:260)
		 * at naru.async.ssl.SslHandler.callbackReadPlain(SslHandler.java:156)
		 * at naru.async.ssl.SslHandler.onRead(SslHandler.java:164) at
		 * naru.aweb.http.WebClientHandler.onRead(WebClientHandler.java:242) at
		 * naru.async.core.Order.internalCallback(Order.java:191) at
		 * naru.async.core.Order.callback(Order.java:228) at
		 * naru.async.core.ChannelContext.callback(ChannelContext.java:347) at
		 * naru.async.core.DispatchManager.service(DispatchManager.java:34) at
		 * naru.queuelet.core.QueueletWrapper.service(QueueletWrapper.java:268)
		 * at naru.queuelet.core.Terminal.service(Terminal.java:487) at
		 * naru.queuelet.core.ServiceThread.run(ServiceThread.java:56) at
		 * java.lang.Thread.run(Thread.java:619) ����ȃG���[���ł鎖������B
		 * debug����߂�AkeepAlive�͊֌W�Ȃ� ����body����M���Ă��� �T�[�o������
		 */
		if (!matcher.matches()) {//
			logger.error("fail to match.uri:" + uri);
			// throw new IllegalArgumentException("fail to match.uri:"+uri);
			parseSt = ST.ERROR;
			return;
		}
		String serverString = matcher.group(1);
		path = matcher.group(2);
		query = matcher.group(3);
		if (path != null) {
			// int pos=path.lastIndexOf("/");
			// if(pos>0){
			// file=path.substring(pos+1);
			// }else{
			// file=path;
			// }
		} else {
			path = "/";
		}
		if (serverString != null) {// http�Ŏn�܂��Ă����Ƃ������Ƃ́Aproxy�Ƃ��ē��삵�Ă���
		// proxy�ւ̃��N�G�X�g
			isProxy = true;
			server = ServerParser.parse(serverString, 80);
		} else {//TODO WebSocket���N�G�X�g��proxy����ꍇ�͂ǂ�����́H
			String upgradeHeader = getHeader(UPGRADE_HEADER);
			if (WEB_SOCKET.equals(upgradeHeader)) {
				isWs = true;
			}
			// Web�T�[�o�ւ̃��N�G�X�g,sslproxy�̏ꍇ��������ʂ�̂ŁA�|�[�g�ԍ��̏ȗ��l�͌��߂��Ȃ�
			isProxy = false;
			serverString = getHeader(HOST_HEADER);
			if (serverString != null) {
				server = ServerParser.parse(serverString, -1);
			} else {// host�w�b�_���Ȃ�...
				server = null;
			}
		}
	}

	// ���N�G�X�g�n�̃��\�b�h
	public String getRequestLine() {
		if (firstLine == null) {
			firstLine = getMethod() + " " + getRequestUri() + " "
					+ getReqHttpVersion();
		}
		return firstLine;
	}

	public String getMethod() {
		return firstLineToken1;
	}

	public void setMethod(String method) {
		firstLineToken1 = method;
	}

	public String getRequestUri() {
		return firstLineToken2;
	}

	public void setRequestUri(String requestUri) {
		firstLineToken2 = requestUri;
	}

	public String getReqHttpVersion() {
		return firstLineToken3;
	}

	public void setReqHttpVersion(String httpVersion) {
		firstLineToken3 = httpVersion;
	}

	public boolean isProxy() {
		return isProxy;
	}

	public boolean isSslProxy() {
		return isSslProxy;
	}

	public boolean isWs() {
		return isWs;
	}

	public ServerParser getServer() {
		return server;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getQuery() {
		return query;
	}

	/**
	 * ���N�G�X�g�n��͌��ʂɑ΂��āAweb�ɂ���proxy�ɂ���A�u���E�U�̃A�h���X�o�[�ɉ������͂��ꂽ����ԋp
	 * ���\���l���Č��ʂ̓L���b�V������addressBar !!!!�Q�x�ڈȍ~�̌Ăяo���Ńp�����^��ω��������ꍇ�́A�Ӑ}�ʂ�̌��ʂƂȂ�Ȃ�!!!
	 * 
	 * @param isSsl
	 *            ssl���ۂ��́A���N�G�X�g�w�b�_�����ł͕�����Ȃ�����A�g�p�ґ����w�肷��
	 * @param domain
	 *            �ȗ����ꂽ�ꍇ�́AHOST�w�b�_����擾
	 * @return
	 */
	public String getAddressBar(boolean isSsl, ServerParser domain) {
		if (isProxy) {
			return getRequestUri();
		}
		if (addressBar != null) {
			return addressBar;
		}
		StringBuilder sb = new StringBuilder();
		if (isWs) {
			if (isSsl) {
				sb.append("wss://");
			} else {
				sb.append("ws://");
			}
		} else {
			if (isSsl) {
				sb.append("https://");
			} else {
				sb.append("http://");
			}
		}
		if (domain == null) {
			if (server == null) {
				throw new RuntimeException(
						"fail to getAddressBar cause by host");
			}
			domain = server;
		}
		sb.append(domain.toServerString(isSsl));
		sb.append(getRequestUri());
		addressBar = sb.toString();
		return addressBar;
	}

	public String getAddressBar() {
		return getAddressBar(false, null);
	}

	public String getAddressBar(boolean isSsl) {
		return getAddressBar(isSsl, null);
	}

	// ���X�|���X�n�̃��\�b�h
	public String getStatusLine() {
		return firstLine;
	}

	public String getResHttpVersion() {
		return firstLineToken1;
	}

	public void setResHttpVersion(String httpVersion) {
		firstLineToken1 = httpVersion;
	}

	public String getStatusCode() {
		return firstLineToken2;
	}

	public void setStatusCode(String statusCode) {
		String reasonPhrase = resonPhraseProperties.getProperty(statusCode);
		if (reasonPhrase == null) {
			reasonPhrase = "Unkown reason Phrase:" + statusCode;
		}
		setStatusCode(statusCode, reasonPhrase);
	}

	public void setStatusCode(String statusCode, String reasonPhrase) {
		firstLineToken2 = statusCode.trim();
		firstLineToken3 = reasonPhrase.trim();
	}

	public String getReasonPhrase() {
		return firstLineToken3;
	}

	// ���N�G�X�g���X�|���X���p
	// �����̓����w�b�_��z��
	private void putHeaderValues(String name, List<String> values) {
		headerNames.put(name.toUpperCase(), name);
		headers.put(name, values);
	}

	public List<String> getHeaders(String name) {
		Object realName = headerNames.get(name.toUpperCase());
		return headers.get(realName);
	}

	public String getHeader(String name) {
		List<String> values = getHeaders(name);
		if (values == null || values.size() == 0) {
			return null;
		}
		return (String) values.get(0);
	}

	public void replaceHeader(String name, String value) {
		List<String> values = getHeaders(name);
		if (values == null) {
			values = new ArrayList<String>();
			putHeaderValues(name, values);
		} else {
			values.clear();
		}
		values.add(value);
		/*
		 * if (this.headers.size() > MAX_HEADER_COUNT) { throw new
		 * RuntimeException("too many headers:" + this.headers.size()); }
		 */
	}

	public void removeHeader(String name) {
		List<String> values = getHeaders(name);
		if (values == null) {
			return;
		}
		Object realName = headerNames.remove(name.toUpperCase());
		headers.remove(realName);
	}

	public void setHeader(String name, String value) {
		List<String> values = getHeaders(name);
		if (values == null) {
			addHeader(name, value);
			return;
		}
		values.clear();
		values.add(value);
	}

	public void addHeader(String name, String value) {
		List<String> values = getHeaders(name);
		if (values == null) {
			values = new ArrayList<String>();
			putHeaderValues(name, values);
		}
		values.add(value);
		/*
		 * if (this.headers.size() > MAX_HEADER_COUNT) { throw new
		 * RuntimeException("too many headers:" + this.headers.size()); }
		 */
	}

	/**
	 * �S�w�b�_���ꊇ���Đݒ肷��B
	 * 
	 * @param header
	 */
	public void setAllHeaders(HeaderParser header) {
		headerNames.putAll(header.headerNames);
		Iterator<String> itr = header.headers.keySet().iterator();
		while (itr.hasNext()) {
			String name = (String) itr.next();
			List<String> values = header.headers.get(name);
			Iterator<String> valueItr = values.iterator();
			while (valueItr.hasNext()) {
				addHeader(name, (String) valueItr.next());
			}
		}
		// headers.putAll(header.headers);
		contentType = getHeader(CONTENT_TYPE_HEADER);
		contentLength = getLongHeader(CONTENT_LENGTH_HEADER);
	}

	public ByteBuffer[] getHeaderBuffer() {
		return getHeaderBuffer(null, null, null);
	}

	// [;][�󔒂���]key[�󔒂���]=[�󔒂���]value[�󔒂���] �`���̐��K�\��
	// '"'�Ŋ�����\��������炵���������ł͍l�����Ă��Ȃ�
	private Pattern makeCookiePatten(String name) {
		return Pattern.compile(" ?" + name + " ?= ?([^\\s;]*);?");
	}

	private String getAndRemoveCookieHeader(String cookieHeader,
			Pattern pattern, StringBuffer sb) {
		Matcher m = pattern.matcher(cookieHeader);
		String value = null;
		while (m.find()) {
			m.appendReplacement(sb, "");
			if (value == null) {
				value = m.group(1);
			}
		}
		m.appendTail(sb);
		return value;
	}

	/**
	 * Cookie(���N�G�X�g)����w�肳���name�ɑΉ�����value���擾 �����āA����Cookie���폜����
	 */
	public String getAndRemoveCookieHeader(String name) {
		// Cookie�w�b�_�́A�P�����Ȃ����O�̂��ߕ��������A���Ԃ��ς��
		// ���A����l�́Apath�Œ���v���擪�ł��邽�߁A�擪��value��ԋp����
		List<String> cookieHeaders = getHeaders(COOKIE_HEADER);
		if (cookieHeaders == null) {
			return null;
		}
		Pattern cookiePattern = makeCookiePatten(name);
		StringBuffer sb = new StringBuffer();
		String value = null;
		String cookieHeader = null;
		List<String> newCookieHeaders = null;
		Iterator<String> itr = cookieHeaders.iterator();
		while (itr.hasNext()) {
			cookieHeader = itr.next();
			String tmpValue = getAndRemoveCookieHeader(cookieHeader,
					cookiePattern, sb);
			if (tmpValue != null) {
				itr.remove();
				if (value == null) {
					value = tmpValue;
				}
				String newCookieHeader = sb.toString().trim();
				if (newCookieHeader.length() != 0) {
					if (newCookieHeaders == null) {
						newCookieHeaders = new ArrayList<String>();
					}
					newCookieHeaders.add(newCookieHeader);
				}
			}
			sb.setLength(0);
		}
		if (newCookieHeaders != null) {
			cookieHeaders.addAll(newCookieHeaders);
		}
		if (cookieHeaders.size() == 0) {
			removeHeader(COOKIE_HEADER);
		}
		return value;
	}

	private void addHeader(Page page, String name, String value)
			throws UnsupportedEncodingException {
		page.putBytes(name.getBytes(HEADER_ENCODE));
		page.putBytes(HEADER_SEP);
		if(value!=null){
			page.putBytes(value.getBytes(HEADER_ENCODE));
		}
		page.putBytes(CRLF);
	}

	// GET [/xxxx|http://xxx] HTTP1.1
	// HTTP1.1 200 OK
	public ByteBuffer[] getHeaderBuffer(String requestLine,
			Map<String, String> overloadHeaders, Set<String> deleteHeaders) {
		if (requestLine == null
				&& (firstLineToken1 == null || firstLineToken2 == null)) {
			logger.warn("getHeaderBuffer return null.firstLineToken1:"
					+ firstLineToken1 + ":firstLineToken2:" + firstLineToken2);
			return null;// statusCode���m�肵�Ă��Ȃ��ꍇ
		}
		ByteBuffer[] buffers = null;
		Page page = (Page) PoolManager.getInstance(Page.class);
		try {
			if (requestLine != null) {
				page.putBytes(requestLine.getBytes(HEADER_ENCODE));
			} else {
				page.putBytes(firstLineToken1.getBytes(HEADER_ENCODE));
				page.putBytes(SPACE);
				page.putBytes(firstLineToken2.getBytes(HEADER_ENCODE));
				page.putBytes(SPACE);
				page.putBytes(firstLineToken3.getBytes(HEADER_ENCODE));
			}
			page.putBytes(CRLF);
			Iterator<String> itr = headers.keySet().iterator();
			while (itr.hasNext()) {
				String name = itr.next();
				if (overloadHeaders != null
						&& overloadHeaders.get(name) != null) {
					continue;// overload���ꂽ�w�b�_�͏o�͂��Ȃ�
				}
				if (deleteHeaders != null && deleteHeaders.contains(name)) {
					continue;
				}
				List<String> values = headers.get(name);
				int n = values.size();
				for (int i = 0; i < n; i++) {
					String value = (String) values.get(i);
					addHeader(page, name, value);
				}
			}
			if (overloadHeaders != null) {
				itr = overloadHeaders.keySet().iterator();
				while (itr.hasNext()) {
					String name = itr.next();
					String value = overloadHeaders.get(name);
					addHeader(page, name, value);
				}
			}
			// �w�b�_�I�[
			page.putBytes(CRLF);
			buffers = page.getBuffer();
			// logger.debug("-------");
			// for(int i=0;i<buffers.length;i++){
			// logger.debug(new
			// String(buffers[i].array(),0,buffers[i].remaining()));
			// }
		} catch (UnsupportedEncodingException e) {// ���蓾�Ȃ�
			logger.error("ENCODE error." + HEADER_ENCODE, e);
			throw new RuntimeException("fail to writeHeader", e);
		} finally {
			page.unref(true);
		}
		return buffers;
	}

	/* �w�b�_����os�ɓ]������ */
	public void writeHeader(OutputStream os) {
		if (firstLineToken1 == null) {
			return;// statusCode���m�肵�Ă��Ȃ��ꍇ
		}
		try {
			os.write(firstLineToken1.getBytes(HEADER_ENCODE));
			os.write(SPACE);
			os.write(firstLineToken2.getBytes(HEADER_ENCODE));
			os.write(SPACE);
			os.write(firstLineToken3.getBytes(HEADER_ENCODE));
			os.write(CRLF);
			Iterator<String> itr = headers.keySet().iterator();
			while (itr.hasNext()) {
				String name = itr.next();
				List<String> values = headers.get(name);
				int n = values.size();
				for (int i = 0; i < n; i++) {
					os.write(name.getBytes(HEADER_ENCODE));
					os.write(HEADER_SEP);
					String value = (String) values.get(i);
					os.write(value.getBytes(HEADER_ENCODE));
					os.write(CRLF);
				}
			}
			// �w�b�_�I�[
			os.write(CRLF);
		} catch (UnsupportedEncodingException e) {// ���蓾�Ȃ�
			logger.error("ENCODE error." + HEADER_ENCODE, e);
			throw new RuntimeException("fail to writeHeader", e);
		} catch (IOException e) {
			logger.error("ENCODE error." + HEADER_ENCODE, e);
			throw new RuntimeException("fail to writeHeader", e);
		}
		return;
	}

	public long getLongHeader(String name) {
		String value = getHeader(name);
		if (value == null) {
			return -1;
		}
		return Long.parseLong(value);
	}

	public Iterator<String> getHeaderNames() {
		return headers.keySet().iterator();
	}

	public void setContentType(String contentType) {
		setHeader(CONTENT_TYPE_HEADER, contentType);
		this.contentType = contentType;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentLength(long contentLength) {
		setHeader(CONTENT_LENGTH_HEADER, Long.toString(contentLength));
		this.contentLength = contentLength;
	}

	public long getContentLength() {
		return contentLength;
	}

	public void removeContentLength() {
		removeHeader(CONTENT_LENGTH_HEADER);
		contentLength = -1;
	}

	public long getHeaderLength() {
		return headerLength;
	}

	public void setHost(String hostHeader) {
		setHeader(HOST_HEADER, hostHeader);
		if (server != null) {
			server.unref();
		}
		server = ServerParser.parse(hostHeader);
	}
	
	public void forceWebRequest(){//�����I��web���N�G�X�g�ɂ���
		isProxy=false;
		if(query==null){
			setRequestUri(path);
		}else{
			setRequestUri(path +'?'+ query);
		}
	}
}
