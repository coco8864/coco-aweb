package naru.aweb.pa;

import java.io.File;
import java.nio.ByteBuffer;

import naru.async.AsyncBuffer;
import naru.async.BufferGetter;
import naru.async.cache.CacheBuffer;
import naru.async.cache.FileInfo;
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import net.sf.json.JSONObject;

/**
 * javascriopt File Blobオブジェクトをシミュレート
 * http://www.w3.org/TR/FileAPI/
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
public class Blob extends PoolBase implements AsyncBuffer,BufferGetter{
	private CacheBuffer buffer;
	private long offset;
	private long size;
	private JSONObject meta;/*その他の属性情報 */
	
	public static Blob create(File file,JSONObject meta){
		CacheBuffer buffer=CacheBuffer.open(file);
		return create(buffer,meta);
	}
	
	public static Blob create(ByteBuffer[] byteBuffer,JSONObject meta){
		CacheBuffer buffer=CacheBuffer.open(byteBuffer);
		return create(buffer,meta);
	}
	
	public static Blob create(CacheBuffer buffer,JSONObject meta){
		return create(buffer,0,buffer.bufferLength(),meta);
	}
	
	/* bufferは、Blob終了時に開放される */
	public static Blob create(CacheBuffer buffer,long offset,long size,JSONObject meta){
		Blob blob=(Blob)PoolManager.getInstance(Blob.class);
		buffer.ref();
		blob.buffer=buffer;
		blob.offset=offset;
		blob.size=size;
		blob.meta=new JSONObject();
		FileInfo fileInfo=buffer.getFileInfo();
		if(fileInfo!=null){
			blob.meta.element("name",fileInfo.getFile().getName());
			blob.meta.element("lastModifiedDate",fileInfo.getLastModified());
		}
		//TODO metaの値をoffset,size,name,lastModifiedDataに反映?
		//meta情報のsizeは信用しない..パラメタの値で上書き
		meta.element("size", size);
		blob.meta=meta;
		return blob;
	}
	
	public long size(){
		return size;
	}
	
	public String getType() {
		return meta.optString("type");
	}

	public String getName() {
		return meta.optString("name");
	}
	public long getLastModifiedDate() {
		return meta.optLong("lastModifiedDate",0);
	}

	public void setType(String type) {
		meta.element("type", type);
	}

	public void setName(String name) {
		meta.element("name", name);
	}

	public void setLastModifiedDate(long lastModifiedDate) {
		meta.element("lastModifiedDate", lastModifiedDate);
	}
	
	@Override
	public void recycle() {
		if(buffer!=null){
			buffer.unref();
			buffer=null;
		}
		size=offset=0;
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, Object userContext) {
		throw new UnsupportedOperationException("asyncBuffer(BufferGetter bufferGetter, Object userContext)");
	}

	public boolean asyncBuffer(BufferGetter bufferGetter, long offset,Object userContext) {
		long maxLength=size-offset;
		Object[] ctx={bufferGetter,userContext,maxLength};
		return buffer.asyncBuffer(this, this.offset+offset,ctx);
	}

	public long bufferLength() {
		return size;
	}

	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		long len=BuffersUtil.remaining(buffers);
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		Long maxLength=(Long)ctx[2];
		if(len>maxLength){
			BuffersUtil.cut(buffers, maxLength);
		}
		bufferGetter.onBuffer(orgUserContext, buffers);
		return false;
	}

	public void onBufferEnd(Object userContext) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		bufferGetter.onBufferEnd(orgUserContext);
	}

	public void onBufferFailure(Object userContext, Throwable failure) {
		Object[] ctx=(Object[])userContext;
		BufferGetter bufferGetter=(BufferGetter)ctx[0];
		Object orgUserContext=ctx[1];
		bufferGetter.onBufferFailure(orgUserContext,failure);
	}

	public JSONObject getMeta() {
		return meta;
	}

	public void setMeta(JSONObject meta) {
		this.meta = meta;
	}
	
	public void appendMeta(JSONObject appendMeta){
		if(meta==null){
			setMeta(appendMeta);
			return;
		}
		for(Object key:appendMeta.keySet()){
			if("size".equals(key)){
				continue;//sizeは設定させない
			}
			meta.accumulate((String)key, appendMeta.get(key));
		}
	}
}
