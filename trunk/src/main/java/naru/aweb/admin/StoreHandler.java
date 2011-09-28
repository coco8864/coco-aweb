package naru.aweb.admin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;
import naru.async.store.Store;
import naru.aweb.http.ChunkContext;
import naru.aweb.http.GzipContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.CodeConverter;
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
		}else{
			setContentLength(length);
		}
		if(getStatusCode()==null){
			setStatusCode("200");
		}
		this.store=store;
		ref();//storeの面倒をみるため
		skipLength = offset;
		leftLength = length;
		store.asyncBuffer(this, store);
	}
	
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
		responsStore(store,headers,null,offset,length,false,false);
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
	public void startResponseReqBody() {
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
		logger.debug("#failer.cid:" + getChannelId() + ":" + t.getMessage());
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" + getChannelId());
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
	
	private void closeStore(){
		Store tmpStore=store;//急にnullクリアされるのを防ぐ
		if(tmpStore!=null){
			tmpStore.close(this,store);
		}
	}

	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		if(store==null){
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
		logger.debug("onGot traceStore:" + store.getStoreId() + ":length:"
				+ BuffersUtil.remaining(buffers));
		for (int i = 0; i < buffers.length; i++) {
			skip(buffers[i]);
		}
		if(BuffersUtil.remaining(buffers)==0){
			return true;//次のバッファを要求
		}
		logger.debug("onGot skip traceStore:" + store.getStoreId() + ":length:"
				+ BuffersUtil.remaining(buffers));
		if(isCodeConvert){
			try {
				buffers=codeConverter.convert(buffers);
			} catch (IOException e) {
				logger.error("convert error.",e);
				closeStore();
				asyncClose(userContext);
				return false;
			}
			if(BuffersUtil.remaining(buffers)==0){
				return true;//次のバッファを要求
			}
		}
		responseBody(buffers);
		return false;
	}

	public void onBufferEnd(Object userContext) {
		//gzipContextに入っているバッファをフラッシュする
//		onBuffer(userContext,null);
		if(isCodeConvert){
			try {
				ByteBuffer buffers[]=codeConverter.close();
				responseBody(buffers);
			} catch (IOException e) {
				logger.error("convert error.",e);
			}
		}
		responseEnd();
//		onBufferEndが通知されたという事は、storeは開放されている
		synchronized(this){
			if(store!=null){//２回呼ばれるとは思えないが念のため
				unref();//storeとの関係がきれたので
				store=null;//既にnuullかもしれないが
			}
		}
	}

	public void onBufferFailure(Object userContext, Throwable falure) {
		closeStore();
		logger.error("onGotFailure error.", falure);
		responseEnd();
//		onBufferFailureが通知されたという事は、storeは開放されている
		synchronized(this){
			if(store!=null){//２回呼ばれるとは思えないが念のため
				unref();//storeとの関係がきれたので
				store=null;//既にnuullかもしれないが
			}
		}
	}

	public void onWrittenBody() {
		if (store == null) {
			return;
		}
		if (leftLength == 0) {
			closeStore();
		} else {
			store.asyncBuffer(this, store);
		}
	}

	public void onFinished() {
		closeStore();
		super.onFinished();
	}
}
