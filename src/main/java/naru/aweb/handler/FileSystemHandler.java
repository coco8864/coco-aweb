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
import naru.async.cache.CacheBuffer;
import naru.async.cache.FileInfo;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping; //import naru.aweb.config.FileCache.FileCacheInfo;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

/**
 * �t�@�C�����烌�X�|���X���쐬����A���̂ق��ɂ��ȉ��̏���������
 * 1)�w��t�@�C�����f�B���N�g���������ꍇ�A���̈ꗗ��ԋp
 * 2)/proxy.pac�ւ̃��N�G�X�g�������ꍇ�A(option��proxy_pac_path�w��)pac��ԋp
 * 3)velocityPage�Ώۃp�X�������ꍇ�AvelocityHandler��forward
 * 4)phoffline.html,phoffline.appcache�������ꍇ�Aoffline�R���e���c�̕ԋp
 * @author naru
 *
 */
public class FileSystemHandler extends WebServerHandler implements BufferGetter {
	private static Logger logger = Logger.getLogger(FileSystemHandler.class);
	private static Config config = null;// Config.getConfig();
	private static String LISTING_TEMPLATE = "/template/listing.vsp";
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
	 * MappingResult��Option�ɏ]����VelocityHandler�ɏ�����C���� 1)"velocityUse"��false�ȊO
	 * 2)"velocityExtentions"�Ɗg���q����v���� TODO mapping.setOption(key, value)���g���čœK��
	 */
	private boolean isVelocityUse(MappingResult mapping, String path) {
		if (path == null) {
			return false;
		}
		if (!mapping.getBooleanOption(Mapping.OPTION_VELOCITY_USE,true)) {
			return false;
		}
		if (path.endsWith("ph-loader.js")) {// ���ʈ���
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE,"application/javascript");
			return true;
		}
		Pattern pattern=(Pattern)mapping.getOption(Mapping.OPTION_VELOCITY_PATTERN);
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
		String contentType = (String) getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE);
		if (contentType != null) {
			return contentType;
		}
		String fileName = file.getName();
		return calcContentType(fileName);
	}

	private long getContentLength(long fileLength) {
		Long length = (Long) getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_LENGTH);
		if (length != null) {
			return length.longValue();
		}
		return fileLength;
	}
	
	
	/**
	 * �X������t�@�C�����Ȃ������A�ȉ���T��
	 * 1)proxy.pac
	 * 2)phoffline.html
	 * 3)phoffline.appcache
	 * @param path
	 */
	private void fileNotFound(MappingResult mapping,HeaderParser requestHeader,String path){
		/* proxy.pac�̏ꍇ */
		if(path.equals(mapping.getOption(Mapping.OPTION_PROXY_PAC_PATH))){
			String localHost=requestHeader.getHeader(HeaderParser.HOST_HEADER);
			String pac=getConfig().getProxyPac(localHost);
			setContentType("text/plain");
			completeResponse("200", pac);
			return;
		}
		if(path.equals(mapping.getOption(Mapping.OPTION_APPCACHE_HTML_PATH))){
		}
		boolean useOffline=(Boolean)getAuthSession().getAttribute(Mapping.OPTION_USE_APPCACHE);
		if(useOffline && path.equals(mapping.getOption(Mapping.OPTION_APPCACHE_MANIFEST_PATH))){
		}
		logger.debug("Not found." + path);
		completeResponse("404", "file not exists");
	}

	private void responseBodyFromFile(CacheBuffer asyncFile) {
		Long offset = (Long) getRequestAttribute(ATTRIBUTE_STORE_OFFSET);
		if (offset != null) {
			asyncFile.position(offset);
		}
		this.asyncFile = asyncFile;
		asyncFile.asyncBuffer(this, asyncFile);
	}

	private boolean fileListIfNessesary(MappingResult mapping,String selfPath,File dir,boolean isBase){
		boolean listing = mapping.getBooleanOption(Mapping.OPTION_FILE_LISTING,true);
		if (listing && dir.isDirectory()) {// �f�B���N�g����������
			// velocityPage���烊�X�g�o��
			return snedFileList(mapping,selfPath, dir, isBase);
		}
		logger.debug("Not allow listing");
		completeResponse("404");
		return true;
	}
	
	// ���݊m�F�ς݂̃f�B���N�g�����ꗗ���X�|���X����B
	private boolean snedFileList(MappingResult mapping,String uri, File dir, boolean isBase) {
		if (!uri.endsWith("/")) {
			uri = uri + "/";
		}
		setRequestAttribute("isBase", isBase);
		setRequestAttribute("base", uri);
		try {
			setRequestAttribute("source", dir.getCanonicalFile());
		} catch (IOException e) {
			setRequestAttribute("source", null);
		}
		setRequestAttribute("fileList", dir.listFiles());

		setRequestAttribute(ATTRIBUTE_VELOCITY_ENGINE,getConfig().getVelocityEngine());
		setRequestAttribute(ATTRIBUTE_VELOCITY_TEMPLATE,LISTING_TEMPLATE);
		forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
		return false;// �Ϗ�
	}

	// ���݊m�F�ς݂̃t�@�C�������X�|���X����B
	private boolean sendFile(MappingResult mapping, File baseDirectory,
			String path, String ifModifiedSince, CacheBuffer asyncFile) {
		if (isVelocityUse(mapping, path)) {
			// TODO �����Ƃ���
			mapping.setResolvePath(path);// ���H���path��ݒ�
			mapping.setDesitinationFile(baseDirectory);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			asyncFile.close();
			return false;// �Ϗ�
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
		// �t�@�C�����t�Ƃ��ĕ\���ł���l�ɂ́A�덷�����邽�߁A�\���ł��鎞�����擾
		lastModifiedTime = HeaderParser.parseDateHeader(lastModified).getTime();
		if (ifModifiedSinceTime >= lastModifiedTime) {
			completeResponse("304");
			asyncFile.close();
			return true;
		}
		setHeader(HeaderParser.LAST_MODIFIED_HEADER, lastModified);
		long contentLength = getContentLength(fileInfo.length());
		setContentLength(contentLength);
		String contentDisposition = (String) getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION);
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

	public void startResponseReqBody() {
		MappingResult mapping=getRequestMapping();
		if(mapping.getBooleanOption(Mapping.OPTION_REPLAY)){
			ReplayHelper helper=Config.getConfig().getReplayHelper();
			if(helper.doReplay(this,null)){
				return;//replay�ł���,body�͏����Ă���
			}
		}
		if (response()) {
			responseEnd();// TODO�K�v�Ȃ��Ǝv��
			return;
		}
	}

	private CacheBuffer welcomPage(File dir,String[] welcomlist){
		CacheBuffer asyncFile=null;
		for(String welcom:welcomlist){
			asyncFile=CacheBuffer.open(new File(dir,welcom));
			FileInfo info=asyncFile.getFileInfo();
			if(info.exists()&&info.canRead()&&info.isFile()){
				return asyncFile;
			}
			asyncFile.close();
		}
		return null;
	}
	
	private boolean response() {
		HeaderParser requestHeader = getRequestHeader();
		String ifModifiedSince = requestHeader.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
		String selfPath = requestHeader.getRequestUri();

		MappingResult mapping = getRequestMapping();
		File file = (File) getRequestAttribute(ATTRIBUTE_RESPONSE_FILE);
		if (file != null) {// ���X�|���X����t�@�C�����A���ڎw�肳�ꂽ�ꍇ
		// FileCacheInfo fileCacheInfo=null;
			boolean useCache = true;
			if (getRequestAttribute(ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE) == null) {
				useCache = false;
			}
			CacheBuffer asyncFile = CacheBuffer.open(file, useCache);
			FileInfo fileInfo = asyncFile.getFileInfo();
			if (!fileInfo.exists()) {
				logger.debug("Not found." + file.getAbsolutePath());
				completeResponse("404", "file not exists");
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
		// �N�G���̍폜
		int pos = path.indexOf('?');
		if (pos >= 0) {
			path = path.substring(0, pos);
		}
		/*
		if(isTemplateContents(path)){
			setRequestAttribute(ATTRIBUTE_VELOCITY_ENGINE,getConfig().getVelocityEngine());
			setRequestAttribute(ATTRIBUTE_VELOCITY_TEMPLATE,"/template" +path);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return false;
		}
		*/
		File baseDirectory = mapping.getDestinationFile();
		CacheBuffer asyncFile = CacheBuffer.open(new File(baseDirectory, path));
		FileInfo info = asyncFile.getFileInfo();
		if (info.isError()) {
			logger.warn("fail to getCanonicalPath.");
			completeResponse("500", "fail to getCanonicalPath.");
			asyncFile.close();
			return true;
			//TODO �g���o�[�T��
			// }else if(!info.isInBase()){
			// //�g���o�[�T�����ꂽ��Alogging����404
			// logger.warn("traversal error.");
			// completeResponse("404","traversal error");
			// return true;
		} else if (!info.exists() || !info.canRead()) {
			asyncFile.close();
			fileNotFound(mapping,requestHeader,path);
			return true;
		}
		// welcomefile����
		String[] welcomeFiles = (String[])mapping.getOption(Mapping.OPTION_FILE_WELCOME_FILES);
		if (info.isDirectory() && welcomeFiles != null) {
			File dir=info.getCanonicalFile();
			asyncFile.close();
			asyncFile=welcomPage(dir, welcomeFiles);
			if(asyncFile==null){//welcomfile����������
//				completeResponse("404", "file not exists");
				return fileListIfNessesary(mapping, selfPath, dir,"/".equals(path));
			}
			info=asyncFile.getFileInfo();
			if (info.exists() && info.canRead() && !path.endsWith("/")) {
				asyncFile.close();
				// �����AURI��"/"�ŏI����Ă��Ȃ������瑊�΂������ł��Ȃ��̂ŁA���_�C���N�g
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
		if (info.isFile()) {// �t�@�C����������
			return sendFile(mapping, baseDirectory, path, ifModifiedSince, asyncFile);
		}
		asyncFile.close();
		File dir=info.getCanonicalFile();
		return fileListIfNessesary(mapping, selfPath, dir,"/".equals(path));
	}
	

	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" + getChannelId() + ":" + t.getMessage());
		asyncClose(userContext);
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" + getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}

	/* asyncFile����̃_�E�����[�h */
	private CacheBuffer asyncFile;

	public void onWrittenBody() {
		logger.debug("#writtenBody.cid:" + getChannelId());
		if(asyncFile!=null){
			asyncFile.asyncBuffer(this, asyncFile);
		}
		super.onWrittenBody();
	}

	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		responseBody(buffers);
		return false;
	}

	public void onBufferEnd(Object userContext) {
		responseEnd();
	}

	public void onBufferFailure(Object userContext, Throwable failure) {
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
