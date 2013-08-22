/**
 * 
 */
package naru.aweb.handler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import naru.async.pool.PoolManager;
import naru.async.store.Page;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientHandler;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.spdy.SpdySession;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

/**
 * reversProxy�Ƃ��ē���
 * 1)reverse�p�X�ƈ�v�����ꍇ
 * 2)sslProxy��peek����ꍇ
 * @author Naru
 *
 */
public class ProxyHandler extends  WebServerHandler implements WebClient{
	private static Logger logger=Logger.getLogger(ProxyHandler.class);
	private static Config config=Config.getConfig();
	private boolean isReplay=false;
	private boolean isTryAgain=false;//���X�|���X�w�b�_�����Ă�����x���N�G�X�g���Ȃ��������Ɣ��f�����ꍇ
	private boolean isReplace=false;//���N�G�X�g�w�b�_�����ăR���e���c��u��������Ɣ��f�����ꍇ,�u�������R���e���c�́Ainject�R���e���c�Ƃ��đ}��
//	private Object injectContext=null;
//	private PortalSession portalSession=null;
	private ProxyInjector injector;
	private Page bodyPage=new Page();//replay��,AccessLog DB�Ƀq�b�g���Ȃ������ꍇ�Ɏg�p
	private WebClientHandler webClientHandler;
	
	//response���̃w�b�_�ҏW�Ώ�
	private boolean isRequestEnd=false;//isRequestEnd�́A���ȏ�Ă΂�Ȃ��悤�ɂ���
	private boolean isResponseHeader=false;//���X�|���X����M���Ă��Ȃ���ΓƎ��ŃG���[��ԋp����
	private List<String> removeResponseHeaders=new ArrayList<String>();
	private Map<String,String> addResponseHeaders=new HashMap<String,String>();
	
	public void recycle() {
		isResponseHeader=isRequestEnd=isTryAgain=isReplay=isReplace=false;
		if(webClientHandler!=null){
			webClientHandler.endRequest();
		}
		webClientHandler=null;
		bodyPage.recycle();
		if(injector!=null){
			injector.term();
			injector=null;
		}
		addResponseHeaders.clear();
		removeResponseHeaders.clear();
		super.recycle();
	}

	//response�w�b�_�ҏW���\�b�h
	public void removeResponseHeader(String name){
		removeResponseHeaders.add(name);
	}
	public void addResponseHeader(String name,String value){
		addResponseHeaders.put(name, value);
	}
	
	private WebClientHandler getWebClientHandler(KeepAliveContext keepAliveContext,HeaderParser requestHeader){
		MappingResult mapping=getRequestMapping();
		ServerParser targetHostServer=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		requestHeader.setPath(path);
		return keepAliveContext.getWebClientHandler(mapping.isResolvedHttps(), targetHostServer.getHost(), targetHostServer.getPort());
	}
	
	private void calcResolveDigest(HeaderParser requestHeader){
		MappingResult mapping=getRequestMapping();
		Mapping.LogType logType=mapping.getLogType();
		if (logType!=Mapping.LogType.RESPONSE_TRACE&&logType!=Mapping.LogType.TRACE){
			return;
		}
		ServerParser targetHostServer=mapping.getResolveServer();
		String path=mapping.getResolvePath();
		String resolveDigest=AccessLog.calcResolveDigest(requestHeader.getMethod(),mapping.isResolvedHttps(),targetHostServer.toString(),path,requestHeader.getQuery());
		AccessLog accessLog=getAccessLog();
		if(accessLog!=null){
			accessLog.setResolveDigest(resolveDigest);
		}else{
			logger.warn("accessLog is null",new Throwable());
		}
	}
	
	private void editRequestHeader(HeaderParser requestHeader) {
		MappingResult mapping=getRequestMapping();
		Mapping.LogType logType=mapping.getLogType();
		/*
		 * ���������@�\ 2013/2/24
		if (logType==Mapping.LogType.RESPONSE_TRACE||logType==Mapping.LogType.TRACE){
			//TODO conf����폜���ׂ��w�b�_�������炤
			// ���X�|���X�̃g���[�X���̎悷��ꍇ�́A�u���E�U�L���b�V�����g�킹�Ȃ�
			requestHeader.removeHeader(HeaderParser.IF_MODIFIED_SINCE_HEADER);
			requestHeader.removeHeader(HeaderParser.IF_NONE_MATCH);
		}
		*/
		//reverse proxy�̏ꍇreferer������������
		//if(mapping.getMapping().getSourceType()!=Mapping.SourceType.WEB){
		//	return;
		//}
		String referer=requestHeader.getHeader(HeaderParser.REFERER);
		if(referer==null){
			return;
		}
		referer=mapping.reverseReferer(referer);
		requestHeader.setHeader(HeaderParser.REFERER,referer);
	}
	
	private boolean doProxy(){
		logger.debug("#startResponse.cid:"+getChannelId());
		HeaderParser requestHeader=getRequestHeader();
		
		boolean isCallerKeepAlive=false;//�o�b�N��keepAlive���Ȃ�
		long keepAliveTimeout=0;
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		if(keepAliveContext!=null){
			isCallerKeepAlive=keepAliveContext.isKeepAlive();
			keepAliveTimeout=keepAliveContext.getKeepAliveTimeout();
		}
		//resolveDigest�̌v�Z
		calcResolveDigest(requestHeader);
		webClientHandler=getWebClientHandler(keepAliveContext,requestHeader);
		//�F�؃w�b�_��IF_MODIFIED_SINCE_HEADER�̒���
		editRequestHeader(requestHeader);
		
		long connectTimeout=config.getConnectTimeout();
		boolean rc=webClientHandler.startRequest(this, null,connectTimeout,requestHeader, isCallerKeepAlive, keepAliveTimeout);
		if(rc==false){
			completeResponse("500","fail to request backserver");
			webClientHandler=null;
			return false;
		}
		logger.debug("client cid:"+getChannelId() +" server cid:"+webClientHandler.getChannelId());
		webClientHandler.setReadTimeout(getReadTimeout());
		webClientHandler.setWriteTimeout(getWriteTimeout());
		return true;
	}
	
	//�u���E�U����̃��N�G�X�g�w�b�_�͎�M���������A�������X�|�����Ă�������...���ă��\�b�h
	public void startResponse(){
		MappingResult mapping=getRequestMapping();
		if(mapping.getBooleanOption(Mapping.OPTION_FILTER)){
			FilterHelper helper=config.getFilterHelper();
			if(helper.doFilter(this)==false){
				logger.debug("filter blocked");
				return;
			}
		}
		
		InjectionHelper helper=config.getInjectionHelper();
		injector=helper.getInjector((String)mapping.getOption(Mapping.OPTION_INJECTOR));
		if(injector!=null){
			injector.init(this);
			injector.onRequestHeader(getRequestHeader());
		}
//		if(Boolean.TRUE.equals(mapping.getOption("portal"))){
//			portalSession=PortalSession.getPortalSession(this);
//			portalSession.onRequestHeader(this);
//		}
		if(mapping.getBooleanOption(Mapping.OPTION_REPLAY)){
			isReplay=true;
		}else{
			if(doProxy()==false){
				return;//proxy�Ɏ��s����
			}
		}
		startParseRequestBody();
	}
	
	public void requestBody(ByteBuffer[] buffers) {
		if(isReplay){
			bodyPage.putBuffer(PoolManager.duplicateBuffers(buffers), true);
			super.requestBody(buffers);//�K�v�Ȃ��H
			return;
		}
		webClientHandler.requestBody(buffers);
	}
	
	/**
	 * body�����ׂēǂݍ���ł���Ăяo�����,
	 * replay�̏ꍇ�A�������J�n����
	 * 
	 */
	public void startResponseReqBody(){
		if(!isReplay){
			return;
		}
		ReplayHelper helper=config.getReplayHelper();
		ByteBuffer[] body=bodyPage.getBuffer();
		if(helper.doReplay(this,body)){
			return;//replay�ł���,body�͏����Ă���
		}
		if(doProxy()==false){
			return;//proxy���s
		}
		if(body!=null){
			webClientHandler.requestBody(body);
		}
	}
	
	public void onTimeout(Object userContext){
		logger.debug("#timeout.cid:"+getChannelId());
		asyncClose(userContext);
		responseEnd();
	}
	
	public void onFailure(Object userContext,Throwable t){
		logger.debug("#failure.cid:"+getChannelId(),t);
		asyncClose(userContext);
		responseEnd();
	}

	public void onClosed(Object userContext) {
		logger.debug("#closed.cid:"+getChannelId());
		super.onClosed(userContext);
	}
	
	public void onFinished(){
		logger.debug("#finished.cid:"+getChannelId());
		responseEnd();
		super.onFinished();
	}

	public void onResponseBody(Object userContext,ByteBuffer[] buffer) {
		logger.debug("#responseBody.cid:"+getChannelId());
		if(isRequestEnd){
			return;
		}
		if(isTryAgain||isReplace){
			PoolManager.poolBufferInstance(buffer);
			return;//�Ď��s�����X�|���Xbody�͎̂Ă�
		}
		if(injector!=null){
			logger.debug("inject add contents cid:"+getChannelId());
			buffer=injector.onResponseBody(buffer);
		}
//		if(injectContext!=null && buffer!=null){
//			logger.debug("inject add contents cid:"+getChannelId());
//			InjectionHelper helper=config.getInjectionHelper();
//			helper.doInject(injectContext, this, buffer);
//			return;
//		}
		responseBody(buffer);
	}
	
	private void rewriteLocation(HeaderParser responseHeader){
		String location=responseHeader.getHeader(HeaderParser.LOCATION_HEADER);
		if(location==null){
			return;
		}
		MappingResult mapping=getRequestMapping();
		location=mapping.reverseResolve(location);
		responseHeader.setHeader(HeaderParser.LOCATION_HEADER, location);
	}
	
	public void onResponseHeader(Object userContext,HeaderParser responseHeader) {
		if(isRequestEnd){
			return;
		}
		isResponseHeader=true;
		logger.debug("#responseHeader.cid:"+getChannelId());
		long injectLength=0;
		boolean isInject=false;
		if(injector!=null){
			injector.onResponseHeader(responseHeader);
			isInject=injector.isInject();
			if(isInject){
				//chunk�Agzip����������body��ʒm����悤��webClientHandler�Ɉ˗��i�ʐM��̓���͊֌W���Ȃ��j
				webClientHandler.setReadableCallback(true);
				injectLength=injector.getInjectLength();
				logger.debug("inject cid:"+getChannelId()+":injectLength:"+injectLength+":ContentsLength:"+responseHeader.getContentLength());
			}
		}
		/*
		if(portalSession!=null){//Portal���������Ă���ꍇ�́A�����R���e���c�̏����́APortalSession�ɔC����
			injectContext=portalSession.onResponseHeaderAndInject(this,responseHeader);
		}else if(injectionFile!=null){//mapping����w�肳��Ă���ꍇ�́AProxyHandler����
			injectContext=helper.getInjectContext(injectionFile,responseHeader);
		}
//		long injectContentsLength=0;
		if(injectContext!=null){
			//chunk�Agzip����������body��ʒm����悤��webClientHandler�Ɉ˗��i�ʐM��̓���͊֌W���Ȃ��j
			webClientHandler.setReadableCallback(true);
			injectContentsLength=helper.getInjectContentsLength(injectContext);
			logger.debug("inject cid:"+getChannelId()+":injectContentsLength:"+injectContentsLength+":ContentsLength:"+responseHeader.getContentLength());
		}
		*/
		//���_�C���N�g�̏ꍇ�A�����ɋA���Ă���悤��location�w�b�_��ݒ�
		String statusCode=responseHeader.getStatusCode();
		if("301".equals(statusCode)||"302".equals(statusCode)||"303".equals(statusCode)){
			rewriteLocation(responseHeader);
		}
		//set-cookie�w�b�_��domein,secure�����������������悢
		setResponseHeader(responseHeader);
		
		//�w�b�_�폜����
		for(String removeHeader:removeResponseHeaders){
			removeHeader(removeHeader);
		}
		//�w�b�_�ǉ�����
		for(String name:addResponseHeaders.keySet()){
			String value=addResponseHeaders.get(name);
			setHeader(name, value);
		}
		if(isInject){
			String contentEncoding=responseHeader.getHeader(HeaderParser.CONTENT_ENCODING_HEADER);
			long contentLength=0;
			if(!isReplace){
				contentLength=responseHeader.getContentLength();
			}
			if(contentEncoding!=null){
				removeHeader(HeaderParser.CONTENT_ENCODING_HEADER);
				//���k���contentLength���ݒ肳��Ă���
				removeContentLength();
			}else if(contentLength>=0){
				setContentLength(contentLength+injectLength);
				logger.debug("inject change contentLength cid:"+getChannelId()+":contentLength:"+(contentLength+injectLength));
			}
			//onResponseBody�ɂ́Achunk���f�R�[�h����Ēʒm����邽��
			removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		}
	}
	
	private boolean requestEnd(){
		if(isRequestEnd){
			return false;
		}
		isRequestEnd=true;
		webClientHandler=null;
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		keepAliveContext.setWebClientHandler(null);
		return true;
	}
	
	public void onRequestEnd(Object userContext,int stat) {
		logger.debug("#webClientEnd.cid:"+getChannelId());
		if(requestEnd()==false){
			return;
		}
		if(isTryAgain){
			isTryAgain=false;
			startResponse();
			return;//�Ď��s�����X�|���Xbody�͎̂Ă�
		}
		//contents�ǉ�����
//		ByteBuffer[] addContents=addResponseContents.getBuffer();
		if(injector!=null){
			ByteBuffer[] buffers=injector.onResponseBody(null);
			if(buffers!=null){
				logger.debug("inject add contents last cid:"+getChannelId());
				responseBody(buffers);
			}
		}
//		if(injectContext!=null){
//			logger.debug("inject add contents cid:"+getChannelId());
//			InjectionHelper helper=config.getInjectionHelper();
//			helper.doInject(injectContext, this, null);
//		}
		if(isResponseHeader){//���X�|���X�w�b�_���m�肵�Ă��Ȃ�
			responseEnd();
		}else{
			completeResponse("500","no response");
		}
	}

	public void onRequestFailure(Object userContext,int stat,Throwable t) {
		logger.debug("#webClientFailure.cid:"+getChannelId()+":"+stat,t);
		if(requestEnd()==false){
			return;
		}
		if(isResponseHeader){
			responseEnd();
		}else{
			//header�����X�|���X���鎖�Ȃ��ُ�ƂȂ���
			completeResponse("500", "proxyHandler error.stat:"+stat);
		}
	}
	
	public void onWrittenRequestHeader(Object userContext) {
		//backServer�ւ�requestHeader�������ݒʒm�A���Ɏg��Ȃ�
	}
	public void onWrittenRequestBody(Object userContext) {
		//backServer�ւ�requestBody�������ݒʒm�A���Ɏg��Ȃ�
	}

	public void setTryAgain(boolean isTryAgain) {
		this.isTryAgain = isTryAgain;
	}
	public void setReplace(boolean isReplace) {
		this.isReplace = isReplace;
	}
	
	public void onWebConnected(Object userContext){
	}
	public void onWebHandshaked(Object userContext){
	}
	public void onWebProxyConnected(Object userContext) {
	}

}
