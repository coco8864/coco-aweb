package naru.aweb.http;

import java.nio.ByteBuffer;


/**
 * Web�N���C�A���g�̒ʒm�C���^�t�F�[�X
 * WebClientHandler�ɓn���ʒm���󂯂�
 * 
 * @author Naru
 *
 */
public interface WsClient {
	public void onWsConnected(Object userContext);

	public void onWsProxyConnected(Object userContext);

	public void onSslHandshaked(Object userContext);
	
	public void onWsHandshaked(Object userContext);
	
	
	public void onWsClose(Object userContext);
	
	public void onMessage(Object userContext,String message);
	public void onMessage(Object userContext,ByteBuffer[] message);
	
	public void onWsFailure(Object userContext,int stat,Throwable t);
}
