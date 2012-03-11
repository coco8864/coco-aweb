package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;

import naru.async.pool.BuffersUtil;

/**
 * binaryMessageのインタフェースに利用
 * 送信時には、データの持ち方を判断する
 * 1)connectしていないsubId毎にbufferで持つとメモリがパンクする
 * 2)subId毎にFileをreadするのは非効率
 * 
 * データの持ち方は以下の3つ
 * 1)buffer
 * 2)file
 * 3)blobFile offset
 * 
 * 最初の実装は2)を許容する
 * @author Naru
 */
public class Blob {
	private String mimeType;
	private String jsType;
	private String name;
	private long length;
	
	/* bufferは常に設定されているとは限らない　*/
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
