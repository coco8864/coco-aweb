package naru.aweb;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.aweb.http.ChunkContext;
import naru.aweb.http.GzipContext;
import naru.aweb.util.CodeConverter;

import org.junit.Test;


public class GzipTest {
	@Test
	public void testGzip() throws Throwable{
		GzipContext gc=new GzipContext();
		CodeConverter cc=new CodeConverter();
		cc.init("ms932", "utf-8");
		InputStream is=getClass().getClassLoader().getResourceAsStream("storeDownload");
		while(true){
			ByteBuffer buf=BuffersUtil.toBuffer(is);
			if(buf==null){
				break;
			}
			buf.flip();
			gc.putZipedBuffer(buf);
			ByteBuffer[] buffers=gc.getPlainBuffer();
			BuffersUtil.mark(buffers);
			dump(buffers);
			BuffersUtil.reset(buffers);
			dump(cc.convert(buffers));
		}
		dump(cc.close());
		is.close();
		
	}
	
	int debugFileCounter=0;
	private void dump(ByteBuffer[] body){
		if(body==null){
			return;
		}
		ByteBuffer[] d=PoolManager.duplicateBuffers(body);
		File f=new File(Integer.toString(hashCode())+"_"+ debugFileCounter+".txt");
		debugFileCounter++;
		System.out.println("f:"+f.getAbsolutePath());
		OutputStream os=null;
		try {
			os=new FileOutputStream(f);
			BuffersUtil.toStream(d, os);
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			if(os!=null){
				try {
					os.close();
				} catch (IOException ignore) {
				}
			}
		}
	}
	


}
