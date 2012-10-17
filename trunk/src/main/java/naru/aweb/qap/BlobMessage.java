package naru.aweb.qap;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
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
 * このデータはこの接続固有のheader
 * header長(4byte,BigEndigan)
 * header=jsonデータ
 * {
* type:'publish',
* qname:'qname',
* subId:'subId',***
* dataType:blobMessage
* blobHeaderLength:blobヘッダ長
* }
*/
/* このデータは、複数の回線で同じデータが共有される
 * BlobMessageの形
 * blobHeader=jsonデータ
 * {
 * //count:データ数,
 * //totalLength:総データ長,
 * message:任意
 * metas:[
 *  {length:1番目のdeta長,jsType:'ArrayBuffer'|string|Blob|object,name:,mimeType:,isGz圧縮の有無以降任意},
 *  {length:2番目のdeta長,..},
 *  {length:3番目のdeta長,..,}
 *  ]
 * }//ここまでがtopBufferに入っている事を期待
 * 1番目のデータ
 * 2番目のデータ
 * 3番目のデータ
 */
public class BlobMessage extends PoolBase implements AsyncBuffer,BufferGetter{
//	private JSONArray metas;
	private Object message;
	private List<Blob> blobData=new ArrayList<Blob>();
	private long[] offsets=null;
	private ByteBuffer blobHeaderBuffer=null;
	
	public static BlobMessage parse(JSONObject blobHeader,long  totalHeaderLength,CacheBuffer buffer){
		BlobMessage result=(BlobMessage)PoolManager.getInstance(BlobMessage.class);
		result.message=blobHeader.get("message");
		JSONArray metas=blobHeader.getJSONArray("metas");
		long offset=totalHeaderLength;
		for(int i=0;i<metas.size();i++){
			JSONObject meta=metas.getJSONObject(i);
			long length=meta.getLong("size");
			Blob blob=Blob.create(buffer,offset,length,meta);
			offset+=length;
			result.addBlob(blob);
		}
		return result;
	}
	
	@Override
	public void recycle() {
		if(blobHeaderBuffer!=null){
			PoolManager.poolBufferInstance(blobHeaderBuffer);
			blobHeaderBuffer=null;
		}
		Iterator<Blob> blobItr=blobData.iterator();
		while(blobItr.hasNext()){
			Blob blob=blobItr.next();
			blobItr.remove();
			blob.unref();
		}
		offsets=null;
	}
	
	/* 設定モードから送信モードへの切り替え */
	public synchronized void flip(){
		int i=0;
		long totalLength=0;
		JSONArray metas=new JSONArray();
		for(Blob blob:blobData){
			totalLength+=blob.size();
			metas.element(i,blob.getMeta());
			i++;
		}
		int count=blobData.size();
		
		JSONObject blobHeader=new JSONObject();
		blobHeader.element("message", message);
		blobHeader.element("metas", metas);
		blobHeader.element("count", blobData.size());
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
		offsets=new long[count+1];
		offsets[0]=blobHeaderBuffer.remaining();
		i=1;
		for(Blob blob:blobData){
			long length=blob.size();
			offsets[i]=offsets[i-1]+length;
			i++;
		}
	}
	
	public int blobCount(){
		return blobData.size();
	}
	public Blob getBlob(int index) {
		return blobData.get(index);
	}
	
	public Blob addBlob(Blob blob){
		blobData.add(blob);
		return blob;
	}
	
	public Blob addBlob(ByteBuffer[] blob){
		return addBlob(blob,null);
	}
	public Blob addBlob(ByteBuffer[] buffer,JSONObject meta){
		Blob blob=Blob.create(buffer,meta);
		blobData.add(blob);
		return blob;
	}
	
	public Blob addBlob(File file){
		return addBlob(file,null);
	}
	
	public Blob addBlob(File file,JSONObject meta){
		Blob blob=Blob.create(file,meta);
		blobData.add(blob);
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

	/* 子Blob読み込み用 */
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
}
