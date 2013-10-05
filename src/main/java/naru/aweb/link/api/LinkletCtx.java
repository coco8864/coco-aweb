package naru.aweb.link.api;

import java.util.Set;

/**
 * linkアプリのサーバ側<br/>
 * このオブジェクトを操作してブラウザにメッセージを送信する。<br/>
 * qname毎に作成され、関連づいている複数のlinkletで共有される。<br/>
 * 各Linkletのinitメソッドに通知される。<br/>
 * @author naru
 *
 */
public interface LinkletCtx{
	/**
	 * subscribe中の全peerにmessageを送信する<br/>
	 * dataはMap型を想定、Blobが含まれていた場合は、処理後解放さる。<br/>
	 * (message呼び出し後もそのblobが必要な場合は、参照カウンターをrefにて調整要)<br/>
	 * @param data 送信データ
	 * @return 送信した端末数
	 */
	public int message(Object data);
	
	/**
	 * subnameにsubscribe中の端末にmessageを送信する<br/>
	 * @param data 送信データ
	 * @param subname
	 * @return　送信した端末数
	 */
	public int message(Object data,String subname);
	
	/**
	 * excptPeers以外でsubnameにsubscribe中の端末で、messageを送信する<br/>
	 * @param data 送信データ
	 * @param subname
	 * @param excptPeers　送信しない端末情報
	 * @return　送信した端末数
	 */
	public int message(Object data,String subname,Set<LinkPeer> exceptPeers);
	
	/**
	 * excptPeers以外でsubnameにsubscribe中の端末に、messageを送信する<br/>
	 * @param data 送信データ
	 * @param subname
	 * @param excptPeer　送信しない端末情報
	 * @return　送信した端末数
	 */
	public int message(Object data,String subname,LinkPeer exceptPeer);
	
	/**
	 * excptPeers以外でpeersに含まれる端末に、messageを送信する<br/>
	 * @param data 送信データ
	 * @param peers 送信する端末情報
	 * @param excptPeers　送信しない端末情報
	 * @return　送信した端末数
	 */
	public int message(Object data,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers);
	
	/**
	 * excptPeers以外でpeersに含まれる端末に、messageを送信する<br/>
	 * @param data 送信データ
	 * @param peers 送信する端末情報
	 * @param excptPeer　送信しない端末情報
	 * @return　送信した端末数
	 */
	public int message(Object data,Set<LinkPeer> peers,LinkPeer exceptPeer);
	
	/**
	 * ブラウザにblobをダウンロードさせる<br/>
	 * blobは処理後解放される。<br/>
	 * (download呼び出し後もそのblobが必要な場合は、参照カウンターをrefにて調整要)<br/>
	 * @param blob ダウンロードさせるBlobデータ
	 * @param peers 送信する端末情報
	 * @param excptPeer　送信しない端末情報
	 * @return　送信した端末数
	 */
	public int download(Blob blob,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers);
	
	/**
	 * qnameを返却します。<br/>
	 * @return　qname
	 */
	public String getQname();
	
	/**
	 * 現在qnameにsubscribe中の端末情報を返却します。<br/>
	 * @return 端末情報
	 */
	public Set<LinkPeer> getPeers();
	
	/**
	 * 現在subnameにsubscribe中の端末情報を返却します。<br/>
	 * @return 端末情報
	 */
	public Set<LinkPeer> getPeers(String subname);
	
	/**
	 * 監視間隔を指定します<br/>
	 * @param interval　間隔(ミリ秒)
	 * @return 設定の可否
	 */
	public boolean setInterval(long interval);
	
	/**
	 * このqnameのlinkletを停止します。<br/>
	 * @return 停止の可否
	 */
	public boolean terminate();
	
	/**
	 * 同一のqnameに登録されている別のpaletを取得する<br/>
	 * @param subname
	 * @return　linklet
	 */
	public Linklet getLinklet(String subname);
	
	/**
	 * 属性情報を取得します。<br/>
	 * @param name
	 * @return value
	 */
	public Object getAttribute(String name);
	
	/**
	 * 属性情報を設定します<br/>
	 * @param name
	 * @param value
	 */
	public void setAttribute(String name, Object value);
}
