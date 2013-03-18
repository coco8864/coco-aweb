package naru.aweb;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.zip.GZIPOutputStream;

import naru.async.BufferGetter;
import naru.async.pool.BuffersUtil;
import naru.aweb.util.UnzipConverter;

import org.junit.Test;

public class ConverterTest {

//	@Test
	public void test() throws IOException {
		String enc1;
		InputStreamReader isr=new InputStreamReader(new InnerInputStream(),"shift_jis");
		OutputStreamWriter osw=new OutputStreamWriter(new InnerOutputStream(),"utf-8");
		System.out.println("log1");
		osw.write((String)"“ú–{Œê");
		System.out.println("log2");
		osw.flush();
		System.out.println("log3");
		
		//input
		//ByteBuffer buffer;
	}
	
	private static class InnerOutputStream extends OutputStream{
		@Override
		public void write(int c) throws IOException {
			System.out.print(c);
		}
	}
	
	private static class InnerInputStream extends InputStream{

		@Override
		public int read() throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
	}
	
//	@Test
	public void test1() {
		Charset charsetIn=Charset.forName("utf-8");
		CharsetDecoder cd=charsetIn.newDecoder();
		Charset charsetOut=Charset.forName("shift_jis");
		CharsetEncoder ce=charsetOut.newEncoder();
		
		ByteBuffer in=null;
		CharBuffer charBuffer=null;
		cd.decode(in, charBuffer, false);
//		ce.encode(charBuffer, out, false);
	}
	@Test
	public void test2() throws IOException {
		UnzipConverter unzipConverter=new UnzipConverter();
		unzipConverter.init(new Getter());
//		unzipConverter.put(ByteBuffer.wrap("Testxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes()));
		
		FileInputStream fis=new FileInputStream("make.zip");
		ByteBuffer buf=ByteBuffer.allocate(10240);
		fis.getChannel().read(buf);
		buf.flip();
		unzipConverter.put(buf);
		unzipConverter.end();
		
		
		
		
	}
	
	private static class Getter implements BufferGetter{

		@Override
		public boolean onBuffer(Object arg0, ByteBuffer[] buffers) {
			System.out.println("OnBuffer."+buffers.length);
			try {
				System.out.println(new String(buffers[0].array(),"utf-8"));
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return false;
		}

		@Override
		public void onBufferEnd(Object arg0) {
			System.out.println("onBufferEnd.");
		}

		@Override
		public void onBufferFailure(Object arg0, Throwable e) {
			System.out.println("onBufferFailure.");
			e.printStackTrace();
		}
	}
	

}
