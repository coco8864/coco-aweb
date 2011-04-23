/**
 * 
 */
package naru.aweb.handler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import naru.async.ChannelHandler;
import naru.async.pool.BuffersUtil;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

/**
 * WebHandlerを継承しているが、HTTPプロトコル的なところは、利用しない。
 * 
 * @author Naru
 *
 */
public class SslProxyHandler extends WebServerHandler {
	static private Logger logger=Logger.getLogger(SslProxyHandler.class);
	private static Config config=Config.getConfig();
	private static final String READ_REQUEST="readRequest";
	private static final String WRITE_REQUEST="writeRequest";
	
	private static byte[] ProxyOkResponse="HTTP/1.0 200 Connection established\r\n\r\n".getBytes();
	private SslProxyHandler client;
	private SslServer server=new SslServer();
	private long readTimeout=5000;//readはタイムアウトさせない
	private long connectTimeout=5000;
	private boolean isUseProxy=false;
	private boolean isNeesClientRead=false;
	private boolean isConnected=false;
	private long lastIo=0;
	
	public void recycle() {
//		requestParser=null;
		server.recycle();
		super.recycle();
	}
	
	public void startResponse(){
		logger.debug("#doResponse client.cid:"+getChannelId());
		this.client=this;
		HeaderParser requestHeader=getRequestHeader();
		ServerParser sslServer=requestHeader.getServer();
		String targetHost=sslServer.getHost();
		int targetPort=sslServer.getPort();
		
		ServerParser parser=config.findProxyServer(true, targetHost);
		this.isUseProxy=false;
		if(parser!=null){
			this.isUseProxy=true;
			targetHost = parser.getHost();
			targetPort = parser.getPort();
		}
		/*
		isUseProxy=config.isUseProxy(true,targetHost);
		if(isUseProxy){
			targetHost=config.getString("sslProxyServer");
			targetPort=config.getInt("sslProxyPort");
		}
		*/
		isConnected=false;
//		attachHandler(server);
		if(server.asyncConnect(this, targetHost, targetPort, connectTimeout)){
			client.ref();//clientがonFinishedするまでこのインスタンスを押さえる
		}
	}
	
	public void onRead(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#read client.cid:"+client.getChannelId());
		lastIo=System.currentTimeMillis();
		server.asyncWrite(WRITE_REQUEST, buffers);
		asyncRead(READ_REQUEST);
	}
	
	public void onTimeout(Object userContext) {
		logger.debug("#timeout client.cid:"+client.getChannelId());
		if(userContext==READ_REQUEST){
			long now=System.currentTimeMillis();
			if( (now-lastIo)<readTimeout ){
				client.asyncRead(READ_REQUEST);
				return;
			}
		}
		client.asyncClose(userContext);
	}

	public void onClosed(Object userContext) {
		logger.debug("#closed client.cid:"+client.getChannelId());
		if(isConnected){
			server.asyncClose(userContext);
		}
		super.onClosed(userContext);
	}

	private class SslServer extends ChannelHandler {
		public void onConnected(Object requestId) {
			logger.debug("#connect.cid:"+getChannelId());
			HeaderParser requestParser=getRequestHeader();
			isConnected=true;
			if(isUseProxy){
				OutputStream os=null;
				try {
					os=server.getOutputStream(WRITE_REQUEST);
					requestParser.writeHeader(os);//CONNECT要求をリダイレクト
					isNeesClientRead=true;//本当に必要？
				} finally {
					try {
						os.close();
					} catch (IOException ignore) {
					}
				}
			}else{
				/* 接続成功 */
				client.setStatusCode("200");//アクセスログに記録するためWebHandlerに通知
				client.asyncWrite(WRITE_REQUEST, BuffersUtil.toByteBufferArray(ByteBuffer.wrap(ProxyOkResponse)));
				client.asyncRead(READ_REQUEST);
				ByteBuffer[] body=requestParser.getBodyBuffer();
				if(body!=null){
					server.asyncWrite(WRITE_REQUEST,body);
				}
			}
			server.asyncRead(READ_REQUEST);
		}

		public void onRead(Object userContext, ByteBuffer[] buffers) {
			logger.debug("#read.cid:"+getChannelId());
			lastIo=System.currentTimeMillis();
			long length=BuffersUtil.remaining(buffers);
			client.asyncWrite(WRITE_REQUEST, buffers);
			client.responseBodyLength(length);
			asyncRead(READ_REQUEST);
			if(isUseProxy && isNeesClientRead){
				client.asyncRead(READ_REQUEST);
				isNeesClientRead=false;
			}
		}
		
		public void onTimeout(Object userContext) {
			logger.debug("#timeout.cid:"+getChannelId());
			if(userContext==READ_REQUEST){
				long now=System.currentTimeMillis();
				if( (now-lastIo)<readTimeout ){
					server.asyncRead(READ_REQUEST);
					return;
				}
			}
			server.asyncClose(userContext);
		}

		public void onFailure(Object userContext, Throwable t) {
			logger.debug("#failure.cid:"+getChannelId(),t);
			server.asyncClose(userContext);
		}

		public void onFinished() {
			logger.debug("#finished.cid:"+getChannelId());
			isConnected=false;
			client.asyncClose(null);
			client.unref();
		}
	}
}
