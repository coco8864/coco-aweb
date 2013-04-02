package naru.aweb.admin;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.store.Store;
import naru.async.store.StoreManager;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.pa.Blob;
import naru.aweb.pa.PaPeer;
import naru.aweb.pa.Palet;
import naru.aweb.pa.PaletCtx;
import naru.aweb.robot.Browser;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

public class AccessLogPalet implements Palet {
	private static Logger logger = Logger.getLogger(AccessLogPalet.class);
	private static Config config=Config.getConfig();

	private PaletCtx ctx;
	@Override
	public void init(String qname,String subname,PaletCtx ctx) {
		this.ctx=ctx;
	}

	@Override
	public void term(String reason) {
	}

	@Override
	public void onTimer() {
	}

	@Override
	public void onSubscribe(PaPeer peer) {
	}

	@Override
	public void onUnsubscribe(PaPeer peer, String reason) {
	}

	@Override
	public void onPublishText(PaPeer peer, String data) {
	}

	@Override
	public void onPublishObj(PaPeer peer, Map<String, ?> parameter) {
		JSONObject res=new JSONObject();
		String command=(String)parameter.get("command");
		res.put("command", command);
		if("list".equals(command)){
			JSON list=listAccessLogJson(parameter);
			res.put("data", list);
			peer.message(res);
			return;
		}else if("entry".equals(command)){
			Integer id=(Integer)parameter.get("id");
			AccessLog accessLog=AccessLog.getById((long)id);
			JSONObject json=accessLog.toJson();
			json.put("kind", "accessLog");
			peer.message(json);
			return;
		}else if("import".equals(command)){
			importAccesslog(peer,parameter);
			peer.message("done");
			return;
		}else if("exportIds".equals(command)){
			Collection<Long>ids=list(parameter);
			config.getLogPersister().exportAccessLog(ids, peer);
			return;
		}else if("exportQuery".equals(command)){
			config.getLogPersister().exportAccessLog(query(parameter), peer);
			return;
		}else if("deleteIds".equals(command)){
			Collection<Long>ids=list(parameter);
			config.getLogPersister().deleteAccessLog(ids, peer);
			return;
		}else if("deleteQuery".equals(command)){
			config.getLogPersister().deleteAccessLog(query(parameter), peer);
			return;
		}else if("runAccessLog".equals(command)){
			try {
				runAccessLog((JSONObject)parameter,peer);
			} catch (Exception e) {
				logger.error("runAccessLog error.",e);
				peer.message(res);
			}
		}else if("saveAccessLog".equals(command)){
			try {
				AccessLog accessLog=getEditedAccessLog((JSONObject)parameter);
				accessLog.setOriginalLogId(accessLog.getId());
				accessLog.setId(null);
				JSONObject json=accessLog.toJson();
				accessLog.setPersist(true);
				accessLog.persist();
				json.put("id", accessLog.getId());
				json.put("kind", "accessLog");
				peer.message(json);
			} catch (Exception e) {
				logger.error("getEditedAccessLog error.",e);
				peer.message(res);
			}
		}
	}

	@Override
	public void onPublishArray(PaPeer peer, List<?> data) {
	}
	
	private static final String HEX_0A =new String(new byte[]{(byte)0x0a});
	private static final String HEX_0D0A =new String(new byte[]{(byte)0x0d,(byte)0x0a});
	private static final byte[] HEADER_END_BYTE =new byte[]{(byte)0x0d,(byte)0x0a,(byte)0x0d,(byte)0x0a};
	
	private byte[] bytes(String text,String encode) throws UnsupportedEncodingException{
		if(encode==null||"".equals(encode)){
			encode="utf8";
		}
		return text.getBytes(encode);
	}
	
	private HeaderParser createHeader(ByteBuffer[] buffers){
		if(buffers==null){
			return null;
		}
		HeaderParser header=(HeaderParser)PoolManager.getInstance(HeaderParser.class);
		for(int i=0;i<buffers.length;i++){
			header.parse(buffers[i]);
		}
		PoolManager.poolArrayInstance(buffers);
		if(!header.isParseEnd()){
			header.parse(ByteBuffer.wrap(HEADER_END_BYTE));
		}
		return header;
	}
	
	private HeaderParser getPartHeader(JSONObject parameter,String part,String digest) throws UnsupportedEncodingException{
		String text=parameter.optString(part,null);
		String encode=parameter.optString(part+"Encode",null);
		if(text!=null){
			text=text.replaceAll(HEX_0A,HEX_0D0A);
		}
		ByteBuffer[] buffers;
		if(text==null){
			if(digest==null){
				return null;
			}
			buffers=DataUtil.toByteBuffers(digest);
		}else{
			byte[] data=null;
			data=bytes(text,encode);//TODO content-typeのcharset=の右なので、javaのencodeとして齟齬がある
			buffers=BuffersUtil.toByteBufferArray(ByteBuffer.wrap(data));
		}
		return  createHeader(buffers);
	}
	
	private String digest(ByteBuffer[] data){
		Store store=Store.open(true);
		store.putBuffer(data);
		store.close();
		return store.getDigest();
	}
	
	private ByteBuffer[] getPartBuffer(JSONObject parameter,String part,String digest) throws UnsupportedEncodingException{
		String body=parameter.optString(part,null);
		String bodyEncode=parameter.optString(part+"Encode",null);
		if(body==null){
			if(digest==null){
				return null;
			}
			return DataUtil.toByteBuffers(digest);
		}
		byte[] data=bytes(body,bodyEncode);//TODO content-typeのcharset=の右なので、javaのencodeとして齟齬がある
		return BuffersUtil.toByteBufferArray(ByteBuffer.wrap(data));
	}
	
	private void updateResolveDigest(AccessLog accessLog,HeaderParser requestHeader){
		//bodyが存在しない場合resolveDigestは計算しない
		if(accessLog.getResponseBodyDigest()==null||accessLog.getResponseHeaderDigest()==null){
			return; 
		}
		boolean isHttps=(accessLog.getDestinationType()==AccessLog.DESTINATION_TYPE_HTTPS);
		String resolveDigest=AccessLog.calcResolveDigest(requestHeader.getMethod(),
				isHttps,
				accessLog.getResolveOrigin(),
				requestHeader.getPath(),requestHeader.getQuery());
		accessLog.setResolveDigest(resolveDigest);
	}
	
	/*　編集されていないstoreの参照カウンタをアップする */
	private void storeRef(String digest){
		StoreManager.ref(digest);
	}
	
	/*
	 * traceタブにあるsave newボタン
	 * runした後は、そのプロトコル情報をtrace画面で表示
	 */
	private AccessLog getEditedAccessLog(JSONObject parameter) throws UnsupportedEncodingException{
		String accessLogId=parameter.getString("accessLogId");
		String requestHeader=parameter.optString("requestHeader",null);
		String requestBody=parameter.optString("requestBody",null);
		String responseHeader=parameter.optString("responseHeader",null);
		String responseBody=parameter.optString("responseBody",null);

		AccessLog accessLog=AccessLog.getById(Long.parseLong(accessLogId));
		accessLog.setSourceType(AccessLog.SOURCE_TYPE_EDIT);
		
		HeaderParser requestHeaderParser=null;
		ByteBuffer[] requestBodyBuffer=null;
		if(requestBody!=null){
			requestHeaderParser=getPartHeader(parameter,"requestHeader",accessLog.getRequestHeaderDigest());
			requestBodyBuffer=getPartBuffer(parameter,"requestBody",accessLog.getRequestBodyDigest());
			//通知されたbodyは、chunkもされていないとする
			requestHeaderParser.removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
			requestHeaderParser.setContentLength(BuffersUtil.remaining(requestBodyBuffer));
		}else if(requestHeader!=null){
			requestHeaderParser=getPartHeader(parameter,"requestHeader",accessLog.getRequestHeaderDigest());
		}
		if(requestHeaderParser!=null){
			//HOSTヘッダを適切に修正
			requestHeaderParser.setHost(accessLog.getResolveOrigin());
			//headerをいじった場合は、resolveDigestも更新する
			updateResolveDigest(accessLog,requestHeaderParser);
			accessLog.setRequestLine(requestHeaderParser.getRequestLine());
			String requestHeaderDigest=digest(requestHeaderParser.getHeaderBuffer());
			accessLog.setRequestHeaderDigest(requestHeaderDigest);//xx
			
			/* requestHeaderの解放 */
			requestHeaderParser.unref();
			requestHeaderParser=null;
		}else{
			storeRef(accessLog.getRequestHeaderDigest());
		}
		if(requestBodyBuffer!=null){
			String requestBodyDigest=digest(requestBodyBuffer);
			accessLog.setRequestBodyDigest(requestBodyDigest);//xx
		}else{
			storeRef(accessLog.getRequestBodyDigest());
		}
		HeaderParser responseHeaderParser=null;
		ByteBuffer[] responseBodyBuffer=null;
		if(responseBody!=null){
			responseHeaderParser=getPartHeader(parameter,"responseHeader",accessLog.getResponseHeaderDigest());
			responseBodyBuffer=getPartBuffer(parameter,"responseBody",accessLog.getResponseBodyDigest());
			//通知されたbodyは、zgipもchunkもされていないとする
			accessLog.setTransferEncoding(null);
			accessLog.setContentEncoding(null);
			responseHeaderParser.removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
			responseHeaderParser.removeHeader(HeaderParser.CONTENT_ENCODING_HEADER);
			long contentLength=BuffersUtil.remaining(responseBodyBuffer);
			responseHeaderParser.setContentLength(contentLength);
			accessLog.setResponseLength(contentLength);
		}else if(responseHeader!=null){
			responseHeaderParser=getPartHeader(parameter,"responseHeader",accessLog.getResponseHeaderDigest());
		}
		if(responseHeaderParser!=null){
			accessLog.setTransferEncoding(responseHeaderParser.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER));
			accessLog.setContentEncoding(responseHeaderParser.getHeader(HeaderParser.CONTENT_ENCODING_HEADER));
			accessLog.setContentType(responseHeaderParser.getContentType());
			String responseHeaderDigest=digest(responseHeaderParser.getHeaderBuffer());
			accessLog.setResponseHeaderDigest(responseHeaderDigest);//xx
			responseHeaderParser.unref(true);//getPartHeaderメソッドで獲得したオブジェクトを開放
			responseHeaderParser=null;
		}else{
			storeRef(accessLog.getResponseHeaderDigest());
		}
		if(responseBodyBuffer!=null){
			String responseBodyDigest=digest(responseBodyBuffer);
			accessLog.setResponseBodyDigest(responseBodyDigest);//xx
		}else{
			storeRef(accessLog.getResponseBodyDigest());
		}
		return accessLog;
	}
	
	/*
	 * traceタブにあるrunボタン
	 * runした後は、そのプロトコル情報をtrace画面で表示
	 */
	private void runAccessLog(JSONObject parameter,PaPeer peer) throws UnsupportedEncodingException{
		String accessLogId=parameter.getString("accessLogId");
		String requestBody=parameter.optString("requestBody",null);
		AccessLog accessLog=AccessLog.getById(Long.parseLong(accessLogId));
		if(accessLog.getDestinationType()!=AccessLog.DESTINATION_TYPE_HTTP&&accessLog.getDestinationType()!=AccessLog.DESTINATION_TYPE_HTTPS){
			peer.message("fail to runAccessLog");
			return;
		}
		HeaderParser requestHeaderParser=getPartHeader(parameter,"requestHeader",accessLog.getRequestHeaderDigest());
		if(requestHeaderParser==null){
			peer.message("fail to runAccessLog");
		}
		if(HeaderParser.CONNECT_METHOD.equalsIgnoreCase(requestHeaderParser.getMethod())){
			//CONNECTメソッドの場合は、真のヘッダがbody部分に格納されている
			ByteBuffer[] realHeader=requestHeaderParser.getBodyBuffer();
			requestHeaderParser.recycle();
			for(ByteBuffer buffer:realHeader){
				requestHeaderParser.parse(buffer);
			}
			PoolManager.poolArrayInstance(realHeader);
			if(!requestHeaderParser.isParseEnd()){
				peer.message("fail to runAccessLog");
				return;
			}
		}
		ByteBuffer[] requestBodyBuffer=getPartBuffer(parameter,"requestBody",accessLog.getRequestBodyDigest());
		if(requestBody!=null){
			//通知されたbodyは、chunkもされていないとする
			requestHeaderParser.removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
			requestHeaderParser.setContentLength(BuffersUtil.remaining(requestBodyBuffer));
		}
		requestHeaderParser.setHost(accessLog.getResolveOrigin());
		boolean isHttps=(accessLog.getDestinationType()==AccessLog.DESTINATION_TYPE_HTTPS);
		Browser browser=Browser.create("run:"+accessLogId,isHttps, requestHeaderParser, requestBodyBuffer);
		browser.start(peer);//TODO
	}
	
	private JSON listAccessLogJson(Map<String, ?> parameter){
		try{
			String query=(String)parameter.get("query");
			Integer from=(Integer)parameter.get("from");
			Integer to=(Integer)parameter.get("to");
			String orderby=(String)parameter.get("orderby");
			if(from==null){
				from=-1;
			}
			if(to==null){
				to=Integer.MAX_VALUE;
			}
			Collection<AccessLog> accessLogs=AccessLog.query(query, from, to,orderby);
			return AccessLog.collectionToJson(accessLogs);
		}catch(Exception e){
			logger.error("accessLogJson error.",e);
		}
		return null;
	}
	
	private void importAccesslog(PaPeer peer,Map<String, ?> parameter) {
		Blob importsFile=(Blob)parameter.get("importsFile");
		config.getLogPersister().importAccessLog(importsFile,peer);
	}
	
	private Set<Long> list(Map<String, ?> parameter){
		String param=(String)parameter.get("list");
		Set<Long> accessLogIds=new HashSet<Long>();
		if(param!=null){
			String[] ids=param.split(",");
			for(int i=0;i<ids.length;i++){
				long accessLogId=Long.parseLong(ids[i]);
				accessLogIds.add(accessLogId);
			}
		}
		return accessLogIds;
	}
	
	private static String LIST_QUERY_BASE="SELECT id from " + AccessLog.class.getName();
	private String query(Map<String, ?> parameter){
		String query=(String)parameter.get("query");
		if(query!=null&&!query.equals("")){
			query = LIST_QUERY_BASE + " WHERE " + query;
		}else{
			query=LIST_QUERY_BASE;
		}
		return query;
	}
}
