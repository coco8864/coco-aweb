package naru.aweb.pa;

import java.util.Set;

public interface PaletCtx{
	/**
	 * �Speer��message�𑗐M����
	 * @param data
	 * @return
	 */
	public int message(Object data);
	
	/**
	 * subname�Ŏw�肳�ꂽpeer��message�𑗐M����
	 * @param data
	 * @param subname
	 * @return
	 */
	public int message(Object data,String subname);
	
	/**
	 * subname�Ŏw�肳�ꂽpeer(excptPeers������)��message�𑗐M����
	 * @param data
	 * @param subname
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,String subname,Set<PaPeer> excptPeers);
	
	/**
	 * 
	 * @param data
	 * @param peers
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,Set<PaPeer> peers,Set<PaPeer> excptPeers);
	
	public String getQname();
	public Set<PaPeer> getPeers();
	public Set<PaPeer> getPeers(String subname);
	public boolean setInterval(long interval);
	public boolean terminate();
}
