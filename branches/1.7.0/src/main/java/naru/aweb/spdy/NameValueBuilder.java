package naru.aweb.spdy;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.util.HeaderParser;

public class NameValueBuilder {
	private short version;
//	private ByteBuffer workBuffer;
//	private ByteBuffer workBuffer=ByteBuffer.allocate(16);
	private Deflater compresser=new Deflater();

	public void init(short version) {
		this.version=version;
		compresser.reset();
		setDictionary();
	}
	
	private void setDictionary() {
		if (version == SpdyFrame.VERSION_V2) {
			compresser.setDictionary(SpdyFrame.DICTIONARY_V2);
		} else if (version == SpdyFrame.VERSION_V3||version == SpdyFrame.VERSION_V31) {
			compresser.setDictionary(SpdyFrame.DICTIONARY_V3);
		}
	}
	
	/*
	private void buildStart() {
		workBuffer=PoolManager.getBufferInstance();
		workBuffer.order(ByteOrder.BIG_ENDIAN);
	}

	private void buildEnd() {
		if(workBuffer!=null){
			PoolManager.poolBufferInstance(workBuffer);
			workBuffer=null;
		}
	}
	*/
	
	private void putLength(ByteBuffer workBuffer,int data){
//		workBuffer.clear();
		if(version==SpdyFrame.VERSION_V2){
			workBuffer.putShort((short)data);
		}else if(version==SpdyFrame.VERSION_V3||version==SpdyFrame.VERSION_V31){
			workBuffer.putInt(data);
		}
//		compresser.setInput(workBuffer.array(),0,workBuffer.position());
	}
	
	private void putString(ByteBuffer workBuffer,String data){
		if(data==null){
			return;
		}
		try {
			byte[] bytes=data.getBytes(SpdyFrame.ENCODE);
			putLength(workBuffer,bytes.length);
			workBuffer.put(bytes);
//			compresser.setInput(bytes,0,bytes.length);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void build(ByteBuffer workBuffer,HeaderParser header){
		String status=header.getStatusCode();
		String v=header.getResHttpVersion();
		//”O‚Ì‚½‚ß
		//String transfer=header.getHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		//header.removeHeader(HeaderParser.TRANSFER_ENCODING_HEADER);
		header.removeHeader(HeaderParser.KEEP_ALIVE_HEADER);
		
		Set<String> headerNames=header.getHeaderNames();
		putLength(workBuffer,headerNames.size()+2);
		if (version == SpdyFrame.VERSION_V2) {
			putString(workBuffer,"status");
		} else if (version == SpdyFrame.VERSION_V3||version == SpdyFrame.VERSION_V31) {
			putString(workBuffer,":status");
		}
		putString(workBuffer,status);
		if (version == SpdyFrame.VERSION_V2) {
			putString(workBuffer,"version");
		} else if (version == SpdyFrame.VERSION_V3||version == SpdyFrame.VERSION_V31) {
			putString(workBuffer,":version");
		}
		putString(workBuffer,v);
		StringBuffer sb=new StringBuffer();
		for(String headerName:headerNames){
			List<String>values=header.getHeaders(headerName);
			int size=values.size();
			if(size==0){
				continue;
			}
			putString(workBuffer,headerName.toLowerCase());
			if(size==1){
				putString(workBuffer,values.get(0));
			}else{
				sb.setLength(0);
				sb.append(values.get(0));
				for(int i=1;i<size;i++){
					sb.append('\0');
					sb.append(values.get(i));
				}
				putString(workBuffer,sb.toString());
			}
		}
	}
	
	public ByteBuffer[] encode(HeaderParser header) {
		ByteBuffer workBuffer=PoolManager.getBufferInstance();
		workBuffer.order(ByteOrder.BIG_ENDIAN);
		try{
			build(workBuffer,header);
			compresser.setInput(workBuffer.array(),0,workBuffer.position());
//			compresser.finish();
			List<ByteBuffer> buffers=new ArrayList<ByteBuffer>();
			while(true){
				ByteBuffer buffer=PoolManager.getBufferInstance();
				int length=compresser.deflate(buffer.array(),0,buffer.capacity(),Deflater.FULL_FLUSH);
				if(length<=0){
					PoolManager.poolBufferInstance(buffer);
					break;
				}
				buffer.limit(length);
//				buffer.flip();
				buffers.add(buffer);
				if(compresser.finished()){
					break;
				}
			}
			return BuffersUtil.toByteBufferArray(buffers);
		}finally{
			PoolManager.poolBufferInstance(workBuffer);
		}
	}
}
