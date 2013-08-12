package naru.aweb.link.api;

import java.util.Set;

public interface LinkletCtx{
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
	public int message(Object data,String subname,Set<LinkPeer> exceptPeers);
	public int message(Object data,String subname,LinkPeer exceptPeer);
	
	/**
	 * 
	 * @param data
	 * @param peers
	 * @param excptPeers
	 * @return
	 */
	public int message(Object data,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers);
	public int message(Object data,Set<LinkPeer> peers,LinkPeer exceptPeer);
	
	/**
	 * �u���E�U��blob���_�E�����[�h������
	 * blob�͏������������B
	 * (download�Ăяo���������blob���K�v�ȏꍇ�́A�Q�ƃJ�E���^�[��ref�ɂĒ����v)
	 * @param blob
	 * @param peers
	 * @param exceptPeers
	 * @return
	 */
	public int download(Blob blob,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers);
	
	public String getQname();
	public Set<LinkPeer> getPeers();
	public Set<LinkPeer> getPeers(String subname);
	public boolean setInterval(long interval);
	public boolean terminate();
	
	/**
	 * �����qname�ɓo�^����Ă���ʂ�palet���擾����
	 * @param subname
	 * @return�@palet
	 */
	public Linklet getPalet(String subname);
	
	public Object getAttribute(String name);
	public void setAttribute(String name, Object value);
	
}
