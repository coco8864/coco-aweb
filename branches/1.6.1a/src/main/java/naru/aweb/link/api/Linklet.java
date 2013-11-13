package naru.aweb.link.api;

/**
 * link�A�v���̃T�[�o��<br/>
 * ���̃C���X�^���X��implement���ăT�[�o����link�A�v�����L�q����<br/>
 * pub/sub���f���̃g�s�b�N�Ɉʒu����<br/>
 * qname/subname�P�ʂŃC���X�^���X�������Bservlet�̃��C�t�T�C�N���Ɏ��Ă���B<br/>
 * �u���E�U����̃A�N�V������onXX���\�b�h�ɃC�x���g�Ƃ��Ēʒm�����B<br/>
 * @author naru
 *
 */
public interface Linklet {
	/**
	 * �T�[�r�X�J�n���ɌĂяo�����B<br/>
	 * @param qname �S������qname
	 * @param subname �S������subname,null�̏ꍇ��rootLinklet
	 * @param ctx �u���E�U�ɃA�N�V�������邽�߂̃n���h���[
	 */
	void init(String qname,String subname,LinkletCtx ctx);
	
	/**
	 * �T�[�r�X�I�����ɌĂяo�����B<br/>
	 * @param reason �I�����R
	 */
	void term(String reason);
	
	/**
	 * ����I�ɌĂяo�����C�x���g�ʒm<br/>
	 * LinkletCtx#setInterval�ŌĂяo���Ԋu���w�肷��B<br/>
	 * @see LinkletCtx#setInterval
	 */
	void onTimer();
	
	/**
	 * �u���E�U��subscribe���ɌĂяo�����C�x���g�ʒm<br/>
	 * @param peer subscribe�����[�����
	 */
	void onSubscribe(LinkPeer peer);
	
	/**
	 * �u���E�U��unsubscribe���ɌĂяo�����C�x���g�ʒm<br/>
	 * @param peer unsubscribe���ꂽ�[�����
	 * @param reason ����
	 */
	void onUnsubscribe(LinkPeer peer,String reason);
	
	/**
	 * �u���E�U��publish�����ۂɌĂяo�����C�x���g�ʒm<br/>
	 * ���M�f�[�^�ɂ́ABlob��Date�^�̃f�[�^���܂܂��ꍇ������B<br />
	 * data�́A���\�b�h���A���������B���\�b�h���A������p����ꍇ�́Adata.ref���\�b�h���Ăяo�����p���Ă��������B
	 * ���̌�A�K�v�Ȃ��Ȃ����^�C�~���O��unref���\�b�h�ŉ�����Ă��������B<br />
	 * @param peer�@���M�����[�����A�T�[�o������message�����ꍇ�́ALinkSession���ݒ肳��Ȃ��B
	 * @param data�@���M���ꂽ�f�[�^
	 */
	void onPublish(LinkPeer peer,LinkMsg data);
	
	/**
	 * �u���E�U��publish�����ۂɌĂяo�����C�x���g�ʒm<br/>
	 * @param peer�@���M�����[�����A�T�[�o������message�����ꍇ�́ALinkSession���ݒ肳��Ȃ��B
	 * @param data�@���M���ꂽ������f�[�^
	 */
	void onPublish(LinkPeer peer,String data);
}
