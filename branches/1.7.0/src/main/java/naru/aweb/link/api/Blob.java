package naru.aweb.link.api;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Date;

import org.apache.log4j.Logger;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.cache.Cache;
import naru.async.cache.FileInfo;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.util.HeaderParser;
import net.sf.json.JSONObject;

/**
 * javascriopt File Blob�I�u�W�F�N�g���V�~�����[�g
 * http://www.w3.org/TR/FileAPI/
 * binaryMessage�̃C���^�t�F�[�X�ɗ��p
 * ���M���ɂ́A�f�[�^�̎������𔻒f����
 * 1)connect���Ă��Ȃ�subname����buffer�Ŏ��ƃ��������p���N����
 * 2)subId����File��read����͔̂����
 * 
 * �f�[�^�̎������͈ȉ���3��
 * 1)buffer
 * 2)file
 * 3)blobFile offset
 * 
 * �ŏ��̎�����2)�����e����
 * @author Naru
 */
public class Blob extends PoolBase implements AsyncBuffer,BufferGetter{
	private static Logger logger = Logger.getLogger(Blob.class);
	private Cache buffer;
	private long offset;
	private long size;
	private String name;
	private Date lastModifiedDate;
	private String type;
	private String jsType;
	private File deleteFile;
	
	public static Blob create(File file,boolean deleteOnFinish){
		Cache buffer=Cache.open(file);
		Blob blob=create(buffer);
		buffer.unref();//Blob�̉���ƂƂ��ɉ������悤�ɂ���
		if(deleteOnFinish){
			blob.deleteFile=file;
		}
		return blob;
	}
	
	public static Blob create(ByteBuffer[] byteBuffer){
		Cache buffer=Cache.open(byteBuffer);
		try{
			return create(buffer);
		}finally{
			buffer.unref();//Blob�̉���ƂƂ��ɉ������悤�ɂ���
		}
	}
	
	public static Blob create(Cache buffer){
		return create(buffer,0,buffer.bufferLength());
	}
	
	/* buffer�́ABlob�I�����ɊJ������� */
	public static Blob create(Cache buffer,long offset,long size){
		Blob blob=(Blob)PoolManager.getInstance(Blob.class);
		buffer.ref();
		blob.buffer=buffer;
		blob.offset=offset;
		blob.size=size;
//		blob.meta=new JSONObject();
		FileInfo fileInfo=buffer.getFileInfo();
		if(fileInfo!=null){
			blob.name=fileInfo.getFile().getName();
//			blob.lastModifiedDate=fileInfo.getLastModified();
		}
		//TODO meta�̒l��offset,size,name,lastModifiedData�ɔ��f?
		//meta����size�͐M�p���Ȃ�..�p�����^�̒l�ŏ㏑��
		blob.size=size;
		return blob;
	}
	
	public long size(){
		return size;
	}
	
	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}
	public long getLastModifiedDate() {
		if(lastModifiedDate==null){
			return -1;
		}
		return lastModifiedDate.getTime();
	}

	public String getJsType() {
		return jsType;
	}

	public void setType(String type) {
		this.type=type;
	}

	public void setName(String name) {
		this.name=name;
	}

	public void setLastModifiedDate(long lastModifiedDate) {
		this.lastModifiedDate=new Date(lastModifiedDate);
	}
	
	public void setJsType(String jsType) {
		this.jsType = jsType;
	}
	
	public JSONObject meta(){
		JSONObject meta=new JSONObject();
		meta.element("size", size);
		meta.element("type", type);
		meta.element("name", name);
		meta.element("jsType", jsType);
		meta.element("lastModifiedDate", getLastModifiedDate());
		return meta;
	}
	
	@Override
	public void recycle() {
		if(buffer!=null){
			buffer.close();
			//buffer.unref();
			buffer=null;
		}
		size=offset=0;
		if(deleteFile!=null){
			try {
				deleteFile.delete();
			} catch (Throwable e) {
				logger.warn("Blob fail to file delete.");
			}
			deleteFile=null;
		}
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		throw new UnsupportedOperationException("asyncBuffer(BufferGetter bufferGetter, Object userContext)");
	}

	/* ���̌Ăяo���łP���callback */
	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext) {
		long maxLength=size-offset;
		if(maxLength==0){//�S���ǂ�
			bufferGetter.onBufferEnd(userContext);
			return false;
		}
		Object[] ctx={bufferGetter,userContext,maxLength};
		return buffer.asyncBuffer(this, this.offset+offset,ctx);
	}

	public long bufferLength() {
		return size;
	}

	public boolean onBuffer(ByteBuffer[] buffers, Object userContext) {
		long len=BuffersUtil.remaining(buffers);
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		Long maxLength=(Long)ctx[2];
		if(len>maxLength){
			BuffersUtil.cut(buffers, maxLength);
		}
		bufferGetter.onBuffer(buffers, orgUserContext);
		return false;
	}

	public void onBufferEnd(Object userContext) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		bufferGetter.onBufferEnd(orgUserContext);
	}

	public void onBufferFailure(Throwable failure, Object userContext) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		bufferGetter.onBufferFailure(failure,orgUserContext);
	}
	
	private static class DownloadGetter implements BufferGetter{
		private Blob blob;
		private long offset=0;
		
		DownloadGetter(Blob blob){
			this.blob=blob;
			this.offset=0;
		}
		
		@Override
		public boolean onBuffer(ByteBuffer[] buffers, Object h) {
			WebServerHandler handler=(WebServerHandler)h;
			offset+=BuffersUtil.remaining(buffers);
			handler.responseBody(buffers);
			blob.asyncBuffer(this,offset,handler);
			return true;
		}

		@Override
		public void onBufferEnd(Object h) {
			WebServerHandler handler=(WebServerHandler)h;
			handler.responseEnd();
			blob.unref();
		}

		@Override
		public void onBufferFailure(Throwable arg1, Object h) {
			WebServerHandler handler=(WebServerHandler)h;
			handler.responseEnd();
			blob.unref();
		}
	}
	
	/* download����������Γ��YBlob�͉������� */
	public void download(WebServerHandler handler){
		if(name!=null){
			handler.setHeader(HeaderParser.CONTENT_DISPOSITION_HEADER, "attachment; filename=\"" + getName()+"\"");
		}
		if(type!=null){
			handler.setHeader(HeaderParser.CONTENT_TYPE_HEADER, getType());
		}else{
			handler.setHeader(HeaderParser.CONTENT_TYPE_HEADER, "application/octet-stream");
		}
		handler.setNoCacheResponseHeaders();
		handler.setStatusCode("200");
		DownloadGetter downloadGetter=new DownloadGetter(this);
		asyncBuffer(downloadGetter,0,handler);
	}
	
}