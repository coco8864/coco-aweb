package naru.aweb.wsq;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.cache.CacheBuffer;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
/*
 * ���̃f�[�^�͂��̐ڑ��ŗL��header
 * header��(4byte,BigEndigan)
 * header=json�f�[�^
 * {
* type:'publish',
* qname:'qname',
* subId:'subId',***
* dataType:blobMessage
* blobHeaderLength:blob�w�b�_��
* }
*/
/* ���̃f�[�^�́A�����̉���œ����f�[�^�����L�����
 * BlobMessage�̌`
 * blobHeader=json�f�[�^
 * {
 * count:�f�[�^��,
 * totalLength:���f�[�^��,
 * (isGz:gz���k�̗L��),
 * message:�C��
 * metas:[
 *  {length:1�Ԗڂ�deta��,jsType:'ArrayBuffer'|string|Blob|object,name:,mimeType:,�ȍ~�C��},
 *  {length:2�Ԗڂ�deta��,..},
 *  {length:3�Ԗڂ�deta��,..,}
 *  ]
 * }//�����܂ł�topBuffer�ɓ����Ă��鎖������
 * 1�Ԗڂ̃f�[�^
 * 2�Ԗڂ̃f�[�^
 * 3�Ԗڂ̃f�[�^
 */
public class BlobMessage extends PoolBase implements AsyncBuffer,BufferGetter{
	//parse���ɂ̂ݗL���A�o�͎��ɂ͗��p����Ȃ��iBlobMessage�̍\���v�f�ł͂Ȃ��Aparse���ʂ̎󂯓n���p�Ɏg���̂�)
	private JSONObject header;
	
	private JSONArray metas;
	private Object message;
	private List<Blob> blobs=new ArrayList<Blob>();
	private long[] offsets=null;
	private ByteBuffer blobHeaderBuffer=null;
	
	public static ByteBuffer headerBuffer(JSONObject header,BlobMessage message){
		header.element("dataType", "blobMessage");
		header.element("blobHeaderLength", message.getBlobHeaderLength());
		String headerString=header.toString();
		byte[] headerBytes=null;
		try {
			headerBytes = headerString.getBytes("uft-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException("WsqHandler onMessage");
		}
		ByteBuffer headerBuffer=PoolManager.getBufferInstance(4+headerBytes.length);
		headerBuffer.order(ByteOrder.BIG_ENDIAN);
		headerBuffer.putLong(headerBytes.length);
		headerBuffer.put(headerBytes);
		headerBuffer.flip();
		return headerBuffer;
	}
	
	private static String getString(ByteBuffer buf,int length){
		int pos=buf.position();
		if((pos+length)>buf.limit()){
			throw new UnsupportedOperationException("WsqHandler onMessage");
		}
		String result;
		try {
			result = new String(buf.array(),pos,length,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException("WsqHandler onMessage");
		}
		buf.position(pos+length);
		return result;
	}
	
	public static BlobMessage create(CacheBuffer buffer){
		if(!buffer.isInTopBuffer()){
			buffer.unref();
			throw new UnsupportedOperationException("WsqHandler onMessage");
		}
		//TODO �擪��1�o�b�t�@��header�ނ��ێ�����Ă��鎖�Ɉˑ�
		ByteBuffer[] topBufs=buffer.popTopBuffer();
		ByteBuffer topBuf=topBufs[0];
		topBuf.order(ByteOrder.BIG_ENDIAN);
		int headerLength=topBuf.getInt();
		String headerString=getString(topBuf,headerLength);
		JSONObject header=JSONObject.fromObject(headerString);
		int blobHeaderLength=header.getInt("blobHeaderLength");
		String blobHeaderString=getString(topBuf,blobHeaderLength);
		JSONObject blobHeader=JSONObject.fromObject(blobHeaderString);
		BlobMessage result=(BlobMessage)PoolManager.getInstance(BlobMessage.class);
		result.header=header;//parse���ɂ����ݒ肳���
		result.metas=blobHeader.getJSONArray("metas");
		result.message=blobHeader.get("message");
		if(blobHeader.getInt("totalLength")!=0){
//			BlobFile blobFile=BlobFile.create(data,meta.getBoolean("isGz"));
			JSONArray metas=blobHeader.getJSONArray("metas");
			long offset=4+headerLength+blobHeaderLength;
			for(int i=0;i<metas.size();i++){
				JSONObject meta=metas.getJSONObject(i);
				long length=meta.getLong("length");
				Blob blob=Blob.create(buffer,offset,length);
				offset+=length;
				blob.setName(meta.optString("name"));
				blob.setJsType(meta.optString("jsType"));
				blob.setMimeType(meta.optString("mimeType"));
				result.addBlob(blob);
			}
		}
		return result;
	}
	@Override
	public void recycle() {
		if(blobHeaderBuffer!=null){
			PoolManager.poolBufferInstance(blobHeaderBuffer);
			blobHeaderBuffer=null;
		}
		Iterator<Blob> blobItr=blobs.iterator();
		while(blobItr.hasNext()){
			Blob blob=blobItr.next();
			blobItr.remove();
			blob.unref();
		}
		offsets=null;
	}
	
	/* �ݒ胂�[�h���瑗�M���[�h�ւ̐؂�ւ� */
	public synchronized void flip(){
		if(metas==null){
			metas=new JSONArray();
		}
		int i=0;
		long totalLength=0;
		for(Blob blob:blobs){
			JSONObject meta=metas.getJSONObject(i);
			if(meta==null){
				meta=new JSONObject();
			}
			long length=blob.length();
			totalLength+=length;
			meta.element("length",length);
			meta.element("name",blob.getName());
			meta.element("jsType",blob.getJsType());
			meta.element("mimeType",blob.getMimeType());
			metas.element(i,meta);
			i++;
		}
		int count=blobs.size();
		
		JSONObject blobHeader=new JSONObject();
		blobHeader.element("message", message);
		blobHeader.element("metas", metas);
		blobHeader.element("count", blobs.size());
		blobHeader.element("totalLength", totalLength);
		
		byte[] blobHeaderBytes=null;
		try {
			blobHeaderBytes=blobHeader.toString().getBytes("utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UnsupportedEncodingException utf-8");
		}
		blobHeaderBuffer=PoolManager.getBufferInstance(blobHeaderBytes.length);
		blobHeaderBuffer.put(blobHeaderBytes);
		blobHeaderBuffer.flip();
		offsets[0]=blobHeaderBuffer.remaining();
		offsets=new long[count+1];
		i=1;
		for(Blob blob:blobs){
			long length=blob.length();
			offsets[i]=offsets[i-1]+length;
			i++;
		}
	}
	
	public int blobCount(){
		return blobs.size();
	}
	public Blob getBlob(int index) {
		return blobs.get(index);
	}
	
	public void setMeta(int index,JSONObject meta){
		if(metas==null){
			metas=new JSONArray();
		}
		JSONObject orgMeta=null;
		if(metas.size()>index){
			orgMeta=metas.getJSONObject(index);
		}
		if(orgMeta!=null){
			for(Object key:orgMeta.keySet()){
				if(meta.get(key)!=null){
					continue;
				}
				meta.put(key, orgMeta.get(key));
			}
		}
		metas.element(index,meta);
	}
	
	public Blob addBlob(Blob blob){
		return addBlob(blob,null);
	}
	public Blob addBlob(Blob blob,JSONObject meta){
		blob.ref();
		blobs.add(blob);
		if(meta!=null){
			setMeta(blobs.size()-1,meta);
		}
		return blob;
	}
	
	public Blob addBlob(ByteBuffer[] blob){
		return addBlob(blob,null);
	}
	public Blob addBlob(ByteBuffer[] buffer,JSONObject meta){
		Blob blob=Blob.create(buffer);
		blobs.add(blob);
		if(meta!=null){
			setMeta(blobs.size()-1,meta);
		}
		return blob;
	}
	
	public Blob addBlob(File file){
		return addBlob(file,null);
	}
	
	public Blob addBlob(File file,JSONObject meta){
		Blob blob=Blob.create(file);
		blobs.add(blob);
		if(meta==null){
			meta=new JSONObject();
			meta.element("name",file.getName());
		}
		setMeta(blobs.size()-1,meta);
		return blob;
	}
	public Object getMessage() {
		return message;
	}
	public void setMessage(Object message) {
		this.message=message;
	}
	
	public int getBlobHeaderLength(){
		return blobHeaderBuffer.remaining();
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		throw new UnsupportedOperationException("asyncBuffer(BufferGetter bufferGetter, Object userContext)");
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext) {
		int blobNo=0;
		for(;blobNo<offsets.length;blobNo++){
			if(offsets[blobNo]>offset){
				break;
			}
		}
		if(blobNo==0){
			ByteBuffer h=PoolManager.duplicateBuffer(blobHeaderBuffer);
			BuffersUtil.skip(h,offset);
			bufferGetter.onBuffer(userContext, BuffersUtil.toByteBufferArray(h));
		}else{
			long blobOffset=offset-offsets[blobNo-1];
			Blob blob=getBlob(blobNo-1);
			Object[] ctx={bufferGetter,userContext};
			blob.asyncBuffer(this,blobOffset,ctx);
		}
		return false;
	}

	public long bufferLength() {
		return offsets[offsets.length-1];
	}

	/* �qBlob�ǂݍ��ݗp */
	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		return bufferGetter.onBuffer(ctx[1], buffers);
	}

	public void onBufferEnd(Object userContext) {
		throw new IllegalStateException("BlobMessage not use onBufferEnd");
	}

	public void onBufferFailure(Object userContext, Throwable failure) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		bufferGetter.onBufferFailure(ctx[1], failure);
	}

	public JSONObject getHeader() {
		return header;
	}
}
