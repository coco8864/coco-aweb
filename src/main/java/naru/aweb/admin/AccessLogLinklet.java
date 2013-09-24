package naru.aweb.admin;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import naru.aweb.link.api.Blob;
import naru.aweb.link.api.LinkMsg;
import naru.aweb.link.api.LinkPeer;
import naru.aweb.link.api.Linklet;
import naru.aweb.link.api.LinkletCtx;
import naru.aweb.robot.Browser;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

public class AccessLogLinklet implements Linklet {
	private static Logger logger = Logger.getLogger(AccessLogLinklet.class);
	private static Config config=Config.getConfig();

	private LinkletCtx ctx;
	@Override
	public void init(String qname,String subname,LinkletCtx ctx) {
		this.ctx=ctx;
		ctx.setInterval(2000);
	}

	@Override
	public void term(String reason) {
	}

	@Override
	public void onTimer() {
		LinkPeer[] peers=null;
		synchronized(watchsMap){
			peers=(LinkPeer[])watchsMap.keySet().toArray(new LinkPeer[0]);
		}
		for(LinkPeer peer:peers){
			LinkMsg parameter=watchsMap.get(peer);
			if(parameter==null){
				continue;
			}
			if(peer.isLinkSession()==false){
				watchsMap.remove(peer);
				peer.unref();
				parameter.unref();
				continue;
			}
			list(peer, parameter);
		}
	}

	@Override
	public void onSubscribe(LinkPeer peer) {
	}

	@Override
	public void onUnsubscribe(LinkPeer peer, String reason) {
	}

	private void list(LinkPeer peer, LinkMsg parameter){
		JSONObject res=new JSONObject();
		res.put("command", "list");
		JSON list=listAccessLogJson(parameter);
		res.put("data", list);
		peer.message(res);
	}
	
	private Map<LinkPeer,LinkMsg> watchsMap=new HashMap<LinkPeer,LinkMsg>();
	private void checkWatch(LinkPeer peer, LinkMsg parameter){
		boolean isWatch=parameter.getBoolean("isWatch");
		synchronized(watchsMap){
			if(isWatch){
				parameter.ref();
				peer.ref();
				watchsMap.put(peer,parameter);
			}else{
				LinkMsg msg=watchsMap.remove(peer);
				if(msg!=null){
					msg.unref();
					peer.unref();
				}
			}
		}
	}
	
	@Override
	public void onPublish(LinkPeer peer, LinkMsg parameter) {
		JSONObject res=new JSONObject();
		String command=parameter.getString("command");
		res.put("command", command);
		if("list".equals(command)){
			JSON list=listAccessLogJson(parameter);
			res.put("data", list);
			peer.message(res);
			checkWatch(peer, parameter);
			return;
		}else if("entry".equals(command)){
			Integer id=(Integer)parameter.get("id");
			AccessLog accessLog=AccessLog.getById((long)id);
			if(accessLog==null){
				res.put("result", false);
				res.put("reason", "not found");
				peer.message(res);
				return;
			}
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
				runAccessLog(parameter,peer);
			} catch (Exception e) {
				logger.error("runAccessLog error.",e);
				peer.message(res);
			}
		}else if("saveAccessLog".equals(command)){
			try {
				AccessLog accessLog=getEditedAccessLog(parameter);
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
	
	private HeaderParser getPartHeader(LinkMsg parameter,String part,String digest) throws UnsupportedEncodingException{
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
	
	private ByteBuffer[] getPartBuffer(LinkMsg parameter,String part,String digest) throws UnsupportedEncodingException{
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
	private AccessLog getEditedAccessLog(LinkMsg parameter) throws UnsupportedEncodingException{
		long accessLogId=parameter.getLong("accessLogId");
		String requestHeader=parameter.optString("requestHeader",null);
		String requestBody=parameter.optString("requestBody",null);
		String responseHeader=parameter.optString("responseHeader",null);
		String responseBody=parameter.optString("responseBody",null);

		AccessLog accessLog=AccessLog.getById(accessLogId);
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
	private void runAccessLog(LinkMsg parameter,LinkPeer peer) throws UnsupportedEncodingException{
		long accessLogId=parameter.getLong("accessLogId");
		String requestBody=parameter.optString("requestBody",null);
		AccessLog accessLog=AccessLog.getById(accessLogId);
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
	
	private JSON listAccessLogJson(LinkMsg parameter){
		try{
			String query=parameter.getString("query");
			Integer from=parameter.getInt("from");
			Integer to=parameter.getInt("to");
			String orderby=parameter.getString("orderby");
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
	
	private void importAccesslog(LinkPeer peer,Map<String, ?> parameter) {
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
