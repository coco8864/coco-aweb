package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.PoolManager;
import naru.aweb.auth.Authorizer;
import naru.aweb.config.Config;
import naru.aweb.core.DispatchHandler;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.http.HeaderParser;
import naru.aweb.mapping.Mapper;

/**
 * @author Naru
 *
 */
public class SpdyHandler extends ServerBaseHandler {
	private static Logger logger=Logger.getLogger(SpdyHandler.class);
	private static Config config = null;//Config.getConfig();
	private static Mapper mapper = null;//config.getMapper();
	private static Authorizer authorizer=null;//config.getAuthorizer();
	
	private static Config getConfig(){
		if(config==null){
			config=Config.getConfig();
		}
		return config;
	}
	private static Mapper getMapper(){
		if(mapper==null){
			mapper=getConfig().getMapper();
		}
		return mapper;
	}
	private static Authorizer getAuthorizer(){
		if(authorizer==null){
			authorizer=getConfig().getAuthorizer();
		}
		return authorizer;
	}
	
	private SpdyFrame frame=new SpdyFrame();
	private Map<Integer,SpdySession> sessions=new HashMap<Integer,SpdySession>();
	
	@Override
	public void recycle() {
		super.recycle();
	}

	public boolean onHandshaked(String protocol) {
		logger.debug("#handshaked.cid:" + getChannelId() +":"+protocol);
		frame.init(protocol);
		return false;//Ž©—Í‚ÅasyncRead‚µ‚½‚½‚ß
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
			HeaderParser requestHeader=frame.getHeader();
			logger.info("url:" + requestHeader.getHeader("url"));
			session=SpdySession.create(this, streamId, requestHeader,frame.isFin());
			
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
			PoolManager.poolBufferInstance(frame.getDataBuffers());
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
		if(userContext==null){//ping‚Ìê‡
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
	
	//SpdySession‚ªŽ©•ª‚ÌI—¹‚ð’Ê’m‚µ‚Ä‚­‚é
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
		dispatchHandler.setSpdySession(session);
		dispatchHandler.mappingHandler();
	}
}
