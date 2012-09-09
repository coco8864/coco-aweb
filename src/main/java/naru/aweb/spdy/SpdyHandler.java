package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.core.DispatchHandler;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.RequestContext;
import naru.aweb.util.ServerParser;

/**
 * @author Naru
 *
 */
public class SpdyHandler extends ServerBaseHandler {
	private static Logger logger=Logger.getLogger(SpdyHandler.class);
	private static SpdyConfig spdyConfig=Config.getConfig().getSpsyConfig();
	
	private SpdyFrame frame=new SpdyFrame();
	private Map<Integer,SpdySession> sessions=Collections.synchronizedMap(new HashMap<Integer,SpdySession>());
	private long inFrameCount[];
	private long outFrameCount[];
	private int lastGoodStreamId;
	private char sendGoawayStatusCode;
	private char rcvGoawayStatusCode;
	private long readLength;
	private long writeLength;
	
	
	public boolean onHandshaked(String protocol) {
		logger.debug("#handshaked.cid:" + getChannelId() +":"+protocol);
		frame.init(protocol,spdyConfig.getSpdyFrameLimit());
		inFrameCount=new long[SpdyFrame.TYPE_WINDOW_UPDATE+1];
		outFrameCount=new long[SpdyFrame.TYPE_WINDOW_UPDATE+1];
		lastGoodStreamId=0;
		sendGoawayStatusCode=rcvGoawayStatusCode='*';
		readLength=writeLength=0;
		return false;//自力でasyncReadしたため
	}
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
//		BuffersUtil.hexDump("SPDY c->s row data",buffers);
		readLength+=BuffersUtil.remaining(buffers);
		try {
			for(int i=0;i<buffers.length;i++){
				ByteBuffer buffer=buffers[i];
				while(buffer.hasRemaining()){
					if( frame.parse(buffer) ){
						doFrame();
						frame.prepareNext();
					}
				}
				buffers[i]=null;
				PoolManager.poolBufferInstance(buffer);
			}
			setReadTimeout(spdyConfig.getSpdyTimeout());
			asyncRead(null);
		} catch (RuntimeException e) {
			logger.error("SpdyHandler parse error.",e);
			asyncClose(null);
		}finally{
			PoolManager.poolArrayInstance(buffers);
		}
	}
	
	private void doFrame(){
		if(frame.isError()){
			sendGoaway(SpdyFrame.GOWST_PROTOCOL_ERROR);
			return;
		}
		int streamId=frame.getStreamId();
		SpdySession session=null;
		if(streamId>0){
			session=sessions.get(streamId);
		}
		short type=frame.getType();
		inFrameCount[type]++;//統計情報
		logger.debug("SpdyHandler#doFrame cid:"+getChannelId()+":streamId:"+streamId+":" +type);
		switch(type){
		case SpdyFrame.TYPE_DATA_FRAME:
			ByteBuffer[] dataBuffer=frame.getDataBuffers();
			if(session!=null){
				session.onReadPlain(dataBuffer,frame.isFin());
			}else{
				logger.error("illegal streamId:"+streamId);
				sendReset(streamId, SpdyFrame.RSTST_INVALID_STREAM);
			}
			break;
		case SpdyFrame.TYPE_SYN_STREAM:
			if(session!=null){
				logger.error("aleady exist streamId:"+streamId);
				sendReset(streamId, SpdyFrame.RSTST_STREAM_IN_USE);
				break;
			}
			lastGoodStreamId=streamId;
			//KeepAliveContext作って
			KeepAliveContext keepAliveContext=(KeepAliveContext)PoolManager.getInstance(KeepAliveContext.class);
			//KeepAliveContextからRequestContext取って
			RequestContext requestContext=keepAliveContext.getRequestContext();
			//RequestContextからrequestHeader取って
			HeaderParser requestHeader=requestContext.getRequestHeader();
			//ヘッダの内容を設定、ここにSpdyのVLが関係してくるので、SpdyFrameの中で実行
			frame.setupHeader(requestHeader);
			ServerParser server=requestHeader.getServer();
			server.ref();
			keepAliveContext.setAcceptServer(server);
			logger.info("url:" + requestHeader.getRequestUri());
			//KeepAliveContextからSpdySessionを作る
			session=SpdySession.create(this, streamId, keepAliveContext,frame.isFin());
			sessions.put(streamId, session);
			mappingHandler(session);
			break;
		case SpdyFrame.TYPE_RST_STREAM:
			int statusCode=frame.getStatusCode();
			if(session!=null){
				session.onRst(statusCode);
			}
			break;
		case SpdyFrame.TYPE_PING:
			int pingId=frame.getPingId();
			if(pingId%2==1){
				sendPing(pingId);
			}
			break;
		case SpdyFrame.TYPE_GOAWAY:
			statusCode=frame.getStatusCode();
			if(statusCode<16){
				rcvGoawayStatusCode=Integer.toHexString(statusCode).charAt(0);
			}else{
				rcvGoawayStatusCode='$';
			}
			resetAll();
			asyncClose(null);
			break;
		case SpdyFrame.TYPE_SETTINGS:
		case SpdyFrame.TYPE_HEADERS:
		default:
		}
	}
	
	@Override
	public void onFailure(Object userContext, Throwable t) {
		logger.warn("onFailure.cid:"+getChannelId(),t);
		sendGoaway(SpdyFrame.GOWST_INTERNAL_ERROR);
		super.onFailure(userContext, t);
	}

	@Override
	public void onReadTimeout(Object userContext) {
		logger.warn("onReadTimeout.cid:"+getChannelId());
		sendGoaway(SpdyFrame.GOWST_INTERNAL_ERROR);
		super.onReadTimeout(userContext);
	}
	
	private void sendGoaway(int statusCode){
		if(statusCode<16){
			sendGoawayStatusCode=Integer.toHexString(statusCode).charAt(0);
		}else{
			sendGoawayStatusCode='$';
		}
		outFrameCount[SpdyFrame.TYPE_GOAWAY]++;
		ByteBuffer[] resetFrame=frame.buildGoaway(lastGoodStreamId, statusCode);
		asyncWrite(null, resetFrame);
	}
	
	
	private void sendReset(int streamId,int statusCode){
		outFrameCount[SpdyFrame.TYPE_RST_STREAM]++;
		ByteBuffer[] resetFrame=frame.buildRstStream(streamId, statusCode);
		asyncWrite(null, resetFrame);
	}
	
	private void sendPing(int pingId){
		outFrameCount[SpdyFrame.TYPE_PING]++;
		ByteBuffer[] pingFrame=frame.buildPIngFrame(pingId);
		asyncWrite(null, pingFrame);
	}
	
	private static final String WRITE_CONTEXT_BODY = "writeContextBody";
	private static final String WRITE_CONTEXT_HEADER = "writeContextHeader";
	
	private static class SpdyCtx{
		SpdySession spdySession;
		Object ctx;
		SpdyCtx(SpdySession spdySession,String ctx){
			this.spdySession=spdySession;
			this.ctx=ctx;
		}
	}
	
	public void responseHeader(SpdySession spdySession,boolean isFin,HeaderParser responseHeader){
		char flags=0;
		if(isFin){
			flags=SpdyFrame.FLAG_FIN;
		}
		ByteBuffer[] synReplyFrame=frame.buildSynReply(spdySession.getStreamId(),flags,responseHeader);
		outFrameCount[SpdyFrame.TYPE_SYN_REPLY]++;
		asyncWrite(new SpdyCtx(spdySession,WRITE_CONTEXT_HEADER), synReplyFrame);
	}
	
	public void responseBody(SpdySession spdySession,boolean isFin,ByteBuffer[] body){
		char flags=0;
		if(isFin){
			flags=SpdyFrame.FLAG_FIN;
		}
		ByteBuffer[] dataFrame=frame.buildDataFrame(spdySession.getStreamId(), flags, body);
		outFrameCount[SpdyFrame.TYPE_DATA_FRAME]++;
		asyncWrite(new SpdyCtx(spdySession,WRITE_CONTEXT_BODY), dataFrame);
	}
	
	/* 同期するためにオーバライド,けどそもそもsynchronizedがいい */
	public synchronized boolean asyncWrite(Object context,ByteBuffer[] buffers){
		writeLength+=BuffersUtil.remaining(buffers);
		return super.asyncWrite(context,buffers);
	}
	
	@Override
	public void onWrittenPlain(Object userContext) {
		if(userContext==null){//pingの場合
			return;
		}
		SpdyCtx spdyCtx=(SpdyCtx)userContext;
		SpdySession session=spdyCtx.spdySession;
		if(spdyCtx.ctx==WRITE_CONTEXT_HEADER){
			session.onWrittenHeader();
		}else if(spdyCtx.ctx==WRITE_CONTEXT_BODY){
			session.onWrittenBody();
		}
	}
	
	//SpdySessionが自分の終了を通知してくる
	void endOfSession(int streamId){
		SpdySession session;
		session=sessions.remove(streamId);
		if(session!=null){
			session.unref();
		}
	}
	
	private void resetAll(){
		Object[] ss=sessions.values().toArray();
		for(Object session:ss){
			((SpdySession)session).onRst(SpdyFrame.RSTST_REFUSED_STREAM);
		}
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:"+getChannelId());
		resetAll();
		AccessLog accessLog=getAccessLog();
		accessLog.endProcess();
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		accessLog.setRealHost(keepAliveContext.getRealHost().getName());
		accessLog.setRawRead(getTotalReadLength());
		accessLog.setRawWrite(getTotalWriteLength());
		StringBuffer sb=new StringBuffer("in[");
		for(int i=0;i<inFrameCount.length;i++){
			sb.append(i);
			sb.append(':');
			sb.append(inFrameCount[i]);
			sb.append(' ');
		}
		sb.append("]out[");
		for(int i=0;i<outFrameCount.length;i++){
			sb.append(i);
			sb.append(':');
			sb.append(outFrameCount[i]);
			sb.append(' ');
		}
		sb.append("]lastGoodStreamId:");
		sb.append(lastGoodStreamId);
		accessLog.setRequestLine(sb.toString());
		sb.setLength(0);
		sb.append(sendGoawayStatusCode);
		sb.append('_');
		sb.append(rcvGoawayStatusCode);
		accessLog.setStatusCode(sb.toString());
		accessLog.setResponseHeaderLength(readLength);
		accessLog.setRequestHeaderLength(writeLength);
		super.onFinished();
	}
	
	private void mappingHandler(SpdySession session) {
		DispatchHandler dispatchHandler = (DispatchHandler) allocChaildHandler(DispatchHandler.class);
		if (dispatchHandler == null) {
			logger.warn("fail to forwardHandler:cid:" + getChannelId() + ":" + this);
			return;
		}
		dispatchHandler.setKeepAliveContext(session.getKeepAliveContext());
		session.setServerHandler(dispatchHandler);
		dispatchHandler.getRequestContext().setAttribute(ATTRIBUTE_SPDY_SESSION, session);
		dispatchHandler.mappingHandler();
	}
	
	public int getSpdyVersion(){
		return frame.getVersion();
	}
	public int getSpdyPri(){
		return frame.getPriority();
	}

}
