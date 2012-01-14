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
import naru.async.pool.BuffersUtil;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;

public class FileCache implements Timer{
	private static Logger logger=Logger.getLogger(FileCache.class);
	private static final long INTERVAL = 60000;
	private static final long FILE_MAX_SIZE=4*1024*1024;
	private static final long TOTAL_MAX_SIZE=64*1024*1024;
	private static final int MAX_COUNT=256;
	
	private boolean newEntry=true;
//	private long totalSize=0;
	private int cacheCount=0;

	private List<FileCacheInfo> pool=new LinkedList<FileCacheInfo>();
	private Map<File,FileCacheInfo> cache=new HashMap<File,FileCacheInfo>();
	private Set<FileCacheInfo> queue=new HashSet<FileCacheInfo>();
	
	public class FileCacheInfo{
		private FileCacheInfo parent;
		private Map<String,FileCacheInfo> children=new HashMap<String,FileCacheInfo>();
		
//		private boolean isInPool;//poolオブジェクトか否か?
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
		private File[] listFiles;
		private ByteBuffer[] contents;
		
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
			if(!isInCache){
				synchronized(queue){
					queue.add(this);
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
		
		private ByteBuffer[] readFile(){
			length=canonFile.length();
			ByteBuffer[] buffers=null;
			try {
				FileChannel channel=null;
				FileInputStream fis=new FileInputStream(canonFile);
				channel=fis.getChannel();
				buffers=BuffersUtil.prepareBuffers(length);
//				buffer = PoolManager.getBufferInstance((int)length);
				if( channel.read(buffers)<length){
					PoolManager.poolBufferInstance(buffers);
					logger.error("readFile length error."+length);
					isError=true;
					return null;
				}
				BuffersUtil.flipBuffers(buffers);
			} catch (IOException e) {
				if(buffers!=null){
					PoolManager.poolBufferInstance(buffers);
					buffers=null;
				}
				isError=true;
				logger.error("readFile error",e);
			}
			return buffers;
		}
		
		public ByteBuffer[] getContents() {
			ByteBuffer[] buffers=null;
			if(contents!=null){
				return PoolManager.duplicateBuffers(contents, true);
			}else{
				buffers=readFile();
			}
			if(length>=FILE_MAX_SIZE){
				return buffers;
			}else{
				contents=buffers;
//				totalSize+=length;
				return PoolManager.duplicateBuffers(contents, true);
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
		public boolean isInBase(){
			return isInBase;
		}
		public boolean canRead(){
			return canRead;
		}
		
		public File[] listFiles(){
			return listFiles;
		}
		
		public File getCanonicalFile(){
			return canonFile;
		}
		
		public FileCacheInfo lockWelcomefile(String[] paths){
			FileCacheInfo baseInfo=null;
			FileCacheInfo info=null;
			String basePath=null;
			if(parent!=null){
				baseInfo=parent;
				basePath=path;
				if(!basePath.endsWith("/")){
					basePath+="/";
				}
			}else{
				baseInfo=this;
			}
			for(String path:paths){
				String reasPath=(basePath==null)?path:basePath+path;
				info=baseInfo.children.get(reasPath);
				if(info==null){
					info=lockFileInfo(baseInfo.base, reasPath);
				}else{
					info.lock();
				}
				if(info.exists){
					return info;
				}
				info.unlock();
			}
			return null;
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
			File[] listFiles=null;
			if(exists){
				lastModified=canonFile.lastModified();
				length=canonFile.length();
				isDirectory=canonFile.isDirectory();
				isFile=canonFile.isFile();
				canRead=canonFile.canRead();
				if(isDirectory && canRead){
					listFiles=canonFile.listFiles();
				}
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
			ByteBuffer[] orgContents=this.contents;
//			long orgLength=this.length;
			synchronized(this){
				waitLock();
				this.lastModified=lastModified;
				this.length=length;
				this.contents=null;
				this.isDirectory=isDirectory;
				this.isFile=isFile;
				this.canRead=canRead;
				this.listFiles=listFiles;
				this.exists=exists;
			}
			if(orgContents!=null){
				PoolManager.poolBufferInstance(orgContents);
//				totalSize-=orgLength;
			}
		}
		
		public void clear(){
			if(contents==null){
				return;
			}
			PoolManager.poolBufferInstance(contents);
			contents=null;
			lastModified=0;
			length=0;
			isDirectory=false;
			isFile=false;
			canRead=false;
			listFiles=null;
			exists=false;
			isInCache=false;//cache中か否か?
			idleCounter=0;//直近利用からの時間
			accessCounter=0;//通算利用回数
			lockCounter=0;
			children.clear();
			parent=null;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((base == null) ? 0 : base.hashCode());
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final FileCacheInfo other = (FileCacheInfo) obj;
			if (base == null) {
				if (other.base != null)
					return false;
			} else if (!base.equals(other.base))
				return false;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			return true;
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
			info.isInCache=false;
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
		FileCacheInfo chFileInfo=parentInfo.children.get(path);
		if(chFileInfo==null){
			chFileInfo=getFileCacheInfo(base,path,parentInfo);
		}
		chFileInfo.lock();
		return chFileInfo;
	}
	
	public FileCacheInfo lockFileInfo(File file){
		return lockFileInfo(file,null);
	}

	private void addPool(FileCacheInfo info){
		info.clear();
		if(pool.size()>MAX_COUNT){
			return;
		}
		synchronized(pool){
			pool.add(info);//再利用にまわす
		}
	}
	
	private void addCache(FileCacheInfo info){
		if(cache.containsKey(info.base)){
			addPool(info);
			return;
		}
		cacheCount++;
		if(cacheCount>=MAX_COUNT){
			newEntry=false;
		}
		info.isInCache=true;
		cache.put(info.base,info);
		logger.info("cache in file."+info.canonFile+":" + info.length +":" +(info.contents!=null));
	}
	
	private void addParentCache(FileCacheInfo info){
		FileCacheInfo parent=cache.get(info.base);
		if(parent==null){
			parent=info.parent;
			parent.isInCache=true;
			cache.put(parent.base,parent);
		}
		if(parent.children.containsKey(info.path)){
			addPool(info);
			return;
		}
		cacheCount++;
		if(cacheCount>=MAX_COUNT){
			newEntry=false;
		}
		info.isInCache=true;
		parent.children.put(info.path, info);
		logger.info("cache children in file."+info.canonFile+":" + info.length +":" +(info.contents!=null));
	}
	
	public void onTimer(Object userContext) {
		//cacheコンテンツに変更がないかを確認
		for(File file:cache.keySet()){
			FileCacheInfo fileInfo=cache.get(file);
			fileInfo.check(false);
			for(FileCacheInfo childFileInfo:fileInfo.children.values()){
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
				addPool(info);
			}
			if(info.parent==null){
				addCache(info);
			}else{
				addParentCache(info);
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
