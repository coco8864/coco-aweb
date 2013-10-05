package naru.aweb.link.api;

import java.util.Set;

/**
 * link�A�v���̃T�[�o��<br/>
 * ���̃I�u�W�F�N�g�𑀍삵�ău���E�U�Ƀ��b�Z�[�W�𑗐M����B<br/>
 * qname���ɍ쐬����A�֘A�Â��Ă��镡����linklet�ŋ��L�����B<br/>
 * �eLinklet��init���\�b�h�ɒʒm�����B<br/>
 * @author naru
 *
 */
public interface LinkletCtx{
	/**
	 * subscribe���̑Speer��message�𑗐M����<br/>
	 * data��Map�^��z��ABlob���܂܂�Ă����ꍇ�́A������������B<br/>
	 * (message�Ăяo���������blob���K�v�ȏꍇ�́A�Q�ƃJ�E���^�[��ref�ɂĒ����v)<br/>
	 * @param data ���M�f�[�^
	 * @return ���M�����[����
	 */
	public int message(Object data);
	
	/**
	 * subname��subscribe���̒[����message�𑗐M����<br/>
	 * @param data ���M�f�[�^
	 * @param subname
	 * @return�@���M�����[����
	 */
	public int message(Object data,String subname);
	
	/**
	 * excptPeers�ȊO��subname��subscribe���̒[���ŁAmessage�𑗐M����<br/>
	 * @param data ���M�f�[�^
	 * @param subname
	 * @param excptPeers�@���M���Ȃ��[�����
	 * @return�@���M�����[����
	 */
	public int message(Object data,String subname,Set<LinkPeer> exceptPeers);
	
	/**
	 * excptPeers�ȊO��subname��subscribe���̒[���ɁAmessage�𑗐M����<br/>
	 * @param data ���M�f�[�^
	 * @param subname
	 * @param excptPeer�@���M���Ȃ��[�����
	 * @return�@���M�����[����
	 */
	public int message(Object data,String subname,LinkPeer exceptPeer);
	
	/**
	 * excptPeers�ȊO��peers�Ɋ܂܂��[���ɁAmessage�𑗐M����<br/>
	 * @param data ���M�f�[�^
	 * @param peers ���M����[�����
	 * @param excptPeers�@���M���Ȃ��[�����
	 * @return�@���M�����[����
	 */
	public int message(Object data,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers);
	
	/**
	 * excptPeers�ȊO��peers�Ɋ܂܂��[���ɁAmessage�𑗐M����<br/>
	 * @param data ���M�f�[�^
	 * @param peers ���M����[�����
	 * @param excptPeer�@���M���Ȃ��[�����
	 * @return�@���M�����[����
	 */
	public int message(Object data,Set<LinkPeer> peers,LinkPeer exceptPeer);
	
	/**
	 * �u���E�U��blob���_�E�����[�h������<br/>
	 * blob�͏������������B<br/>
	 * (download�Ăяo���������blob���K�v�ȏꍇ�́A�Q�ƃJ�E���^�[��ref�ɂĒ����v)<br/>
	 * @param blob �_�E�����[�h������Blob�f�[�^
	 * @param peers ���M����[�����
	 * @param excptPeer�@���M���Ȃ��[�����
	 * @return�@���M�����[����
	 */
	public int download(Blob blob,Set<LinkPeer> peers,Set<LinkPeer> exceptPeers);
	
	/**
	 * qname��ԋp���܂��B<br/>
	 * @return�@qname
	 */
	public String getQname();
	
	/**
	 * ����qname��subscribe���̒[������ԋp���܂��B<br/>
	 * @return �[�����
	 */
	public Set<LinkPeer> getPeers();
	
	/**
	 * ����subname��subscribe���̒[������ԋp���܂��B<br/>
	 * @return �[�����
	 */
	public Set<LinkPeer> getPeers(String subname);
	
	/**
	 * �Ď��Ԋu���w�肵�܂�<br/>
	 * @param interval�@�Ԋu(�~���b)
	 * @return �ݒ�̉�
	 */
	public boolean setInterval(long interval);
	
	/**
	 * ����qname��linklet���~���܂��B<br/>
	 * @return ��~�̉�
	 */
	public boolean terminate();
	
	/**
	 * �����qname�ɓo�^����Ă���ʂ�palet���擾����<br/>
	 * @param subname
	 * @return�@linklet
	 */
	public Linklet getLinklet(String subname);
	
	/**
	 * ���������擾���܂��B<br/>
	 * @param name
	 * @return value
	 */
	public Object getAttribute(String name);
	
	/**
	 * ��������ݒ肵�܂�<br/>
	 * @param name
	 * @param value
	 */
	public void setAttribute(String name, Object value);
}
