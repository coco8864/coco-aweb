package naru.aweb.handler.ws;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.util.CodeConverter;

public class WsHybiFrame {
	private static Logger logger=Logger.getLogger(WsHybiFrame.class);
	private static Config config=Config.getConfig(); 
	private static Random random=config.getRandom("WsHibiFrame"+System.currentTimeMillis());
	private static int webSocketMessageLimit=config.getInt("webSocketMessageLimit",2048000);
	
	/*
	 * 	RFC 6455 	
	 * http://www.ietf.org/rfc/rfc6455.txt
	 * 5.4.  Fragmentation
	 * トップのFragmentationにopcodeが入って、以降のFragmentationのopcodeは、0
	 */
	public static final byte PCODE_CONTINUE = 0x00;
	public static final byte PCODE_TEXT = 0x01;
	public static final byte PCODE_BINARY = 0x02;
	public static final byte PCODE_CLOSE = 0x08;
	public static final byte PCODE_PING = 0x09;
	public static final byte PCODE_PONG = 0x0A;
	

	public static final short CLOSE_UNKOWN=-1;
	
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
			return length+8;
		}
	}
	
	private static ByteBuffer fillFrame(boolean isFin, byte pcode,boolean isMask,int payloadLength,byte[] maskBytes){
		int frameLength=frameLength(isMask, payloadLength);
		ByteBuffer frame = PoolManager.getBufferInstance(frameLength);
		frame.order(ByteOrder.BIG_ENDIAN);
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
			frame.putLong(payloadLength);
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
		return createFrame(true,pcode, false, (ByteBuffer[])null);
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
		return createFrame(true,pcode, isMask, payloadBuffers);
	}

	public static ByteBuffer[] createFrame(boolean isFin,byte pcode,boolean isMask, ByteBuffer[] payloadBuffers) {
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
		framedBuffers[0]=fillFrame(isFin,pcode,isMask,length,maskBytes);
		return framedBuffers;
	}
	
	public static ByteBuffer[] createTextFrame(boolean isMask, String message) {
		return createFinFrame(PCODE_TEXT, isMask, message);
	}

	/*
	public static ByteBuffer[] createTopBinaryFrame(boolean isFin,boolean isMask,ByteBuffer header,ByteBuffer[] message) {
		ByteBuffer[] conMessage=BuffersUtil.concatenate(header, message,null);
		return createFrame(isFin,PCODE_BINARY, isMask, conMessage);
	}
	*/

	public static ByteBuffer[] createBinaryFrame(boolean isTop,boolean isFin,boolean isMask, ByteBuffer[] message) {
		if(isTop){
			return createFrame(isFin,PCODE_BINARY, isMask, message);
		}else{
			return createFrame(isFin,PCODE_CONTINUE, isMask, message);
		}
	}
	
	public static ByteBuffer[] createPingFrame(boolean isMask, String message) {
		return createFinFrame(PCODE_PING, isMask, message);
	}

	public static ByteBuffer[] createPongFrame(boolean isMask, ByteBuffer[] message) {
		return createFrame(true,PCODE_PONG, isMask, message);
	}
	
	public static ByteBuffer[] createCloseFrame(boolean isMask, short code,String reason) {
		ByteBuffer buffer=PoolManager.getBufferInstance();
		buffer.putShort(code);
		if(reason==null){
			reason="OK";
		}
		try {
			buffer.put(reason.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException();
		}
		buffer.flip();
		return createFrame(true,PCODE_CLOSE, isMask, BuffersUtil.toByteBufferArray(buffer));
	}
	
	private enum ParseStat{
		START,
		LENGTH,
		MASK,
		PAYLOAD,
		END,
		ERROR
	}
	
	private ParseStat parseStat;
	private boolean isFin;
	private byte pcode;
	private boolean isMask;
	private byte rawLen;//top parse時にwork
	private int curPayloadPos=0;
	private int payloadLength;
	private byte[] maskBytes=new byte[4];
	private List<ByteBuffer> payloadBuffers=new ArrayList<ByteBuffer>();
	private List<ByteBuffer> nextFrameBuffers=new ArrayList<ByteBuffer>();
	
	private short closeCode;
	private String closeReason;
	
	public WsHybiFrame(){
		init();
	}
	
	public void init(){
		parseStat=ParseStat.START;
		payloadLength=0;
		curPayloadPos=0;
		payloadBuffers.clear();
		nextFrameBuffers.clear();
		workBuffer.clear();
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
				break;
//				return false;
			}
		case LENGTH:
			if(!parseLength(buffer)){
				break;
//				return false;
			}
		case MASK:
			if(!parseMask(buffer)){
				break;
//				return false;
			}
		case PAYLOAD:
			boolean rc=parsePayload(buffer);
			if(parseStat==ParseStat.PAYLOAD){
				break;
			}
			if(pcode==PCODE_CLOSE){
				parseClosePayload();
			}
			if(rc){//bufferを消費した場合
				return true;
			}
		case END:
			if(buffer.hasRemaining()){
				nextFrameBuffers.add(buffer);
			}
			return true;
		default://ERROR
		}
		return false;
//		throw new RuntimeException("parseStat:"+parseStat);
	}
	
	private CodeConverter codeConverte=new CodeConverter();
	private void parseClosePayload(){
		if(payloadBuffers.size()==0){
			closeCode=CLOSE_ABNORMAL_CLOSURE;
			closeReason=null;
			return;
		}
		boolean isParseCode=false;
		try {
			codeConverte.init("utf-8");
			for(ByteBuffer buffer:payloadBuffers){
				if(isParseCode==false){
					ByteBuffer readBuffer=fillBuffer(buffer,2);
					if(readBuffer!=null){
						closeCode=readBuffer.getShort();
						isParseCode=true;
					}else{
						PoolManager.poolBufferInstance(buffer);
						continue;
					}
				}
				if(buffer.hasRemaining()){
					codeConverte.putBuffer(buffer);
				}else{
					PoolManager.poolBufferInstance(buffer);
				}
			}
			closeReason=codeConverte.convertToString();
		} catch (IOException e) {
			logger.error("parseClosePayload error.",e);
		}finally{
			payloadBuffers.clear();
		}
	}
	
	private ByteBuffer workBuffer=ByteBuffer.allocate(16);
	/**
	 * !!要求したlength長はtrue復帰後かならず、読む事!!
	 * @param buffer
	 * @param length
	 * @return
	 */
	private ByteBuffer fillBuffer(ByteBuffer buffer,int length){
		int pos=workBuffer.position();
		int limit=workBuffer.limit();
		if(pos!=0 && pos==limit){
			workBuffer.clear();
			pos=0;
		}
		for(int i=pos;i<length;i++){
			if(!buffer.hasRemaining()){
				return null;
			}
			byte b=buffer.get();
			workBuffer.put(b);
		}
		workBuffer.flip();
		return workBuffer;
	}
	
	private boolean parseStart(ByteBuffer buffer){
		ByteBuffer readBuffer=fillBuffer(buffer,2);
		if(readBuffer==null){
			PoolManager.poolBufferInstance(buffer);
			return false;
		}
		byte b1 = readBuffer.get();
		byte b2 = readBuffer.get();
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
			ByteBuffer readBuffer=fillBuffer(buffer,2);
			if(readBuffer==null){
				PoolManager.poolBufferInstance(buffer);
				return false;
			}
			readBuffer.order(ByteOrder.BIG_ENDIAN);
			payloadLength=0x0000ffff&((int)readBuffer.getShort());
		}else if(rawLen==0x7F){
			ByteBuffer readBuffer=fillBuffer(buffer,8);
			if(readBuffer==null){
				PoolManager.poolBufferInstance(buffer);
				return false;
			}
			//TODO overflow check
			readBuffer.order(ByteOrder.BIG_ENDIAN);
			payloadLength=(int)readBuffer.getLong();
		}
		if(payloadLength>webSocketMessageLimit){
			parseStat=ParseStat.ERROR;//パケットが長すぎる
			PoolManager.poolBufferInstance(buffer);
			return false;
		}
		parseStat=ParseStat.MASK;
		return true;
	}
	
	private boolean parseMask(ByteBuffer buffer){
		if(!isMask){
			parseStat=ParseStat.PAYLOAD;
			return true;
		}
		ByteBuffer readBuffer=fillBuffer(buffer,4);
		if(readBuffer==null){
			PoolManager.poolBufferInstance(buffer);
			return false;
		}
		readBuffer.get(maskBytes);
		parseStat=ParseStat.PAYLOAD;
		return true;
	}
	
	//return bufferを全て処理した場合　true,残りがある場合false
	private boolean parsePayload(ByteBuffer buffer){
		int remain=buffer.remaining();
		ByteBuffer payloadBuffer=null;
		boolean rc=false;
		int readLen=0;
		if(remain==0){//進展なし
			if(payloadLength==0){
				parseStat=ParseStat.END;
//				return true;
			}
			PoolManager.poolBufferInstance(buffer);
			return true;
		}else if(payloadLength>=(curPayloadPos+remain)){//全部を対象としてよい
			payloadBuffer=buffer;
			readLen=remain;
			rc=true;
		}else{//途中でFrameが終わっている
			readLen=payloadLength-curPayloadPos;
			int curPos=buffer.position();
			payloadBuffer=PoolManager.duplicateBuffer(buffer);
			payloadBuffer.limit(curPos+readLen);
			buffer.position(curPos+readLen);
			rc=false;
		}
		if(isMask){
			//通信bufferを直接書き換えるので、traceで通信bufferを参照していた場合、maskBuffer結果が採取される可能性がある
			maskBuffer(curPayloadPos,maskBytes,payloadBuffer);
		}
		payloadBuffers.add(payloadBuffer);
		curPayloadPos+=readLen;
		if(payloadLength==curPayloadPos){
			parseStat=ParseStat.END;
		}
		return rc;
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

	public short getCloseCode() {
		return closeCode;
	}

	public String getCloseReason() {
		return closeReason;
	}
}
