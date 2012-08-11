package naru.aweb.spdy;

import java.nio.ByteBuffer;

import naru.async.pool.PoolBase;
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
	
	public boolean init(SpdyHandler spdyHandler,int streamId,HeaderParser requestHeader){
		this.spdyHandler=spdyHandler;
		this.streamId=streamId;
		
		webserverHandler.getLocalIp();
		webserverHandler.getLocalPort();
		
		return true;
	}
	
	//SpdyHandler������Ăяo�����
	public void requestBody(ByteBuffer[] buffers){
		webserverHandler.requestBody(buffers);
	}
	public void onWrittenBody(){
		webserverHandler.onWrittenBody();
//		webserverHandler.onWrittenPlain(userContext);
	}
	
	//webserverHandler������Ăяo�����
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
