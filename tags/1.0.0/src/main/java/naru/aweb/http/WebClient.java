package naru.aweb.http;

import java.nio.ByteBuffer;


/**
 * Web�N���C�A���g�̒ʒm�C���^�t�F�[�X
 * WebClientHandler�ɓn���ʒm���󂯂�
 * 
 * @author Naru
 *
 */
public interface WebClient {
	/**
	 *  requestHeader���������񂾎���ʒm WebClientHandler#startRequest�ɑΉ�
	 */
	public void onWrittenRequestHeader(Object userContext);
	
	/**
	 *  requestBody���������񂾎���ʒm WebClientHandler#requestBody�ɑΉ�
	 */
	public void onWrittenRequestBody(Object userContext);
	
	/**
	 * ���X�|���X�w�b�_�̎�M��ʒm�@WebClientHandler#doRequest�ɑΉ�
	 * @param responseHeader ���X�|���X�w�b�_
	 */
	public void onResponseHeader(Object userContext,HeaderParser responseHeader);
	
	/**
	 * ���X�|���Xbody�̎�M��ʒm�@WebClientHandler#startRequest�ɑΉ�
	 * chunk�̓f�R�[�h����Ēʒm�����B
	 * @param buffer�@��M����body�f�[�^
	 */
	public void onResponseBody(Object userContext,ByteBuffer[] buffer);
	
	/**
	 * �S���X�|���X����M������������ʒm
	 */
	public void onRequestEnd(Object userContext);
	
	/**
	 * ���N�G�X�g�������ɃG���[�������������Ƃ�ʒm
	 */
	public void onRequestFailure(Object userContext,Throwable t);
}