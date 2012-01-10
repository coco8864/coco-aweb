package naru.aweb.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;

public class FileCache2 implements Timer{
	private static Logger logger=Logger.getLogger(FileCache2.class);
	private static final long INTERVAL = 60000;
	private static final long FILE_MAX_SIZE=4*1024*1024;
	private static final long TOTAL_MAX_SIZE=64*1024*1024;
	private static final int MAX_COUNT=256;

	public class FileCacheInfo{
		private FileCacheInfo parent;
		private Map<String,FileCacheInfo> child=new HashMap<String,FileCacheInfo>();
		private boolean isInPool;//poolオブジェクトか否か?
		private boolean isInCache;//cache中か否か?
		
		private int idleCounter=0;//直近利用からの時間
		private int accessCounter=0;//通算利用回数
		private int lockCounter=0;
		
		private File base;
		private String path;
		private File canonFile;
		
		private boolean isInBase;//トラバーサルチェック
		private boolean isDirectory;
		private boolean isFile;
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
			if(isInPool){
				synchronized(pool){
					pool.add(this);
				}
			}else if(lockCounter==0){
				notify();//読み込みthreadが待っているかもしれない
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
	
	private List<FileCacheInfo> pool=new LinkedList<FileCacheInfo>();
	private Map<File,FileCacheInfo> cache=new HashMap<File,FileCacheInfo>();
	
	private Set<File> queue=new HashSet<File>();
	private boolean newEntry=true;
	private long totalSize=0;
	
	private FileCacheInfo getPoolFileCacheInfo(){
		synchronized(pool){
			if(pool.size()>0){
				return pool.remove(0);
			}else{
				FileCacheInfo info=new FileCacheInfo();
				info.isInPool=true;
				return info;
			}
		}
	}
	
	/*
	 * 平常時にlockなしにするには、その場でputする事ができない。
	 * unlockのタイミングでqueueしてtimerにputしてもらう
	 */
	public FileCacheInfo lockFileInfo(File base,String path){
		FileCacheInfo fileInfo=cache.get(base);
		if(fileInfo!=null){
		}else{
			if(newEntry){
				fileInfo=new FileCacheInfo();
			}else{
				fileInfo=getPoolFileCacheInfo();
			}
		}
		if(path==null){
			fileInfo.lock();
			return fileInfo;
		}
			
			
			
			
			
			FileCacheInfo chFileInfo=fileInfo.child.get(path);
			if(chFileInfo==null){
				if(newEntry){
					chFileInfo=new FileCacheInfo();
					fileInfo.child.put(path, chFileInfo);
				}else{
					chFileInfo=getPoolFileCacheInfo();
				}
				return chFileInfo;
			}
			chFileInfo.lock();
			return chFileInfo;
		}else{
			if(newEntry){
				fileInfo=new FileCacheInfo();
				cache.put(base, fileInfo);
				fileInfo.child.put(path, chFileInfo);
			}else{
				fileInfo=getPoolFileCacheInfo();
			}
			return fileInfo;
		}
		return null;
	}
	
	public FileCacheInfo lockFileInfo(File file){
		return lockFileInfo(file,null);
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
		File file=fileInfo.canonFile;
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
	
	private FileCacheInfo createFileInfo(File base,String path){
		FileCacheInfo fileInfo=new FileCacheInfo();
		File file;
		if(path!=null){
			file=new File(base,path);
		}else{
			file=base;
		}
		try {
			fileInfo.canonFile=file.getCanonicalFile();
			String baseCanon=base.getCanonicalPath();
			if(fileInfo.canonFile.getAbsolutePath().startsWith(baseCanon)){
				fileInfo.isInBase=true;
			}else{
				fileInfo.isInBase=false;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		if(fileInfo.isInBase==false){
			return fileInfo;
		}
		file=fileInfo.canonFile;
		fileInfo.exists=file.exists();
		if(fileInfo.exists){
			fileInfo.length=file.length();
			if(fileInfo.length>=FILE_MAX_SIZE){
				return null;
			}
			fileInfo.lastModified=file.lastModified();
			fileInfo.isDirectory=file.isDirectory();
			fileInfo.isFile=file.isFile();
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
			for(FileCacheInfo childFileInfo:fileInfo.child.values()){
				checkFileInfo(childFileInfo);
			}
		}
		Object[] queueFiles=null;
		synchronized(queue){
			queueFiles=queue.toArray();
			queue.clear();
		}
		for(Object fileO:queueFiles){
			File file=(File)fileO;
			FileCacheInfo fileInfo=createFileInfo(file,null);
			if(fileInfo!=null){
				cache.put(file, fileInfo);
				if( cache.size()>=MAX_COUNT ){
					newEntry=false;
				}
			}
		}
	}
	
	private Object intervalObj=null;
	public static FileCache2 create(){
		FileCache2 filecache=new FileCache2();
		filecache.intervalObj=TimerManager.setInterval(INTERVAL, filecache, null);
		return filecache;
	}
	
	public void term(){
		if(intervalObj!=null){
			TimerManager.clearInterval(intervalObj);
		}
	}

}
