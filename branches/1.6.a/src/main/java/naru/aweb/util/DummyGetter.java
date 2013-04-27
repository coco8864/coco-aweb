package naru.aweb.util;

import java.nio.ByteBuffer;

import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;

public class DummyGetter implements BufferGetter {

	@Override
	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		System.out.println("DummyGetter onBuffer."+BuffersUtil.remaining(buffers));
		return true;
	}

	@Override
	public void onBufferEnd(Object userContext) {
		System.out.println("DummyGetter onBufferEnd.");
	}

	@Override
	public void onBufferFailure(Object userContext, Throwable failure) {
		System.out.println("DummyGetter onBufferFailure.");
		failure.printStackTrace();
	}

}
