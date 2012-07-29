package naru.aweb.spdy;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.core.ServerBaseHandler;
import naru.aweb.http.HeaderParser;

/**
 * @author Naru
 *
 */
public class SpdyHandler extends ServerBaseHandler {
	private static Logger logger=Logger.getLogger(SpdyHandler.class);
	private SpdyFrame frame=new SpdyFrame();
	
	@Override
	public void recycle() {
		super.recycle();
	}

	public boolean onHandshaked(String protocol) {
		logger.debug("#handshaked.cid:" + getChannelId() +":"+protocol);
		frame.init(protocol);
//		asyncRead(null);
		return false;//Ž©—Í‚ÅasyncRead‚µ‚½‚½‚ß
	}
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
//		BuffersUtil.hexDump("SPDY c->s row data",buffers);
		try {
			for(ByteBuffer buffer:buffers){
				if( frame.parse(buffer) ){
					doFrame();
//					ByteBuffer[] dataBuffers=frame.getDataBuffers();
//					BuffersUtil.hexDump("SPDY c->s dataBuffers",dataBuffers);
					frame.prepareNext();
				}
			}
			PoolManager.poolArrayInstance(buffers);
//			if(frame.getPayloadLength()>getWebSocketMessageLimit()){
//				logger.debug("WsHybi10#doFrame too long frame.frame.getPayloadLength():"+frame.getPayloadLength());
//				sendClose(WsHybiFrame.CLOSE_MESSAGE_TOO_BIG,"too long frame");
//			}
			asyncRead(null);
		} catch (RuntimeException e) {
			logger.error("SpdyHandler parse error.",e);
			asyncClose(null);
		}
	}
	
	private void doFrame(){
		logger.debug("SpdyHandler#doFrame cid:"+getChannelId());
		int streamId=frame.getStreamId();
		ByteBuffer[] dataBuffer;
		HeaderParser header;
		int statusCode;
		if(!frame.isControle()){
			dataBuffer=frame.getDataBuffers();
			return;
		}
		short type=frame.getType();
		switch(type){
		case SpdyFrame.TYPE_SYN_STREAM:
			header=frame.getHeader();
			header.unref();
			HeaderParser response=(HeaderParser)PoolManager.getInstance(HeaderParser.class);
			
			response.setStatusCode("200");
			response.setResHttpVersion(HeaderParser.HTTP_VESION_11);
			response.setContentType("text/plain");
			ByteBuffer[] res=frame.buildSynReply(streamId, header);
			asyncWrite(null, res);
			ByteBuffer resBody=ByteBuffer.wrap("test OK".getBytes());
			res=frame.buildDataFrame(streamId, SpdyFrame.FLAG_FIN, BuffersUtil.toByteBufferArray(resBody));
			asyncWrite(null, res);
//			res=frame.buildRstStream(streamId, 200);
//			asyncWrite(null, res);
			break;
		case SpdyFrame.TYPE_RST_STREAM:
			statusCode=frame.getStatusCode();
			break;
		}
	}
		

	

}
