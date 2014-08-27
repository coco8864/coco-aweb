package naru.aweb.http;

import java.nio.ByteBuffer;

import naru.async.pool.PoolBase;

/*
 *�@�����Ӗ�������request�ł��ȉ��̉ϗv�f������B
 * 1)���N�G�X�g���C���𓮓I�ɕύX����B
 * 2)cookie�l
 * 3)body�𓮓I�ɕύX����B
 * 4)3�ɔ���content-length�w�b�_
 * 5)web�F�؃w�b�_
 * 6)proxy�F�؃w�b�_
 * �t�ɍl����Ə�L�ȊO�́A�Œ�o�b�t�@�Ƃ��Ď����Ƃ��ł���
 */
public class RequestBuffers extends PoolBase {
	private ByteBuffer fixedRequestLine;/* requestLine���Œ�̏ꍇ */
	private String requestLineTemplate;/* requestLine���ςȏꍇ */
	private ByteBuffer[] fixedHeader;/* header�̌Œ蕔��(cookie content-length�w�b�_�ȊO) */
	private ByteBuffer[] fixedBody;/* body���Œ�̏ꍇ */
	private String bodyTemplate;/* body���ςȏꍇ */
	private long bodyStoreId;/* ����ȃ��N�G�X�g�𑗂�t����ꍇ */
	
	public ByteBuffer[] getHeaderBuffers(){
		return null;
	}
	public ByteBuffer[] getBodyBuffers(){
		return null;
	}

}
