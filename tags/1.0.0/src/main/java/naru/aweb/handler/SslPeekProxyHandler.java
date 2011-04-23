/**
 * 
 */
package naru.aweb.handler;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.ssl.SslAdapter;
import naru.async.ssl.SslHandler;
import naru.async.store.Store;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.util.ServerParser;

import org.apache.log4j.Logger;

/**
 * 自分は、SSL Proxyサーバとして動作する。その意味でSslProxyHandlerと同じ
 * ブラウザに対しては、自身をSSLサーバとしてハンドシェイク
 * SSLサーバに対しては、自身をブラウザとしてハンドシェイク
 * 自身の中を平文メッセージが流れるのでpeekする事ができる。
 * SSLサーバは、直接通信の場合とSSL Proxy経由の２種類が考えられる。
 * 
 * @author Naru
 *
 */
public class SslPeekProxyHandler extends WebServerHandler {
	static private Logger logger=Logger.getLogger(SslPeekProxyHandler.class);
	private static Config config=Config.getConfig();
	
	private static byte[] ProxyOkResponse="HTTP/1.0 200 Connection established\r\nProxy-Connection: close\r\n\r\n".getBytes();
	private SslPeekProxyHandler client;
	private SslServer server=new SslServer();
	private HeaderParser requestParser;
	private long readTimeout=5000;
	private long writeTimeout=5000;
	private boolean isUseProxy=false;
	private boolean isConnected=false;
	private boolean isProxyConnect=false;
	private long lastIo=0;
	private HeaderParser requestDecodeHeader=new HeaderParser();
	
	public void recycle() {
		requestParser=null;
		requestDecodeHeader.recycle();
		server.recycle();
		super.recycle();
	}
	
	public void startResponse(){
		logger.debug("#doResponse.id:"+getChannelId());
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
		server.asyncConnect(this, targetHost, targetPort, writeTimeout);
	}
	
	/**
	 * フロントクライアントとのshakehand完了メソッド
	 */
	public boolean onHandshaked() {
		logger.debug("#handshaked client.id:"+getChannelId());
		/* このクラスが使われるという事は、トレースを採取するという事 */
//		AccessLog accessLog=(AccessLog)getAttribute(ATTRIBUTE_ACCESSLOG);
		server.asyncRead(null);
		return true;//クライアントからもデータを受け付ける
	}
	
	/**
	 * フロントCLからのリクエストメッセージ
	 * SSLデコードは、WebHandlerで処理、平文化結果がこのメソッドに通知される。
	 * @param buffers
	 */
	public void onReadPlain(Object userContext,ByteBuffer[] buffers) {
		logger.debug("#readPlain client.id:"+getChannelId());
		if(!requestDecodeHeader.isParseEnd()){
			for(int i=0;i<buffers.length;i++){
				buffers[i].mark();
				boolean isEnd=requestDecodeHeader.parse(buffers[i]);
				buffers[i].reset();
				if(isEnd){
					//読みすぎたバッファは開放する
					requestDecodeHeader.getBodyBuffer();
					break;
				}
			}
		}
		server.asyncWrite(null, buffers);
		client.asyncRead(null);
		lastIo=System.currentTimeMillis();
		return;
	}
	
	public void onClosed(Object userContext) {
		logger.debug("#close client.id:"+getChannelId());
		if(requestDecodeHeader.isParseEnd()){
			AccessLog accessLog=getAccessLog();
			String requestLine=accessLog.getRequestLine();
			if(requestLine==null){
				logger.error("onClosed requestLine is null");
				requestLine="[null]";
			}
			//もともとCONNECT...が入っているはず
			//不思議だがrequestLineがnullとなる事がある？？？
			StringBuffer sb=new StringBuffer(requestLine);
			sb.append("[");
			sb.append(requestDecodeHeader.getRequestLine());
			sb.append("]");
			accessLog.setRequestLine(sb.toString());
			requestDecodeHeader.recycle();
		}
		super.onClosed(userContext);
		server.asyncClose(null);
	}
	
	public void onFinished() {
		logger.debug("#finished client.id:"+getChannelId());
		Store readPeek=popReadPeekStore();
		readPeek.close();
		Store writePeek=popWritePeekStore();
		writePeek.close();
		super.onFinished();
	}
	
	public void onTimeout(Object userContext) {
		logger.debug("#timeout client.id:"+getChannelId());
		//SslHandlerは、userContextをSSLCTX_READ_NETWORKにしてreadする
		if(userContext==SslAdapter.SSLCTX_READ_NETWORK){
			long now=System.currentTimeMillis();
			if( (now-lastIo)<readTimeout ){
				client.asyncRead(userContext);
				return;
			}
		}
		logger.warn("client timeout."+userContext);
		client.asyncClose(userContext);
	}
	
	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failure client.id:"+getChannelId(),t);
		client.asyncClose(userContext);
	}
	
	private class SslServer extends SslHandler {
		boolean isHandshaked=false;
		private HeaderParser headerParser=new HeaderParser();
		
		public void recycle() {
//			setPoolStauts(POOLST_NOT_POOL_OBJECT);
			headerParser.recycle();
			super.recycle();
		}
		
		public void onConnected(Object userContext) {
			logger.debug("#connected server.id:"+getChannelId()+":client id:"+client.getChannelId());
			isConnected=true;
			if(isUseProxy){
				//TODO back proxyが認証を必要とする場合どうする？
				//CONNECTION メソッドをデリゲート
				ByteBuffer[] headerBuffers=requestParser.getHeaderBuffer();
				asyncWrite(null,headerBuffers);
				isProxyConnect=false;
				asyncRead(null);
			}else{//直接SSLサーバと通信する処理
				isHandshaked=false;
				sslOpen(true);
			}
		}
		
		private static final String SSL_PROXY_OK_CONTEXT="sslProxyOkContext";
		
		/**
		 * バックshakehand完了メソッド
		 * 本当は、フロントとバックのshkehandを同時に行った方が効率がよいかはずだがシーケンスが面倒
		 * まずは、バックのshkehandを完了した後、フロントのshakehandを実行するようにプログラム
		 */
		public boolean onHandshaked() {
			logger.debug("#handshaked server.id:"+getChannelId());
			isHandshaked=true;
			client.setStatusCode("200");//アクセスログに記録するためWebHandlerに通知
			client.asyncWrite(SSL_PROXY_OK_CONTEXT, BuffersUtil.toByteBufferArray(ByteBuffer.wrap(ProxyOkResponse)));
			//サーバ側からのデータはまだ要求しない。clientとの接続が完了した時に要求する
			return false;
		}

		@Override
		public void onWrittenPlain(Object userContext) {
			logger.debug("#writtenPlain server.id:"+getChannelId());
			if(userContext==SSL_PROXY_OK_CONTEXT){
				client.sslOpen(false);
			}
		}

		public void onRead(Object userContext,ByteBuffer[] buffers){
			logger.debug("#read server.cid:"+getChannelId());
			if(isUseProxy && isProxyConnect==false){
				for(int i=0;i<buffers.length;i++){
					headerParser.parse(buffers[i]);
				}
				PoolManager.poolArrayInstance(buffers);
				if(headerParser.isParseEnd()){
					if(headerParser.isParseError()){
						logger.warn("ssl proxy header error");
						client.completeResponse("500","fail to ssl proxy connect");
						return;
					}else{
						isProxyConnect=true;
					}
				}else{
					//まだSSLモードに入っていないので直接asyncReadを呼んでよい
					asyncRead(null);
					return;
				}
				String statusCode=headerParser.getStatusCode();
				if(!"200".equals(statusCode)){
					client.completeResponse(statusCode,"fail to ssl proxy connect".getBytes());
					return;
				}
				isHandshaked=false;
				sslOpenWithBuffer(true, buffers);
				return;
			}
			super.onRead(userContext, buffers);
		}
		
		/**
		 * バックサーバからの応答メッセージ
		 * @param buffers
		 */
		public void onReadPlain(Object userContext,ByteBuffer[] buffers) {
			logger.debug("#readPlain server.cid:"+getChannelId());
			long length=BuffersUtil.remaining(buffers);
			client.asyncWrite(null, buffers);
			client.responseBodyLength(length);
			server.asyncRead(null);
			lastIo=System.currentTimeMillis();
		}
		
		//orderしていないタイミングで回線が切断されると、
		//onClosedが通知されないタイミングがある。
		public void onFinished(){
			logger.debug("#finished server.id:"+getChannelId());
			if(!isConnected || !isHandshaked){
				//connect時のエラー
				client.completeResponse("500","fail to connect");
			}else{
				isConnected=false;
				client.responseEnd();
			}
			super.onFinished();
		}
		
		/* sslHandshake中のエラーもここに通知される */
		public void onFailure(Object userContext,Throwable t){
			logger.debug("#failure server.id:"+getChannelId(),t);
			server.asyncClose(userContext);
		}
		
		public void onTimeout(Object userContext) {
			logger.debug("#timeout server.id:"+getChannelId());
			//SslHandlerは、userContextをSSLCTX_READ_NETWORKにしてreadする
			if(userContext==SslAdapter.SSLCTX_READ_NETWORK){
				long now=System.currentTimeMillis();
				if( (now-lastIo)<readTimeout ){
					server.asyncRead(userContext);
					return;
				}
			}
			logger.warn("server timeout."+userContext);
			server.asyncClose(userContext);
		}

		public SSLEngine getSSLEngine() {
			return config.getSslEngine(null);
		}
	}
}
