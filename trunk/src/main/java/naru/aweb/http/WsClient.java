package naru.aweb.http;

import naru.async.cache.CacheBuffer;


/**
 * Web�N���C�A���g�̒ʒm�C���^�t�F�[�X
 * WebClientHandler�ɓn���ʒm���󂯂�
 * 
 * @author Naru
 *
 */
public interface WsClient {
	//�����I��connect�����Ƃ�
	public void onWcConnected(Object userContext);
	//proxy����200���ԋp���ꂽ��
	public void onWcProxyConnected(Object userContext);
	//sslHandshake������������
	public void onWcSslHandshaked(Object userContext);
	//response header����M�����Ƃ�
	public void onWcResponseHeader(Object userContext,HeaderParser responseHeader);
	//websocket handshake������������
	public void onWcHandshaked(Object userContext,String subprotocol);
	
	//�I����
	public void onWcClose(Object userContext,int stat,short closeCode,String colseReason);
	//�G���[�I����
	public void onWcFailure(Object userContext,int stat,Throwable t);
	//���b�Z�[�W��M��
	public void onWcMessage(Object userContext,String message);
	//���b�Z�[�W��M��
	public void onWcMessage(Object userContext,CacheBuffer message);
	//header�������݂�����������
	public void onWcWrittenHeader(Object userContext);
}
