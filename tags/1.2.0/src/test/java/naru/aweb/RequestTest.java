package naru.aweb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import naru.aweb.util.StreamUtil;

import org.junit.Test;

public class RequestTest {
	private SocketAddress phantomAddress=new InetSocketAddress("localhost", 1280);

	private byte[] request(String reqString) throws IOException{
		return request(reqString,"iso8859_1");
	}
	private byte[] request(String reqString,String charset) throws IOException{
		return request(reqString.getBytes(charset));
	}
	
	private byte[] request(byte[] req) throws IOException{
		return StreamUtil.socketIo(phantomAddress, req);
	}
	
	@Test
	public void test0() throws IOException{
	byte[] response=request("GET /proxy.pac HTTP/1.0\r\nHOST: localhost:1280\r\n\r\n");
	System.out.println(new String(response));
	}
	@Test
	public void test1() throws IOException{
	byte[] response=request("GET http://inj.java.sun.com/j2se/1.5.0/ja/docs/ja/api/index.html HTTP/1.0\r\nHOST: inj.java.sun.com\r\n\r\n");
	System.out.println(new String(response,"euc_jp"));
	}
}
