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
 * 1)指定されたヘッダを付加してレスポンス
 * 2)chunkされている事を前提にデーコードしてレスポンス
 * 3)zipされている事を前提にデコードしてレスポンス
 * 4)offset,length指定
 * @author Naru
 *
 */
public class StoreHandler extends WebServerHandler implements BufferGetter {
	private static Logger logger = Logger.getLogger(StoreHandler.class);

	/* tracelogのダウンロード処理 */
	private long skipLength = 0;
	private long leftLength = 0;
	//storeを設定した場合は、自分をrefする。endが通知され時にunrefする
	private Store store = null;
	
	/*
	 * storeを取得した場合は、自分をref,and asyncBufferを呼ぶ
	 * 自分をunrefするのは、以下のタイミング、同時にstoreにnullを設定
	 * 1)onBuffer をfalse(次bufferの要求なし)で復帰する直前
	 * 2)onBufferEnd,onBufferFailureが通知された場合
	 * 
	 * onBufferでbufferが通知された場合
	 * 1)通信中ならbuffer(or その加工物)をレスポンスに返却
	 * 2)継続の有無により復帰値を制御
	 * 3)継続しない場合、endOfRequestを呼び出し=>通信中フラグを下げる
	 * 4)通信中じゃなければ、falseを返却(上記に続く)
	 * 
	 * onBufferEnd,onBufferFailureが通知された場合
	 * 1)通信中なら、endOfRequestを呼び出し=>通信中フラグを下げる
	 * 2)通信中じゃなければ、何もしない
	 * 
	 * 回線の切断が通知された場合
	 * 1)通信中フラグを下げる
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
	 * @param encode storeに格納されているデータのコード系
	 * @param offset storeの先頭offsetをスキップ
	 * @param length response長(負数の場合、storeのサイズに従う)
	 * @param isChunk storeに格納されているデータがchunkされているとしてデコードしてレスポンス//TODO 常にfalse 
	 * @param isGzip storeに格納されているデータがzip圧縮されているとしてデコードしてレスポンス//TODO 常にfalse
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
		ref();//storeの面倒をみるため
		store.asyncBuffer(this, store);
	}
	
	/**
	 * forwardする前にcontentTypeを設定すること
	 * 設定されていない場合は、textと判断、コンテンツ内容からcharsetを推定して以下を設定する
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
		//SPDYの場合、chankヘッダがあるとエラーになる。
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
	 * リクエストパターン
	 * 1)他handlerからforwardされて呼び出される場合 --> requestContextから
	 * 2)json形式でPOSTされる場合-->parameterにjsonオブジェクトがあるか否か
	 * 3)クエリにパラメタをしてしてリクエストされる場合　--> 上記以外
	 */
	public void onRequestBody() {
		Store store=(Store)getAttribute("Store");
		if(store!=null){//fowardされてきた場合
			setAttribute("Store",null);//削除しないとstoreのライフサイクルが狂う
			responseStoreByForward(store);
			return;
		}
		ParameterParser parameter = getParameterParser();
		JSONObject jsonObject=(JSONObject)parameter.getJsonObject();
		if(jsonObject!=null){//fowardされてきた場合
			responseStoreByPost(jsonObject);
			return;
		}
		//GETでパラメタ指定の場合
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
			// なにもしない
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
	 * charsetを類推
	 * 以下のパターン
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
		if(isResponseEnd()){//レスポンスが終わってる場合
			PoolManager.poolBufferInstance(buffers);
			releaseStore(false);
			return false;
		}
		if(isChunk){
			buffers=chunkContext.decodeChunk(buffers);
			if(BuffersUtil.remaining(buffers)==0){
				return true;//次のバッファを要求
			}
		}
		if(isGzip){
			gzipContext.putZipedBuffer(buffers);
			buffers=gzipContext.getPlainBuffer();
			if(BuffersUtil.remaining(buffers)==0){
				return true;//次のバッファを要求
			}
		}
		if(logger.isDebugEnabled())logger.debug("onBuffer traceStore:" + store.getStoreId() + ":length:" + BuffersUtil.remaining(buffers));
		for (int i = 0; i < buffers.length; i++) {
			skip(buffers[i]);
		}
		if(BuffersUtil.remaining(buffers)==0){
			return true;//次のバッファを要求
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
				return true;//次のバッファを要求
			}
		}
		String contentType=getHeader(HeaderParser.CONTENT_TYPE_HEADER);
		if(contentType==null){
			//TODO 類推してcharsetを設定
			String charset=guessCharset(BuffersUtil.toStringFromBuffer(buffers[0], "iso8859_1"));
			setContentType("text/plain; charset="+charset);
		}
		responseBody(buffers);
		return false;
	}
	
	private void releaseStore(boolean isClose){
		if(store==null){
			return;//無いはず
		}
		if(isClose){
			store.close(this,store);
		}
		store=null;
		unref();
	}

	public void onBufferEnd(Object userContext) {
		releaseStore(false);
		if(isResponseEnd()){//レスポンスが終わってる場合
			return;
		}
		//gzipContextに入っているバッファをフラッシュする
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
		if(isResponseEnd()){//レスポンスが終わってる場合
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
