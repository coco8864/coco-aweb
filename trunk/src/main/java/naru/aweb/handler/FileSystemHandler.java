package naru.aweb.handler;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import naru.async.BufferGetter;
import naru.async.cache.CacheBuffer;
import naru.async.cache.FileInfo;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping; //import naru.aweb.config.FileCache.FileCacheInfo;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

public class FileSystemHandler extends WebServerHandler implements BufferGetter {
	private static Logger logger = Logger.getLogger(FileSystemHandler.class);
	private static Config config = null;// Config.getConfig();
	private static String LISTING_PAGE = "/fileSystem/listing.vsp";
	private static Configuration contentTypeConfig = null;// config.getConfiguration("ContentType");
	private static Map<String, String> contentTypeMap = new HashMap<String, String>();

	private static Config getConfig() {
		if (config == null) {
			config = Config.getConfig();
		}
		return config;
	}

	private static Configuration getContentTypeConfig() {
		if (contentTypeConfig == null) {
			contentTypeConfig = getConfig().getConfiguration("ContentType");
		}
		return contentTypeConfig;
	}

	private static String calcContentType(String ext) {
		String contentType = contentTypeMap.get(ext);
		if (contentType != null) {
			return contentType;
		}
		contentType = getContentTypeConfig().getString(ext,
				"application/octet-stream");
		synchronized (contentTypeMap) {
			contentTypeMap.put(ext, contentType);
		}
		return contentType;
	}

	// TODO adminSetting����f�t�H���g�l���擾����
	private static boolean defaultListing = true;
	private static String[] defaultWelcomeFiles = new String[] { "index.html",
			"index.htm", "index.vsp" };
	// vsp ... "velocity server page" vsf ... "velocity server flagment"
	private static String[] defaultVelocityExtentions = new String[] { ".vsp",
			"vsf" };

	private String[] getWelcomeFiles(MappingResult mapping) {
		String welcomFiles = (String) mapping
				.getOption(MappingResult.PARAMETER_FILE_WELCOME_FILES);
		if (welcomFiles == null) {
			return defaultWelcomeFiles;
		}
		return welcomFiles.split(",");
	}

	private boolean isListing(MappingResult mapping) {
		String listing = (String) mapping
				.getOption(MappingResult.PARAMETER_FILE_LISTING);
		if (listing == null) {
			return defaultListing;
		}
		return !"false".equalsIgnoreCase(listing);
	}

	/*
	 * MappingResult��Option�ɏ]����VelocityHandler�ɏ�����C���� 1)"velocityUse"��false�ȊO
	 * 2)"velocityExtentions"�Ɗg���q����v���� TODO mapping.setOption(key, value)���g���čœK��
	 */
	private boolean isVelocityUse(MappingResult mapping, String path) {
		if (path == null) {
			return false;
		}
		String velocityUse = (String) mapping
				.getOption(MappingResult.PARAMETER_VELOCITY_USE);
		if ("false".equalsIgnoreCase(velocityUse)) {
			return false;
		}
		if (path.endsWith("ph-loader.js")) {// ���ʈ���
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE,
					"application/javascript");
			return true;
		}
		String velocityExtentionsParam = (String) mapping
				.getOption(MappingResult.PARAMETER_VELOCITY_EXTENTIONS);
		String[] velocityExtentions = defaultVelocityExtentions;
		if (velocityExtentionsParam != null) {
			velocityExtentions = velocityExtentionsParam.split(",");
		}
		for (int i = 0; i < velocityExtentions.length; i++) {
			if (path.endsWith(velocityExtentions[i])) {
				return true;
			}
		}
		return false;
	}

	private String getContentType(File file) {
		String contentType = (String) getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE);
		if (contentType != null) {
			return contentType;
		}
		String name = file.getName();
		int pos = name.lastIndexOf(".");
		if (pos > 0) {
			String ext = name.substring(pos + 1);
			contentType = calcContentType(ext);
			if (contentType != null) {
				return contentType;
			}
		}
		// �^�킵���́AOctedStream
		return "application/octet-stream";
	}

	private long getContentLength(long fileLength) {
		Long length = (Long) getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_LENGTH);
		if (length != null) {
			return length.longValue();
		}
		return fileLength;
	}

	private void responseBodyFromFile(CacheBuffer asyncFile) {
		Long offset = (Long) getRequestAttribute(ATTRIBUTE_STORE_OFFSET);
		if (offset != null) {
			asyncFile.position(offset);
		}
		this.asyncFile = asyncFile;
		asyncFile.asyncBuffer(this, asyncFile);
	}

	// ���݊m�F�ς݂̃f�B���N�g�����ꗗ���X�|���X����B
	private boolean snedFileList(String uri, FileInfo info, boolean isBase) {
		if (!uri.endsWith("/")) {
			uri = uri + "/";
		}
		setRequestAttribute("isBase", isBase);
		setRequestAttribute("base", uri);
		setRequestAttribute("source", info.getCanonicalFile());
		setRequestAttribute("fileList", info.listFiles());
		MappingResult mapping = getRequestMapping();

		mapping.setResolvePath(LISTING_PAGE);
		mapping.setDesitinationFile(getConfig().getAdminDocumentRoot());
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
		String ifModifiedSince = requestHeader
				.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
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
			logger.debug("Not found." + info.getCanonicalFile());
			completeResponse("404", "file not exists");
			return true;
		}
		// welcomefile����
		String[] welcomeFiles = getWelcomeFiles(mapping);
		if (info.isDirectory() && welcomeFiles != null) {
			File dir=info.getCanonicalFile();
			asyncFile.close();
			asyncFile=welcomPage(dir, welcomeFiles);
			if(asyncFile==null){
				completeResponse("404", "file not exists");
				return true;
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
		boolean listing = isListing(mapping);
		if (listing && info.isDirectory()) {// �f�B���N�g����������
			// velocityPage���烊�X�g�o��
			return snedFileList(selfPath, info, "/".equals(path));
		}
		logger.debug("Not allow listing");
		completeResponse("404");
		return true;
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

	/*
	public void onFinished() {
		logger.debug("#finished.cid:" + getChannelId());
		if (asyncFile != null) {
			asyncFile.close();
			asyncFile = null;
		}
		super.onFinished();
	}
	*/

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
