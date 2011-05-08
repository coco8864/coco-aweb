package naru.aweb.admin;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import naru.async.core.ChannelContext;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.store.DataUtil;
import naru.async.store.Store;
import naru.async.store.StoreManager;
import naru.aweb.auth.Authenticator;
import naru.aweb.auth.Authorizer;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.core.Main;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.queue.QueueManager;
import naru.aweb.robot.Browser;
import naru.aweb.robot.Scenario;
import naru.aweb.util.ServerParser;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.log4j.Logger;

public class AdminHandler extends WebServerHandler{
	private static Logger logger = Logger.getLogger(AdminHandler.class);
	private static Config config=Config.getConfig();
	private File replayRootDir=new File(config.getString("path.replayDocroot"));
	
	/* ディレクトリ消す場合は、これでもよいが全ファイルを消去するので危険
	private void replayFileClean(File file){
		if(file.isFile()){
			file.delete();
			return;
		}
		for(File child:file.listFiles()){
			replayFileClean(child);
		}
	}
	*/
	private void replayDelete(){
		Iterator<File> itr=replayUploadFiles.iterator();
		while(itr.hasNext()){
			File uploadFile=itr.next();
			uploadFile.delete();
			itr.remove();
		}
	}
	
	private static List<File> replayUploadFiles=new ArrayList<File>();
	private static final String REPLAY_UPLOAD_PATH="/replayUpload";
	
	private void replayUpload(ParameterParser parameter){
		String path=parameter.getParameter("replaypath");
		path=path.replace("://", "/");
		DiskFileItem fileItem=parameter.getItem("replayFile");
		File file=fileItem.getStoreLocation();
		File target=new File(replayRootDir,path);
		String targetCanonPath;
		String rootCanonPath;
		try {
			targetCanonPath = target.getCanonicalPath();
			rootCanonPath = replayRootDir.getCanonicalPath();
		} catch (IOException e) {
			logger.warn("fail to getCanonicalPath.",e);
			completeResponse("404","getCanonicalPath error:");
			return;
		}
		if( !targetCanonPath.startsWith(rootCanonPath) ){
			logger.warn("traversal error. target:" + targetCanonPath + " root:" + rootCanonPath);
			completeResponse("404","traversal error:"+ targetCanonPath);
			return;
		}
		File parent=target.getParentFile();
		if(!parent.exists()){
			parent.mkdirs();
		}
		if(replayUploadFiles.contains(target)){
			target.delete();
		}
		file.renameTo(target);
		replayUploadFiles.add(target);
		redirectAdmin();
	}
	
	private String doStress(String name,int browserCount,int callCount,AccessLog[] accessLogs,
			boolean isCallerKeepAlive,boolean isAccessLog,boolean isResponseHeaderTrace,boolean isResponseBodyTrace){
		QueueManager queueManager=QueueManager.getInstance();
		String chId=queueManager.createQueue();
		if( Scenario.run(name, browserCount, callCount, accessLogs, isCallerKeepAlive, isAccessLog, isResponseHeaderTrace, isResponseBodyTrace,chId)){
			return chId;
		}
		return null;
	}

	private byte[] bytes(String text,String encode) throws UnsupportedEncodingException{
		if(encode==null||"".equals(encode)){
			encode="utf8";
		}
		return text.getBytes(encode);
	}
	
	private static final String HEX_0A =new String(new byte[]{(byte)0x0a});
	private static final String HEX_0D0A =new String(new byte[]{(byte)0x0d,(byte)0x0a});
	
	private HeaderParser getPartHeader(ParameterParser parameter,String part,String digest) throws UnsupportedEncodingException{
		String text=parameter.getParameter(part);
		String encode=parameter.getParameter(part+"Encode");
		if(text!=null){
			text=text.replaceAll(HEX_0A,HEX_0D0A);
		}
		ByteBuffer[] buffers;
		if(text==null){
			if(digest==null){
				return null;
			}
			buffers=DataUtil.toByteBuffers(digest);;
		}else{
			byte[] data=null;
			data=bytes(text,encode);//TODO content-typeのcharset=の右なので、javaのencodeとして齟齬がある
			buffers=BuffersUtil.toByteBufferArray(ByteBuffer.wrap(data));
		}
		HeaderParser header=(HeaderParser)PoolManager.getInstance(HeaderParser.class);
		for(int i=0;i<buffers.length;i++){
			header.parse(buffers[i]);
		}
		PoolManager.poolArrayInstance(buffers);
		return  header;
	}
	
	private String digest(ByteBuffer[] data){
		Store store=Store.open(true);
		store.putBuffer(data);
		store.close();
		return store.getDigest();
	}
	
	private ByteBuffer[] getPartBuffer(ParameterParser parameter,String part,String digest) throws UnsupportedEncodingException{
		String body=parameter.getParameter(part);
		String bodyEncode=parameter.getParameter(part+"Encode");
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
	
	private AccessLog getEditedAccessLog(ParameterParser parameter) throws UnsupportedEncodingException{
		String accessLogId=parameter.getParameter("accessLogId");
		String requestHeader=parameter.getParameter("requestHeader");
		String requestBody=parameter.getParameter("requestBody");
		String responseHeader=parameter.getParameter("responseHeader");
		String responseBody=parameter.getParameter("responseBody");

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
	
	private String runAccessLog(ParameterParser parameter) throws UnsupportedEncodingException{
		String accessLogId=parameter.getParameter("accessLogId");
		String requestBody=parameter.getParameter("requestBody");

		AccessLog accessLog=AccessLog.getById(Long.parseLong(accessLogId));
		if(accessLog.getDestinationType()!=AccessLog.DESTINATION_TYPE_HTTP&&accessLog.getDestinationType()!=AccessLog.DESTINATION_TYPE_HTTPS){
			return null;
		}
		HeaderParser requestHeaderParser=getPartHeader(parameter,"requestHeader",accessLog.getRequestHeaderDigest());
		if(HeaderParser.CONNECT_METHOD.equalsIgnoreCase(requestHeaderParser.getMethod())){
			//CONNECTメソッドの場合は、真のヘッダがbody部分に格納されている
			ByteBuffer[] realHeader=requestHeaderParser.getBodyBuffer();
			requestHeaderParser.recycle();
			for(ByteBuffer buffer:realHeader){
				requestHeaderParser.parse(buffer);
			}
			PoolManager.poolArrayInstance(realHeader);
			if(!requestHeaderParser.isParseEnd()){
				return null;
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
		Browser browser=Browser.cleate(isHttps, requestHeaderParser, requestBodyBuffer);
		browser.setName("run:"+accessLog.getId());
		QueueManager queueManager=QueueManager.getInstance();
		String chId=queueManager.createQueue();
		browser.start(chId);
		return chId;
	}
	
	//phamtomProxyの設定はここに集中する
	private void doCommand(ParameterParser parameter){
		String cmd=parameter.getParameter("command");
		String callback=parameter.getParameter("callback");
		if("setTimeouts".equals(cmd)){
			String connectTimeout=parameter.getParameter("connectTimeout");
			String acceptTimeout=parameter.getParameter("acceptTimeout");
			String readTimeout=parameter.getParameter("readTimeout");
			String writeTimeout=parameter.getParameter("writeTimeout");
			config.setConnectTimeout(Long.parseLong(connectTimeout));
			config.setAcceptTimeout(Long.parseLong(acceptTimeout));
			config.setReadTimeout(Long.parseLong(readTimeout));
			config.setWriteTimeout(Long.parseLong(writeTimeout));
			responseJson(true);
		}else if("setProxy".equals(cmd)){
			String pacUrl=parameter.getParameter("pacUrl");
			String proxyServer=parameter.getParameter("proxyServer");
			String sslProxyServer=parameter.getParameter("sslProxyServer");
			String exceptProxyDomains=parameter.getParameter("exceptProxyDomains");
			config.setProperty("pacUrl", pacUrl);
			config.setProperty("proxyServer", proxyServer);
			config.setProperty("sslProxyServer", sslProxyServer);
			config.setProperty("exceptProxyDomains", exceptProxyDomains);
			config.updateProxyFinder();
			responseJson(true);
		}else if("setKeepAlive".equals(cmd)){
			String isWebKeepAlive=parameter.getParameter("isWebKeepAlive");
			String isProxyKeepAlive=parameter.getParameter("isProxyKeepAlive");
			String maxKeepAliveRequests=parameter.getParameter("maxKeepAliveRequests");
			String keepAliveTimeout=parameter.getParameter("keepAliveTimeout");
			String contentEncoding=parameter.getParameter("contentEncoding");
			String allowChunked=parameter.getParameter("allowChunked");
			config.setProperty("isWebKeepAlive", isWebKeepAlive);
			config.setProperty("isProxyKeepAlive", isProxyKeepAlive);
			config.setProperty("maxKeepAliveRequests", maxKeepAliveRequests);
			config.setProperty("keepAliveTimeout", keepAliveTimeout);
			config.setProperty("contentEncoding", contentEncoding);
			config.setProperty("allowChunked", allowChunked);
			responseJson(true);
		}else if("setHtml5".equals(cmd)){
			String isUseWebSocket=parameter.getParameter("isUseWebSocket");
			String isUseSessionStorage=parameter.getParameter("isUseSessionStorage");
			String isUseCrossDomain=parameter.getParameter("isUseCrossDomain");
			config.setProperty("isUseWebSocket", isUseWebSocket);
			config.setProperty("isUseSessionStorage", isUseSessionStorage);
			config.setProperty("isUseCrossDomain", isUseCrossDomain);
			responseJson(true);
		}else if("setAuth".equals(cmd)){
			String scheme=parameter.getParameter("scheme");
			String logoutUrl=parameter.getParameter("logoutUrl");
			Authenticator authenticator=config.getAuthenticator();
			authenticator.setScheme(scheme);
			authenticator.setLogoutUrl(logoutUrl);
			String sessionTimeout=parameter.getParameter("sessionTimeout");
			Authorizer authorizer=config.getAuthorizer();
			authorizer.setSessionTimeout(Long.parseLong(sessionTimeout));
//			JSONObject res=new JSONObject();
//			res.accumulate("scheme", authenticator.getScheme());
			responseJson(true);
		}else if("setBroadcaster".equals(cmd)){
			String interval=parameter.getParameter("interval");
			config.setProperty("broardcastInterval", interval);
			responseJson(true);
		}else if("setSelfDomain".equals(cmd)){
			String domain=parameter.getParameter("domain");
			config.setProperty(Config.SELF_DOMAIN, domain);
			responseJson(true);
		}else if("getStastics".equals(cmd)){
			responseJson(JSONObject.fromObject(config.getStasticsObject()));
		}else if("debugTrace".equals(cmd)){
			String debugTrace=parameter.getParameter("debugTrace");
			config.setProperty(Config.DEBUG_TRACE,debugTrace);
			responseJson(config.getBoolean(Config.DEBUG_TRACE, false));
		}else if("runAccessLog".equals(cmd)){
			try {
				String chId=runAccessLog(parameter);
				responseJson(chId,callback);
			} catch (Exception e) {
				logger.error("runAccessLog error.",e);
				completeResponse("500");
			}
		}else if("saveAccessLog".equals(cmd)){
			try {
				AccessLog accessLog=getEditedAccessLog(parameter);
				accessLog.setOriginalLogId(accessLog.getId());
				accessLog.setId(null);
				accessLog.setPersist(true);
				accessLog.persist();
				responseJson(accessLog.getId(),callback);
			} catch (Exception e) {
				logger.error("getEditedAccessLog error.",e);
				completeResponse("500");
			}
		}else if("stress".equals(cmd)){
			String list=parameter.getParameter("list");
//			Set<Long> accessLogIds=new HashSet<Long>();
			String[] ids=list.split(",");
			AccessLog[] accessLogs=new AccessLog[ids.length];
			for(int i=0;i<ids.length;i++){
				long accessLogId=Long.parseLong(ids[i]);
				accessLogs[i]=AccessLog.getById(accessLogId);
//				accessLogIds.add(accessLogId);
			}
			String name=parameter.getParameter("name");
			String browserCount=parameter.getParameter("browserCount");
			String call=parameter.getParameter("loopCount");
			String time=parameter.getParameter("time");
//			String trace=parameter.getParameter("trace");
			String keepAlive=parameter.getParameter("keepAlive");
			String accessLog=parameter.getParameter("accessLog");
			String tesponseHeaderTrace=parameter.getParameter("tesponseHeaderTrace");
			String tesponseBodyTrace=parameter.getParameter("tesponseBodyTrace");
			String chId=doStress(name,Integer.parseInt(browserCount),Integer.parseInt(call),
					accessLogs,
			"true".equalsIgnoreCase(keepAlive),
			"true".equalsIgnoreCase(accessLog),
			"true".equalsIgnoreCase(tesponseHeaderTrace),
			"true".equalsIgnoreCase(tesponseBodyTrace));
			responseJson(chId,callback);
//		}else if("notify".equals(cmd)){
//			QueueManager queueManager=QueueManager.getInstance();
//			String chId=queueManager.createQueue();
//			responseJson(chId,callback);
		}else if("phlog".equals(cmd)){
			//ph.logダウンロード
			String phlogNumber=parameter.getParameter("phlogNumber");
			String logName;
			if("0".equals(phlogNumber)){
				logName="ph.log";
			}else{
				logName="ph.log."+phlogNumber;
			}
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION,"attachment; filename=\"" + logName + "\"");
			setRequestAttribute(ATTRIBUTE_RESPONSE_FILE,new File(config.getPhantomHome(),"/log/" + logName));
			forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
		}else if("checkPool".equals(cmd)){
			System.gc();
			ChannelContext.dumpChannelContexts();
			PoolManager.dump();
			completeResponse("205");
		}else if("terminate".equals(cmd)){
			Main.terminate();
			responseJson(true);
//			doneResponse("205");
		}else if("replayDelete".equals(cmd)){
			replayDelete();
			completeResponse("205");
		}else if("dumpStore".equals(cmd)){
			try {
				StoreManager.dumpStore();
				completeResponse("205");
			} catch (IOException e) {
				logger.warn("fail to dumpStore",e);
				completeResponse("500");
			}
		}
	}
	
	private void redirectAdmin(){
		HeaderParser requestHeader=getRequestHeader();
		ServerParser selfServer=requestHeader.getServer();
		StringBuilder sb=new StringBuilder();
		if(isSsl()){
			sb.append("https://");
		}else{
			sb.append("http://");
		}
		sb.append(selfServer.toString());
		sb.append("/admin");//TODO
		sb.append("/admin.vsp");
		setHeader(HeaderParser.LOCATION_HEADER, sb.toString());
		completeResponse("302");
		return;
	}
	
	private boolean checkToken(ParameterParser parameter){
		RequestContext requestContext=getRequestContext();
//		KeepAliveContext keepAliveContext=getKeepAliveContext();
		String token=requestContext.getAuthSession().getToken();
		String paramToken=parameter.getParameter("token");
		if(paramToken!=null){
			if(token.equals(paramToken)){
				return true;
			}
			return false;
		}
		JSON json=parameter.getJsonObject();
		if(json instanceof JSONObject){
			JSONObject jsonObj=(JSONObject)json;
			String jsonToken=jsonObj.getString("token");
			if(token.equals(jsonToken)){
				return true;
			}
		}
		return false;
	}

	public void startResponseReqBody(){
		try{
			doAdmin();
		}catch(RuntimeException e){
			logger.warn("fail to doAdmin.",e);
			completeResponse("500");
		}
	}
	
	public void doAdmin(){
		HeaderParser requestHeader=getRequestHeader();
		//自リクエストFile名をテンプレート名とする
		MappingResult mapping=getRequestMapping();
		//実際に処理するpath
		String selfPath=requestHeader.getRequestUri();
		String path=mapping.getResolvePath();
		if("/".equals(path)){
			if(!selfPath.endsWith("/")){
				redirectAdmin();
				return;
			}
			path="/admin.vsp";
			mapping.setResolvePath(path);
		}
		if(path.endsWith(".vsp")||path.endsWith(".vsf")){
			mapping.setDesitinationFile(config.getAdminDocumentRoot());
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return;
		}else if(path.startsWith("/storeDownload")){
			forwardHandler(Mapping.STORE_HANDLER);
			return;
		}
		ParameterParser parameter=getParameterParser();
		if(!checkToken(parameter)){
			//tokenが合致しない。POSTならエラー、GETなら静的コンテンツとして処理
			if(requestHeader.getMethod().equalsIgnoreCase(HeaderParser.POST_METHOD)){
				logger.error("CSRF check error.path:"+path +":cid:"+getChannelId());
				completeResponse("403");
			}else{
				mapping.setOption(MappingResult.PARAMETER_VELOCITY_USE,"false");
				mapping.setDesitinationFile(config.getPublicDocumentRoot());
				forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
			}
			return;
		}
//		if(doCommand(parameter)==false){
//			return;
//		}
		//'/' および queryを含まないfile名
//		String file=requestHeader.getFile();
		if( "/admin".equals(path)){
			doCommand(parameter);
			return;
		}else if( "/accessLog".equals(path)){
			forwardHandler(AdminAccessLogHandler.class);
			return;
		}else if( REPLAY_UPLOAD_PATH.equals(path)){
			replayUpload(parameter);
			return;
		}else if(path.startsWith("/mapping")){
			forwardHandler(AdminMappingHandler.class);
			return;
		}else if(path.startsWith("/realHost")){
			forwardHandler(AdminRealHostHandler.class);
			return;
		}else if(path.startsWith("/user")){//user関連
			forwardHandler(AdminUserHandler.class);
			return;
		}else if(path.startsWith("/perf")){//perfomance関連
			forwardHandler(AdminPerfHandler.class);
			return;
		}else if(path.startsWith("/filter")){//filter関連
			forwardHandler(AdminFilterHandler.class);
			return;
		}else if(path.startsWith("/commissionAuth")){//commissionAuth関連//TODO to portal
			forwardHandler(AdminCommissionAuthHandler.class);
			return;
		}
		logger.error("admin not found error.path:"+path +":cid:"+getChannelId());
		completeResponse("404");
	}
	
}
