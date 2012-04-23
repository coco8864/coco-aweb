package naru.aweb.wsq;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.pool.PoolBase;


public class BlobEnvelope extends PoolBase implements AsyncBuffer {

	public boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,
			Object userContext) {
		// TODO Auto-generated method stub
		return false;
	}

	public long bufferLength() {
		// TODO Auto-generated method stub
		return 0;
	}

}
