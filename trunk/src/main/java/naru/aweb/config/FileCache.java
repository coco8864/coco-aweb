package naru.aweb.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;

public class FileCache implements Timer{
	private static Logger logger=Logger.getLogger(FileCache.class);
	private static final long INTERVAL = 60000;
	private static final long FILE_MAX_SIZE=4*1024*1024;
	private static final long TOTAL_MAX_SIZE=64*1024*1024;
	private static final int MAX_COUNT=256;

	public class FileCacheInfo{
		private int idleCounter=0;//直近利用からの時間
		private int accessCounter=0;//通算利用回数
		private int lockCounter=0;
		private File file;
		private boolean exists;
		private long lastModified;
		private long length;
		
		private boolean isNeedContents;//contentsの用不要
		private ByteBuffer contents;
		
		private synchronized void lock(){
			idleCounter=0;
			accessCounter++;
			lockCounter++;
		}
		private void waitLock(){
			while(true){
				if(lockCounter==0){
					return;
				}
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
		}
		public synchronized void unlock(){
			lockCounter--;
			if(lockCounter==0){
				notify();
			}
		}
		public File getFile() {
			return file;
		}
		public boolean exists() {
			return exists;
		}
		public long lastModified() {
			return lastModified;
		}
		public long length() {
			return length;
		}
		public ByteBuffer getContents() {
			isNeedContents=true;
			return PoolManager.duplicateBuffer(contents, true);
		}
	}
	
	private Map<File,FileCacheInfo> cache=new HashMap<File,FileCacheInfo>();
	private Set<File> queue=new HashSet<File>();
	private boolean newEntry=true;
	private long totalSize=0;
	
	public FileCacheInfo lockFileInfo(File file){
//		File canonFile=file.getCanonicalFile();
		FileCacheInfo fileInfo=cache.get(file);
		if(fileInfo!=null){
			fileInfo.lock();
		}else{
			if(newEntry){
				synchronized (queue) {
					queue.add(file);
				}
			}
		}
		return fileInfo;
	}
	
	private ByteBuffer readFile(File file,long length){
		ByteBuffer buffer=null;
		try {
			FileChannel channel=null;
			FileInputStream fis=new FileInputStream(file);
			channel=fis.getChannel();
			buffer = PoolManager.getBufferInstance((int)length);
			if( channel.read(buffer)<length){
				PoolManager.poolBufferInstance(buffer);
				logger.error("readFile length error."+length);
				return null;
			}
			buffer.flip();
			totalSize+=length;
			logger.info("cache load file."+file.getAbsolutePath()+":" + length);
		} catch (IOException e) {
			if(buffer!=null){
				PoolManager.poolBufferInstance(buffer);
				buffer=null;
			}
			logger.error("readFile error",e);
		}
		return buffer;
	}
	
	private void checkFileInfo(FileCacheInfo fileInfo){
		File file=fileInfo.file;
		fileInfo.idleCounter++;
		boolean exists=file.exists();
		long lastModified=0;
		long length=0;
		if(exists){
			lastModified=file.lastModified();
			length=file.length();
		}
		if(fileInfo.exists){
			if(fileInfo.isNeedContents==true && fileInfo.contents==null){
				//content要求があったがまだ読み込んでいない
			}else if(exists && 
				fileInfo.lastModified==lastModified && 
				fileInfo.length==length){
				return;
			}
		}else{
			if(exists==false){
				return;
			}
		}
		ByteBuffer contents=null;
		if(exists&&fileInfo.isNeedContents==true){
			contents=readFile(file,length);
		}
		ByteBuffer orgContents=null;
		long orgLength=fileInfo.length;
		synchronized(fileInfo){
			fileInfo.waitLock();
			if(exists){
				fileInfo.lastModified=lastModified;
				fileInfo.length=length;
				orgContents=fileInfo.contents;
				fileInfo.contents=contents;
			}else if(fileInfo.contents!=null){
				fileInfo.isNeedContents=false;
				fileInfo.length=0;
				orgContents=fileInfo.contents;
				fileInfo.contents=null;
			}
			fileInfo.exists=exists;
		}
		if(orgContents!=null){
			PoolManager.poolBufferInstance(orgContents);
			totalSize-=orgLength;
		}
		if(totalSize>=(TOTAL_MAX_SIZE-FILE_MAX_SIZE)){
			newEntry=false;
		}
		logger.info("cache reload file."+file.getAbsolutePath() +":" + fileInfo.length);
	}
	
	private FileCacheInfo createFileInfo(File file){
		FileCacheInfo fileInfo=new FileCacheInfo();
		fileInfo.file=file;
		fileInfo.exists=file.exists();
		if(fileInfo.exists){
			fileInfo.length=file.length();
			if(fileInfo.length>=FILE_MAX_SIZE){
				return null;
			}
			fileInfo.lastModified=file.lastModified();
			fileInfo.isNeedContents=false;
//			fileInfo.contents=readFile(file,fileInfo.length);
//			if(fileInfo.contents==null){//読み込み失敗
//				return null;
//			}
		}
		logger.info("cache create file."+file.getAbsolutePath()+":" + fileInfo.length);
		return fileInfo;
	}

	public void onTimer(Object userContext) {
		for(File file:cache.keySet()){
			FileCacheInfo fileInfo=cache.get(file);
			checkFileInfo(fileInfo);
		}
		Object[] queueFiles=null;
		synchronized(queue){
			queueFiles=queue.toArray();
			queue.clear();
		}
		for(Object fileO:queueFiles){
			File file=(File)fileO;
			FileCacheInfo fileInfo=createFileInfo(file);
			if(fileInfo!=null){
				cache.put(file, fileInfo);
				if( cache.size()>=MAX_COUNT ){
					newEntry=false;
				}
			}
		}
	}
	
	private Object intervalObj=null;
	public static FileCache create(){
		FileCache filecache=new FileCache();
		filecache.intervalObj=TimerManager.setInterval(INTERVAL, filecache, null);
		return filecache;
	}
	
	public void term(){
		if(intervalObj!=null){
			TimerManager.clearInterval(intervalObj);
		}
	}

}
