package naru.aweb.spdy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;

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
| Pri|Unused | Slot |                | == spdy/2ではpri 2bitだけ(slotなし)
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
|X|           Stream-ID (31bits)     | == spdy/2ではこの後ろに16bit unuseがある
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
	private static Logger logger=Logger.getLogger(SpdyFrame.class);
	private static Config config=Config.getConfig(); 
//	private static int webSocketMessageLimit=config.getInt("webSocketMessageLimit",2048000);
	
	public static final String PROTOCOL_V2="spdy/2";
	public static final String PROTOCOL_V3="spdy/3";
	public static final String PROTOCOL_HTTP_11="http/1.1";
	public static final String PROTOCOL_HTTP_10="http/1.0";
	
	public static final short VERSION_V2=2;
	public static final short VERSION_V3=3;
	
	public static final String ENCODE="UTF-8"; 
	
	public static final byte[] DICTIONARY_V2 = (
			"optionsgetheadpostputdeletetraceacceptaccept-charsetaccept-encodingaccept-"
			+ "languageauthorizationexpectfromhostif-modified-sinceif-matchif-none-matchi"
			+ "f-rangeif-unmodifiedsincemax-forwardsproxy-authorizationrangerefererteuser"
			+ "-agent10010120020120220320420520630030130230330430530630740040140240340440"
			+ "5406407408409410411412413414415416417500501502503504505accept-rangesageeta"
			+ "glocationproxy-authenticatepublicretry-afterservervarywarningwww-authentic"
			+ "ateallowcontent-basecontent-encodingcache-controlconnectiondatetrailertran"
			+ "sfer-encodingupgradeviawarningcontent-languagecontent-lengthcontent-locati"
			+ "oncontent-md5content-rangecontent-typeetagexpireslast-modifiedset-cookieMo"
			+ "ndayTuesdayWednesdayThursdayFridaySaturdaySundayJanFebMarAprMayJunJulAugSe"
			+ "pOctNovDecchunkedtext/htmlimage/pngimage/jpgimage/gifapplication/xmlapplic"
			+ "ation/xhtmltext/plainpublicmax-agecharset=iso-8859-1utf-8gzipdeflateHTTP/1"
			+ ".1statusversionurl\u0000").getBytes();

	public static final byte[] DICTIONARY_V3 = (
			"\u0000\u0000\u0000\u0007options\u0000\u0000\u0000\u0004head\u0000\u0000\u0000\u0004post"
			+ "\u0000\u0000\u0000\u0003put\u0000\u0000\u0000\u0006delete\u0000\u0000\u0000\u0005trace"
			+ "\u0000\u0000\u0000\u0006accept\u0000\u0000\u0000\u000Eaccept-charset"
			+ "\u0000\u0000\u0000\u000Faccept-encoding\u0000\u0000\u0000\u000Faccept-language"
			+ "\u0000\u0000\u0000\raccept-ranges\u0000\u0000\u0000\u0003age\u0000\u0000\u0000\u0005allow"
			+ "\u0000\u0000\u0000\rauthorization\u0000\u0000\u0000\rcache-control"
			+ "\u0000\u0000\u0000\nconnection\u0000\u0000\u0000\fcontent-base\u0000\u0000\u0000\u0010content-encoding"
			+ "\u0000\u0000\u0000\u0010content-language\u0000\u0000\u0000\u000Econtent-length"
			+ "\u0000\u0000\u0000\u0010content-location\u0000\u0000\u0000\u000Bcontent-md5"
			+ "\u0000\u0000\u0000\rcontent-range\u0000\u0000\u0000\fcontent-type\u0000\u0000\u0000\u0004date"
			+ "\u0000\u0000\u0000\u0004etag\u0000\u0000\u0000\u0006expect\u0000\u0000\u0000\u0007expires"
			+ "\u0000\u0000\u0000\u0004from\u0000\u0000\u0000\u0004host\u0000\u0000\u0000\bif-match"
			+ "\u0000\u0000\u0000\u0011if-modified-since\u0000\u0000\u0000\rif-none-match\u0000\u0000\u0000\bif-range"
			+ "\u0000\u0000\u0000\u0013if-unmodified-since\u0000\u0000\u0000\rlast-modified"
			+ "\u0000\u0000\u0000\blocation\u0000\u0000\u0000\fmax-forwards\u0000\u0000\u0000\u0006pragma"
			+ "\u0000\u0000\u0000\u0012proxy-authenticate\u0000\u0000\u0000\u0013proxy-authorization"
			+ "\u0000\u0000\u0000\u0005range\u0000\u0000\u0000\u0007referer\u0000\u0000\u0000\u000Bretry-after"
			+ "\u0000\u0000\u0000\u0006server\u0000\u0000\u0000\u0002te\u0000\u0000\u0000\u0007trailer"
			+ "\u0000\u0000\u0000\u0011transfer-encoding\u0000\u0000\u0000\u0007upgrade\u0000\u0000\u0000\nuser-agent"
			+ "\u0000\u0000\u0000\u0004vary\u0000\u0000\u0000\u0003via\u0000\u0000\u0000\u0007warning"
			+ "\u0000\u0000\u0000\u0010www-authenticate\u0000\u0000\u0000\u0006method\u0000\u0000\u0000\u0003get"
			+ "\u0000\u0000\u0000\u0006status\u0000\u0000\u0000\u0006200 OK\u0000\u0000\u0000\u0007version"
			+ "\u0000\u0000\u0000\bHTTP/1.1\u0000\u0000\u0000\u0003url\u0000\u0000\u0000\u0006public"
			+ "\u0000\u0000\u0000\nset-cookie\u0000\u0000\u0000\nkeep-alive\u0000\u0000\u0000\u0006origin"
			+ "100101201202205206300302303304305306307402405406407408409410411412413414415416417502504505"
			+ "203 Non-Authoritative Information204 No Content301 Moved Permanently400 Bad Request401 Unauthorized"
			+ "403 Forbidden404 Not Found500 Internal Server Error501 Not Implemented503 Service Unavailable"
			+ "Jan Feb Mar Apr May Jun Jul Aug Sept Oct Nov Dec 00:00:00 Mon, Tue, Wed, Thu, Fri, Sat, Sun, GMT"
			+ "chunked,text/html,image/png,image/jpg,image/gif,application/xml,application/xhtml+xml,text/plain,"
			+ "text/javascript,publicprivatemax-age=gzip,deflate,sdchcharset=utf-8charset=iso-8859-1,utf-,*,enq=0.")
			.getBytes();	
	
	public static final short TYPE_DATA_FRAME = 0x0000;
	public static final short TYPE_SYN_STREAM = 0x0001;
	public static final short TYPE_SYN_REPLY = 0x0002;
	public static final short TYPE_RST_STREAM = 0x0003;
	public static final short TYPE_SETTINGS = 0x0004;
	public static final short TYPE_PING = 0x0006;
	public static final short TYPE_GOAWAY = 0x0007;
	public static final short TYPE_HEADERS = 0x0008;
	public static final short TYPE_WINDOW_UPDATE = 0x0009;
	
	public static final char FLAG_FIN=(char)0x01;
	public static final char FLAG_UNIDIRECTIONAL=(char)0x02;

	private static final int MASK_C =           (int)(0x80000000);
	private static final int MASK_TYPE =        (int)(0x0000FFFF);
	private static final int MASK_VERSION =     (int)(0x7FFF0000);
	private static final int MASK_STREAMID =    (int)(0x7FFFFFFF);
	private static final int MASK_FLAGS =       (int)(0xFF000000);
	private static final int MASK_DATA_LENGTH = (int)(0x00FFFFFF);
	
	/*RST_STREAM Status code */
	public static final int RSTST_PROTOCOL_ERROR = 1;
	public static final int RSTST_INVALID_STREAM = 2;
	public static final int RSTST_REFUSED_STREAM = 3;
	public static final int RSTST_UNSUPPORTED_VERSION = 4;
	public static final int RSTST_CANCEL = 5;
	public static final int RSTST_INTERNAL_ERROR = 6;
	public static final int RSTST_FLOW_CONTROL_ERROR = 7;
	public static final int RSTST_STREAM_IN_USE = 8;
	public static final int STREAM_ALREADY_CLOSED = 9;
	public static final int RSTST_INVALID_CREDENTIALS = 10;
	public static final int RSTST_FRAME_TOO_LARGE = 11;
	
	/*GOAWAY Status code */
	public static final int GOWST_OK = 0;
	public static final int GOWST_PROTOCOL_ERROR = 1;
	public static final int GOWST_INTERNAL_ERROR = 11;
	
	
	private static ByteBuffer setupControlFrame(ByteBuffer frame,short version,short type,char flags,int length){
		int work=MASK_C|((int)version)<<16|type;
		frame.putInt(work);
		work=((int)flags)<<24|length;
		frame.putInt(work);
		return frame;
	}
	
	private static ByteBuffer buildDataFrame(ByteBuffer frame,int streamId,char flags,int length){
		frame.putInt(streamId);
		int work=((int)flags)<<24|length;
		frame.putInt(work);
		return frame;
	}

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
	private int pingId;
	
	private int associatedToStreamId;
	private short pri;//v2 2bit v3 3bit
	private short slot;//v3のみ
	private int statusCode;
	private Map<String,String[]> header;
	
	private NameValueParser nameValueParser=new NameValueParser();
	private NameValueBuilder nameValueBuilder=new NameValueBuilder();
	
	private List<ByteBuffer> dataBuffers=new ArrayList<ByteBuffer>();
	
	public void init(String protocol){
		if(PROTOCOL_V2.equals(protocol)){
			version=VERSION_V2;
		}else if(PROTOCOL_V3.equals(protocol)){
			version=VERSION_V3;
		}
		nameValueParser.init(version);
		nameValueBuilder.init(version);
		prepareNext();
	}
	
	private void prepareNext(){
		parseStat=ParseStat.START;
		dataLength=0;
		curDataPos=0;
		PoolManager.poolBufferInstance(dataBuffers);
		dataBuffers.clear();
		workBuffer.clear();
		workBuffer.order(ByteOrder.BIG_ENDIAN);
	}
	
	public void setupHeader(HeaderParser requestHeader){
		for(String name:header.keySet()){
			String[] values=header.get(name);
			if(version==VERSION_V2){
				if("method".equals(name)){
					requestHeader.setMethod(values[0]);
					continue;
				}else if("url".equals(name)){
					requestHeader.parseUri(values[0]);
					continue;
				}else if("scheme".equals(name)){
					continue;
				}else if("version".equals(name)){
					requestHeader.setReqHttpVersion(values[0]);
					continue;
				}else if("host".equals(name)){
					String host=values[0];
					requestHeader.setServer(host, 443);
					requestHeader.setHeader(HeaderParser.HOST_HEADER,host);
					continue;
				}
			}else if(version==VERSION_V3){
				if(":method".equals(name)){
					requestHeader.setMethod(values[0]);
					continue;
				}else if(":path".equals(name)){
					requestHeader.parseUri(values[0]);
					continue;
				}else if(":scheme".equals(name)){
					continue;
				}else if(":version".equals(name)){
					requestHeader.setReqHttpVersion(values[0]);
					continue;
				}else if(":host".equals(name)){
					String host=values[0];
					requestHeader.setServer(host, 443);
					requestHeader.setHeader(HeaderParser.HOST_HEADER,host);
					continue;
				}
			}
			requestHeader.setHeader(name, values);
		}
		requestHeader.setupContentHeader();
	}
	
	public ByteBuffer[] buildSynReply(int streamId,char flags,HeaderParser header){
		ByteBuffer frame = PoolManager.getBufferInstance();
		frame.order(ByteOrder.BIG_ENDIAN);
		ByteBuffer[] buffers=nameValueBuilder.encode(header);
		//header=nameValueParser.decode(buffers);
		int length;
		if(version==VERSION_V2){
			length=6+(int)BuffersUtil.remaining(buffers);
		}else if(version==VERSION_V3){
			length=4+(int)BuffersUtil.remaining(buffers);
		}else{
			throw new RuntimeException();
		}
		setupControlFrame(frame, (short)version, (short)TYPE_SYN_REPLY, flags, length);
		frame.putInt(streamId);
		if(version==VERSION_V2){
			frame.putShort((short)0);
		}
		frame.flip();
		return BuffersUtil.concatenate(frame,buffers,null);
	}
	
	public ByteBuffer[] buildRstStream(int streamId,int statusCode){
		ByteBuffer frame = PoolManager.getBufferInstance();
		frame.order(ByteOrder.BIG_ENDIAN);
		setupControlFrame(frame, (short)version, (short)TYPE_RST_STREAM, (char)0, 8);
		frame.putInt(streamId);
		frame.putInt(statusCode);
		frame.flip();
		return BuffersUtil.toByteBufferArray(frame);
	}
	
	public ByteBuffer[] buildDataFrame(int streamId,char flags,ByteBuffer[] data){
		ByteBuffer frame = PoolManager.getBufferInstance();
		frame.order(ByteOrder.BIG_ENDIAN);
		frame.putInt(streamId);
		int length=(int)BuffersUtil.remaining(data);
		int work=((int)flags)<<24|length;
		frame.putInt(work);
		frame.flip();
		System.out.println(Integer.toHexString(work));
		return BuffersUtil.concatenate(frame,data,null);
	}
	
	public ByteBuffer[] buildPIngFrame(int pingId){
		ByteBuffer frame = PoolManager.getBufferInstance();
		frame.order(ByteOrder.BIG_ENDIAN);
		setupControlFrame(frame, (short)version, (short)TYPE_PING, (char)0, 4);
		frame.putInt(pingId);
		frame.flip();
		return BuffersUtil.toByteBufferArray(frame);
	}
	
	private void parseType(){
		switch(type){
		case SpdyFrame.TYPE_SYN_STREAM:
			streamId=getIntFromData();
			associatedToStreamId=getIntFromData();
			pri=getShortFromData();
			ByteBuffer[] dataBuffer=getDataBuffers();
			header=nameValueParser.decode(dataBuffer);
			break;
		case SpdyFrame.TYPE_RST_STREAM:
			streamId=getIntFromData();
			statusCode=getIntFromData();
			break;
		case SpdyFrame.TYPE_PING:
			pingId=getIntFromData();
			break;
		case SpdyFrame.TYPE_GOAWAY:
		case SpdyFrame.TYPE_HEADERS:
		case SpdyFrame.TYPE_SETTINGS:
		case SpdyFrame.TYPE_WINDOW_UPDATE:
		case SpdyFrame.TYPE_SYN_REPLY://来ない
		}
	}
	
	/**
	 * 
	 * @param buffers
	 * @return　今回のparseでframeが完結したか否か
	 */
	public boolean parse(ByteBuffer buffer){
		boolean rc;
		switch(parseStat){
		case END:
			prepareNext();
		case START:
			if(!parseStart(buffer)){
				rc=false;
				break;
			}
		case DATA:
			rc=parseData(buffer);
			if(parseStat==ParseStat.DATA){
				return false;//次のbufferがある場合
			}else if(parseStat==ParseStat.END){
				parseType();
			}
			rc=true;
			break;
		default://ERROR
			rc=false;
		}
		return rc;
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
			return false;
		}
		int work=readBuffer.getInt();
		c=((work & MASK_C)!=0);
		if(c){
			version=(short)((work&MASK_VERSION)>>16);
			type=(short)(work&MASK_TYPE);
		}else{
			type=TYPE_DATA_FRAME;
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
			return true;
		}else if(dataLength>=(curDataPos+remain)){//全部を対象としてよい
			dataBuffer=PoolManager.duplicateBuffer(buffer);
			buffer.position(buffer.limit());
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
	
	private int getIntFromData(){
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
	
	private short getShortFromData(){
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

	public char getFlags() {
		return flags;
	}
	
	public boolean isFin(){
		return (flags&FLAG_FIN)!=0;
	}
	

	public int getAssociatedToStreamId() {
		return associatedToStreamId;
	}

	public int getStatusCode() {
		return statusCode;
	}

//	public HeaderParser getHeader() {
//		return header;
//	}

	public int getPingId() {
		return pingId;
	}
	
	public int getVersion(){
		return version;
	}
	
	public int getPri(){
		return pri;
	}
}
