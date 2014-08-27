package naru.aweb.http;

import java.nio.ByteBuffer;

import naru.aweb.util.HeaderParser;


/**
 * Webクライアントの通知インタフェース
 * WebClientHandlerに渡し通知を受ける
 * 
 * @author Naru
 *
 */
public interface WebClient {
	public void onWebConnected(Object userContext);

	public void onWebProxyConnected(Object userContext);

	public void onWebHandshaked(Object userContext);
	
	/**
	 *  requestHeaderを書き込んだ事を通知 WebClientHandler#startRequestに対応
	 */
	public void onWrittenRequestHeader(Object userContext);
	
	/**
	 *  requestBodyを書き込んだ事を通知 WebClientHandler#requestBodyに対応
	 */
	public void onWrittenRequestBody(Object userContext);
	
	/**
	 * レスポンスヘッダの受信を通知　WebClientHandler#doRequestに対応
	 * @param responseHeader レスポンスヘッダ
	 */
	public void onResponseHeader(Object userContext,HeaderParser responseHeader);
	
	/**
	 * レスポンスbodyの受信を通知　WebClientHandler#startRequestに対応
	 * chunkはデコードされて通知される。
	 * @param buffer　受信したbodyデータ
	 */
	public void onResponseBody(Object userContext,ByteBuffer[] buffer);
	
	/**
	 * 全レスポンスを受信しきった事を通知
	 */
	public void onRequestEnd(Object userContext,int stat);
	
	/**
	 * リクエスト処理中にエラーが発生したことを通知
	 * stat:直前の状態を
	 */
	public void onRequestFailure(Object userContext,int stat,Throwable t);
}
