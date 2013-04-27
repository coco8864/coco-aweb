package naru.aweb.pa.api;

public interface Palet {
	/**
	 * call when this object is loaded
	 * subname��null�̏ꍇ�́ArootPalet
	 * @param qname
	 * @param subname
	 * @param ctx
	 */
	void init(String qname,String subname,PaletCtx ctx);
	
	/**
	 * call when this object is unloaded
	 */
	void term(String reason);
	
	/**
	 * call when time interval
	 * @see PaletCtx#setInterval
	 */
	void onTimer();
	
	/**
	 * call when subscribe this queue and subname
	 * @param peer
	 */
	void onSubscribe(PaPeer peer);
	void onUnsubscribe(PaPeer peer,String reason);
	
	/**
	 * Connection Deffed��publish�����ꍇ�́Apeer��subname���ݒ肳��Ȃ��B
	 * ���̏ꍇ�Apeer�ɒ���message���邱�Ƃ��ł��Ȃ�
	 * �T�[�o������message�����ꍇ�́APaSession���ݒ肳��Ȃ��B
	 * ���̏ꍇ�Apeer��message�����qname,subname�ɍ��v����peer�ɍL�񂳂��
	 * data�́ABlob��Date���܂ޏꍇ�AHashMap,����ȊO�̏ꍇ�AJSONObject�Œʒm
	 * @param peer
	 * @param data
	 */
	void onPublish(PaPeer peer,PaMsg data);
}
