package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.ServerParser;

public class SpdySession extends PoolBase{
	private SpdyHandler spdyHandler;
	private int streamId;
	private ServerBaseHandler serverHandler;
	private boolean isOutputClose=false;
	private boolean isInputClose=false;
	private KeepAliveContext keepAliveContext;
//	private Map attribute=new HashMap();//handlerに付随する属性
	
	public static SpdySession create(SpdyHandler spdyHandler,int streamId,HeaderParser parseHeader,boolean isInputClose){
		SpdySession session=(SpdySession)PoolManager.getInstance(SpdySession.class);
//		session.attribute.clear();
		session.spdyHandler=spdyHandler;
		session.streamId=streamId;
		session.isInputClose=isInputClose;
		session.isOutputClose=false;
		session.keepAliveContext=(KeepAliveContext)PoolManager.getInstance(KeepAliveContext.class);
		HeaderParser requestHeader=session.getRequestHeader();
		requestHeader.setMethod(parseHeader.getHeader("method"));
		String url=parseHeader.getHeader("url");
		//String scheme=parseHeader.getHeader("scheme");//httpsのはず
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
		ServerParser server=requestHeader.getServer();
		server.ref();
		session.keepAliveContext.setAcceptServer(server);
		return session;
	}
	
	private void endOfSession(){
		spdyHandler.endOfSession(streamId);
		if(serverHandler!=null){
			serverHandler.onReadClosed(readContext);
			serverHandler.finishChildHandler();
			serverHandler=null;
		}
		/*keepAliveContext.unref();
		keepAliveContext=null;*/
	}
	
	//SpdyHandler側から呼び出される
	public void onReadPlain(ByteBuffer[] buffers,boolean isFin){
		if(isInputClose){
			return;//既にクローズされている
		}
		isInputClose=isFin;
		serverHandler.onReadPlain(readContext,buffers);
		readContext=null;
		if(this.isInputClose&&isOutputClose){
			endOfSession();
		}
	}
	
	public void onWrittenHeader(){
		if(this.isInputClose&&isOutputClose){
			endOfSession();
		}
	}
	
	public void onWrittenBody(){
		if(serverHandler!=null&&serverHandler instanceof WebServerHandler){
			((WebServerHandler)serverHandler).onWrittenBody();
		}
		if(this.isInputClose&&isOutputClose){
			endOfSession();
		}
	}
	
	public void onRst(int statusCode){
		endOfSession();
	}
	
	//webserverHandler側から呼び出される
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
	
	private Object readContext;
	public void asyncRead(Object context){
		this.readContext=context;
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

	public ServerBaseHandler getServerHandler() {
		return serverHandler;
	}

	public void setServerHandler(ServerBaseHandler serverHandler) {
		this.serverHandler = serverHandler;
	}
	
	public SpdyHandler getSpdyHandler(){
		return spdyHandler;
	}
	
	/*
	public Object getAttribute(String name){
		return attribute.get(name);
	}
	
	public void setAttribute(String name, Object value) {
		attribute.put(name,value);
	}
	*/
	
	public boolean isClosed(){
		return (isInputClose&&isOutputClose);
	}
}
