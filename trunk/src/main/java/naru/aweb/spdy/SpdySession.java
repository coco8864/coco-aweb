package naru.aweb.spdy;

import java.nio.ByteBuffer;

import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;

public class SpdySession {
	private SpdyHandler spdyHandler;
	private String streamId;
	private WebServerHandler webserverHandler;
	
	//SpdyHandler側から呼び出される
	public void requestBody(ByteBuffer[] buffers){
		webserverHandler.requestBody(buffers);
	}
	public void onWrittenBody(){
		webserverHandler.onWrittenBody();
//		webserverHandler.onWrittenPlain(userContext);
	}
	
	//webserverHandler側から呼び出される
	public void responseHeader(HeaderParser responseHeader){
	}
	
	public void responseBody(ByteBuffer[] buffers){
	}
	
	

}
