package naru.aweb.wsq;

import java.io.File;
import java.nio.ByteBuffer;

public class BinaryMessage {
	private boolean isBlob;
	private String meta;
	private File[] blobs;
	private ByteBuffer[] buffers;
	
	public BinaryMessage(String meta,File[] blobs){
		this.isBlob=true;
		this.meta=meta;
		this.blobs=blobs;
	}
	public BinaryMessage(String meta,File blob){
		this.isBlob=true;
		this.meta=meta;
		this.blobs=new File[]{blob};
	}
	public BinaryMessage(String meta,ByteBuffer[] buffers){
		this.isBlob=false;
		this.meta=meta;
		this.buffers=buffers;
	}
	public BinaryMessage(String meta,ByteBuffer buffer){
		this.isBlob=false;
		this.meta=meta;
		this.buffers=new ByteBuffer[]{buffer};
	}
	public boolean isBlob() {
		return isBlob;
	}
	public String getMeta() {
		return meta;
	}
	public File[] getBlobs() {
		return blobs;
	}
	public ByteBuffer[] getBuffers() {
		return buffers;
	}
}
