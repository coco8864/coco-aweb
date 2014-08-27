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

import org.junit.Test;


public class ChunkTest {
	@Test
	public void testChunk() throws Throwable{
		ChunkContext context=new ChunkContext();
		context.decodeInit(true, -1);
		InputStream is=getClass().getClassLoader().getResourceAsStream("22223562_0.txt");
		while(true){
			ByteBuffer buf=BuffersUtil.toBuffer(is);
			if(buf==null){
				break;
			}
			buf.flip();
			System.out.println("isEndOfData:"+context.isEndOfData(new ByteBuffer[]{buf}));
		}
		is.close();
		is=getClass().getClassLoader().getResourceAsStream("22223562_1.txt");
		while(true){
			ByteBuffer buf=BuffersUtil.toBuffer(is);
			if(buf==null){
				break;
			}
			buf.flip();
			System.out.println("isEndOfData:"+context.isEndOfData(new ByteBuffer[]{buf}));
		}
		is.close();
		is=getClass().getClassLoader().getResourceAsStream("22223562_2.txt");
		while(true){
			ByteBuffer buf=BuffersUtil.toBuffer(is);
			if(buf==null){
				break;
			}
			buf.flip();
			System.out.println("isEndOfData:"+context.isEndOfData(new ByteBuffer[]{buf}));
		}
		is.close();
	}
	
	int debugFileCounter=0;
	private void dump(ByteBuffer[] body){
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
	
	@Test
	public void testChunkRead() throws Throwable{
		ChunkContext context=new ChunkContext();
		context.decodeInit(true, -1);
		InputStream is=getClass().getClassLoader().getResourceAsStream("22223562_0.txt");
		while(true){
			ByteBuffer buf=BuffersUtil.toBuffer(is);
			if(buf==null){
				break;
			}
			buf.flip();
			dump(context.decodeChunk(new ByteBuffer[]{buf}));
		}
		is.close();
		is=getClass().getClassLoader().getResourceAsStream("22223562_1.txt");
		while(true){
			ByteBuffer buf=BuffersUtil.toBuffer(is);
			if(buf==null){
				break;
			}
			buf.flip();
			dump(context.decodeChunk(new ByteBuffer[]{buf}));
		}
		is.close();
		is=getClass().getClassLoader().getResourceAsStream("22223562_2.txt");
		while(true){
			ByteBuffer buf=BuffersUtil.toBuffer(is);
			if(buf==null){
				break;
			}
			buf.flip();
			dump(context.decodeChunk(new ByteBuffer[]{buf}));
		}
		is.close();
		
	}


}
