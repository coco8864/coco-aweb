package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.core.ServerBaseHandler;

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

	public boolean onHandshaked() {
		asyncRead(null);
		return false;//Ž©—Í‚ÅasyncRead‚µ‚½‚½‚ß
	}
	
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
//		BuffersUtil.hexDump("SPDY c->s row data",buffers);
		try {
			for(ByteBuffer buffer:buffers){
				if( frame.parse(buffer) ){
					doFrame();
					ByteBuffer[] dataBuffers=frame.getDataBuffers();
					BuffersUtil.hexDump("SPDY c->s dataBuffers",dataBuffers);
					frame.init();
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
		int streamId;
		ByteBuffer[] dataBuffer;
		if(!frame.isControle()){
			streamId=frame.getStreamId();
			dataBuffer=frame.getDataBuffers();
			return;
		}
		short type=frame.getType();
		switch(type){
		case SpdyFrame.TYPE_SYN_STREAM:
			streamId=frame.getIntFromData();
			int associatedToStreamId=frame.getIntFromData();
			short priAndSlot=frame.getShortFromData();
			dataBuffer=frame.getDataBuffers();
			BuffersUtil.hexDump("Name/value header block in", dataBuffer);
			
			Inflater decompresser = new Inflater();
		    for(ByteBuffer buf:dataBuffer){
			    decompresser.setInput(buf.array(), buf.position(), buf.remaining());
			    
			    while(true){
				    if(decompresser.needsDictionary()){
					    decompresser.setDictionary(DICTIONARY_V2);
				    }
//			    	ByteBuffer b1=PoolManager.getBufferInstance();
			    	byte[] a=new byte[10240];
			    	int length=0;
			    	try {
			    		length=decompresser.inflate(a);
					} catch (DataFormatException e) {
						e.printStackTrace();
					}
					BuffersUtil.hexDump("Name/value header block out",a,0,length);
			    	if(decompresser.needsInput()){
			    		break;
			    	}
			    }
		    }
			
			
//			reqGzipContext.putZipedBuffer(dataBuffer);
//			ByteBuffer[] plainBuffer=reqGzipContext.getPlainBuffer();
//			BuffersUtil.hexDump("Name/value header block out", plainBuffer);
			break;
		case SpdyFrame.TYPE_RST_STREAM:
			streamId=frame.getIntFromData();
			int statusCode=frame.getIntFromData();
			break;
		}
		
		
	}
		

	

}
