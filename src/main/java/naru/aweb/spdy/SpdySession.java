package naru.aweb.spdy;

import java.nio.ByteBuffer;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.ServerParser;

public class SpdySession extends PoolBase{
	private SpdyHandler spdyHandler;
	private int streamId;
	private WebServerHandler webserverHandler;
	private boolean isOutputClose=false;
	private boolean isInputClose=false;
	private KeepAliveContext keepAliveContext;
	
	public static SpdySession create(SpdyHandler spdyHandler,int streamId,HeaderParser requestHeader){
		SpdySession session=(SpdySession)PoolManager.getInstance(SpdySession.class);
		session.spdyHandler=spdyHandler;
		session.streamId=streamId;
//		webserverHandler.getLocalIp();
//		webserverHandler.getLocalPort();
		return session;
	}
	
	//SpdyHandlerë§Ç©ÇÁåƒÇ—èoÇ≥ÇÍÇÈ
	public void requestBody(ByteBuffer[] buffers){
		webserverHandler.requestBody(buffers);
	}
	public void onWrittenBody(){
		webserverHandler.onWrittenBody();
//		webserverHandler.onWrittenPlain(userContext);
	}
	
	//webserverHandlerë§Ç©ÇÁåƒÇ—èoÇ≥ÇÍÇÈ
	public void responseHeader(HeaderParser responseHeader){
		spdyHandler.responseHeader(this, responseHeader);
	}
	
	public void responseBody(boolean isLast,ByteBuffer[] buffers){
		if(isLast){
			isOutputClose=true;
		}
		spdyHandler.responseBody(this, isLast,buffers);
	}

	public KeepAliveContext getKeepAliveContext() {
		return keepAliveContext;
	}

	public void setKeepAliveContext(KeepAliveContext keepAliveContext) {
		this.keepAliveContext = keepAliveContext;
	}
	
	public ServerParser getAcceptServer(){
		return null;
	}

	public int getStreamId() {
		return streamId;
	}
	
}
