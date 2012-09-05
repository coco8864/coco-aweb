package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.PoolManager;
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
	
	private SpdyFrame frame=new SpdyFrame();
	private Map<Integer,SpdySession> sessions=new HashMap<Integer,SpdySession>();

	public boolean onHandshaked(String protocol) {
		logger.debug("#handshaked.cid:" + getChannelId() +":"+protocol);
		frame.init(protocol);
		return false;//自力でasyncReadしたため
	}
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
//		BuffersUtil.hexDump("SPDY c->s row data",buffers);
		try {
			for(int i=0;i<buffers.length;i++){
				ByteBuffer buffer=buffers[i];
				while(buffer.hasRemaining()){
					if( frame.parse(buffer) ){
						doFrame();
					}
				}
				buffers[i]=null;
				PoolManager.poolBufferInstance(buffer);
			}
			setReadTimeout(60000);
			asyncRead(null);
		} catch (RuntimeException e) {
			logger.error("SpdyHandler parse error.",e);
			asyncClose(null);
		}finally{
			PoolManager.poolArrayInstance(buffers);
		}
	}
	
	private void doFrame(){
		int streamId=frame.getStreamId();
		SpdySession session=null;
		
		short type=frame.getType();
		logger.debug("SpdyHandler#doFrame cid:"+getChannelId()+":streamId:"+streamId+":" +type);
		switch(type){
		case SpdyFrame.TYPE_DATA_FRAME:
			ByteBuffer[] dataBuffer=frame.getDataBuffers();
			session=sessions.get(streamId);
			if(session!=null){
				session.onReadPlain(dataBuffer,frame.isFin());
			}else{
				logger.error("illegal streamId:"+streamId);
				PoolManager.poolBufferInstance(dataBuffer);
			}
			break;
		case SpdyFrame.TYPE_SYN_STREAM:
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
			session=sessions.get(streamId);
			int statusCode=frame.getStatusCode();
			if(session!=null){
				session.onRst(statusCode);
			}
			break;
		case SpdyFrame.TYPE_PING:
			int pingId=frame.getPingId();
			if(pingId%2==1){
				ByteBuffer[] pingFrame=frame.buildPIngFrame(pingId);
				asyncWrite(null, pingFrame);
			}
			break;
		case SpdyFrame.TYPE_HEADERS:
		case SpdyFrame.TYPE_GOAWAY:
		default:
		}
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
		asyncWrite(new SpdyCtx(spdySession,WRITE_CONTEXT_HEADER), synReplyFrame);
	}
	
	public void responseBody(SpdySession spdySession,boolean isFin,ByteBuffer[] body){
		char flags=0;
		if(isFin){
			flags=SpdyFrame.FLAG_FIN;
		}
		ByteBuffer[] dataFrame=frame.buildDataFrame(spdySession.getStreamId(), flags, body);
		asyncWrite(new SpdyCtx(spdySession,WRITE_CONTEXT_BODY), dataFrame);
	}
	
	public synchronized boolean asyncWrite(Object context,ByteBuffer[] buffers){
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
		synchronized(sessions){
			session=sessions.remove(streamId);
		}
		if(session!=null){
			session.unref();
		}
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:"+getChannelId());
		Object[] ss=sessions.values().toArray();
		for(Object session:ss){
			((SpdySession)session).onRst(SpdyFrame.RSTST_REFUSED_STREAM);
		}
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
		return frame.getPri();
	}
}
