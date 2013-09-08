package naru.aweb.admin;

import java.io.File;
import java.io.IOException;
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
import naru.aweb.auth.LogoutEvent;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.core.Main;
import naru.aweb.handler.ws.WsProtocol;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.spdy.SpdyConfig;
import naru.aweb.spdy.SpdyFrame;
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
	
	private static final byte[] HEADER_END_BYTE =new byte[]{(byte)0x0d,(byte)0x0a,(byte)0x0d,(byte)0x0a};
	
	/* レスポンスの内容をそのままレスポンスする */
	private void viewAccessLog(ParameterParser parameter){
		String accessLogId=parameter.getParameter("accessLogId");
		AccessLog accessLog=AccessLog.getById(Long.parseLong(accessLogId));
		if(accessLog==null){
			completeResponse("404","not found accessLog:"+accessLogId);
			return;
		}
		String responsHeaderDigest=accessLog.getResponseHeaderDigest();
		if(responsHeaderDigest==null){
			completeResponse("404","not found response header data.");
			return;
		}
		ByteBuffer[] headerBuffer=DataUtil.toByteBuffers(responsHeaderDigest);
		if(headerBuffer==null){
			completeResponse("404","not found response header data.digest:"+responsHeaderDigest);
			return;
		}
		if( !parseResponseHeader(headerBuffer) ){
			parseResponseHeader(BuffersUtil.toByteBufferArray(ByteBuffer.wrap(HEADER_END_BYTE)));
		}
		String responsBodyDigest=accessLog.getResponseBodyDigest();
		if(responsBodyDigest==null){
			responseEnd();
			return;
		}
		Store body=Store.open(responsBodyDigest);
		if(body==null){
			responseEnd();
			return;
		}
		setAttribute("Store", body);
		forwardHandler(Mapping.STORE_HANDLER);
	}
	
	//phamtomProxyの設定はここに集中する
	private void doCommand(ParameterParser parameter){
		String cmd=parameter.getParameter("command");
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
			if("".equals(pacUrl)){
				pacUrl=null;
			}
			String proxyServer=parameter.getParameter("proxyServer");
			if("".equals(proxyServer)){
				proxyServer=null;
			}
			String sslProxyServer=parameter.getParameter("sslProxyServer");
			if("".equals(sslProxyServer)){
				sslProxyServer=null;
			}
			String exceptProxyDomains=parameter.getParameter("exceptProxyDomains");
			if("".equals(exceptProxyDomains)){
				exceptProxyDomains=null;
			}
			boolean result=config.updateProxyFinder(pacUrl,proxyServer,sslProxyServer,exceptProxyDomains);
			JSONObject json=new JSONObject();
			json.put("pacUrl", config.getProperty("pacUrl"));
			json.put("proxyServer", config.getProperty("proxyServer"));
			json.put("sslProxyServer", config.getProperty("sslProxyServer"));
			json.put("exceptProxyDomains", config.getProperty("exceptProxyDomains"));
			json.put("result", result);
			responseJson(json);
		}else if("setKeepAlive".equals(cmd)){
			String isWebKeepAlive=parameter.getParameter("isWebKeepAlive");
			String isProxyKeepAlive=parameter.getParameter("isProxyKeepAlive");
			String maxKeepAliveRequests=parameter.getParameter("maxKeepAliveRequests");
			String keepAliveTimeout=parameter.getParameter("keepAliveTimeout");
			String contentEncoding=parameter.getParameter("contentEncoding");
			String allowChunked=parameter.getParameter("allowChunked");
			config.setWebKeepAlive("true".equalsIgnoreCase(isWebKeepAlive));
			config.setProxyKeepAlive("true".equalsIgnoreCase(isProxyKeepAlive));
			config.setMaxKeepAliveRequests(Integer.parseInt(maxKeepAliveRequests));
			config.setKeepAliveTimeout(Integer.parseInt(keepAliveTimeout));
			config.setContentEncoding(contentEncoding);
			config.setAllowChaned("true".equalsIgnoreCase(allowChunked));
			responseJson(true);
		}else if("setHtml5".equals(cmd)){
			String websocketSpecs=parameter.getParameter("websocketSpecs");
			String webSocketMessageLimit=parameter.getParameter("webSocketMessageLimit");
			String webSocketPingInterval=parameter.getParameter("webSocketPingInterval");
			String isUseSessionStorage=parameter.getParameter("isUseSessionStorage");
			String isUseCrossDomain=parameter.getParameter("isUseCrossDomain");
			String isWebSocketLog=parameter.getParameter("isWebSocketLog");
			WsProtocol.setWebSocketSpecs(websocketSpecs);
			WsProtocol.setWebSocketMessageLimit(Integer.parseInt(webSocketMessageLimit));
			WsProtocol.setWebSocketPingInterval(Integer.parseInt(webSocketPingInterval));
			WsProtocol.setWebSocketLog(!("false".equalsIgnoreCase(isWebSocketLog)));
			config.setProperty("useSessionStorage", isUseSessionStorage);
			config.setProperty("useCrossDomain", isUseCrossDomain);
			responseJson(true);
		}else if("setFileCache".equals(cmd)){
			String isChache=parameter.getParameter("isChache");
			config.setUseFileCache("true".equalsIgnoreCase(isChache));
			responseJson(true);
		}else if("setSpdy".equals(cmd)){
			SpdyConfig spdyConfig=config.getSpsyConfig();
			String isSpdy3=parameter.getParameter("isSpdy3");
			String isSpdy2=parameter.getParameter("isSpdy2");
			String frameLimit=parameter.getParameter("spdyFrameLimit");
			String timeout=parameter.getParameter("spdyTimeout");
			StringBuffer protocol=new StringBuffer();
			if("true".equalsIgnoreCase(isSpdy3)){
				protocol.append(SpdyFrame.PROTOCOL_V3);
				protocol.append(",");
			}
			if("true".equalsIgnoreCase(isSpdy2)){
				protocol.append(SpdyFrame.PROTOCOL_V2);
				protocol.append(",");
			}
			protocol.append(SpdyFrame.PROTOCOL_HTTP_11);
			spdyConfig.setSpdyProtocols(protocol.toString());
			if(frameLimit!=null){
				spdyConfig.setSpdyFrameLimit(Long.parseLong(frameLimit));
			}
			if(timeout!=null){
				spdyConfig.setSpdyTimeout(Long.parseLong(timeout));
			}
			responseJson(true);
		}else if("setAuth".equals(cmd)){
			String scheme=parameter.getParameter("scheme");
			Authenticator authenticator=config.getAuthenticator();
			authenticator.setScheme(scheme);
			String sessionTimeout=parameter.getParameter("sessionTimeout");
			Authorizer authorizer=config.getAuthorizer();
			authorizer.setSessionTimeout(Long.parseLong(sessionTimeout));
			String authInputTimeout=parameter.getParameter("authInputTimeout");
			if(authInputTimeout!=null && Long.parseLong(authInputTimeout)>0){
				config.setProperty("authInputTimeout",authInputTimeout);
			}
			String authRedirectTimeout=parameter.getParameter("authRedirectTimeout");
			if(authRedirectTimeout!=null && Long.parseLong(authRedirectTimeout)>0){
				config.setProperty("authRedirectTimeout",authRedirectTimeout);
			}
			responseJson(true);
		}else if("setAuthFb".equals(cmd)){
			String useAuthFb=parameter.getParameter("useAuthFb");
			config.setProperty("useAuthFb",useAuthFb);
			String authFbAppId=parameter.getParameter("authFbAppId");
			config.setProperty("authFbAppId",authFbAppId);
			String authFbAppSecret=parameter.getParameter("authFbAppSecret");
			config.setProperty("authFbAppSecret",authFbAppSecret);
			responseJson(true);
		}else if("setAuthGoogle".equals(cmd)){
			String useAuthGoogle=parameter.getParameter("useAuthGoogle");
			config.setProperty("useAuthGoogle",useAuthGoogle);
			String authGoogleClientId=parameter.getParameter("authGoogleClientId");
			config.setProperty("authGoogleClientId",authGoogleClientId);
			String authGoogleClientSecret=parameter.getParameter("authGoogleClientSecret");
			config.setProperty("authGoogleClientSecret",authGoogleClientSecret);
			responseJson(true);
		}else if("setAuthTwitter".equals(cmd)){
			String useAuthTwitter=parameter.getParameter("useAuthTwitter");
			config.setProperty("useAuthTwitter",useAuthTwitter);
			String authTwitterConsumerKey=parameter.getParameter("authTwitterConsumerKey");
			config.setProperty("authTwitterConsumerKey",authTwitterConsumerKey);
			String authTwitterConsumerSecret=parameter.getParameter("authTwitterConsumerSecret");
			config.setProperty("authTwitterConsumerSecret",authTwitterConsumerSecret);
			responseJson(true);
		}else if("setAuthOpenid".equals(cmd)){
			String useAuthOpenid=parameter.getParameter("useAuthOpenid");
			config.setProperty("useAuthOpenid",useAuthOpenid);
			String useAuthDirectOpenid=parameter.getParameter("useAuthDirectOpenid");
			config.setProperty("useAuthDirectOpenid",useAuthDirectOpenid);
			String authOpenidDef=parameter.getParameter("authOpenidDef");
			Authenticator authenticator=config.getAuthenticator();
			authenticator.setupOpenidDef(authOpenidDef);
			config.setProperty("authOpenidDef",authOpenidDef);
			responseJson(true);
		}else if("setBroadcaster".equals(cmd)){
			String interval=parameter.getParameter("interval");
			config.setProperty("broardcastInterval", interval);
			responseJson(true);
		}else if("setSelfDomain".equals(cmd)){
			String domain=parameter.getParameter("domain");
			config.setProperty(Config.SELF_DOMAIN, domain);
//			String port=config.getString(Config.SELF_PORT);
//			config.setProperty(Config.SELF_URL, "http://" + domain +":"+port);
			responseJson(true);
		}else if("getStastics".equals(cmd)){
			responseJson(JSONObject.fromObject(config.getStasticsObject()));
		}else if("debugTrace".equals(cmd)){
			String debugTrace=parameter.getParameter("debugTrace");
			config.setDebugTrace("true".equalsIgnoreCase(debugTrace));
			responseJson(config.isDebugTrace());
		}else if("logdownload".equals(cmd)){
			//ph.log,accesslogダウンロード
			String logType=parameter.getParameter("logType");
			String logName;
			if("accesslog".equals(logType)){
				logName="accesslog.log";
			}else if("phlog".equals(logType)){
				logName="ph.log";
			}else{
				logger.error("logtype error."+logType);
				completeResponse("500");
				return;
			}
			String logNumber=parameter.getParameter("logNumber");
			if(!"0".equals(logNumber)){
				logName=logName +"."+logNumber;
			}
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION,"attachment; filename=\"" + logName + "\"");
			setRequestAttribute(ATTRIBUTE_RESPONSE_FILE,new File(config.getPhantomHome(),"/log/" + logName));
			setRequestAttribute(ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE,ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE);
			forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
		}else if("certificate".equals(cmd)){
			String cerFileName=parameter.getParameter("cerFileName");
			File serFile=config.getSslContextPool().getCerFile(cerFileName);
			if(!serFile.exists()){
				completeResponse("404");
				return;
			}
			setRequestAttribute(ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION,"attachment; filename=\"" + serFile.getName()+ "\"");
			setRequestAttribute(ATTRIBUTE_RESPONSE_FILE,serFile);
			forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
		}else if("checkPool".equals(cmd)){
			System.gc();
			ChannelContext.dumpChannelContexts();
			PoolManager.dump();
			completeResponse("204");
		}else if("terminate".equals(cmd)){
			String isRestartValue=parameter.getParameter("isRestart");
			String isCleanupValue=parameter.getParameter("isCleanup");
			String javaHeapSizeValue=parameter.getParameter("javaHeapSize");
			/* logout時にrestartするように設定する.すぐにterminateすると画面が崩れる場合がある */
			boolean isRestart="true".equals(isRestartValue);
			boolean isCleanup="true".equals(isCleanupValue);
			int javaHeapSize;
			if(javaHeapSizeValue==null){
				javaHeapSize=0;
			}else{
				javaHeapSize=Integer.parseInt(javaHeapSizeValue);
			}
			LogoutEvent logoutEvent=new LogoutEvent(){
				boolean isRestart;
				boolean isCleanup;
				int javaHeapSize;
				public LogoutEvent init(boolean isRestart,boolean isCleanup,int javaHeapSize){
					this.isRestart=isRestart;
					this.isCleanup=isCleanup;
					this.javaHeapSize=javaHeapSize;
					return this;
				}
				@Override
				public void onLogout() {
					if(!isRestart){
						Main.terminate();
					}else{
						Main.terminate(true,isCleanup,javaHeapSize);
					}
				}}.init(isRestart, isCleanup, javaHeapSize);
			boolean addEvent=getAuthSession().addLogoutEvent(logoutEvent);
			responseJson(addEvent);
		}else if("replayDelete".equals(cmd)){
			replayDelete();
			completeResponse("204");
		}else if("dumpStore".equals(cmd)){
			try {
				StoreManager.dumpStore();
				completeResponse("204");
			} catch (IOException e) {
				logger.warn("fail to dumpStore",e);
				completeResponse("500");
			}
		}else if("forceDown".equals(cmd)){
			//System.exit(777);
		}else if("outOfMemory".equals(cmd)){
			/*
			byte[][] dummy=new byte[4096][];
			int i=0;
			try {
				for(i=0;i<dummy.length;i++){
					dummy[i]=new byte[1024*1024];
				}
			} catch (OutOfMemoryError e) {
				System.out.println("outOfMemoryError. size="+i +"m");
				try {
					Thread.sleep(10000000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			*/
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
		String token=getAuthSession().getToken();
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
		ParameterParser parameter=getParameterParser();
		if(path.endsWith(".vsp")||path.endsWith(".vsf")){
//			mapping.setDesitinationFile(config.getAdminDocumentRoot());
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			return;
		}else if(path.startsWith("/storeDownload")){
			forwardHandler(Mapping.STORE_HANDLER);
			return;
		}else if(path.startsWith("/viewAccessLog")){
			viewAccessLog(parameter);
			return;
		}
		if(!checkToken(parameter)){
			//tokenが合致しない。POSTならエラー、GETなら静的コンテンツとして処理
			if(requestHeader.getMethod().equalsIgnoreCase(HeaderParser.POST_METHOD)){
				logger.error("CSRF check error.path:"+path +":cid:"+getChannelId());
				completeResponse("403","token error.");
			}else{
				mapping.setOption(Mapping.OPTION_VELOCITY_USE,false);
				mapping.setDesitinationFile(config.getAdminDocumentRoot());
				forwardHandler(Mapping.FILE_SYSTEM_HANDLER);
			}
			return;
		}
		if( "/admin".equals(path)){
			doCommand(parameter);
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
