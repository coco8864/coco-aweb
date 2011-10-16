package naru.aweb.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Date;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;

public class FileSystemHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(FileSystemHandler.class);
	private static Config config=Config.getConfig();
	private static String LISTING_PAGE="/fileSystem/listing.vsp";
	private static Configuration contentTypeConfig=config.getConfiguration("ContentType");

	//TODO adminSetting����f�t�H���g�l���擾����
	private static boolean defaultListing=true;
	private static String[] defaultWelcomFiles=new String[]{"index.html","index.htm","index.vsp"};
	//vsp ... "velocity server page"  vsf ... "velocity server flagment"
	private static String[] defaultVelocityExtentions=new String[]{".vsp","vsf"};
	
	private String[] getWelcomFiles(MappingResult mapping){
		String welcomFiles=(String)mapping.getOption(MappingResult.PARAMETER_FILE_WELCOM_FILES);
		if(welcomFiles==null){
			return defaultWelcomFiles;
		}
		return welcomFiles.split(",");
	}
	
	private boolean isListing(MappingResult mapping){
		String listing=(String)mapping.getOption(MappingResult.PARAMETER_FILE_LISTING);
		if(listing==null){
			return defaultListing;
		}
		return !"false".equalsIgnoreCase(listing);
	}
	
	/*
	 * MappingResult��Option�ɏ]����VelocityHandler�ɏ�����C����
	 * 1)"velocityUse"��false�ȊO
	 * 2)"velocityExtentions"�Ɗg���q����v����
	 * TODO mapping.setOption(key, value)���g���čœK��
	 */
	private boolean isVelocityUse(MappingResult mapping,String path){
		if(path==null){
			return false;
		}
		String velocityUse=(String)mapping.getOption(MappingResult.PARAMETER_VELOCITY_USE);
		if("false".equalsIgnoreCase(velocityUse)){
			return false;
		}
		if(path.endsWith("ph-loader.js")){//���ʈ���
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE,"application/javascript");
			return true;
		}
		String velocityExtentionsParam=(String)mapping.getOption(MappingResult.PARAMETER_VELOCITY_EXTENTIONS);
		String[] velocityExtentions=defaultVelocityExtentions; 
		if(velocityExtentionsParam!=null){
			velocityExtentions=velocityExtentionsParam.split(",");
		}
		for(int i=0;i<velocityExtentions.length;i++){
			if(path.endsWith(velocityExtentions[i])){
				return true;
			}
		}
		return false;
	}
	
	private String getContentType(File file){
		String contentType=(String)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_TYPE);
		if(contentType!=null){
			return contentType;
		}
		String name=file.getName();
		int pos=name.lastIndexOf(".");
		if( pos>0 ){
			String ext=name.substring(pos+1);
			contentType=contentTypeConfig.getString(ext);
			if( contentType!=null){
				return contentType;
			}
		}
		//�^�킵���́AOctedStream
		return "application/octet-stream";
	}
	
	private long getContentLength(File file){
		Long length=(Long)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_LENGTH);
		if(length!=null){
			return length.longValue();
		}
		return file.length();
	}
	
	private void responseBodyFromFile(File file) throws IOException{
		Long offset=(Long)getRequestAttribute(ATTRIBUTE_STORE_OFFSET);
		FileChannel readChannel=null;
		FileInputStream fis=new FileInputStream(file);
		readChannel=fis.getChannel();
		if(offset!=null){
			readChannel.position(offset.longValue());
		}
		responseBodyFromChannel(readChannel);
	}
	
	//���݊m�F�ς݂̃f�B���N�g�����ꗗ���X�|���X����B
	private boolean snedFileList(String uri,File file,boolean isBase){
		if(!uri.endsWith("/")){
			uri=uri+"/";
		}
		setRequestAttribute("isBase",isBase);
		setRequestAttribute("base",uri);
		setRequestAttribute("source", file.getAbsoluteFile());
		setRequestAttribute("fileList", file.listFiles());
		MappingResult mapping=getRequestMapping();
		
		mapping.setResolvePath(LISTING_PAGE);
		mapping.setDesitinationFile(config.getAdminDocumentRoot());
		forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
		return false;//�Ϗ�
	}
	
	//���݊m�F�ς݂̃t�@�C�������X�|���X����B
	private boolean sendFile(MappingResult mapping,File baseDirectory,String path,String ifModifiedSince,File file){
		if(isVelocityUse(mapping,path)){
			//TODO �����Ƃ���
			mapping.setResolvePath(path);//���H���path��ݒ�
			mapping.setDesitinationFile(baseDirectory);
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return false;//�Ϗ�
		}
		
//		String ifModifiedSince=requestParser.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
		Date ifModifiedSinceDate=HeaderParser.parseDateHeader(ifModifiedSince);
		long ifModifiedSinceTime=-1;
		if(ifModifiedSinceDate!=null){
			ifModifiedSinceTime=ifModifiedSinceDate.getTime();
		}
		long lastModifiedTime=file.lastModified();
		String lastModified=HeaderParser.fomatDateHeader(new Date(lastModifiedTime));
		//�t�@�C�����t�Ƃ��ĕ\���ł���l�ɂ́A�덷�����邽�߁A�\���ł��鎞�����擾
		lastModifiedTime=HeaderParser.parseDateHeader(lastModified).getTime();
		if( ifModifiedSinceTime>=lastModifiedTime ){
			completeResponse("304");
			return true;
		}
		setHeader(HeaderParser.LAST_MODIFIED_HEADER, lastModified);
		long contentLength=getContentLength(file);
		setContentLength(contentLength);
		String contentDisposition=(String)getRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION);
		if( contentDisposition!=null){
			setHeader(HeaderParser.CONTENT_DISPOSITION_HEADER, contentDisposition);
		}
		String contentType=getContentType(file);
		setContentType(contentType);
		setStatusCode("200");
		try {
			responseBodyFromFile(file);
			return false;
		} catch (IOException e) {
			logger.error("responseBodyFromFile error."+file,e);
			setStatusCode("500");//�L�����ǂ����͕s������
		}
		return true;
	}
	
	public void startResponseReqBody(){
		if(response()){
			responseEnd();//TODO�K�v�Ȃ��Ǝv��
			return;
		}
	}
	
	private boolean response(){
		HeaderParser requestHeader=getRequestHeader();
		String ifModifiedSince=requestHeader.getHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
		String selfPath=requestHeader.getRequestUri();
		
		MappingResult mapping=getRequestMapping();
		File file=(File)getRequestAttribute(ATTRIBUTE_RESPONSE_FILE);
		if(file!=null){//���X�|���X����t�@�C�����A���ڎw�肳�ꂽ�ꍇ
			if(!file.exists()){
				logger.debug("Not found."+file.getAbsolutePath());
				completeResponse("404","file not exists");
				return true;
			}
			return sendFile(mapping,null,null,ifModifiedSince,file);
		}
		String path=mapping.getResolvePath();
		try {
			path = URLDecoder.decode(path,"utf-8");
		} catch (UnsupportedEncodingException e) {
			logger.error("URLDecoder.decode error",e);
			throw new IllegalArgumentException("URLDecoder.decode error",e);
		}
		//�N�G���̍폜
		int pos=path.indexOf('?');
		if(pos>=0){
			path=path.substring(0,pos);
		}
		
		//�g���o�[�T�����ꂽ��Alogging����404
		File baseDirectory=mapping.getDestinationFile();
		file = new File(baseDirectory,path);
		String fileCanonPath=null;
		String distCanonPath=null;
		try {
			fileCanonPath = file.getCanonicalPath();
			distCanonPath = baseDirectory.getCanonicalPath();
		} catch (IOException e) {
			logger.warn("fail to getCanonicalPath.",e);
			completeResponse("500","fail to getCanonicalPath.");
			return true;
		}
		if( !fileCanonPath.startsWith(distCanonPath) ){
			logger.warn("traversal error. file:" + fileCanonPath + " dist:" + distCanonPath);
			completeResponse("404","traversal error:"+ fileCanonPath);
			return true;
		}
		
		if( !file.exists() || !file.canRead()){//���݂��Ȃ�������A�ǂݍ��߂Ȃ�������
			logger.debug("Not found."+file.getAbsolutePath());
			completeResponse("404","file not exists");
			return true;
		}
		
		//wellcomfile����
		String[] welcomFiles=getWelcomFiles(mapping);
		if( file.isDirectory() && welcomFiles!=null){
			for(int i=0;i<welcomFiles.length;i++){
				File wellcomFile=new File(file,welcomFiles[i]);
				if( wellcomFile.exists() && wellcomFile.canRead()){
					file=wellcomFile;
					//�����AURI��"/"�ŏI����Ă��Ȃ������瑊�΂������ł��Ȃ��̂ŁA���_�C���N�g
					if(!path.endsWith("/")){
						ServerParser selfServer=requestHeader.getServer();
						StringBuilder sb=new StringBuilder();
						if(isSsl()){
							sb.append("https://");
						}else{
							sb.append("http://");
						}
						sb.append(selfServer.toString());
						sb.append(selfPath);
						sb.append("/");
						setHeader(HeaderParser.LOCATION_HEADER, sb.toString());
						completeResponse("302");
						return true;
					}
					path=path+welcomFiles[i];
					break;
				}
			}
		}
		if(file.isFile()){//�t�@�C����������
			return sendFile(mapping,baseDirectory,path,ifModifiedSince,file);
		}
		boolean listing=isListing(mapping);
		if(listing && file.isDirectory()){//�f�B���N�g����������
			//velocityPage���烊�X�g�o��
			return snedFileList(selfPath,file,"/".equals(path));
		}
		logger.debug("Not allow listing");
		completeResponse("404");
		return true;
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		asyncClose(userContext);
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:" +getChannelId());
		closeBodyChannel();
		super.onFinished();
	}
	
	//file�_�E�����[�h�p���\�b�h
	private ReadableByteChannel bodyChannel;
	private void responseBodyFromChannel(FileChannel readChannel) throws IOException {
		/*
		 * �Ꝅ�ɓǂݍ��ނ��߂̃R�[�h�A������������Ȃ����Aswap����t�ɂȂ邩�烁���������ɓ��
		while(true){
			ByteBuffer buffer=allocBuffer();
			if( channel.read(buffer)<=0){
				return;
			}
			buffer.flip();
			responseBody(buffer);
		}
		*/
		bodyChannel=readChannel;
		responseBodyChannel();
	}
	
	private void responseBodyChannel() throws IOException{
		ByteBuffer buffer=PoolManager.getBufferInstance();
		if( bodyChannel.read(buffer)<=0){
			PoolManager.poolBufferInstance(buffer);
			closeBodyChannel();
			responseEnd();//��close���s
			return;
		}
		buffer.flip();
		logger.debug("responseBodyChannel cid:"+getChannelId() +":length:"+buffer.remaining());
		responseBody(buffer);
	}
	
	private void closeBodyChannel(){
		if(bodyChannel!=null){
			try {
				bodyChannel.close();
			} catch (IOException e) {
				logger.error("fail to close bodyChannel.",e);
			}
			bodyChannel=null;
		}
	}

	public void onWrittenBody() {
		logger.debug("#writtenBody.cid:" +getChannelId());
		if(bodyChannel!=null){
			try {
				responseBodyChannel();
			} catch (IOException e) {
				logger.error("fail to responseBodyChannel",e);
				closeBodyChannel();
			}
		}
		super.onWrittenBody();
	}
}
