package naru.aweb.spdy;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import naru.async.pool.PoolManager;
import naru.aweb.http.HeaderParser;

/**
 * @author Owner
 * 
 */
public class NameValueParser {
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

	private enum Phase {
		START, NumberOfNameValue, LengthOfName, Name, LengthOfValue, Value, END, ERROR,
	}

	// private static class ParserCtx{
	private Inflater decompresser=new Inflater();
	private ByteBuffer workBuffer;
	private Phase phase = Phase.START;
	private String version;
	private int numberOfNameValue;
	private int curNameValue = 0;
	private String name;
	private String[] values;
	private HeaderParser header;
	
	public void init(String version) {
		if(SpdyFrame.PROTOCOL_V2.equals(version)){
			this.version = SpdyFrame.PROTOCOL_V2;
		}else if(SpdyFrame.PROTOCOL_V3.equals(version)){
			this.version = SpdyFrame.PROTOCOL_V3;
		}else{
			throw new IllegalArgumentException(version);
		}
	}
	
	private void parseStart() {
		phase = Phase.START;
		header = (HeaderParser) PoolManager.getInstance(HeaderParser.class);
		workBuffer = PoolManager.getBufferInstance();
		workBuffer.order(ByteOrder.BIG_ENDIAN);
		decompresser.reset();
		curNameValue = 0;
	}

	private void parseEnd() {
		if (header != null) {
			header.unref(true);
			header=null;
		}
		PoolManager.poolBufferInstance(workBuffer);
		workBuffer = null;
		phase = Phase.START;
	}

	private void setDictionary() {
		if (version == SpdyFrame.PROTOCOL_V2) {
			decompresser.setDictionary(DICTIONARY_V2);
		} else if (version == SpdyFrame.PROTOCOL_V3) {
			decompresser.setDictionary(DICTIONARY_V3);
		}
	}

	private int getLength(){
		if(version==SpdyFrame.PROTOCOL_V2){
			return (int)workBuffer.getShort();
		}else if(version==SpdyFrame.PROTOCOL_V3){
			return (int)workBuffer.getInt();
		}
		return -1;
	}
	
	private int lengthOfName;
	private int lengthOfValue;
	private String getString(int length){
		if(workBuffer.remaining()<length){
			throw new IndexOutOfBoundsException();
		}
		int pos=workBuffer.position();
		String result;
		try {
			result = new String(workBuffer.array(),pos,length,ENCODE);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		workBuffer.position(pos+length);
		return result;
	}
	
	private boolean parseBuffer() {
		while (true) {
			switch (phase) {
			case START:
				numberOfNameValue = getLength();
				phase = Phase.NumberOfNameValue;
			case NumberOfNameValue:
				lengthOfName=getLength();
				phase = Phase.LengthOfName;
			case LengthOfName:
				name=getString(lengthOfName);
				phase = Phase.Name;
			case Name:
				lengthOfValue=getLength();
				phase = Phase.LengthOfValue;
			case LengthOfValue:
				String valuesString=getString(lengthOfValue);
				this.values=valuesString.split("\0");
				phase = Phase.Value;
			case Value:
				if("method".equals(name)){
					header.setMethod(values[0]);
				}else if("url".equals(name)){
					header.setReqHttpVersion(values[0]);
				}else if("version".equals(name)){
					header.setReqHttpVersion(values[0]);
				}else if("scheme".equals(name)){
					//header.
				}else{
					header.setHeader(name, values);
				}
				curNameValue++;
				if (numberOfNameValue == curNameValue) {
					phase = Phase.END;
					return true;
				}
				phase = Phase.NumberOfNameValue;
				break;
			case END:
				return true;
			case ERROR:
				return false;
			}
		}
	}

	/**
	 * @param ctx
	 * @return ERROR,ENDになったらtrue,処理を継続する場合false
	 */
	private boolean parse() {
		while (true) {
			workBuffer.compact();
			workBuffer.flip();
			byte[] array = workBuffer.array();
			int pos = workBuffer.position();
			int length = workBuffer.capacity() - pos;
			if (length == 0) {//読み込む余地がない
				//loack of buffer space
				phase=Phase.ERROR;
				return true;
			}
			try {
				length = decompresser.inflate(array, pos, length);
			} catch (DataFormatException e) {
				//inflate error
				phase=Phase.ERROR;
				return true;
			}
			if (length == 0) {
				if (decompresser.needsDictionary()) {
					setDictionary();
					continue;
				}
				return false;
			}
			workBuffer.limit(pos + length);
			try{
				if (parseBuffer()) {
					return true;
				}
			}catch(IndexOutOfBoundsException e){
				return false;
			}
			if (decompresser.needsInput()) {
				return false;
			}
		}
	}

	//いずれにしろbuffersはこのメソッドで消費する
	public HeaderParser decode(ByteBuffer[] buffers) {
		parseStart();
		try{
			for (ByteBuffer buf : buffers) {
				decompresser.setInput(buf.array(), buf.position(), buf.remaining());
				if (parse()) {
					break;
				}
			}
			if (phase == Phase.END) {
				HeaderParser result=this.header;
				this.header=null;
				return result;
			}
			if (phase == Phase.ERROR) {
				return null;
			}
		}finally{
			parseEnd();
			PoolManager.poolBufferInstance(buffers);
		}
		return null;
	}
	

}
