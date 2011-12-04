package naru.aweb.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.async.store.Page;

public class CodeConverter extends PoolBase{
	@Override
	public void recycle() {
		reader=null;
		writer=null;
		codeInputStream=null;
		codeOutputStream=null;
		page.recycle();
	}

	private LinkedList<ByteBuffer> inputBuffers=new LinkedList<ByteBuffer>();
	private Reader reader;
	private Writer writer;
	private CodeInputStream codeInputStream;
	private CodeOutputStream codeOutputStream;
	private Page page=new Page();
	
	private ByteBuffer getBuffer(){
		while(true){
			if(inputBuffers.size()<=0){
				return null;
			}
			ByteBuffer buffer=inputBuffers.removeFirst();
			if(buffer.hasRemaining()){
				return buffer;
			}
			PoolManager.poolBufferInstance(buffer);
		}
	}
	
	private class CodeInputStream extends InputStream{
		private ByteBuffer buffer;
		private boolean hasRemaining(){
			if(buffer==null){
				buffer=getBuffer();
				if(buffer==null){
					return false;
				}
			}
			return buffer.hasRemaining();
		}
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if(buffer==null){
				buffer=getBuffer();
				if(buffer==null){
					return -1;
				}
			}
			int bufferLength=buffer.remaining();
			if(bufferLength<len){
				len=bufferLength;
			}
			int pos=buffer.position();
			System.arraycopy(buffer.array(), pos, b, off, len);
			buffer.position(pos+len);
			if(!buffer.hasRemaining()){
				PoolManager.poolBufferInstance(buffer);
				buffer=getBuffer();
			}
			return len;
		}

		@Override
		public int read(byte[] b) throws IOException {
			return read(b,0,b.length);
		}

		@Override
		public int read() throws IOException {
			return (int)buffer.get();
		}
	}
	
	private class CodeOutputStream extends OutputStream{
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			page.putBytes(b, off, len);
		}

		@Override
		public void write(byte[] b) throws IOException {
			write(b,0,b.length);
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[]{(byte)b},0,1);//使われないとはず
		}
	}
	
	/**
	 * ByteBufferを入力にして、コード変換後のByteBufferを出力する場合
	 * @param inputEncode
	 * @param ouputEncode
	 * @throws IOException
	 */
	public void init(String inputEncode,String ouputEncode) throws IOException{
		//in
		codeInputStream=new CodeInputStream();
		reader=new InputStreamReader(codeInputStream,inputEncode);
		//out
		codeOutputStream=new CodeOutputStream();
		writer=new OutputStreamWriter(codeOutputStream,ouputEncode);
	}
	
	/**
	 * ByteBufferを入力にして、文字列を返却する場合
	 * @param inputEncode
	 * @throws IOException
	 */
	public void init(String inputEncode) throws IOException{
		codeInputStream=new CodeInputStream();
		reader=new InputStreamReader(codeInputStream,inputEncode);
	}
	
	public ByteBuffer[] convert(ByteBuffer[] buffers) throws IOException{
		for(ByteBuffer buffer:buffers){
			inputBuffers.add(buffer);
		}
		PoolManager.poolArrayInstance(buffers);
		char[] chars=new char[1024];
		while(true){
//			if(!codeInputStream.hasRemaining()){
//				break;
//			}
			int length=reader.read(chars);
			if(length<0){
				break;
			}
			writer.write(chars,0,length);
		}
		writer.flush();
		return page.getBuffer();
	}
	
	public void putBuffer(ByteBuffer buffer){
		inputBuffers.add(buffer);
	}
	
	public String convertToString() throws IOException{
		char[] chars=new char[1024];
		StringBuilder sb=new StringBuilder();
		while(true){
//			if(!codeInputStream.hasRemaining()){
//				break;
//			}
			int length=reader.read(chars);
			if(length<0){
				break;
			}
			sb.append(chars,0,length);
		}
		return sb.toString();
	}
	
	public ByteBuffer[] close() throws IOException{
		char[] chars=new char[1024];
		while(true){
			int length=reader.read(chars);
			if(length<=0){
				break;
			}
			writer.write(chars,0,length);
		}
		reader.close();
		writer.flush();
		return page.getBuffer();
	}
	
}
