package naru.aweb.admin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.ChunkContext;
import naru.aweb.http.GzipContext;
import naru.aweb.util.CodeConverter;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

/**
 * 1)�w�肳�ꂽ�w�b�_��t�����ă��X�|���X
 * 2)chunk����Ă��鎖��O��Ƀf�[�R�[�h���ă��X�|���X
 * 3)zip����Ă��鎖��O��Ƀf�R�[�h���ă��X�|���X
 * 4)offset,length�w��
 * @author Naru
 *
 */
public class StoreHandler extends WebServerHandler implements BufferGetter {
	private static Logger logger = Logger.getLogger(StoreHandler.class);

	/* tracelog�̃_�E�����[�h���� */
	private long skipLength = 0;
	private long leftLength = 0;
	//store��ݒ肵���ꍇ�́A������ref����Bend���ʒm���ꎞ��unref����
	private Store store = null;
	
	/*
	 * store���擾�����ꍇ�́A������ref,and asyncBuffer���Ă�
	 * ������unref����̂́A�ȉ��̃^�C�~���O�A������store��null��ݒ�
	 * 1)onBuffer ��false(��buffer�̗v���Ȃ�)�ŕ��A���钼�O
	 * 2)onBufferEnd,onBufferFailure���ʒm���ꂽ�ꍇ
	 * 
	 * onBuffer��buffer���ʒm���ꂽ�ꍇ
	 * 1)�ʐM���Ȃ�buffer(or ���̉��H��)�����X�|���X�ɕԋp
	 * 2)�p���̗L���ɂ�蕜�A�l�𐧌�
	 * 3)�p�����Ȃ��ꍇ�AendOfRequest���Ăяo��=>�ʐM���t���O��������
	 * 4)�ʐM������Ȃ���΁Afalse��ԋp(��L�ɑ���)
	 * 
	 * onBufferEnd,onBufferFailure���ʒm���ꂽ�ꍇ
	 * 1)�ʐM���Ȃ�AendOfRequest���Ăяo��=>�ʐM���t���O��������
	 * 2)�ʐM������Ȃ���΁A�������Ȃ�
	 * 
	 * ����̐ؒf���ʒm���ꂽ�ꍇ
	 * 1)�ʐM���t���O��������
	 * 
	 */

	private boolean isCodeConvert=false;
	private boolean isGzip=false;
	private boolean isChunk=false;
	private CodeConverter codeConverter=new CodeConverter();
	private ChunkContext chunkContext=new ChunkContext();
	private GzipContext gzipContext=new GzipContext();
	
	/**
	 * 
	 * @param store
	 * @param headers
	 * @param encode store�Ɋi�[����Ă���f�[�^�̃R�[�h�n
	 * @param offset store�̐擪offset���X�L�b�v
	 * @param length response��(�����̏ꍇ�Astore�̃T�C�Y�ɏ]��)
	 * @param isChunk store�Ɋi�[����Ă���f�[�^��chunk����Ă���Ƃ��ăf�R�[�h���ă��X�|���X//TODO ���false 
	 * @param isGzip store�Ɋi�[����Ă���f�[�^��zip���k����Ă���Ƃ��ăf�R�[�h���ă��X�|���X//TODO ���false
	 */
	private void responsStore(Store store,Map<String,String> headers,String encode,long offset,long length,boolean isChunk,boolean isGzip) {
		if(length<0 && !isChunk && !isGzip){
			length=store.getPutLength()-offset;
		}
//		if(headers==null || headers.get(HeaderParser.CONTENT_TYPE_HEADER)==null){
//			setHeader(HeaderParser.CONTENT_TYPE_HEADER,"application/octet-stream");
//		}
		if(headers!=null){
			Iterator<String> itr = headers.keySet().iterator();
			while (itr.hasNext()) {
				String name =  itr.next();
				String value = headers.get(name);
				setHeader(name, value);
			}
			
		}
		this.isChunk=isChunk;
		if(isChunk){
			chunkContext.decodeInit(true, -1);
		}
		this.isGzip=isGzip;
		if(isGzip){
			gzipContext.recycle();			
		}
		isCodeConvert=false;
		if(encode!=null&&!"utf-8".equalsIgnoreCase(encode)){
			try {
				codeConverter.init(encode, "utf-8");
//				removeHeader(HeaderParser.CONTENT_LENGTH_HEADER);
				isCodeConvert=true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else if(length>=0){
			setContentLength(length);
		}
		if(getStatusCode()==null){
			setStatusCode("200");
		}
		
		skipLength = offset;
		leftLength = length;
		this.store=store;
		ref();//store�̖ʓ|���݂邽��
		store.asyncBuffer(this, store);
	}
	
	/**
	 * forward����O��contentType��ݒ肷�邱��
	 * �ݒ肳��Ă��Ȃ��ꍇ�́Atext�Ɣ��f�A�R���e���c���e����charset�𐄒肵�Ĉȉ���ݒ肷��
	 * text/plain; charset='???'
	 * @param store
	 */
	private void responseStoreByForward(Store store){
		Map<String,String> headers=(Map<String,String>)getAttribute("headers");
		long offset=0;
		long length=-1;
		Long offsetValue=(Long)getAttribute("offset");
		if(offsetValue!=null){
			offset=offsetValue.longValue();
		}
		Long lengthValue=(Long)getAttribute("length");
		if(lengthValue!=null){
			length=lengthValue.longValue();
		}
		//SPDY�̏ꍇ�Achank�w�b�_������ƃG���[�ɂȂ�B
		boolean isStoreChunk=false;
		String transferEncoding=getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		if(HeaderParser.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncoding)){
			isStoreChunk=true;
			removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		}
		responsStore(store,headers,null,offset,length,isStoreChunk,false);
	}
	
	//TODO
	private void responseStoreByPost(JSONObject jsonObj){
		completeResponse("404");
	}
	
	private void responseStoreByGet(ParameterParser parameter){
		String storeIdParam=parameter.getParameter("storeId");
		String storeDigest=parameter.getParameter("storeDigest");
		Store store=null;
		if(storeIdParam!=null){
			long storeId=Long.parseLong(storeIdParam);
			store=Store.open(storeId);
		}else if(storeDigest!=null){
			store=Store.open(storeDigest);
		}
		if(store==null){
			completeResponse("404");
			return;
		}
		Map<String,String>headers=new HashMap<String,String>();
		Iterator itr=parameter.getParameterNames();
		long offset=0;
		long length=-1;
		String encode=null;
		boolean isZgip=false;
		boolean isChunk=false;
		while(itr.hasNext()){
			String name=(String)itr.next();
			if("storeId".equals(name)){
				continue;
			}
			if("storeDigest".equals(name)){
				continue;
			}
			String value=parameter.getParameter(name);
			if("storeOffset".equals(name)){
				offset=Long.parseLong(value);
				continue;
			}
			if("storeLength".equals(name)){
				length=Long.parseLong(value);
				continue;
			}
			if("encode".equals(name)){
				encode=value;
				continue;
			}
			if("zgip".equals(name)){
				isZgip=true;
				continue;
			}
			if("chunk".equals(name)){
				isChunk=true;
				continue;
			}
			headers.put(name, value);
		}
		responsStore(store,headers,encode,offset,length,isChunk,isZgip);
	}
	

	/**
	 * ���N�G�X�g�p�^�[��
	 * 1)��handler����forward����ČĂяo�����ꍇ --> requestContext����
	 * 2)json�`����POST�����ꍇ-->parameter��json�I�u�W�F�N�g�����邩�ۂ�
	 * 3)�N�G���Ƀp�����^�����Ă��ă��N�G�X�g�����ꍇ�@--> ��L�ȊO
	 */
	public void onRequestBody() {
		Store store=(Store)getAttribute("Store");
		if(store!=null){//foward����Ă����ꍇ
			setAttribute("Store",null);//�폜���Ȃ���store�̃��C�t�T�C�N��������
			responseStoreByForward(store);
			return;
		}
		ParameterParser parameter = getParameterParser();
		JSONObject jsonObject=(JSONObject)parameter.getJsonObject();
		if(jsonObject!=null){//foward����Ă����ꍇ
			responseStoreByPost(jsonObject);
			return;
		}
		//GET�Ńp�����^�w��̏ꍇ
		responseStoreByGet(parameter);
	}

	public void onFailure(Object userContext, Throwable t) {
		if(logger.isDebugEnabled())logger.debug("#failer.cid:" + getChannelId() + ":" + t.getMessage());
		super.onFailure(t, userContext);
	}

	public void onTimeout(Object userContext) {
		if(logger.isDebugEnabled())logger.debug("#timeout.cid:" + getChannelId());
		super.onTimeout(userContext);
	}

	private void skip(ByteBuffer buffer) {
		long remaining = (long) buffer.remaining();
		if (skipLength == 0) {
			// �Ȃɂ����Ȃ�
		} else if (remaining < skipLength) {
			skipLength -= remaining;
			int pos = buffer.position();
			buffer.limit(pos);
			remaining = 0;
		} else {
			int pos = buffer.position();
			buffer.position(pos + (int) skipLength);
			remaining -= skipLength;
			skipLength = 0;
		}
		if(leftLength<0){
			
		}else if (leftLength < remaining) {
			int pos = buffer.position();
			buffer.limit(pos + (int) leftLength);
			leftLength = 0;
		} else {
			leftLength -= remaining;
		}
	}

	/*
	 * charset��ސ�
	 * �ȉ��̃p�^�[��
	 * <?xml version="1.0" encoding="EUC-JP"?>
	 * <meta http-equiv="content-type" content="text/html; charset=EUC-JP" />
	 * <meta charset=utf-8>
	 * <meta charset="utf-8">
	 * 
	 */
	private static Pattern CHARSET_PATTERN = Pattern.compile("(?:encoding=\"([^\"\\s]*)\")|(?:charset=[\"]?([^\"'>\\s]*))",Pattern.CASE_INSENSITIVE);
	private String guessCharset(String text){
		Matcher matcher=null;
		synchronized(CHARSET_PATTERN){
			matcher=CHARSET_PATTERN.matcher(text);
		}
		String charset="utf-8";
		if(matcher.find()){
			for(int i=1;i<=matcher.groupCount();i++){
				String c=matcher.group(i);
				if(c!=null&&!"".equals(c)){
					charset=c;
					break;
				}
			}
		}
		return charset;
	}
	
	public boolean onBuffer(ByteBuffer[] buffers, Object userContext) {
		if(isResponseEnd()){//���X�|���X���I����Ă�ꍇ
			PoolManager.poolBufferInstance(buffers);
			releaseStore(false);
			return false;
		}
		if(isChunk){
			buffers=chunkContext.decodeChunk(buffers);
			if(BuffersUtil.remaining(buffers)==0){
				return true;//���̃o�b�t�@��v��
			}
		}
		if(isGzip){
			gzipContext.putZipedBuffer(buffers);
			buffers=gzipContext.getPlainBuffer();
			if(BuffersUtil.remaining(buffers)==0){
				return true;//���̃o�b�t�@��v��
			}
		}
		if(logger.isDebugEnabled())logger.debug("onBuffer traceStore:" + store.getStoreId() + ":length:" + BuffersUtil.remaining(buffers));
		for (int i = 0; i < buffers.length; i++) {
			skip(buffers[i]);
		}
		if(BuffersUtil.remaining(buffers)==0){
			return true;//���̃o�b�t�@��v��
		}
		if(isCodeConvert){
			try {
				buffers=codeConverter.convert(buffers);
			} catch (IOException e) {
				logger.error("convert error.",e);
				releaseStore(true);
				asyncClose(userContext);
				return false;
			}
			if(BuffersUtil.remaining(buffers)==0){
				return true;//���̃o�b�t�@��v��
			}
		}
		String contentType=getHeader(HeaderParser.CONTENT_TYPE_HEADER);
		if(contentType==null){
			//TODO �ސ�����charset��ݒ�
			String charset=guessCharset(BuffersUtil.toStringFromBuffer(buffers[0], "iso8859_1"));
			setContentType("text/plain; charset="+charset);
		}
		responseBody(buffers);
		return false;
	}
	
	private void releaseStore(boolean isClose){
		if(store==null){
			return;//�����͂�
		}
		if(isClose){
			store.close(this,store);
		}
		store=null;
		unref();
	}

	public void onBufferEnd(Object userContext) {
		releaseStore(false);
		if(isResponseEnd()){//���X�|���X���I����Ă�ꍇ
			return;
		}
		//gzipContext�ɓ����Ă���o�b�t�@���t���b�V������
		if(isCodeConvert){
			try {
				ByteBuffer buffers[]=codeConverter.close();
				responseBody(buffers);
			} catch (IOException e) {
				logger.error("convert error.",e);
			}
		}
		responseEnd();
	}

	public void onBufferFailure(Throwable failure, Object userContext) {
		logger.error("onBufferFailure error.", failure);
		releaseStore(false);
		if(isResponseEnd()){//���X�|���X���I����Ă�ꍇ
			return;
		}
		responseEnd();
	}

	public void onWrittenBody() {
		if (store == null) {
			return;
		}
		if (leftLength == 0) {
			releaseStore(true);
		} else {
			store.asyncBuffer(this, store);
		}
	}

	@Override
	public void onFinished() {
		releaseStore(true);
		super.onFinished();
	}
}
