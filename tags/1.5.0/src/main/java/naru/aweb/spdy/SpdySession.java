package naru.aweb.spdy;

import java.nio.ByteBuffer;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.RequestContext;
import naru.aweb.http.WebServerHandler;

public class SpdySession extends PoolBase{
	private SpdyHandler spdyHandler;
	private int streamId;
	private ServerBaseHandler serverHandler;
	private boolean isOutputClose=false;
	private boolean isInputClose=false;
	private KeepAliveContext keepAliveContext;
	private Store responseBodyStore = null;
	private String sessionInfo;
	
	public static SpdySession create(SpdyHandler spdyHandler,int streamId,KeepAliveContext keepAliveContext,boolean isInputClose){
		SpdySession session=(SpdySession)PoolManager.getInstance(SpdySession.class);
		session.spdyHandler=spdyHandler;
		session.streamId=streamId;
		session.isInputClose=isInputClose;
		session.isOutputClose=false;
		StringBuilder sb=new StringBuilder();
		sb.append(spdyHandler.getSpdyVersion());
		sb.append('|');
		sb.append(spdyHandler.getChannelId());
		sb.append('|');
		sb.append(streamId);
		sb.append('|');
		sb.append(spdyHandler.getSpdyPri());
		session.sessionInfo=sb.toString();
		session.keepAliveContext=keepAliveContext;
		return session;
	}
	
	private void endOfSession(){
		spdyHandler.endOfSession(streamId);
		if(serverHandler!=null){
			serverHandler.onReadClosed(readContext);
			serverHandler.finishChildHandler();
			serverHandler=null;
		}
	}
	
	//SpdyHandler側から呼び出される
	public void onReadPlain(ByteBuffer[] buffers,boolean isFin){
		if(isInputClose){
			return;//既にクローズされている
		}
		isInputClose=isFin;
		//readTraceを取得するため、onReadPlainではなくcallbackReadPlainを呼ぶ
		serverHandler.callbackReadPlain(readContext,buffers);
		readContext=null;
		if(isInputClose&&isOutputClose){
			endOfSession();
		}
	}
	
	public void onWrittenHeader(){
		if(isInputClose&&isOutputClose){
			endOfSession();
		}
	}
	
	public void onWrittenBody(){
		if(serverHandler!=null&&serverHandler instanceof WebServerHandler){
			((WebServerHandler)serverHandler).onWrittenBody();
		}
		if(isInputClose&&isOutputClose){
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
		if(responseBodyStore!=null&&buffers!=null){
			responseBodyStore.putBuffer(PoolManager.duplicateBuffers(buffers));
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
	
	public void pushResponseBodyStore(Store responseBodyStore){
		this.responseBodyStore=responseBodyStore;
	}
	
	public Store popSesponseBodyStore(){
		Store responseBodyPeek=this.responseBodyStore;
		this.responseBodyStore=null;
		return responseBodyPeek;
	}
	
	public String spdyInfo(){
		return sessionInfo;
	}
	
}
