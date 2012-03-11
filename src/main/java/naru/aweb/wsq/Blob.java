package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;

import naru.async.pool.BuffersUtil;

/**
 * binaryMessage�̃C���^�t�F�[�X�ɗ��p
 * ���M���ɂ́A�f�[�^�̎������𔻒f����
 * 1)connect���Ă��Ȃ�subId����buffer�Ŏ��ƃ��������p���N����
 * 2)subId����File��read����͔̂����
 * 
 * �f�[�^�̎������͈ȉ���3��
 * 1)buffer
 * 2)file
 * 3)blobFile offset
 * 
 * �ŏ��̎�����2)�����e����
 * @author Naru
 */
public class Blob {
	private String mimeType;
	private String jsType;
	private String name;
	private long length;
	
	/* buffer�͏�ɐݒ肳��Ă���Ƃ͌���Ȃ��@*/
	private ByteBuffer[] buffer;
	
	private BlobFile blobFile;
	private long offset;
	
	public Blob(ByteBuffer[] buffer){
		this.buffer=buffer;
		this.length=BuffersUtil.remaining(buffer);
	}
	
	public Blob(File file){
		this(new BlobFile(file),0,file.length());
	}
	
	public String getMimeType() {
		return mimeType;
	}

	public String getJsType() {
		return jsType;
	}

	public String getName() {
		return name;
	}

	public void setMimeType(String mimeType) {
		this.mimeType = mimeType;
	}

	public void setJsType(String jsType) {
		this.jsType = jsType;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Blob(BlobFile blobFile,long offset,long length){
		blobFile.addRef(this);
		this.blobFile=blobFile;
		this.offset=offset;
		this.length=length;
	}
	
	public ByteBuffer[] read(){
		if(buffer!=null){
			return buffer;
		}if(blobFile!=null){
			return blobFile.read(offset, length);
		}
		return null;
	}
}
