package naru.aweb.pa.api;

import java.util.Set;

public interface PaletCtx{
	/**
	 * �Speer��message�𑗐M����
	 * data��Map�^��z��ABlob���܂܂�Ă����ꍇ�́A������������B
	 * (message�Ăяo���������blob���K�v�ȏꍇ�́A�Q�ƃJ�E���^�[��ref�ɂĒ����v)
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
	 * �u���E�U��blob���_�E�����[�h������
	 * blob�͏������������B
	 * (download�Ăяo���������blob���K�v�ȏꍇ�́A�Q�ƃJ�E���^�[��ref�ɂĒ����v)
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
	 * �����qname�ɓo�^����Ă���ʂ�palet���擾����
	 * @param subname
	 * @return�@palet
	 */
	public Palet getPalet(String subname);
	
	public Object getAttribute(String name);
	public void setAttribute(String name, Object value);
	
}
