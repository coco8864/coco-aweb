package naru.aweb.pa.api;

import java.util.Set;

public interface PaletCtx{
	/**
	 * 全peerにmessageを送信する
	 * dataはMap型を想定、Blobが含まれていた場合は、処理後解放さる。
	 * (message呼び出し後もそのblobが必要な場合は、参照カウンターをrefにて調整要)
	 * @param data
	 * @return
	 */
	public int message(Object data);
	
	/**
	 * subnameで指定されたpeerにmessageを送信する
	 * @param data
	 * @param subname
	 * @return
	 */
	public int message(Object data,String subname);
	
	/**
	 * subnameで指定されたpeer(excptPeersを除く)にmessageを送信する
	 * @param data
	 * @param subname
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,String subname,Set<PaPeer> exceptPeers);
	public int message(Object data,String subname,PaPeer exceptPeer);
	
	/**
	 * 
	 * @param data
	 * @param peers
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,Set<PaPeer> peers,Set<PaPeer> exceptPeers);
	public int message(Object data,Set<PaPeer> peers,PaPeer exceptPeer);
	
	/**
	 * ブラウザにblobをダウンロードさせる
	 * blobは処理後解放される。
	 * (download呼び出し後もそのblobが必要な場合は、参照カウンターをrefにて調整要)
	 * @param blob
	 * @param peers
	 * @param exceptPeers
	 * @return
	 */
	public int download(Blob blob,Set<PaPeer> peers,Set<PaPeer> exceptPeers);
	
	public String getQname();
	public Set<PaPeer> getPeers();
	public Set<PaPeer> getPeers(String subname);
	public boolean setInterval(long interval);
	public boolean terminate();
	
	/**
	 * 同一のqnameに登録されている別のpaletを取得する
	 * @param subname
	 * @return　palet
	 */
	public Palet getPalet(String subname);
	
	public Object getAttribute(String name);
	public void setAttribute(String name, Object value);
	
}
