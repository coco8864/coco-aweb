package naru.aweb.handler;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.BufferGetter;
import naru.async.cache.Cache;
import naru.async.cache.FileInfo;
import naru.aweb.config.AppcacheOption;
import naru.aweb.config.Config;
import naru.aweb.mapping.Mapping;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

/**
 * ファイルからレスポンスを作成する、そのほかにも以下の処理をする
 * 1)指定ファイルがディレクトリだった場合、その一覧を返却
 * 2)/proxy.pacへのリクエストだった場合、(optionでproxy_pac_path指定)pacを返却
 * 3)velocityPage対象パスだった場合、velocityHandlerにforward
 * 4)phoffline.html,phoffline.appcacheだった場合、offlineコンテンツの返却
 * @author naru
 *
 */
public class FileSystemHandler extends WebServerHandler implements BufferGetter {
	private static Logger logger = Logger.getLogger(FileSystemHandler.class);
	private static Config config = null;// Config.getConfig();
	private static String LISTING_TEMPLATE = "listing.vsp";
	private static Map<String, String> contentTypeMap = new HashMap<String, String>();

	private static Config getConfig() {
		if (config == null) {
			config = Config.getConfig();
		}
		return config;
	}

	private static String calcContentType(String fileName) {
		String contentType = contentTypeMap.get(fileName);
		if (contentType != null) {
			return contentType;
		}
		contentType = getConfig().getContentType(fileName);
		if(contentType==null){
			contentType="application/octet-stream";
		}
		synchronized (contentTypeMap) {
			contentTypeMap.put(fileName, contentType);
		}
		return contentType;
	}

	/*
	 * MappingResultのOptionに従ってVelocityHandlerに処理を任せる 1)"velocityUse"がfalse以外
	 * 2)"velocityExtentions"と拡張子が一致する TODO mapping.setOption(key, value)を使って最適化
	 */
	private boolean isVelocityUse(MappingResult mapping, String path) {
		if (path == null) {
			return false;
		}
		if (!mapping.getBooleanOption(Mapping.OPTION_VELOCITY_USE,true)) {
			return false;
		}
		Pattern pattern=(Pattern)getAttribute(SCOPE.MAPPING,Mapping.OPTION_VELOCITY_PATTERN);
		if(pattern==null){
			return false;
		}
		Matcher matcher=null;
		synchronized(pattern){
			matcher=pattern.matcher(path);
		}
		/*
		 * .*\.vsv$|.*\.vsv$|/ph\.js|/ph\.json
		 */
		return matcher.matches();
	}

	private String getContentType(File file) {
		String contentType = (String) getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_CONTENT_TYPE);
		if (contentType != null) {
			return contentType;
		}
		String fileName = file.getName();
		return calcContentType(fileName);
	}

	private long getContentLength(long fileLength) {
		Long length = (Long) getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_CONTENT_LENGTH);
		if (length != null) {
			return length.longValue();
		}
		return fileLength;
	}
	
	private static final byte[] FILE_NOT_EXIST="file not exists".getBytes();
	/**
	 * 該当するファイルがなかった、以下を探す
	 * 1)phoffline.html
	 * 2)phoffline.appcache
	 * @param path
	 */
	private boolean fileNotFound(MappingResult mapping,HeaderParser requestHeader,String path){
		/* appcacheの場合 */
		AppcacheOption appcacheOption=(AppcacheOption)getAttribute(SCOPE.MAPPING,Mapping.OPTION_APPCACHE);
		if(appcacheOption!=null){
			if(appcacheOption.checkAndForward(this,path,mapping.getSourcePath())){
				return false;
			}
		}
		if(logger.isDebugEnabled())logger.debug("Not found." + path);
		completeResponse("404", FILE_NOT_EXIST);
		return true;
	}

	private void responseBodyFromFile(Cache asyncFile) {
		if(logger.isDebugEnabled())logger.debug("FileSystemHandler#responseBodyFromFile cid:"+getChannelId()+":"+asyncFile.getFileInfo().getCanonicalFile().toString());
		Long offset = (Long) getAttribute(SCOPE.REQUEST,ATTRIBUTE_STORE_OFFSET);
		if (offset != null) {
			asyncFile.position(offset);
		}
		this.asyncFile = asyncFile;
		asyncFile.asyncBuffer(this, asyncFile);
	}

	private boolean fileListIfNessesary(MappingResult mapping,String selfPath,File dir,boolean isBase){
		boolean listing = mapping.getBooleanOption(Mapping.OPTION_FILE_LISTING,true);
		if (listing && dir.isDirectory()) {// ディレクトリだったら
			// velocityPageからリスト出力
			return snedFileList(mapping,selfPath, dir, isBase);
		}
		if(logger.isDebugEnabled())logger.debug("Not allow listing");
		completeResponse("404");
		return true;
	}
	
	// 存在確認済みのディレクトリを一覧レスポンスする。
	private boolean snedFileList(MappingResult mapping,String uri, File dir, boolean isBase) {
		if (!uri.endsWith("/")) {
			uri = uri + "/";
		}
		setAttribute(SCOPE.REQUEST,"isBase", isBase);
		setAttribute(SCOPE.REQUEST,"base", uri);
		try {
			setAttribute(SCOPE.REQUEST,"source", dir.getCanonicalFile());
		} catch (IOException e) {
			setAttribute(SCOPE.REQUEST,"source", null);
		}
		setAttribute(SCOPE.REQUEST,"fileList", dir.listFiles());
		getConfig().forwardVelocityTemplate(this, LISTING_TEMPLATE);
		return false;// 委譲
	}

	// 存在確認済みのファイルをレスポンスする。
	private boolean sendFile(MappingResult mapping, File baseDirectory,
			String path, String ifModifiedSince, Cache asyncFile) {
		if (isVelocityUse(mapping, path)) {
			// TODO ちゃんとする
			mapping.setResolvePath(path);// 加工後のpathを設定
			mapping.setDesitinationFile(baseDirectory);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			asyncFile.close();
			return false;// 委譲
		}

		// String
		// ifModifiedSince=requestParser.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
		Date ifModifiedSinceDate = HeaderParser
				.parseDateHeader(ifModifiedSince);
		long ifModifiedSinceTime = -1;
		if (ifModifiedSinceDate != null) {
			ifModifiedSinceTime = ifModifiedSinceDate.getTime();
		}
		FileInfo fileInfo = asyncFile.getFileInfo();
		long lastModifiedTime = fileInfo.getLastModified();
		String lastModified = HeaderParser.fomatDateHeader(new Date(
				lastModifiedTime));
		// ファイル日付として表現できる値には、誤差があるため、表現できる時刻を取得
		lastModifiedTime = HeaderParser.parseDateHeader(lastModified).getTime();
		if (ifModifiedSinceTime >= lastModifiedTime) {
			completeResponse("304");
			asyncFile.close();
			return true;
		}
		setHeader(HeaderParser.LAST_MODIFIED_HEADER, lastModified);
		long contentLength = getContentLength(fileInfo.length());
		setContentLength(contentLength);
		String contentDisposition = (String) getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION);
		if (contentDisposition != null) {
			setHeader(HeaderParser.CONTENT_DISPOSITION_HEADER,
					contentDisposition);
		}
		String contentType = getContentType(fileInfo.getCanonicalFile());
		setContentType(contentType);
		setStatusCode("200");
		responseBodyFromFile(asyncFile);
		return false;
	}

	public void onRequestBody() {
		MappingResult mapping=getRequestMapping();
		if(mapping.getBooleanOption(Mapping.OPTION_REPLAY)){
			ReplayHelper helper=Config.getConfig().getReplayHelper();
			if(helper.doReplay(this,null)){
				return;//replayできた,bodyは消費されている
			}
		}
		response();//復帰値は処理に関係しない
	}

	private Cache welcomPage(File dir,String[] welcomlist){
		Cache asyncFile=null;
		for(String welcom:welcomlist){
			asyncFile=Cache.open(new File(dir,welcom));
			FileInfo info=asyncFile.getFileInfo();
			if(info.exists()&&info.canRead()&&info.isFile()){
				return asyncFile;
			}
			asyncFile.close();
		}
		return null;
	}
	
	private boolean response() {
		if(logger.isDebugEnabled())logger.debug("FileSystemHandler#response cid:"+getChannelId());
		HeaderParser requestHeader = getRequestHeader();
		String ifModifiedSince = requestHeader.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
		String selfPath = requestHeader.getRequestUri();

		MappingResult mapping = getRequestMapping();
		File file = (File) getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_FILE);
		if (file != null) {// レスポンスするファイルが、直接指定された場合
		// FileCacheInfo fileCacheInfo=null;
			boolean useCache = true;
			if (getAttribute(SCOPE.REQUEST,ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE) == null) {
				useCache = false;
			}
			Cache asyncFile = Cache.open(file, useCache);
			FileInfo fileInfo = asyncFile.getFileInfo();
			if (!fileInfo.exists()) {
				if(logger.isDebugEnabled())logger.debug("Not found." + file.getAbsolutePath());
				completeResponse("404", FILE_NOT_EXIST);
				asyncFile.close();
				return true;
			}
			return sendFile(mapping, null, null, ifModifiedSince, asyncFile);
		}

		String path = mapping.getResolvePath();
		try {
			path = URLDecoder.decode(path, "utf-8");
		} catch (UnsupportedEncodingException e) {
			logger.error("URLDecoder.decode error", e);
			throw new IllegalArgumentException("URLDecoder.decode error", e);
		}
		// クエリの削除
		int pos = path.indexOf('?');
		if (pos >= 0) {
			path = path.substring(0, pos);
		}
		File baseDirectory = mapping.getDestinationFile();
		Cache asyncFile = Cache.open(new File(baseDirectory, path));
		FileInfo info = asyncFile.getFileInfo();
		if (info.isError()) {
			logger.warn("fail to getCanonicalPath.");
			completeResponse("500", "fail to getCanonicalPath.");
			asyncFile.close();
			return true;
			//TODO トラバーサル
			// }else if(!info.isInBase()){
			// //トラバーサルされたら、loggingして404
			// logger.warn("traversal error.");
			// completeResponse("404","traversal error");
			// return true;
		} else if (!info.exists() || !info.canRead()) {
			asyncFile.close();
			return fileNotFound(mapping,requestHeader,path);
		}
		// welcomefile処理
		String[] welcomeFiles = (String[])getAttribute(SCOPE.MAPPING,Mapping.OPTION_FILE_WELCOME_FILES);
		if (info.isDirectory() && welcomeFiles != null) {
			File dir=info.getCanonicalFile();
			asyncFile.close();
			asyncFile=welcomPage(dir, welcomeFiles);
			if(asyncFile==null){//welcomfileが無かった
//				completeResponse("404", "file not exists");
				return fileListIfNessesary(mapping, selfPath, dir,"/".equals(path));
			}
			info=asyncFile.getFileInfo();
			if (info.exists() && info.canRead() && !selfPath.endsWith("/")) {
				asyncFile.close();
				// もし、URIが"/"で終わっていなかったら相対が解決できないので、リダイレクト
				ServerParser selfServer = requestHeader.getServer();
				StringBuilder sb = new StringBuilder();
				if (isSsl()) {
					sb.append("https://");
				} else {
					sb.append("http://");
				}
				sb.append(selfServer.toString());
				sb.append(selfPath);
				sb.append("/");
				setHeader(HeaderParser.LOCATION_HEADER, sb.toString());
				completeResponse("302");
				return true;
			}
		}
		if (info.isFile()) {// ファイルだったら
			return sendFile(mapping, baseDirectory, path, ifModifiedSince, asyncFile);
		}
		asyncFile.close();
		File dir=info.getCanonicalFile();
		return fileListIfNessesary(mapping, selfPath, dir,"/".equals(path));
	}
	

	public void onFailure(Object userContext, Throwable t) {
		if(logger.isDebugEnabled())logger.debug("#failer.cid:" + getChannelId() + ":" + t.getMessage());
		asyncClose(userContext);
		super.onFailure(t, userContext);
	}

	public void onTimeout(Object userContext) {
		if(logger.isDebugEnabled())logger.debug("#timeout.cid:" + getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}

	/* asyncFileからのダウンロード */
	private Cache asyncFile;

	public void onWrittenBody() {
		if(logger.isDebugEnabled())logger.debug("#writtenBody.cid:" + getChannelId());
		if(asyncFile!=null){
			asyncFile.asyncBuffer(this, asyncFile);
		}
	}

	public boolean onBuffer(ByteBuffer[] buffers, Object userContext) {
		responseBody(buffers);
		return false;
	}

	public void onBufferEnd(Object userContext) {
		if(logger.isDebugEnabled())logger.debug("#onBufferEnd.cid:" + getChannelId());
		responseEnd();
	}

	public void onBufferFailure(Throwable failure, Object userContext) {
		logger.error("onGotFailure error.", failure);
		responseEnd();
	}

	@Override
	public void recycle() {
		if(asyncFile!=null){
			asyncFile.close();
			asyncFile = null;
		}
		super.recycle();
	}
}
