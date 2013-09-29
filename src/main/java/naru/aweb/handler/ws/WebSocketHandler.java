/**
 * 
 */
package naru.aweb.handler.ws;

import java.nio.ByteBuffer;

import naru.async.AsyncBuffer;
import naru.async.cache.CacheBuffer;
import naru.aweb.auth.LogoutEvent;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.RequestContext;
import naru.aweb.util.HeaderParser;

import org.apache.log4j.Logger;

/**
 * websocketに対してレスポンスするhandlerの基底クラス<br/>
 * httpリクエストやproxyリクエストに対しては、WebServerHandlerとしても動作できる。<br/>
 * このクラスを継承して、onWsOpen(websocketの開始),onWsClose(websocketの終了),onMessage(websocketからのデータ通知)
 * を実装することにより、websocket対応のアプリケーションが作成できる。
 * 
 * @author Naru
 */
public abstract class WebSocketHandler extends WebServerHandler implements LogoutEvent{
	private static Logger logger=Logger.getLogger(WebSocketHandler.class);
	protected boolean isWs;//WebSocketをハンドリングしているか否か
	private boolean isHandshaked;
	private WsProtocol wsProtocol;
	
	/* このクラスを継承したapplicationから呼び出される */
	/* メッセージを送信する場合(text) */
	/* 同時にpostMessageを受け付ける事はできないのでsynchronized */
	/**
	 * クライアントにtextメッセージを送信します。
	 * @param message 送信メッセージ
	 */
	public synchronized void postMessage(String message){
		wsProtocol.postMessage(message);
	}
	
	/* メッセージを送信する場合(binary) */
	/* 同時にpostMessageを受け付ける事はできないのでsynchronized */
	/**
	 * クライアントにbinaryメッセージを送信します。
	 * @param message 送信メッセージ
	 */
	public synchronized void postMessage(ByteBuffer[] message){
		wsProtocol.postMessage(message);
	}
	
	/**
	 * クライアントにbinaryメッセージを送信します。
	 * @param message 送信メッセージ
	 */
	public synchronized void postMessage(AsyncBuffer message){
		wsProtocol.postMessage(message);
	}
	
	/* 通信をやめる場合 */
	/**
	 * websocket接続前後の状態に応じて回線を切断します。
	 * @param statusCode websocket接続前の場合切断時のhttpステータスコード
	 * @param code websockte接続後の場合、クライアントのcloseに通知するcode
	 * @param reason　websocket接続後の場合、クライアントのcloseに通知する原因文字列
	 */
	public void closeWebSocket(String statusCode,short code,String reason){
		if(isHandshaked){
			wsProtocol.onClose(code,reason);
		}else{
			completeResponse(statusCode);
		}
	}
	
	/**
	 * websocket接続前後の状態に応じて回線を切断します。<br/>
	 * websocket接続後は正常クローズを送信
	 * @param statusCode websocket接続前の場合切断時のhttpステータスコード
	 */
	public void closeWebSocket(String statusCode){
		closeWebSocket(statusCode,WsHybiFrame.CLOSE_NORMAL,"OK");
	}
	
	/**
	 * websocket通信の開始を通知します。
	 * @param subprotocol　サブプロトコル
	 */
	public abstract void onWsOpen(String subprotocol);
	
	/**
	 * websocket通信の終了を通知します。
	 * @param code 終了コード
	 * @param reason 原因文字列
	 */
	public abstract void onWsClose(short code,String reason);
	
	
	/**
	 * textメッセージを受信したことを通知します。
	 * @param msgs 受信メッセージ
	 */
	public abstract void onMessage(String msgs);
	
	/**
	 * binaryメッセージを受信したことを通知します。
	 * @param msgs 受信メッセージ
	 */
	public abstract void onMessage(CacheBuffer  msgs);
	
	/**
	 * WebSocket接続中にセションきれた場合の通知
	 */
	public void onLogout(){
		closeWebSocket("500");
	}
	
	/*
    HTTP/1.1 101 Web Socket Protocol Handshake
    Upgrade: WebSocket
    Connection: Upgrade
    WebSocket-Origin: http://example.com
    WebSocket-Location: ws://example.com/demo
    WebSocket-Protocol: sample		 *上りにあれば下りにも必要 
	 */
	
	/**
	 * overrideしない
	 */
	public void onRequestHeader() {
		logger.debug("#doResponse.cid:"+getChannelId());
		HeaderParser requestHeader=getRequestHeader();
		if(!requestHeader.isWs()){
			super.onRequestHeader();//body到着時に継承したクラスのstartResponseReqBodyに通知される
			return;
		}
		isWs=true;
		/* ログ出力タイプを取得 */
		/* logoff時にonLogoutイベントが通知されるように設定 */
		wsProtocol=WsProtocol.createWsProtocol(requestHeader,getRequestMapping());
		if(wsProtocol==null){
			//webSocketで接続を拒否するのは決まった方法がない
			//http://blog.aklaswad.com/2012/000517.html
			completeResponse("422");//422 Protocol Extension Refused
			logger.warn("not found WebSocket Protocol");
			return;
		}
		logger.debug("wsProtocol class:"+wsProtocol.getClass().getName());
		//subprotocolを特定
		String selectSubprotocol=null;
		String reqSubprotocols=wsProtocol.getRequestSubProtocols(requestHeader);
		if(reqSubprotocols==null){
			if(wsProtocol.isUseSubprotocol()){//subprotocolを必要とするのにない
				completeResponse("422");//422 Protocol Extension Refused
				return;
			}
		}else{
			selectSubprotocol=wsProtocol.checkSubprotocol(reqSubprotocols);
			if(selectSubprotocol==null){//subprotocolが一致しない
				logger.debug("WsHybi10#suprotocol error.webSocketProtocol:"+reqSubprotocols);
				completeResponse("422");//422 Protocol Extension Refused
				return;
			}
		}
		RequestContext requestContext=getRequestContext();
		requestContext.registerLogoutEvnet(this);
		onWebSocket(requestHeader,selectSubprotocol);
	}
	
	/**
	 * websocket処理の開始<br/>
	 * handshakeはまだ完了していない。<br/>
	 * doHandshakeメソッドを呼び出すとhandshake処理を完了し、onWsOpenメソッドが呼び出される。<br/>
	 * すぐにhandshakeせずに事前処理が必要な場合にoverrideして利用
	 * @param requestHeader websocket要求ヘッダ
	 * @param subprotocol　要求されたsuprotocol
	 */
	public 	void onWebSocket(HeaderParser requestHeader,String subprotocol){
		doHandshake(subprotocol);
	}
	
	//web socketのhandshakeを実施
	/**
	 * websocketのhandshake処理を行います。<br/>
	 * handshakeに成功した場合、onWsOpenに通知されます。<br/>
	 * @param subProtocol
	 * @return　handshakeが成功した場合true
	 */
	public boolean doHandshake(String subProtocol){
		HeaderParser requestHeader=getRequestHeader();
		wsProtocol.setup(this);
		isHandshaked=wsProtocol.onHandshake(requestHeader,subProtocol);
		return isHandshaked;
	}
	
	/**
	 * データを受信したことを通知<br/>
	 * overrideしない<br/>
	 */
	public void onReadPlain(Object userContext, ByteBuffer[] buffers) {
		logger.debug("#read.cid:"+getChannelId());
		if(!isWs){
			super.onReadPlain(userContext, buffers);
			return;
		}
		wsProtocol.onBuffer(buffers);
	}
	
	/**
	 * ioが失敗したことを通知<br/>
	 * overrideしない<br/>
	 */
	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		if(isWs){
			closeWebSocket("500");
		}
		super.onFailure(userContext, t);
	}

	/**
	 * read処理がタイムアウトしたことを通知<br/>
	 * overrideしない<br/>
	 */
	public void onReadTimeout(Object userContext) {
		logger.debug("#readTimeout.cid:" +getChannelId());
		if(isWs){
			wsProtocol.onReadTimeout();
		}else{
			super.onReadTimeout(userContext);
		}
	}
	
	/**
	 * ioがタイムアウトしたことを通知。<br/>
	 * overrideしない<br/>
	 */
	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		if(isWs){
			closeWebSocket("500");
		}
		super.onTimeout(userContext);
	}

	/**
	 * 当該handlerで処置中に回線が回収された場合に通知されます。<br/>
	 * overrideする場合は、元メソッドも呼び出してください。<br/>
	 */
	@Override
	public void onFinished() {
		logger.debug("#finished client.cid:"+getChannelId());
		if(wsProtocol!=null){
			wsProtocol.onClose(WsHybiFrame.CLOSE_UNKOWN,null);
		}
		super.onFinished();
	}

	/**
	 * このオブジェクトを再利用する際に呼び出される。<br/>
	 * overrideした場合は、必ず元メソッドも呼び出してください。
	 */
	@Override
	public void recycle() {
		isWs=false;
		isHandshaked=false;
		if(wsProtocol!=null){
			wsProtocol.unref(true);
			wsProtocol=null;
		}
		super.recycle();
	}
	
	/*----asyncBuffer処理----*/
	/**
	 * posteMessageの送信完了を通知
	 */
	public void onPosted(){
	}

	/**
	 * 書き込みが完了したことを通知。<br/>
	 * overrideしない<br/>
	 */
	@Override
	public void onWrittenPlain(Object userContext) {
		if(wsProtocol!=null){
			wsProtocol.onWrittenPlain(userContext);
		}
		super.onWrittenPlain(userContext);
	}

	/**
	 * websocketのレスポンスヘッダを返却します。
	 */
	protected void flushHeaderForWebSocket(String spec,String subprotocol) {
		super.flushHeaderForWebSocket(spec, subprotocol);
	}
	
	/*
	@Override
	public void ref() {
		logger.debug("ref.cid:"+getChannelId(),new Throwable());
		super.ref();
	}

	@Override
	public boolean unref() {
		logger.debug("unref.cid:"+getChannelId(),new Throwable());
		return super.unref();
	}
	*/
}
