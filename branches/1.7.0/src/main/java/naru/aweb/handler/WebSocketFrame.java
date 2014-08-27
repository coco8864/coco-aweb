package naru.aweb.handler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;

public class WebSocketFrame {
	public static byte PCODE_CONT = 0;
	public static byte PCODE_TEXT = 1;
	public static byte PCODE_BIN = 2;
	public static byte PCODE_PING = 10;
	public static byte PCODE_PONG = 10;
	public static byte PCODE_CLOSE = 10;

	private static final byte MASK_FIN = (byte) (0xf0);
	private static final byte MASK_PCODE = (byte) (0x0f);
	private static final byte MASK_MASK = (byte) (0x80);
	private static final byte MASK_LEN = (byte) (0x7f);

	private static ByteBuffer createFrame(boolean isFin, byte pcode,
			boolean isMask, int length, byte[] maskBytes) {
		ByteBuffer frame = PoolManager.getBufferInstance(16);
		if (isFin) {
			frame.put((byte) (MASK_FIN | pcode));
		} else {
			frame.put(pcode);
		}
		byte maskMask = 0;
		if (isMask) {
			maskMask = MASK_MASK;
		}
		if (length > 0xffff) {
			frame.put((byte) (maskMask | 0xff));
			frame.putInt(length);
		} else if (length > 125) {
			frame.put((byte) (maskMask | 0xfe));
			frame.putShort((short) length);
		} else {
			frame.put((byte) (maskMask | length));
		}
		if (isMask) {
			frame.put(maskBytes);
		}
		frame.flip();
		return frame;
	}

	private static int maskBuffer(int absPos, byte[] maskBytes,
			ByteBuffer buffer,int length) {
		int pos = buffer.position();
//		int limit = buffer.limit();
		byte[] array = buffer.array();
		for (int i=0; i < length; i++) {
			int mod = absPos & 0x3;
			array[pos+i] ^= maskBytes[mod];
			absPos++;
		}
		return absPos;
	}

	public static WebSocketFrame paseFrame(ByteBuffer buffer) {
		byte b1 = buffer.get();
		byte b2 = buffer.get();
		boolean isFin = (b1 & MASK_PCODE) != 0;
		byte pcode = (byte) (b1 & MASK_PCODE);
		boolean isMask = ((b2 & MASK_MASK) != 0);
		byte byteLen = (byte) (b2 & MASK_LEN);
		int length;
		if (byteLen == (byte) 0xff) {
			length = buffer.getInt();
		} else if (byteLen == (byte) 0xfe) {
			length = (int) buffer.getShort();
		} else {
			length = (int) byteLen;
		}
		byte[] maskBytes = null;
		if (isMask) {
			maskBytes = new byte[4];
			buffer.get(maskBytes);
		}
		return new WebSocketFrame(isFin, pcode, isMask, length, maskBytes);
	}

	public WebSocketFrame(boolean isFin, byte pcode, boolean isMask,
			int length, byte[] maskBytes) {
		this.isFin = isFin;
		this.pcode = pcode;
		this.isMask = isMask;
		this.length = length;
		if (isMask && maskBytes == null) {
			this.maskBytes = null;// TODO
		} else {
			this.maskBytes = maskBytes;
		}
		this.curPos=0;
	}

	boolean isFin;
	byte pcode;
	boolean isMask;
	int length;
	byte[] maskBytes;

	public ByteBuffer toFrameBuffer() {
		return WebSocketFrame.createFrame(isFin, pcode, isMask, length,
				maskBytes);
	}

	public int mask(int absPos, ByteBuffer buffer,int maskLength) {
		if (isMask) {
			return WebSocketFrame.maskBuffer(absPos, maskBytes, buffer,maskLength);
		} else {
			return (absPos + maskLength);
		}
	}
	
	public int mask(int absPos, ByteBuffer buffer) {
		return mask(absPos,buffer,buffer.remaining());
	}
	
	private int curPos;
	private List<ByteBuffer> nextFrameBuffers=new ArrayList<ByteBuffer>();
	
	public ByteBuffer decode(ByteBuffer buffer){
		int maskLength=buffer.remaining();
		int endPos=curPos + maskLength;
		if(endPos>length){
			maskLength=endPos-length;
		}
		if(maskLength==0){
			nextFrameBuffers.add(buffer);
			return null;
		}
		curPos=mask(curPos,buffer,maskLength);
		if(curPos<=length){
			return buffer;
		}
		ByteBuffer nextBuffer=PoolManager.duplicateBuffer(buffer);
		int pos=buffer.position();
		buffer.limit(pos+maskLength);
		nextBuffer.position(pos+maskLength);
		nextFrameBuffers.add(nextBuffer);
		return buffer;
	}
	
	public boolean isDecodeEnd(){
		return (curPos>=length);
	}
	
	public ByteBuffer[] getNextFrameBuffers(){
		int size=nextFrameBuffers.size();
		if(size==0){
			return null;
		}
		ByteBuffer[] result = BuffersUtil.newByteBufferArray(size);
		int i=0;
		for(ByteBuffer buffer:nextFrameBuffers){
			result[i]=buffer;
			i++;
		}
		return result;
	}

	public static ByteBuffer[] encode(ByteBuffer[] plainBuffers, boolean isFin,boolean isMask) {
		ByteBuffer[] encodeBuffers = BuffersUtil.newByteBufferArray(plainBuffers.length + 1);
		int length = (int) BuffersUtil.remaining(plainBuffers);
		WebSocketFrame frame = new WebSocketFrame(isFin, PCODE_TEXT, isMask,length, null);
		int absPos = 0;
		for (int i = 0; i < plainBuffers.length; i++) {
			absPos = frame.mask(absPos, plainBuffers[i]);
			encodeBuffers[i + 1] = plainBuffers[i];
		}
		PoolManager.poolArrayInstance(plainBuffers);
		encodeBuffers[0] = frame.toFrameBuffer();
		return encodeBuffers;
	}

	public static ByteBuffer[] encode(ByteBuffer[] plainBuffers) {
		return encode(plainBuffers, true, true);
	}
	
	public static ByteBuffer[] encode(ByteBuffer plainBuffer) {
		return encode(BuffersUtil.toByteBufferArray(plainBuffer), true, true);
	}
	
	public static ByteBuffer[] encode(String text) {
		return encode(ByteBuffer.wrap(text.getBytes()));
	}
	
}
