package naru.aweb.wsq;

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

/* BlobMessageの形
 * header長(4byte,BigEndigan)
 * header=jsonデータ
 * {
 * type:'publish',qname:'qname',dataCount:データ数,totalLength:総データ長,(isGz:gz圧縮の有無),message:任意
 * metas:[
 *  {length:1番目のdeta長,jsType:'ArrayBuffer'|string|Blob|object,name:,mimeType:,以降任意},
 *  {length:2番目のdeta長,..},
 *  {length:3番目のdeta長,..,}
 *  ]
 * }
 * 1番目のデータ
 * 2番目のデータ
 * 3番目のデータ
 */
public class BlobMessage extends PoolBase implements AsyncBuffer,BufferGetter{
	private JSONObject header;
	private List<Blob> blobs=new ArrayList<Blob>();
	private ByteBuffer headerBuffer=null;
	private long[] offsets=null;
	
	public static BlobMessage create(JSONObject header,CacheBuffer buffer){
		BlobMessage result=new BlobMessage(header);
		if(header.getInt("totalLength")!=0){
//			BlobFile blobFile=BlobFile.create(data,meta.getBoolean("isGz"));
			JSONArray metas=header.getJSONArray("metas");
			long offset=0;
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
		if(headerBuffer!=null){
			PoolManager.poolBufferInstance(headerBuffer);
			headerBuffer=null;
		}
		header=null;
		Iterator<Blob> blobItr=blobs.iterator();
		while(blobItr.hasNext()){
			Blob blob=blobItr.next();
			blobItr.remove();
			blob.unref();
		}
		offsets=null;
	}
	
	/* 設定モードから送信モードへの切り替え */
	public synchronized void flip(){
		if(headerBuffer!=null){
			return;
		}
		JSONArray metas=header.getJSONArray("metas");
		if(metas==null){
			metas=new JSONArray();
		}
		int i=0;
		for(Blob blob:blobs){
			JSONObject meta=metas.getJSONObject(i);
			if(meta==null){
				meta=new JSONObject();
			}
			meta.element("length",blob.length());
			meta.element("name",blob.getName());
			meta.element("jsType",blob.getJsType());
			meta.element("mimeType",blob.getMimeType());
			metas.element(i,meta);
			i++;
		}
		header.element("metas", metas);
		
		String jsonHeader=header.toString();
		int size=blobs.size()+1;
		offsets=new long[size];
		try {
			headerBuffer=ByteBuffer.wrap(jsonHeader.getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("UnsupportedEncodingException utf-8");
		}
		offsets[0]=headerBuffer.remaining();
		i=1;
		for(Blob blob:blobs){
			long length=blob.length();
			offsets[i]=offsets[i-1]+length;
			i++;
		}
	}
	
	public BlobMessage(JSONObject header){
		if(header==null){
			header=new JSONObject();
		}
		this.header=header;
	}
	
	public JSONObject getHeader() {
		return header;
	}
	public int blobCount(){
		return blobs.size();
	}
	public Blob getBlob(int index) {
		return blobs.get(index);
	}
	
	public void setMeta(int index,JSONObject meta){
		JSONArray metas=header.getJSONArray("metas");
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
		header.element("metas", metas);
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
		return header.opt("message");
	}
	public void setMessage(Object message) {
		if(header==null){
			header=new JSONObject();
		}
		header.element("message", message);
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
			ByteBuffer h=PoolManager.duplicateBuffer(headerBuffer);
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
