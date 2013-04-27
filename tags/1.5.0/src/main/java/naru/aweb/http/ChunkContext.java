package naru.aweb.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;

public class ChunkContext {
	static private Logger logger=Logger.getLogger(ChunkContext.class);
	
	private static int MODE_CHUNK_START=0;
	private static int MODE_CHUNK_START_R=1;
	private static int MODE_CHUNK=2;
	private static int MODE_CHUNK_END_R=3;
	private static int MODE_CHUNK_DATA=4;
	private static int MODE_END_OF_DATA=5;
	private static int MODE_NOT_CHUNK_DATA=-1;
	
	private static final byte[] DATA_AND_LAST_CHUNK="\r\n0\r\n\r\n".getBytes();
	private static final byte[] LAST_CHUNK="0\r\n\r\n".getBytes();
	public static final byte[] CRLF = "\r\n".getBytes();
	
	private boolean isChunked;
	private long contentLength;
	private long processContentLength;
	
	private long nextChunk;
	private boolean isSendLastChunk;
	private int mode=MODE_CHUNK_START;
	private int chunkBufferPtr=0;
	private byte[] chunkBuffer=new byte[1024];
	
	public void encodeInit(boolean isChunked){
		this.isChunked=isChunked;
		this.isSendLastChunk=false;
	}
	
	/**
	 * chunkしたいデータをbuffersに順次渡す
	 * 
	 * @param isLast　buffersはnullではないが最終データである事が分かっている場合
	 * @param buffers　nullの場合レスポンス終端をあらわす
	 * @return
	 */
	public ByteBuffer[] encodeChunk(boolean isLast,ByteBuffer[] buffers){
		if(!isChunked){
			return buffers;
		}
		ByteBuffer head=null;
		if(buffers==null){
			if(isLast && !isSendLastChunk){
				isSendLastChunk=true;
				return BuffersUtil.toByteBufferArray(ByteBuffer.wrap(LAST_CHUNK));
			}
			/* buffersがnullで最後のデータじゃないというのは異常 */
			throw new IllegalArgumentException("chunkedIfNeed");
		}else{
			long length=BuffersUtil.remaining(buffers);
			String headString=Long.toHexString(length)+"\r\n";
			head=ByteBuffer.wrap(headString.getBytes());
		}
		ByteBuffer tail=null;
		if(isLast){
			isSendLastChunk=true;
			tail=ByteBuffer.wrap(DATA_AND_LAST_CHUNK);
		}else{
			tail=ByteBuffer.wrap(CRLF);
		}
		ByteBuffer[] chunkedBuffer=BuffersUtil.concatenate(head, buffers, tail);
		return chunkedBuffer;
	}
	
	/**
	 * modeがMODE_CHUNK_DATAもしくはMODE_END_OF_DATAになったらtrueを返却する
	 * @param c
	 * @return
	 */
	private boolean nextMode(byte c){
		if(mode==MODE_CHUNK){
			if(c=='\r'){
				mode=MODE_CHUNK_END_R;
				return false;
			}
			if(chunkBufferPtr>=chunkBuffer.length){
				/*
				String msg=null;
				try {
					msg=new String(chunkBuffer,"utf-8");
					msg=new String(chunkBuffer,HeaderParser.HEADER_ENCODE);
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				*/
				logger.warn("too long chunk.chunk:"+chunkBufferPtr,new Exception());
				mode=MODE_NOT_CHUNK_DATA;				
				return true;
			}
			chunkBuffer[chunkBufferPtr]=c;
			chunkBufferPtr++;
			return false;
		}else if(mode==MODE_CHUNK_END_R){
			if(c=='\n'){
				String chunk=null;
				try {
					chunk = new String(chunkBuffer,0,chunkBufferPtr,HeaderParser.HEADER_ENCODE);
				} catch (UnsupportedEncodingException e) {
					logger.warn("nextMode.MODE_CHUNK_END_R"+chunkBuffer.length,e);
					mode=MODE_NOT_CHUNK_DATA;				
					return true;
				}
//				logger.debug("chunk:'"+chunk.toString()+"'");
				try{
					nextChunk=Long.parseLong(chunk.split(" ")[0],16);
				} catch (NumberFormatException e) {
					logger.warn("nextMode.MODE_CHUNK_END_R"+chunkBuffer.length,e);
					mode=MODE_NOT_CHUNK_DATA;
					return true;
				}
				if(nextChunk==0){
					mode=MODE_END_OF_DATA;
				}else{
					mode=MODE_CHUNK_DATA;
				}
				return true;
			}else{
				if(chunkBufferPtr>=(chunkBuffer.length-1)){
					logger.warn("too long chunk.chunk:"+chunkBufferPtr,new Exception());
					mode=MODE_NOT_CHUNK_DATA;				
					return true;
				}
				chunkBuffer[chunkBufferPtr]='\r';
				chunkBufferPtr++;
				chunkBuffer[chunkBufferPtr]=c;
				chunkBufferPtr++;
				mode=MODE_CHUNK;
				return false;
			}
		}else if(mode==MODE_CHUNK_START){
			if(c==(byte)'\r'){
				mode=MODE_CHUNK_START_R;
				return false;
			}
			throw new IllegalStateException("nextMode.MODE_CHUNK_START c:"+c);
		}else if(mode==MODE_CHUNK_START_R){
			if(c=='\n'){
				mode=MODE_CHUNK;
				chunkBufferPtr=0;
				return false;
			}
			logger.warn("nextMode.MODE_CHUNK_START_R c:"+c,new Exception());
			mode=MODE_NOT_CHUNK_DATA;				
			return true;
		}
		logger.warn("nextMode.illegalmode mode:"+mode,new Exception());
		mode=MODE_NOT_CHUNK_DATA;
		return true;
	}
	
	public void decodeInit(boolean isChunked,long contentLength){
		this.isChunked=isChunked;
		this.contentLength=contentLength;
		this.processContentLength=0;
		this.chunkBufferPtr=0;
		this.nextChunk=0;
		if(contentLength!=0){
			this.mode=MODE_CHUNK;
		}else{
			this.mode=MODE_END_OF_DATA;
		}
		
	}
	
//	private long nextChunk;
//	private boolean isSendLastChunk;
//	private int mode;
//	private int chunkBufferPtr;
//	private byte[] chunkBuffer=new byte[1024];
	
	
	public boolean isEndOfData(){
		return (mode==MODE_END_OF_DATA);
	}
	
	int debugFileCounter=0;
	private void dump(ByteBuffer[] body){
		ByteBuffer[] d=PoolManager.duplicateBuffers(body);
		File f=new File(Integer.toString(hashCode())+"_"+ debugFileCounter+".txt");
		debugFileCounter++;
		System.out.println("f:"+f.getAbsolutePath());
		OutputStream os=null;
		try {
			os=new FileOutputStream(f);
			BuffersUtil.toStream(d, os);
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			if(os!=null){
				try {
					os.close();
				} catch (IOException ignore) {
				}
			}
		}
	}
	
	/**
	 * chunkedじゃない場合も含めて終端を確認するメソッド
	 * @param body
	 * @return
	 */
	public boolean isEndOfData(ByteBuffer[] body){
		if(!isChunked){
			long processLength=BuffersUtil.remaining(body);
			processContentLength += processLength;
			if (contentLength <= processContentLength) {
				mode=MODE_END_OF_DATA;
				return true;
			}
			return false;
		}
		if(body==null){
			return (mode==MODE_END_OF_DATA);
		}
//		dump(body);//TODO 最適化
		BuffersUtil.mark(body);
		ByteBuffer buffer=null;
		int i=0;
		while(true){
			if(buffer==null||!buffer.hasRemaining()){
				if(body.length<=i||mode==MODE_END_OF_DATA){
					break;
				}
				buffer=body[i];
				i++;
			}
			if(nextBuffer(buffer)==false){
				continue;
			}
			long bufLength=buffer.remaining();
			if(bufLength>nextChunk){
				mode=MODE_CHUNK_START;
				bufLength=nextChunk;
			}
			buffer.position(buffer.position()+(int)bufLength);
			nextChunk-=bufLength;
		}
		BuffersUtil.reset(body);
		return (mode==MODE_END_OF_DATA);
	}
	
	/**
	 * このメソッドを呼び出すとfreeはされないが、bufferは消費される。
	 * @param buffer
	 * @return
	 */
	private boolean nextBuffer(ByteBuffer buffer){
		if(buffer==null||!buffer.hasRemaining()){
			return false;
		}
		if( mode==MODE_CHUNK_DATA ){
			return true;
		}else if(mode==MODE_END_OF_DATA || mode==MODE_NOT_CHUNK_DATA){
			return false;
		}
		while(buffer.hasRemaining()){
			if( nextMode(buffer.get()) ){
				if(mode==MODE_END_OF_DATA || mode==MODE_NOT_CHUNK_DATA){
					buffer.position(buffer.limit());//残りがあっても使った事にする
					return false;
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * chunkedじゃない場合も含めて生データを順次返却する。
	 * @param body
	 * @return
	 */
	public ByteBuffer[] decodeChunk(ByteBuffer[] body){
		if(body==null){
			return null;
		}
		if(!isChunked){
			long processLength=BuffersUtil.remaining(body);
			processContentLength += processLength;
			if (contentLength <= processContentLength) {
				BuffersUtil.cut(body,processLength-(processContentLength-contentLength));
				processContentLength=contentLength;
				mode=MODE_END_OF_DATA;
				return body;
			}
			return body;
		}
		List<ByteBuffer> result=new ArrayList<ByteBuffer>();
		ByteBuffer buffer=null;
		int i=0;
		while(true){
			if(buffer==null||!buffer.hasRemaining()){
				if(body.length<=i||mode==MODE_END_OF_DATA){
					break;
				}
				buffer=body[i];
				i++;
			}
			if(nextBuffer(buffer)==false){
				continue;
			}
			long bufLength=buffer.remaining();
			if(bufLength>nextChunk){
				mode=MODE_CHUNK_START;
				bufLength=nextChunk;
			}
			ByteBuffer dupBuffer=PoolManager.duplicateBuffer(buffer);
			BuffersUtil.cut(dupBuffer,bufLength);
			result.add(dupBuffer);
			buffer.position(buffer.position()+(int)bufLength);
			nextChunk-=bufLength;
		}
		PoolManager.poolBufferInstance(body);
		int size=result.size();
		if(size==0){
			return null;
		}
		return (ByteBuffer[])result.toArray(BuffersUtil.newByteBufferArray(size)); 
	}
}
