package naru.aweb.pa;

import java.util.Set;

public class PaletCtx {
	/**
	 * �Speer��message�𑗐M����
	 * @param data
	 * @return
	 */
	public int message(Object data){
		return message(data,getPeers(),null);
	}
	
	/**
	 * subname�Ŏw�肳�ꂽpeer��message�𑗐M����
	 * @param data
	 * @param subname
	 * @return
	 */
	public int message(Object data,String subname){
		return message(data,getPeers(subname),null);
	}
	
	/**
	 * subname�Ŏw�肳�ꂽpeer(excptPeers������)��message�𑗐M����
	 * @param data
	 * @param subname
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,String subname,Set<PaPeer> excptPeers){
		return message(data,getPeers(subname),excptPeers);
	}
	
	/**
	 * 
	 * @param data
	 * @param peers
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,Set<PaPeer> peers,Set<PaPeer> excptPeers){
		int count=0;
		for(PaPeer peer:peers){
			if(excptPeers!=null && excptPeers.contains(peer)){
				continue;
			}
			peer.message(data);
			count++;
		}
		return count;
	}
	
	public String getQname(){
		return null;
	}
	
	public Set<PaPeer> getPeers(){
		return null;
	}
	
	public Set<PaPeer> getPeers(String subname){
		return null;
	}
	
	public boolean setInterval(long interval){
		return false;
	}
	
	public boolean terminate(){
		return false;
	}
}
