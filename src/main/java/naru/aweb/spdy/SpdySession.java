package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.ServerParser;

public class SpdySession extends PoolBase{
	private SpdyHandler spdyHandler;
	private int streamId;
	private WebServerHandler webserverHandler;
	private boolean isOutputClose=false;
	private boolean isInputClose=false;
	private KeepAliveContext keepAliveContext;
	private Map attribute=new HashMap();//handlerÇ…ïtêèÇ∑ÇÈëÆê´
	
//	private HeaderParser requestHeader;
	
	
	public static SpdySession create(SpdyHandler spdyHandler,int streamId,HeaderParser parseHeader){
		SpdySession session=(SpdySession)PoolManager.getInstance(SpdySession.class);
		session.attribute.clear();
		session.spdyHandler=spdyHandler;
		session.streamId=streamId;
		session.keepAliveContext=(KeepAliveContext)PoolManager.getInstance(KeepAliveContext.class);
		HeaderParser requestHeader=session.getRequestHeader();
		requestHeader.setMethod(parseHeader.getHeader("method"));
		String url=parseHeader.getHeader("url");
		//String scheme=parseHeader.getHeader("scheme");//httpsÇÃÇÕÇ∏
		requestHeader.parseUri(url);
		requestHeader.setReqHttpVersion(parseHeader.getHeader("version"));
		requestHeader.setAllHeaders(parseHeader);
		requestHeader.removeHeader("method");
		requestHeader.removeHeader("url");
		requestHeader.removeHeader("scheme");
		requestHeader.removeHeader("version");
		requestHeader.setServer(parseHeader.getHeader(HeaderParser.HOST_HEADER), 443);
		//session.getRequestContext().getAccessLog();
		parseHeader.unref();
		session.keepAliveContext.setAcceptServer(requestHeader.getServer());
		return session;
	}
	
	//SpdyHandlerë§Ç©ÇÁåƒÇ—èoÇ≥ÇÍÇÈ
	public void onReadPlain(ByteBuffer[] buffers){
		webserverHandler.onReadPlain(null,buffers);
	}
	public void onWrittenBody(){
		webserverHandler.onWrittenBody();
//		webserverHandler.onWrittenPlain(userContext);
	}
	
	//webserverHandlerë§Ç©ÇÁåƒÇ—èoÇ≥ÇÍÇÈ
	public void responseHeader(boolean isLast,HeaderParser responseHeader){
		if(isOutputClose){
			return;
		}else if(isLast){
			isOutputClose=true;
		}
		spdyHandler.responseHeader(this, isLast,responseHeader);
	}
	
	public void responseBody(boolean isLast,ByteBuffer[] buffers){
		if(isOutputClose){
			PoolManager.poolBufferInstance(buffers);
			return;
		}else if(isLast){
			isOutputClose=true;
		}
		spdyHandler.responseBody(this, isLast,buffers);
	}

	public HeaderParser getRequestHeader() {
		return getRequestContext().getRequestHeader();
	}
	
	public KeepAliveContext getKeepAliveContext() {
		return keepAliveContext;
	}

	public RequestContext getRequestContext(){
		RequestContext requestContext=keepAliveContext.getRequestContext();
		return requestContext;
	}
	
	public ServerParser getAcceptServer(){
		return keepAliveContext.getAcceptServer();
	}

	public int getStreamId() {
		return streamId;
	}

	public WebServerHandler getWebserverHandler() {
		return webserverHandler;
	}

	public void setWebserverHandler(WebServerHandler webserverHandler) {
		this.webserverHandler = webserverHandler;
	}
	
	public SpdyHandler getSpdyHandler(){
		return spdyHandler;
	}
	
	public Object getAttribute(String name){
		return attribute.get(name);
	}
	
	public void setAttribute(String name, Object value) {
		attribute.put(name,value);
	}
}
