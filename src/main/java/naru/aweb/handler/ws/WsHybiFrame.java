package naru.aweb.handler.ws;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;

public class WsHybiFrame {
	private static Config config=Config.getConfig(); 
	private static Random random=config.getRandom("WsHibiFrame"+System.currentTimeMillis());
	
	public static final byte PCODE_CONTINUE = 0x00;
	public static final byte PCODE_TEXT = 0x01;
	public static final byte PCODE_BINARY = 0x02;
	public static final byte PCODE_CLOSE = 0x08;
	public static final byte PCODE_PING = 0x09;
	public static final byte PCODE_PONG = 0x0A;

	public static final short CLOSE_NORMAL=1000;
	public static final short CLOSE_GOING_AWAY=1001;
	public static final short CLOSE_PROTOCOL_ERROR=1002;
	public static final short CLOSE_UNSUPPORTED_DATA=1003;
	public static final short CLOSE_NO_STATUS_RCVD =1005;
	public static final short CLOSE_ABNORMAL_CLOSURE=1006;
	public static final short CLOSE_INVALID_FRAME=1007;
	public static final short CLOSE_POLICY_VIOLATION=1008;
	public static final short CLOSE_MESSAGE_TOO_BIG=1009;
	public static final short CLOSE_MANDATORY_EXT=1010;
	
	private static final byte MASK_FIN = (byte) (0x80);
	private static final byte MASK_PCODE = (byte) (0x0f);
	private static final byte MASK_MASK = (byte) (0x80);
	private static final byte MASK_LEN = (byte) (0x7f);
	
//	private static final String ENCODE="utf-8";

	private static int frameLength(boolean isMask,int payloadLength){
		int length=2;
		if(isMask){
			length+=4;
		}
		if(payloadLength<0x7E){
			return length;
		}else if(payloadLength<0xFFFF){
			return length+2;
		}else{
			return length+4;
		}
	}
	
	private static ByteBuffer fillFrame(boolean isFin, byte pcode,boolean isMask,int payloadLength,byte[] maskBytes){
		int frameLength=frameLength(isMask, payloadLength);
		ByteBuffer frame = PoolManager.getBufferInstance(frameLength);
		if (isFin) {
			frame.put((byte) (MASK_FIN | pcode));
		} else {
			frame.put(pcode);
		}
		byte maskMask = 0;
		if (isMask) {
			maskMask = MASK_MASK;
		}
		if(payloadLength<0x7E){
			frame.put((byte) (maskMask | payloadLength));
		}else if(payloadLength<0xFFFF){
			frame.put((byte) (maskMask | 0x7e));
			frame.putShort((short) payloadLength);
		}else{
			frame.put((byte) (maskMask | 0x7f));
			frame.putInt(payloadLength);
		}
		if (isMask) {
			frame.put(maskBytes);
		}
		frame.flip();
		return frame;
	}
	
	private static int maskBuffer(int absPos, byte[] maskBytes,
			ByteBuffer buffer) {
		int length=buffer.remaining();
		int pos = buffer.position();
		byte[] array = buffer.array();
		for (int i=0; i < length; i++) {
			int mod = absPos & 0x3;
			array[pos+i] ^= maskBytes[mod];
			absPos++;
		}
		return absPos;
	}
	
	public static ByteBuffer[] createFinFrame(byte pcode) {
		return createFinFrame(pcode, false, (ByteBuffer[])null);
	}
	
	public static ByteBuffer[] createFinFrame(byte pcode,boolean isMask, String payload) {
		byte[] msgBytes=null;
		try {
			msgBytes = payload.getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException();
		}
		return createFinFrame(pcode, isMask, msgBytes);
	}
	
	public static ByteBuffer[] createFinFrame(byte pcode,boolean isMask, byte[] payload) {
		ByteBuffer[] payloadBuffers=null;
		if(payload!=null){
			payloadBuffers=BuffersUtil.toByteBufferArray(ByteBuffer.wrap(payload));
		}else{
			isMask=false;//payloadがないならmaskもない
		}
		return createFinFrame(pcode, isMask, payloadBuffers);
	}
	
	public static ByteBuffer[] createFinFrame(byte pcode,boolean isMask, ByteBuffer[] payloadBuffers) {
		ByteBuffer[] framedBuffers=null;
		int length;
		if(payloadBuffers!=null){
			framedBuffers=BuffersUtil.newByteBufferArray(payloadBuffers.length+1);
			length=(int)BuffersUtil.remaining(payloadBuffers);
			for(int i=0;i<payloadBuffers.length;i++){
				framedBuffers[i+1]=payloadBuffers[i];
			}
			PoolManager.poolArrayInstance(payloadBuffers);
		}else{
			isMask=false;//payloadがないならmaskもない
			framedBuffers=BuffersUtil.newByteBufferArray(1);
			length=0;
		}
		byte[] maskBytes=null;
		if(isMask){
			maskBytes=new byte[4];
			random.nextBytes(maskBytes);
			int pos=0;
			for(int i=1;i<framedBuffers.length;i++){
				pos=maskBuffer(pos,maskBytes,framedBuffers[i]);
			}
		}
		framedBuffers[0]=fillFrame(true,pcode,isMask,length,maskBytes);
		return framedBuffers;
	}
	
	public static ByteBuffer[] createTextFrame(boolean isMask, String message) {
		return createFinFrame(PCODE_TEXT, isMask, message);
	}
	
	public static ByteBuffer[] createBinaryFrame(boolean isMask, ByteBuffer[] message) {
		return createFinFrame(PCODE_BINARY, isMask, message);
	}
	
	public static ByteBuffer[] createPingFrame(boolean isMask, String message) {
		return createFinFrame(PCODE_PING, isMask, message);
	}

	public static ByteBuffer[] createPoingFrame(boolean isMask, ByteBuffer[] message) {
		return createFinFrame(PCODE_PONG, isMask, message);
	}
	
	//TODO close frame
	public static ByteBuffer[] createCloseFrame(boolean isMask, short code,String reason) {
//		ByteBuffer buffer=
		return createFinFrame(PCODE_CLOSE, isMask, (ByteBuffer[])null);
	}
	
	private enum ParseStat{
		START,
		LENGTH,
		MASK,
		PAYLOAD,
		END,
		ERROR
	}
	
	ParseStat parseStat;
	private boolean isFin;
	private byte pcode;
	private boolean isMask;
	private byte rawLen;//top parse時にwork
	private int curPayloadPos=0;
	private int payloadLength;
	private byte[] maskBytes=new byte[4];
	private List<ByteBuffer> payloadBuffers=new ArrayList<ByteBuffer>();
	private List<ByteBuffer> nextFrameBuffers=new ArrayList<ByteBuffer>();
	
	public WsHybiFrame(){
		init();
	}
	
	public void init(){
		parseStat=ParseStat.START;
		payloadLength=0;
		curPayloadPos=0;
		payloadBuffers.clear();
		nextFrameBuffers.clear();
	}
	
	
	/**
	 * 
	 * @param buffers
	 * @return　今回のparseでframeが完結したか否か
	 */
	public boolean parse(ByteBuffer buffer){
		switch(parseStat){
		case START:
			if(!parseStart(buffer)){
				return false;
			}
		case LENGTH:
			if(!parseLength(buffer)){
				return false;
			}
		case MASK:
			if(!parseMask(buffer)){
				return false;
			}
		case PAYLOAD:
			if(!parsePayload(buffer)){
				return false;
			}
			return true;
		case END:
			nextFrameBuffers.add(buffer);
			return true;
		}
		throw new RuntimeException("parseStat:"+parseStat);
	}

	
	private boolean parseStart(ByteBuffer buffer){
		int remain=buffer.remaining();
		if(remain==0){
			return false;
		}else if(remain<2){
			parseStat=ParseStat.ERROR;
			throw new RuntimeException("parseTop");
		}
		byte b1 = buffer.get();
		byte b2 = buffer.get();
		isFin = (b1 & MASK_PCODE) != 0;
		pcode = (byte) (b1 & MASK_PCODE);
		isMask = ((b2 & MASK_MASK) != 0);
		rawLen = (byte) (b2 & MASK_LEN);
		parseStat=ParseStat.LENGTH;
		return true;
	}
	
	private boolean parseLength(ByteBuffer buffer){
		int remain=buffer.remaining();
		if(rawLen<0x7E){
			payloadLength=(int)rawLen;
		}else if(rawLen==0x7E){
			if(remain==0){
				return false;
			}else if(remain<2){
				parseStat=ParseStat.ERROR;
//				throw IllegalFormatException();
				new RuntimeException("parseLength short."+remain);
			}
			payloadLength=(int)buffer.getShort();
		}else if(rawLen==0x7F){
			if(remain==0){
				return false;
			}else if(remain<4){
				parseStat=ParseStat.ERROR;
				throw new RuntimeException("parseLength int."+remain);
			}
			payloadLength=buffer.getInt();
		}
		parseStat=ParseStat.MASK;
		return true;
	}
	
	private boolean parseMask(ByteBuffer buffer){
		if(!isMask){
			parseStat=ParseStat.PAYLOAD;
			return true;
		}
		int remain=buffer.remaining();
		if(remain==0){
			return false;
		}else if(remain<4){
			parseStat=ParseStat.ERROR;
			throw new RuntimeException("parseMask");
		}
		buffer.get(maskBytes);
		parseStat=ParseStat.PAYLOAD;
		return true;
	}
	
	private boolean parsePayload(ByteBuffer buffer){
		int remain=buffer.remaining();
		ByteBuffer payloadBuffer=null;
		int readLen=0;
		if(remain==0){//進展なし
			return false;
		}else if(payloadLength>=(curPayloadPos+remain)){//全部を対象としてよい
			payloadBuffer=buffer;
			readLen=remain;
		}else{//途中でFrameが終わっている
			readLen=payloadLength-curPayloadPos;
			int curPos=buffer.position();
			payloadBuffer=PoolManager.duplicateBuffer(buffer);
			payloadBuffer.limit(curPos+readLen);
			buffer.position(curPos+readLen);
		}
		if(isMask){
			maskBuffer(curPayloadPos,maskBytes,payloadBuffer);
		}
		payloadBuffers.add(payloadBuffer);
		curPayloadPos+=readLen;
		if(payloadLength==curPayloadPos){
			parseStat=ParseStat.END;
			return true;
		}
		return false;
	}
	
	public boolean parseNextFrame(){
		if(parseStat!=ParseStat.END){
			throw new RuntimeException("parseNextFrame");
		}
		int size=nextFrameBuffers.size();
		ByteBuffer[] framedBuffers=BuffersUtil.newByteBufferArray(size);
		for(int i=0;i<size;i++){
			framedBuffers[i]=nextFrameBuffers.get(i);
		}
		nextFrameBuffers.clear();
		init();
		for(ByteBuffer buffer:framedBuffers){
			parse(buffer);
		}
		PoolManager.poolArrayInstance(framedBuffers);
		return isParseEnd();
	}
	
	public boolean isParseEnd(){
		return (parseStat==ParseStat.END||parseStat==ParseStat.ERROR);
	}

	public boolean isFin() {
		return isFin;
	}

	public byte getPcode() {
		return pcode;
	}

	public boolean isMask() {
		return isMask;
	}

	public int getPayloadLength() {
		return payloadLength;
	}

	public byte[] getMaskBytes() {
		return maskBytes;
	}

	public ByteBuffer[] getPayloadBuffers() {
		int size=payloadBuffers.size();
		ByteBuffer[] payload=BuffersUtil.newByteBufferArray(size);
		for(int i=0;i<size;i++){
			payload[i]=payloadBuffers.get(i);
		}
		payloadBuffers.clear();
		return payload;
	}
	
}
