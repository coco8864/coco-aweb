package naru.aweb.spdy;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.aweb.handler.KeepAliveContext;
import naru.aweb.handler.ServerBaseHandler;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.ChunkContext;
import naru.aweb.http.RequestContext;
import naru.aweb.util.HeaderParser;

public class SpdySession extends PoolBase{
	private static Logger logger=Logger.getLogger(SpdySession.class);
	
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
		session.setSpdyHandler(spdyHandler);
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
		session.setKeepAliveContext(keepAliveContext);
		return session;
	}
	
	@Override
	public void recycle(){
		setSpdyHandler(null);
		setKeepAliveContext(null);
	}
	
	private void setKeepAliveContext(KeepAliveContext keepAliveContext) {
		if(keepAliveContext!=null){
			keepAliveContext.ref();
		}
		if(this.keepAliveContext!=null){
			this.keepAliveContext.unref();
		}
		this.keepAliveContext=keepAliveContext;
	}

	private void setSpdyHandler(SpdyHandler spdyHandler) {
		if(spdyHandler!=null){
			spdyHandler.ref();
		}
		if(this.spdyHandler!=null){
			this.spdyHandler.unref();
		}
		this.spdyHandler=spdyHandler;
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
		try{
			serverHandler.callbackReadPlain(readContext,buffers);
		}catch(Throwable t){//aplが例外した場合
			logger.error("spdy data dispatch apl error.",t);
			//アプリが異常したのでセション終了
			endOfSession();
			return;
		}
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
	
	private boolean isChunk=false;
	private ChunkContext chunkContext=new ChunkContext();
	
	//webserverHandler側から呼び出される
	public void responseHeader(boolean isLast,HeaderParser responseHeader){
		if(isOutputClose){
			return;
		}else if(isLast){
			isOutputClose=true;
		}
		//SPDYにTransferEncodingは使えない
		isChunk=false;
		String transferEncoding=responseHeader.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		if(HeaderParser.TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncoding)){
			responseHeader.removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
			isChunk=true;
			chunkContext.decodeInit(true, -1);
		}
		spdyHandler.responseHeader(this, isLast,responseHeader);
	}
	
	public void responseBody(boolean isLast,ByteBuffer[] buffers){
		if(isChunk){
			buffers=chunkContext.decodeChunk(buffers);
		}
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
		if(serverHandler!=null){
			serverHandler.ref();
		}
		if(this.serverHandler!=null){
			this.serverHandler.ref();
		}
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
	
	@Override
	public void ref(){
		super.ref();
		logger.debug("#+#.cid:"+getPoolId(),new Throwable());
	}
	@Override
	public boolean unref(){
		logger.debug("#-#.cid:"+getPoolId(),new Throwable());
		return super.unref();
	}
	
}
