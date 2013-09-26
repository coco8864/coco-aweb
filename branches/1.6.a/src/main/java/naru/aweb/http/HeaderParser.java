package naru.aweb.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
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
import naru.aweb.util.ServerParser;
import naru.aweb.util.StreamUtil;

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
	public static final String HOST_HEADER = "host";
	public static final String CONNECT_METHOD = "CONNECT";
	public static final String GET_METHOD = "GET";
	public static final String POST_METHOD = "POST";
	public static final String HEAD_METHOD = "HEAD";
	public static final String CONNECION_KEEP_ALIVE = "keep-alive";
	public static final String CONNECION_CLOSE = "close";
	public static final String TRANSFER_ENCODING_CHUNKED = "chunked";
	public static final String CONTENT_LENGTH_HEADER = "content-length";
	public static final String CONTENT_TYPE_HEADER = "content-type";
	public static final String CONTENT_DISPOSITION_HEADER = "content-disposition";
	public static final String IF_MODIFIED_SINCE_HEADER = "if-modified-since";
	public static final String IF_NONE_MATCH = "if-none-match";
	public static final String LAST_MODIFIED_HEADER = "last-modified";
	public static final String REFERER = "referer";
	
	public static final String LOCATION_HEADER = "location";
	public static final String COOKIE_HEADER = "cookie";// リクエストヘッダ
	public static final String SET_COOKIE_HEADER = "set-cookie";// レスポンスヘッダ
	public static final String PROXY_AUTHORIZATION_HEADER = "proxy-authorization";// リクエストヘッダ
	public static final String PROXY_AUTHENTICATE_HEADER = "proxy-authenticate";// レスポンスヘッダ
	public static final String WWW_AUTHORIZATION_HEADER = "authorization";
	public static final String WWW_AUTHENTICATE_HEADER = "www-Authenticate";
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN="access-control-allow-origin";
	
	public static final String CONNECTION_HEADER = "connection";
	public static final String SERVER_HEADER = "server";
	public static final String PROXY_CONNECTION_HEADER = "proxy-connection";
	public static final String KEEP_ALIVE_HEADER = "keep-alive";
	public static final String UPGRADE_HEADER = "upgrade";
	public static final String WEB_SOCKET = "WebSocket";
	public static final String WEB_SOCKET_CONNECTION = "Upgrade";
	// リクエストに指定される、ブラウザが解釈できる圧縮タイプ,例）Accept-Encoding: gzip, deflate
	public static final String ACCEPT_ENCODING_HEADER = "accept-encoding";
	// レスポンスに指定される、コンテンツの圧縮タイプ,例）Content-Encoding: gzip
	public static final String CONTENT_ENCODING_HEADER = "content-encoding";
	public static final String CONTENT_ENCODING_GZIP = "gzip";
	// chunkedか否か,例）Transfer-encoding: chunked
	public static final String TRANSFER_ENCODING_HEADER = "transfer-encoding";
	public static final String HEADER_ENCODE = "ISO8859_1";
	public static final byte[] HEADER_SEP = ": ".getBytes();
	public static final byte[] CRLF = "\r\n".getBytes();
	public static final byte[] SPACE = " ".getBytes();

//	private static SimpleDateFormat headerDateFormat = null;
//	static {
//		headerDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",Locale.US);
//		headerDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
//	}
	private static ThreadLocal<HeaderDataFormat> headerDateFormatThradLocal=new ThreadLocal<HeaderDataFormat>();
	private static class HeaderDataFormat{
		HeaderDataFormat(){
			headerDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z",Locale.US);
			headerDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		}
		private SimpleDateFormat headerDateFormat;
		private long cacheDate;
		private String casheHeader;
		private Map<String,Date> headerCashe=new HashMap<String,Date>();
		public Date parseDateHeader(String header) {
			Date date=headerCashe.get(header);
			if(date!=null){
				return date;
			}
			try {
				date=headerDateFormat.parse(header);
				headerCashe.put(header, date);
				return date;
			} catch (ParseException e) {
				logger.warn("fail to parse date header." + header);
			}
			return null;
		}
		public String fomatDateHeader(Date date) {
			long t=date.getTime()/1000L;
			if(t!=cacheDate){
				cacheDate=t;
				casheHeader=headerDateFormat.format(date);
			}
			return casheHeader;
		}
	}
	
	private static HeaderDataFormat getHeaderDateFormat(){
		HeaderDataFormat headerDateFormat=headerDateFormatThradLocal.get();
		if(headerDateFormat==null){
			headerDateFormat = new HeaderDataFormat();
			headerDateFormatThradLocal.set(headerDateFormat);
		}
		return headerDateFormat;
	}

	public static Date parseDateHeader(String header) {
		if (header == null) {
			return null;
		}
		String fields[] = header.split(";");
		HeaderDataFormat headerDateFormat=getHeaderDateFormat();
		return headerDateFormat.parseDateHeader(fields[0]);
	}
	
	public static String fomatDateHeader(Date date) {
		HeaderDataFormat headerDateFormat=getHeaderDateFormat();
		return headerDateFormat.fomatDateHeader(date);
	}

	public static HeaderParser createByStore(Store store) {
		if (store == null) {
			return null;
		}
		HeaderParser headerParser = (HeaderParser) PoolManager.getInstance(HeaderParser.class);
		ByteBuffer[] buffers = DataUtil.toByteBuffers(store);
		for (ByteBuffer buffer : buffers) {
			headerParser.parse(buffer);
		}
		// 中身はparseで消費されるので配列を回収する
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
	
	public static HeaderParser createByUrl(URL url) {
		HeaderParser headerParser = (HeaderParser) PoolManager.getInstance(HeaderParser.class);
		headerParser.setMethod(GET_METHOD);
		String path=url.getPath();
		if("".equals(path)){
			path="/";
		}
		headerParser.setPath(path);
		headerParser.setQuery(url.getQuery());
		int defaultPort=80;
		if("https".equalsIgnoreCase(url.getProtocol())){
			defaultPort=443;
		}
		headerParser.server=ServerParser.parse(url.getHost(), defaultPort);
		headerParser.setReqHttpVersion(HeaderParser.HTTP_VESION_11);
		return headerParser;
	}
	

	// 当該ヘッダ受信前にCONNECTメソッドで指定されていた情報
	// private String sslPeekHost;
	// private int sslPeekPort;
	private String firstLine;
	private String firstLineToken1;
	private String firstLineToken2;
	private String firstLineToken3;

	// このクラスでparseした場合のヘッダ長を保持する。
	private long headerLength;
	// private List rawHeaders;
	private Map<String, String> headerNames;// ヘッダとしては、大文字小文字の区別がない、対応表を持つ
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
		// resonPhraseの中には、スペースを含むものがある
		firstLineToken3 = st.nextToken("");
		firstLineToken3 = firstLineToken3.trim();

		if (firstLine.startsWith("HTTP")) {
			// レスポンスのステータスライン"HTTP/1.1 200 OK"
			isRequest = false;
		} else {
			// リクエストのリクエストライン"GET / HTTP/1.0"
			isRequest = true;
		}
	}

	public static String stringfier(byte[] header, int pos, int end) {
		try {
			// TODOもう少し最適化できる
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
		int sep = StreamUtil.indexOfByte(header, pos, end, ':');
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
		PoolManager.poolBufferInstance(bodyBuffers);
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

	public void setParseOk(){
		parseSt=ST.OK;
	}
	
	private void parseLestOfHeader() {
		if (lestOfHeader != null) {
			addLine(lestOfHeader, 0, lestOfHeader.length);
			lestOfHeader = null;
		}
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
		int posR = StreamUtil.indexOfByte(headers, begin, end, '\r');
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
		//完全ではないが、可読文字をチェック、早期にparseを失敗させる
//		if(Character.isISOControl(headers[position])){
//			parseSt = ST.ERROR;
//			return position + 1;
//		}
		int posR;
		switch (parseSt) {
		case INIT:
			return parseHeader(headers, position, end);
		case LEST:
			posR = StreamUtil.indexOfByte(headers, position, end, '\r');
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
				parseSt = ST.RNR;// \r\rは終端と解釈
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
		ByteBuffer[] result = (ByteBuffer[]) bodyBuffers.toArray(BuffersUtil.newByteBufferArray(bodyBuffers.size()));
		return PoolManager.duplicateBuffers(result);
	}

	public ByteBuffer[] getBodyBuffer() {
		if (bodyBuffers.size() == 0) {
			return null;
		}
		ByteBuffer[] result = (ByteBuffer[]) bodyBuffers.toArray(BuffersUtil.newByteBufferArray(bodyBuffers.size()));
		bodyBuffers.clear();// 勢いあまって読んじゃっただけだがらゴミ回収のため早めに開放する。
		return result;
	}

	private boolean isRequest;// リクエストヘッダか？,レスポンスヘッダか？
	// 以降リクエストヘッダの場合に有効
	private boolean isProxy;
	private boolean isSslProxy;
	private boolean isWs;// webSocketsリクエストか否か?
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
			setServer(uri, 443);
			return;
		}
		Matcher matcher = null;
		synchronized (httpServerPattern) {
			matcher = httpServerPattern.matcher(uri);
		}
		if (!matcher.matches()) {//
			logger.error("fail to match.uri:" + uri);
			// throw new IllegalArgumentException("fail to match.uri:"+uri);
			parseSt = ST.ERROR;
			return;
		}
		String serverString = matcher.group(1);
		path = matcher.group(2);
		query = matcher.group(3);
		if (path == null) {
			path = "/";
		}
		if (serverString != null) {// httpで始まっていたということは、proxyとして動作している
		// proxyへのリクエスト
			isProxy = true;
			setServer(serverString, 80);
		} else {//TODO WebSocketリクエストをproxyする場合はどうするの？
			String upgradeHeader = getHeader(UPGRADE_HEADER);
			if (WEB_SOCKET.equalsIgnoreCase(upgradeHeader)) {
				isWs = true;
			}
			// Webサーバへのリクエスト,sslproxyの場合もここを通るので、ポート番号の省略値は決められない
			isProxy = false;
			serverString = getHeader(HOST_HEADER);
			setServer(serverString);
		}
	}
	
	public void parseUri(String uri){
		Matcher matcher = null;
		synchronized (httpServerPattern) {
			matcher = httpServerPattern.matcher(uri);
		}
		if (!matcher.matches()) {//
			logger.error("fail to match.uri:" + uri);
			// throw new IllegalArgumentException("fail to match.uri:"+uri);
			parseSt = ST.ERROR;
			return;
		}
		path = matcher.group(2);
		query = matcher.group(3);
		if (path == null) {
			path = "/";
		}
		setRequestUri(uri);
	}
	public void setupContentHeader() {
		contentType = getHeader(CONTENT_TYPE_HEADER);
		contentLength = getLongHeader(CONTENT_LENGTH_HEADER);
	}
	
	public void setServer(String hostHeader){
		setServer(hostHeader,-1);
	}
	
	public void setServer(String hostHeader,int defaultPort){
		if(hostHeader!=null){
			server = ServerParser.parse(hostHeader, defaultPort);
		}else{
			server=null;
		}
	}

	// リクエスト系のメソッド
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
	 * リクエスト系解析結果に対して、webにしろproxyにしろ、ブラウザのアドレスバーに何が入力されたかを返却
	 * 性能を考えて結果はキャッシュするaddressBar !!!!２度目以降の呼び出しでパラメタを変化させた場合は、意図通りの結果とならない!!!
	 * 
	 * @param isSsl
	 *            sslか否かは、リクエストヘッダだけでは分からないから、使用者側が指定する
	 * @param domain
	 *            省略された場合は、HOSTヘッダから取得
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

	// レスポンス系のメソッド
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
		if(statusCode!=null){
			firstLineToken2 = statusCode.trim();
		}else{
			firstLineToken2 ="500";
		}
		if(reasonPhrase!=null){
			firstLineToken3 = reasonPhrase.trim();
		}else{
			firstLineToken3 = "unknown";
		}
	}

	public String getReasonPhrase() {
		return firstLineToken3;
	}

	// リクエストレスポンス共用
	// 複数の同名ヘッダを想定
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

	public void setHeader(String name, String[] values) {
		for(String value:values){
			addHeader(name,value);
		}
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
	 * 全ヘッダを一括して設定する。
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

	// [;][空白かも]key[空白かも]=[空白かも]value[空白かも] 形式の正規表現
	// '"'で括られる可能性もあるらしいがここでは考慮していない
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
	 * Cookie(リクエスト)から指定されてnameに対応するvalueを取得 加えて、そのCookieを削除する
	 */
	public String getAndRemoveCookieHeader(String name) {
		// Cookieヘッダは、１つしかないが念のため複数処理、順番が変わる
		// 復帰する値は、path最長一致が先頭であるため、先頭のvalueを返却する
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
	/**
	 * overloadHeaders,deleteHeadersのキーは、lowercaseで設定すること
	 */
	public ByteBuffer[] getHeaderBuffer(String requestLine,
			Map<String, String> overloadHeaders, Set<String> deleteHeaders) {
		if (requestLine == null	&& (firstLineToken1 == null || firstLineToken2 == null)) {
			logger.warn("getHeaderBuffer return null.firstLineToken1:"
					+ firstLineToken1 + ":firstLineToken2:" + firstLineToken2);
			return null;// statusCodeが確定していない場合
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
				String lowerName=name.toLowerCase();
				if (overloadHeaders != null	&& overloadHeaders.get(lowerName) != null) {
					continue;// overloadされたヘッダは出力しない
				}
				if (deleteHeaders != null && deleteHeaders.contains(lowerName)) {
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
			// ヘッダ終端
			page.putBytes(CRLF);
			buffers = page.getBuffer();
			// logger.debug("-------");
			// for(int i=0;i<buffers.length;i++){
			// logger.debug(new
			// String(buffers[i].array(),0,buffers[i].remaining()));
			// }
		} catch (UnsupportedEncodingException e) {// あり得ない
			logger.error("ENCODE error." + HEADER_ENCODE, e);
			throw new RuntimeException("fail to writeHeader", e);
		} finally {
			page.unref(true);
		}
		return buffers;
	}

	/* ヘッダ情報をosに転送する */
	public void writeHeader(OutputStream os) {
		if (firstLineToken1 == null) {
			return;// statusCodeが確定していない場合
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
			// ヘッダ終端
			os.write(CRLF);
		} catch (UnsupportedEncodingException e) {// あり得ない
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

	/*
	public Iterator<String> getHeaderNames() {
		return headers.keySet().iterator();
	}
	*/
	
	public Set<String> getHeaderNames() {
		return headers.keySet();
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
	
	public void forceWebRequest(){//強制的にwebリクエストにする
		isProxy=false;
		if(query==null){
			setRequestUri(path);
		}else{
			setRequestUri(path +'?'+ query);
		}
	}
}
