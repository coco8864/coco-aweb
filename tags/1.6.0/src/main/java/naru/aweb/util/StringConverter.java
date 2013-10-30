package naru.aweb.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;

public class StringConverter extends PoolBase implements BufferGetter{
	private long offset=0;
	private AsyncBuffer asyncBuffer;
	private CharsetDecoder charsetDecoder;
	private CharBuffer charBuffer;
	private Event event;
	
	public static StringConverter decode(Event event,AsyncBuffer asyncBuffer,String encode,int limit){
		StringConverter stringConverter=(StringConverter)PoolManager.getInstance(StringConverter.class);
		stringConverter.init(event,asyncBuffer, encode, limit);
		return stringConverter;
	}
	
	private void init(Event event,AsyncBuffer asyncBuffer,String encode,int limit){
		this.event=event;
		this.asyncBuffer=asyncBuffer;
		offset=0;
		charBuffer=CharBuffer.allocate(limit);
		Charset charset=Charset.forName(encode);
		charsetDecoder=charset.newDecoder();
		charsetDecoder.reset();
		asyncBuffer.asyncBuffer(this, offset, null);
	}
	
	private void term(boolean result){
		if(asyncBuffer!=null && asyncBuffer instanceof PoolBase){
			((PoolBase)asyncBuffer).unref();
			asyncBuffer=null;
		}
		if(result){
			charBuffer.flip();
			event.done(true, charBuffer.toString());
		}else{
			event.done(false,null);
		}
		unref();
	}

	@Override
	public boolean onBuffer(Object arg0, ByteBuffer[] buffers) {
		long length=asyncBuffer.bufferLength();
		for(ByteBuffer buffer:buffers){
			offset+=buffer.remaining();
			CoderResult coderResult=charsetDecoder.decode(buffer, charBuffer, (offset>=length));
/*			if(!coderResult.isUnderflow()){
				term(false);
				return false;
			}*/
		}
		PoolManager.poolBufferInstance(buffers);
		asyncBuffer.asyncBuffer(this, offset, null);
		return false;
	}

	@Override
	public void onBufferEnd(Object arg0) {
		charsetDecoder.flush(charBuffer);
		term(true);
	}

	@Override
	public void onBufferFailure(Object arg0, Throwable arg1) {
		term(false);
	}
}
