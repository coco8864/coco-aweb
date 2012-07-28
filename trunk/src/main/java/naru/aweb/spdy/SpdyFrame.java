package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;

/**
 * http://dev.chromium.org/spdy/spdy-protocol/spdy-protocol-draft3
 * 
2.2.1 Control frames
+----------------------------------+
|C| Version(15bits) | Type(16bits) |
+----------------------------------+
| Flags (8)  |  Length (24 bits)   |
+----------------------------------+
|               Data               |
+----------------------------------+

2.2.2 Data frames

+----------------------------------+
|C|       Stream-ID (31bits)       |
+----------------------------------+
| Flags (8)  |  Length (24 bits)   |
+----------------------------------+
|               Data               |
+----------------------------------+

2.6.1 SYN_STREAM

+------------------------------------+
|1|    version    |         1        |
+------------------------------------+
|  Flags (8)  |  Length (24 bits)    |
+------------------------------------+
|X|           Stream-ID (31bits)     |
+------------------------------------+
|X| Associated-To-Stream-ID (31bits) |
+------------------------------------+
| Pri|Unused | Slot |                |
+-------------------+                |
| Number of Name/Value pairs (int32) |   <+
+------------------------------------+    |
|     Length of name (int32)         |    | This section is the "Name/Value
+------------------------------------+    | Header Block", and is compressed.
|           Name (string)            |    |
+------------------------------------+    |
|     Length of value  (int32)       |    |
+------------------------------------+    |
|          Value   (string)          |    |
+------------------------------------+    |
|           (repeats)                |   <+


2.6.2 SYN_REPLY

+------------------------------------+
|1|    version    |         2        |
+------------------------------------+
|  Flags (8)  |  Length (24 bits)    |
+------------------------------------+
|X|           Stream-ID (31bits)     |
+------------------------------------+
| Number of Name/Value pairs (int32) |   <+
+------------------------------------+    |
|     Length of name (int32)         |    | This section is the "Name/Value
+------------------------------------+    | Header Block", and is compressed.
|           Name (string)            |    |
+------------------------------------+    |
|     Length of value  (int32)       |    |
+------------------------------------+    |
|          Value   (string)          |    |
+------------------------------------+    |
|           (repeats)                |   <+


2.6.3 RST_STREAM

+----------------------------------+
|1|   version    |         3       |
+----------------------------------+
| Flags (8)  |         8           |
+----------------------------------+
|X|          Stream-ID (31bits)    |
+----------------------------------+
|          Status code             |
+----------------------------------+

 * 
 * @author Naru
 *
 */
public class SpdyFrame {
	private static ByteBuffer buildControlFrame(short version,short type,char flags,int length,Object data){
		ByteBuffer frame = PoolManager.getBufferInstance();
		frame.order(ByteOrder.BIG_ENDIAN);
		int work=MASK_C|((int)version)<<16|type;
		frame.putInt(work);
		work=((int)flags)<<24|length;
		frame.putInt(work);
		return frame;
	}
	
	private static ByteBuffer buildDataFrame(int streamId,char flags,int length,Object data){
		ByteBuffer frame = PoolManager.getBufferInstance();
		frame.order(ByteOrder.BIG_ENDIAN);
		frame.putInt(streamId);
		int work=((int)flags)<<24|length;
		frame.putInt(work);
		return frame;
	}
	
	
	private static Logger logger=Logger.getLogger(SpdyFrame.class);
	private static Config config=Config.getConfig(); 
//	private static int webSocketMessageLimit=config.getInt("webSocketMessageLimit",2048000);
	
	public static final String PROTOCOL_V2="spdy/2";
	public static final String PROTOCOL_V3="spdy/3";
	
	public static final short TYPE_SYN_STREAM = 0x0001;
	public static final short TYPE_SYN_REPLY = 0x0002;
	public static final short TYPE_RST_STREAM = 0x0003;
	public static final short TYPE_SETTINGS = 0x0004;
	public static final short TYPE_PING = 0x0006;
	public static final short TYPE_GOAWAY = 0x0007;
	public static final short TYPE_HEADERS = 0x0008;
	public static final short TYPE_WINDOW_UPDATE = 0x0009;
	
	public static final char FLAGS_FIN=(char)0x01;
	public static final char FLAG_UNIDIRECTIONAL=(char)0x02;

	private static final int MASK_C =           (int)(0x80000000);
	private static final int MASK_TYPE =        (int)(0x0000FFFF);
	private static final int MASK_VERSION =     (int)(0x7FFF0000);
	private static final int MASK_STREAMID =    (int)(0x7FFFFFFF);
	private static final int MASK_FLAGS =       (int)(0xFF000000);
	private static final int MASK_DATA_LENGTH = (int)(0x00FFFFFF);
	
//	private static final String ENCODE="utf-8";

	public static ByteBuffer[] createFrame(boolean isFin,ByteBuffer[] payloadBuffers) {
		return null;
	}
	
	private enum ParseStat{
		START,
		DATA,
		END,
		ERROR
	}
	
	private ParseStat parseStat;
	private boolean c;//contorole bit
	private short version;
	private int dataLength;
	private int curDataPos;
	private char flags;
	private short type;
	private int streamId;
	
	private List<ByteBuffer> dataBuffers=new ArrayList<ByteBuffer>();
	private List<ByteBuffer> nextFrameBuffers=new ArrayList<ByteBuffer>();
	
//	private short closeCode;
//	private String closeReason;
	
	public SpdyFrame(){
		init();
	}
	
	public void init(){
		parseStat=ParseStat.START;
		dataLength=0;
		curDataPos=0;
		dataBuffers.clear();
		nextFrameBuffers.clear();
		workBuffer.clear();
		workBuffer.order(ByteOrder.BIG_ENDIAN);
	}
	
	
	
	public ByteBuffer[] buildSynReply(int streamId,ByteBuffer[] buffers){
	}
	
	public ByteBuffer[] buildRstStream(int streamId,int statusCode){
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
		case DATA:
			boolean rc=parseData(buffer);
			if(parseStat==ParseStat.DATA){
				break;
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
	
	private ByteBuffer workBuffer=ByteBuffer.allocate(8);
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
		ByteBuffer readBuffer=fillBuffer(buffer,8);
		if(readBuffer==null){
			PoolManager.poolBufferInstance(buffer);
			return false;
		}
		int work=readBuffer.getInt();
		c=((work & MASK_C)!=0);
		if(c){
			version=(short)((work&MASK_VERSION)>>16);
			type=(short)(work&MASK_TYPE);
		}else{
			streamId=work&MASK_STREAMID;
		}
		work=readBuffer.getInt();
		flags=(char)((work&MASK_FLAGS)>>24);
		dataLength=work&MASK_DATA_LENGTH;
		parseStat=ParseStat.DATA;
		return true;
	}
	
	//return bufferを全て処理した場合　true,残りがある場合false
	private boolean parseData(ByteBuffer buffer){
		int remain=buffer.remaining();
		ByteBuffer dataBuffer=null;
		boolean rc=false;
		int readLen=0;
		if(remain==0){//進展なし
			if(dataLength==0){
				parseStat=ParseStat.END;
			}
			PoolManager.poolBufferInstance(buffer);
			return true;
		}else if(dataLength>=(curDataPos+remain)){//全部を対象としてよい
			dataBuffer=buffer;
			readLen=remain;
			rc=true;
		}else{//途中でFrameが終わっている
			readLen=dataLength-curDataPos;
			int curPos=buffer.position();
			dataBuffer=PoolManager.duplicateBuffer(buffer);
			dataBuffer.limit(curPos+readLen);
			buffer.position(curPos+readLen);
			rc=false;
		}
		dataBuffers.add(dataBuffer);
		curDataPos+=readLen;
		if(dataLength==curDataPos){
			parseStat=ParseStat.END;
		}
		return rc;
	}
	
	public boolean parseNextFrame(){
		if(parseStat!=ParseStat.END){
			throw new RuntimeException("parseNextFrame");
		}
		ByteBuffer[] framedBuffers=BuffersUtil.toByteBufferArray(nextFrameBuffers);
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

	public short getType() {
		return type;
	}

	public boolean isControle() {
		return c;
	}

	public int getDataLength() {
		return dataLength;
	}
	
	public int getStreamId(){
		return streamId;
	}

	public ByteBuffer[] getDataBuffers() {
		int size=dataBuffers.size();
		ByteBuffer[] payload=BuffersUtil.newByteBufferArray(size);
		for(int i=0;i<size;i++){
			payload[i]=dataBuffers.get(i);
		}
		dataBuffers.clear();
		return payload;
	}
	
	public int getIntFromData(){
		if(parseStat!=ParseStat.END){
			throw new RuntimeException("getIntFromData");
		}
		for(ByteBuffer buffer:dataBuffers){
			ByteBuffer readBuffer=fillBuffer(buffer,4);
			if(readBuffer!=null){
				return readBuffer.getInt();
			}
		}
		throw new RuntimeException("getIntFromData");
	}
	
	public short getShortFromData(){
		if(parseStat!=ParseStat.END){
			throw new RuntimeException("getIntFromData");
		}
		for(ByteBuffer buffer:dataBuffers){
			ByteBuffer readBuffer=fillBuffer(buffer,2);
			if(readBuffer!=null){
				return readBuffer.getShort();
			}
		}
		throw new RuntimeException("getIntFromData");
	}
	
}
