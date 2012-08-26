package naru.aweb.spdy;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.log4j.Logger;

import naru.async.pool.PoolManager;
import naru.aweb.http.HeaderParser;
import naru.aweb.util.ServerParser;

/**
 * @author Owner
 * 
 */
public class NameValueParser {
	private static Logger logger = Logger.getLogger(NameValueParser.class);

	private enum Phase {
		START, NumberOfNameValue, LengthOfName, Name, LengthOfValue, Value, END, ERROR,
	}

	// private static class ParserCtx{
	private Inflater decompresser=new Inflater();
	private ByteBuffer workBuffer;
	private Phase phase = Phase.START;
	private short version;
	private int numberOfNameValue;
	private int curNameValue = 0;
	private String name;
	private String[] values;
	private HeaderParser header;
	
	public void init(short version) {
		this.version=version;
		decompresser.reset();
	}
	
	private void parseStart() {
		phase = Phase.START;
		header = (HeaderParser) PoolManager.getInstance(HeaderParser.class);
		workBuffer = PoolManager.getBufferInstance();
		workBuffer.order(ByteOrder.BIG_ENDIAN);
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
		if (version == SpdyFrame.VERSION_V2) {
			decompresser.setDictionary(SpdyFrame.DICTIONARY_V2);
		} else if (version == SpdyFrame.VERSION_V3) {
			decompresser.setDictionary(SpdyFrame.DICTIONARY_V3);
		}
	}

	private int getLength(){
		if(version==SpdyFrame.VERSION_V2){
			return (int)workBuffer.getShort();
		}else if(version==SpdyFrame.VERSION_V3){
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
			result = new String(workBuffer.array(),pos,length,SpdyFrame.ENCODE);
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
					header.setRequestUri(values[0]);
					header.setPath(values[0]);
				}else if("version".equals(name)){
					header.setReqHttpVersion(values[0]);
				}else if("scheme".equals(name)){
					//header.
				}else if("host".equals(name)){
					header.setServer(values[0], 443);
				}else{
					header.setHeader(name, values);
				}
				curNameValue++;
				if (numberOfNameValue == curNameValue) {
					phase = Phase.END;
					header.setParseOk();
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
				logger.error("infrate error",e);
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
				e.printStackTrace();
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
