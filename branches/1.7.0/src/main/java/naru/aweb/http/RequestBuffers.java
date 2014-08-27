package naru.aweb.http;

import java.nio.ByteBuffer;

import naru.async.pool.PoolBase;

/*
 *　同じ意味を持つrequestでも以下の可変要素がある。
 * 1)リクエストラインを動的に変更する。
 * 2)cookie値
 * 3)bodyを動的に変更する。
 * 4)3に伴うcontent-lengthヘッダ
 * 5)web認証ヘッダ
 * 6)proxy認証ヘッダ
 * 逆に考えると上記以外は、固定バッファとして持つことができる
 */
public class RequestBuffers extends PoolBase {
	private ByteBuffer fixedRequestLine;/* requestLineが固定の場合 */
	private String requestLineTemplate;/* requestLineが可変な場合 */
	private ByteBuffer[] fixedHeader;/* headerの固定部分(cookie content-lengthヘッダ以外) */
	private ByteBuffer[] fixedBody;/* bodyが固定の場合 */
	private String bodyTemplate;/* bodyが可変な場合 */
	private long bodyStoreId;/* 巨大なリクエストを送り付ける場合 */
	
	public ByteBuffer[] getHeaderBuffers(){
		return null;
	}
	public ByteBuffer[] getBodyBuffers(){
		return null;
	}

}
