package naru.aweb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import naru.aweb.util.CodeConverter;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Test;


public class ConvertTest {
	
	private void readInputStream(InputStream is) throws IOException{
		byte[] buffer=new byte[1024];
		while(true){
			int len=is.read(buffer);
			if(len<=0){
				return;
			}
			System.out.print(new String(buffer,0,len));
		}
	}
	
	@Test
	public void testConvert() throws IOException{

		//in
		PipedOutputStream inPos=new PipedOutputStream();
		PipedInputStream inPis=new PipedInputStream(inPos);
		InputStreamReader reader=new InputStreamReader(inPis,"ms932");
		
		//out
		PipedInputStream outPis=new PipedInputStream();
		PipedOutputStream outPos=new PipedOutputStream(outPis);
		OutputStreamWriter writer=new OutputStreamWriter(outPos,"utf-8");
		
		char[] charBuffer=new char[1024];
		byte[] bBuffer=new byte[1024];
//		CharBuffer c=CharBuffer.allocate(1024);
		inPos.write("“ú–{Œê".getBytes("ms932"));
		System.out.println(reader.ready());
		int length=reader.read(charBuffer);
		System.out.println(reader.ready());
		
		writer.write(charBuffer,0,length);
		writer.flush();
		System.out.println(outPis.available());
		length=outPis.read(bBuffer);
		System.out.println(new String(bBuffer,0,length,"utf-8"));
		System.out.println(outPis.available());
//		length=outPis.read(bBuffer);
	}
	
	@Test
	public void testConvert2() throws IOException{
		CodeConverter cc=new CodeConverter();
		cc.init("euc_jp", "utf-8");
		ByteBuffer b=ByteBuffer.allocate(8092);
		for(int i=0;i<1024;i++){
			b.put("Žõ".getBytes("euc_jp"));
		}
		b.flip();
//		ByteBuffer[] result=cc.convert(new ByteBuffer[]{ByteBuffer.wrap("“ú–{Œê".getBytes("ms932"))});
		ByteBuffer[] result=cc.convert(new ByteBuffer[]{b});
		System.out.println(new String(result[0].array(),result[0].position(),result[0].limit(),"utf-8"));
		result=cc.close();
		if(result!=null && result.length>=1){
			System.out.println(new String(result[0].array(),result[0].position(),result[0].limit(),"utf-8"));
		}
		
	}
	

}
