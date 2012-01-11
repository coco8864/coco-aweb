package naru.aweb.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
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
	
	private boolean newEntry=true;
	private long totalSize=0;
	private int cacheCount=0;

	private List<FileCacheInfo> pool=new LinkedList<FileCacheInfo>();
	private Map<File,FileCacheInfo> cache=new HashMap<File,FileCacheInfo>();
	private Set<FileCacheInfo> queue=new HashSet<FileCacheInfo>();
	
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
		
		private boolean isError;//処理中にエラー
		private boolean isInBase;//トラバーサルチェック
		
		private boolean isDirectory;
		private boolean isFile;
		private boolean canRead;
		private boolean exists;
		private long lastModified;
		private long length;
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
			if(isInCache && lockCounter==0){
				notify();//読み込みthreadが待っているかもしれない
			}
			if(isInPool){
				synchronized(pool){
					pool.add(this);
				}
			}
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
		
		private ByteBuffer readFile(){
			length=canonFile.length();
			ByteBuffer buffer=null;
			try {
				FileChannel channel=null;
				FileInputStream fis=new FileInputStream(canonFile);
				channel=fis.getChannel();
				buffer = PoolManager.getBufferInstance((int)length);
				if( channel.read(buffer)<length){
					PoolManager.poolBufferInstance(buffer);
					logger.error("readFile length error."+length);
					isError=true;
					return null;
				}
				buffer.flip();
				logger.info("cache load file."+canonFile.getAbsolutePath()+":" + length);
			} catch (IOException e) {
				if(buffer!=null){
					PoolManager.poolBufferInstance(buffer);
					buffer=null;
				}
				isError=true;
				logger.error("readFile error",e);
			}
			return buffer;
		}
		
		public ByteBuffer getContents() {
			ByteBuffer buffer=null;
			if(contents!=null){
				return PoolManager.duplicateBuffer(contents, true);
			}else{
				buffer=readFile();
			}
			if(length>=FILE_MAX_SIZE){
				return buffer;
			}else{
				contents=buffer;
				return PoolManager.duplicateBuffer(contents, true);
			}
		}
		
		public boolean isError(){
			return isError;
		}
		public boolean isDirectory(){
			return isDirectory;
		}
		public boolean isFile(){
			return isFile;
		}
		public boolean canRead(){
			return canRead;
		}
		
		private void setup(File base,String path,FileCacheInfo parent){
			this.base=base;
			this.path=path;
			this.parent=parent;
			this.isError=false;
			File file;
			if(path!=null){
				file=new File(base,path);
			}else{
				file=base;
			}
			try {
				canonFile=file.getCanonicalFile();
				String baseCanon=base.getCanonicalPath();
				if(canonFile.getAbsolutePath().startsWith(baseCanon)){
					isInBase=true;
				}else{
					isInBase=false;
				}
			} catch (IOException e) {
				isError=true;
				logger.error("getCanonical error.",e);
				return;
			}
			if(isInBase==false){
				return;
			}
			check(true);
		}
		
		//cache内容が正しいかチェックする
		private void check(boolean isSetup){
			idleCounter++;
			boolean exists=canonFile.exists();
			long lastModified=0;
			long length=0;
			boolean isDirectory=false;
			boolean isFile=false;
			boolean canRead=false;
			if(exists){
				lastModified=canonFile.lastModified();
				length=canonFile.length();
				isDirectory=canonFile.isDirectory();
				isFile=canonFile.isFile();
				canRead=canonFile.canRead();
			}
			if(!isSetup){
				if(this.exists){
					if(exists && 
						this.lastModified==lastModified && 
						this.length==length){
						return;
					}
				}else{
					if(exists==false){
						return;
					}
				}
			}
			ByteBuffer orgContents=null;
			long orgLength=length;
			synchronized(this){
				waitLock();
				this.lastModified=lastModified;
				this.length=length;
				orgContents=this.contents;
				this.contents=null;
				this.isDirectory=isDirectory;
				this.isFile=isFile;
				this.canRead=canRead;
				this.exists=exists;
			}
			if(orgContents!=null){
				PoolManager.poolBufferInstance(orgContents);
				totalSize-=orgLength;
			}
		}
	}
	
	private FileCacheInfo getFileCacheInfo(File base,String path,FileCacheInfo parent){
		FileCacheInfo info=null;
		synchronized(pool){
			if(pool.size()>0){
				info=pool.remove(0);
			}
		}
		if(info==null){
			info=new FileCacheInfo();
			info.isInPool=true;
		}
		info.setup(base, path, parent);
		return info;
	}
	
	/*
	 * 平常時にlockなしにするには、その場でputする事ができない。
	 * unlockのタイミングでqueueしてtimerにputしてもらう
	 */
	public FileCacheInfo lockFileInfo(File base,String path){
		FileCacheInfo parentInfo=cache.get(base);
		if(parentInfo==null){
			parentInfo=getFileCacheInfo(base,null,null);
		}
		if(path==null){
			parentInfo.lock();
			return parentInfo;
		}
		FileCacheInfo chFileInfo=parentInfo.child.get(path);
		if(chFileInfo==null){
			chFileInfo=getFileCacheInfo(base,path,parentInfo);
		}
		chFileInfo.lock();
		return chFileInfo;
	}
	
	public FileCacheInfo lockFileInfo(File file){
		return lockFileInfo(file,null);
	}

	public void onTimer(Object userContext) {
		//cacheコンテンツに変更がないかを確認
		for(File file:cache.keySet()){
			FileCacheInfo fileInfo=cache.get(file);
			fileInfo.check(false);
			for(FileCacheInfo childFileInfo:fileInfo.child.values()){
				childFileInfo.check(false);
			}
		}
		//新たなcache候補を取り込み
		List<FileCacheInfo> queueInfos=new ArrayList<FileCacheInfo>();
		synchronized(queue){
			queueInfos.addAll(queue);
			queue.clear();
		}
		for(FileCacheInfo info:queueInfos){
			if(!newEntry){
				synchronized(pool){
					pool.add(info);//再利用にまわす
				}
			}
			cacheCount++;
			if(cacheCount>=MAX_COUNT){
				newEntry=false;
			}
			info.isInCache=true;
			if(info.parent==null){
				cache.put(info.base,info);
			}else{
				if(info.parent.isInCache==false){
					info.parent.isInCache=true;
					cache.put(info.parent.base,info.parent);
				}
				info.parent.child.put(info.path, info);
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
