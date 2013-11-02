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
import naru.aweb.core.RealHost;
import naru.aweb.handler.KeepAliveContext;
import naru.aweb.handler.ServerBaseHandler;
import naru.aweb.http.RequestContext;
import naru.aweb.util.HeaderParser;
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
	private RealHost realHost;
	private ServerParser acceptServer;
	private boolean isProxy;//CONNECTの後にSPDYが始まったか否か
	private short version;/* protocolのversion */
	
	public boolean onHandshaked(String protocol,boolean isProxy) {
		logger.debug("#handshaked.cid:" + getChannelId() +":"+protocol);
		this.isProxy=isProxy;
		if(SpdyFrame.PROTOCOL_V2.equals(protocol)){
			version=SpdyFrame.VERSION_V2;
		}else if(SpdyFrame.PROTOCOL_V3.equals(protocol)){
			version=SpdyFrame.VERSION_V3;
		}else if(SpdyFrame.PROTOCOL_V31.equals(protocol)){
			version=SpdyFrame.VERSION_V31;
		}else{
			throw new IllegalArgumentException(protocol);
		}
		frame.init(version,spdyConfig.getSpdyFrameLimit());
		inFrameCount=new long[SpdyFrame.TYPE_WINDOW_UPDATE+1];
		outFrameCount=new long[SpdyFrame.TYPE_WINDOW_UPDATE+1];
		lastGoodStreamId=0;
		sendGoawayStatusCode=rcvGoawayStatusCode='*';
		readLength=writeLength=0;
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		realHost=keepAliveContext.getRealHost();
		acceptServer=keepAliveContext.getAcceptServer();
		
		/*　無条件でsetting frameを送る */
		sendSetting(SpdyFrame.FLAG_SETTINGS_PERSIST_VALUE,SpdyFrame.SETTINGS_MAX_CONCURRENT_STREAMS,100);
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
			long length=BuffersUtil.remaining(dataBuffer);
			if(session!=null){
				session.onReadPlain(dataBuffer,frame.isFin());
			}else{
				logger.error("illegal streamId:"+streamId);
				sendReset(streamId, SpdyFrame.RSTST_INVALID_STREAM);
			}
			sendWindowUpdate(streamId,(int)length);
			if(version==SpdyFrame.VERSION_V31){
				sendWindowUpdate(0,(int)length);
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
			ServerParser server=null;
			if(isProxy){//proxyの場合は、ヘッダからproxy先を見つける
				server=requestHeader.getServer();
			}
			acceptServer.ref();
			/* spdy固有のkeepAlive初期化 */
			keepAliveContext.setSpdyAcceptServer(acceptServer,realHost,server);
			logger.debug("url:" + requestHeader.getRequestUri());
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
		case SpdyFrame.TYPE_WINDOW_UPDATE:
			logger.debug("streamId:"+streamId+" deltaWindowSize:"+frame.getDeltaWindowSize());
			break;
		case SpdyFrame.TYPE_SETTINGS:
			logger.debug("setting:");
			break;
		case SpdyFrame.TYPE_HEADERS:
			logger.debug("headers:");
			break;
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
		if(sessions.size()==0){
			logger.debug("onReadTimeout nomal end.cid:"+getChannelId());
			sendGoaway(SpdyFrame.GOWST_OK);
		}else{
			logger.warn("onReadTimeout abnomal end.cid:"+getChannelId()+":"+sessions.size());
			sendGoaway(SpdyFrame.GOWST_INTERNAL_ERROR);
		}
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
	
	private void sendWindowUpdate(int streamId,int deltaWindowSize){
		outFrameCount[SpdyFrame.TYPE_WINDOW_UPDATE]++;
		ByteBuffer[] resetFrame=frame.buildWindowUpdate(streamId, deltaWindowSize);
		asyncWrite(null, resetFrame);
	}
	
	private void sendSetting(int flags,int id,int value){
		outFrameCount[SpdyFrame.TYPE_SETTINGS]++;
		ByteBuffer[] resetFrame=frame.buildSetting(flags, id, value);
		asyncWrite(null, resetFrame);
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
		if(responseHeader.getStatusCode()==null){
			logger.warn("spdyHandler stausCode unsetted.");
			responseHeader.setStatusCode("500");
		}
		if(responseHeader.getResHttpVersion()==null){
			logger.warn("spdyHandler httpVersion unsetted.");
			responseHeader.setResHttpVersion(HeaderParser.HTTP_VESION_11);
		}
		
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
		if(accessLog==null){
			super.onFinished();
			return;
		}
		accessLog.endProcess();
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		accessLog.setRealHost(keepAliveContext.getRealHost().getName());
		accessLog.setRawRead(getTotalReadLength());
		accessLog.setRawWrite(getTotalWriteLength());
		StringBuffer sb=new StringBuffer("in[");
		for(int i=0;i<inFrameCount.length;i++){
			if(i!=0){
				sb.append(' ');
			}
			sb.append(i);
			sb.append(':');
			sb.append(inFrameCount[i]);
		}
		sb.append("]out[");
		for(int i=0;i<outFrameCount.length;i++){
			if(i!=0){
				sb.append(' ');
			}
			sb.append(i);
			sb.append(':');
			sb.append(outFrameCount[i]);
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
		try{
			dispatchHandler.mappingHandler();
		}catch(Throwable t){//aplが例外した場合
			logger.error("spdy dispatch apl error.",t);
			session.onRst(-1);
		}
	}
	
	public int getSpdyVersion(){
		return version;
	}
	public int getSpdyPri(){
		return frame.getPriority();
	}

}
