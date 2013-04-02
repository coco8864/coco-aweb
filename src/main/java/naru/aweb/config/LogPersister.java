package naru.aweb.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.apache.log4j.Logger;

import naru.async.BufferGetter;
import naru.async.Timer;
import naru.async.pool.PoolManager;
import naru.async.store.Store;
import naru.async.store.StoreManager;
import naru.async.store.StoreStream;
import naru.async.timer.TimerManager;
import naru.aweb.pa.Blob;
import naru.aweb.pa.PaPeer;
import naru.aweb.util.JdoUtil;
import naru.aweb.util.UnzipConverter;
import net.sf.json.JSONObject;

public class LogPersister implements Timer {
	private static Logger logger = Logger.getLogger(LogPersister.class);

	private long intervalTimeout = 1000;
	private Object interval=null;

	private List requestQueue = new LinkedList();
	private boolean isTerm = false;
	private Config config;

	public LogPersister(Config config) {
		this.config = config;
		interval = TimerManager.setInterval(intervalTimeout, this, null);
	}

	public void term() {
		TimerManager.clearInterval(interval);
//		synchronized(queueManager){
			doJobs();//残っているjobを処理
			isTerm=true;
//		}
	}

	private AccessLog getAccessLog(List<AccessLog> list) {
		synchronized (list) {
			if (list.size() == 0) {
				return null;
			}
			return list.remove(0);
		}
	}

	private void executeInsert(PersistenceManager pm, AccessLog accessLog) {
		if (accessLog == null) {
			return;
		}
		// ログに出力
//		logger.info("###"+accessLog,new Exception());
		accessLog.log(false);
//		String chId = accessLog.getChId();
		if (accessLog.isPersist()) {
			pm.currentTransaction().begin();
			pm.makePersistent(accessLog);
			pm.currentTransaction().commit();
			pm.makeTransient(accessLog);// オブジェクトを再利用するために必要
		}
//		if (chId != null) {
//			queueManager.complete(chId, accessLog.getId());
//		}
		accessLog.unref();
	}
	
	private static class ImportGetter implements BufferGetter {
		private PaPeer peer;
//		private PersistenceManager pm;
		private LogPersister logPersister;
		private ZipEntry currentZe=null;
		private Store store=null;//Store.open(true);
		private CharsetDecoder charsetDecoder=null;
		private CharBuffer charBuffer=null;
		private Set<String> addDigests = new HashSet<String>();
		private List<String> refDigests = new ArrayList<String>();
		
		private ImportGetter(LogPersister logPersister,PaPeer peer){
			this.logPersister=logPersister;
	//		this.pm=pm;
			this.peer=peer;
		}
		
		private void endBuffer(ZipEntry ze){
			if(ze==null){
				return;
			}
			if(store!=null){
				store.close();//終了処理は別スレッドで実行中
				String digest=store.getDigest();
				logPersister.addDigest(addDigests, digest);
				store=null;
			}else if(charsetDecoder!=null){
				charBuffer.flip();
				charBuffer.array();
				String accessLogJson=new String(charBuffer.array(),charBuffer.position(),charBuffer.limit());
				charsetDecoder=null;
				charBuffer=null;
				AccessLog accessLog = AccessLog.fromJson(accessLogJson);
				if (accessLog == null) {
					return;
				}
				logPersister.addDigest(refDigests, accessLog.getRequestHeaderDigest());
				logPersister.addDigest(refDigests, accessLog.getRequestBodyDigest());
				logPersister.addDigest(refDigests, accessLog.getResponseHeaderDigest());
				logPersister.addDigest(refDigests, accessLog.getResponseBodyDigest());
				accessLog.setId(null);
				accessLog.setPersist(true);
				
				PersistenceManager pm = JdoUtil.getPersistenceManager();
				logPersister.executeInsert(pm, accessLog);
				if(pm.currentTransaction().isActive()){
					pm.currentTransaction().rollback();
				}
			}
		}
		
		private void startBuffer(ZipEntry ze){
			String name=ze.getName();
			if(name.startsWith("/store")){
				store=Store.open(true);
			}else if(name.startsWith("/accessLog")){
				Charset c=Charset.forName("utf-8");
				charsetDecoder=c.newDecoder();
				//accessLogをjson化したものなので無制限に大きくならない
				charBuffer=CharBuffer.allocate(4096);
			}
		}

		@Override
		public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
			if(currentZe!=userContext){
				endBuffer(currentZe);
				currentZe=(ZipEntry)userContext;
				startBuffer(currentZe);
			}
			if(store!=null){
				store.putBuffer(buffers);
			}else if(charsetDecoder!=null){
				for(ByteBuffer buffer:buffers){
					charsetDecoder.decode(buffer, charBuffer, false);
				}
				PoolManager.poolBufferInstance(buffers);
			}
			return true;
		}

		@Override
		public void onBufferEnd(Object userContext) {
			endBuffer(currentZe);
			Iterator<String> itr = refDigests.iterator();
			while (itr.hasNext()) {
				StoreManager.ref(itr.next());
			}
			itr = addDigests.iterator();
			while (itr.hasNext()) {
				StoreManager.unref(itr.next());
			}
			JSONObject response=new JSONObject();
			response.element("command", "import");
			response.element("result", "success");
			peer.message(response);
		}

		@Override
		public void onBufferFailure(Object userContext, Throwable failure) {
			if(store!=null){
				store.close(false);
				store=null;
			}
		}
	}

	/**
	 * /store id1 id2 /accessLog id1 id2 id3
	 * 
	 * @param pm
	 * @throws IOException
	 */
	// TODO非同期
	public void executeImport(Blob importBlob,PaPeer peer)
			throws IOException {
		UnzipConverter converter=UnzipConverter.create(new ImportGetter(this,peer));
		converter.parse(importBlob);
	}

	private void addDigest(Collection<String> digests, String digest) {
		if (digest != null) {
			digests.add(digest);
		}
	}

	// TODO非同期
	public File executeExport(PersistenceManager pm, Collection<Long> accessLogsIds)
			throws IOException {
		File exportFile = File.createTempFile("export", ".zip", config.getTmpDir());
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(exportFile));
		Set<String> traceDigests = new HashSet<String>();
		for (Long id : accessLogsIds) {
			AccessLog accessLog = null;
			try {
				accessLog = (AccessLog) pm.detachCopy(pm.getObjectById(
						AccessLog.class, id));
			} catch (JDOObjectNotFoundException e) {
				continue;
			}
			ZipEntry ze = new ZipEntry("/accessLog/" + accessLog.getId());
			addDigest(traceDigests, accessLog.getRequestHeaderDigest());
			addDigest(traceDigests, accessLog.getRequestBodyDigest());
			addDigest(traceDigests, accessLog.getResponseHeaderDigest());
			addDigest(traceDigests, accessLog.getResponseBodyDigest());
			String json = accessLog.toJson().toString();
			byte[] jsonBytes = json.getBytes("utf-8");
			int length = jsonBytes.length;
			ze.setSize(length);
			zos.putNextEntry(ze);
			zos.write(jsonBytes);
			zos.closeEntry();
		}
		for (String digest : traceDigests) {
			long length = StoreManager.getStoreLength(digest);
			long storeId = StoreManager.getStoreId(digest);
			if (length < 0 || storeId == Store.FREE_ID) {
				logger.warn("illegal digest:" + digest);
				continue;
			}
			ZipEntry ze = new ZipEntry("/store/" + Long.toString(storeId));
			ze.setSize(length);
			zos.putNextEntry(ze);
			StoreStream.storeToStream(storeId, zos);
			zos.closeEntry();
		}
		zos.close();// 必要
		return exportFile;
	}
	
	// TODO非同期
	public Collection<Long> queryAccessLog(PersistenceManager pm,String query) {
		Query q=pm.newQuery(query);
		Collection<Long>queryResult=(Collection<Long>)q.execute();
		return queryResult;
	}
	

	// TODO非同期
	public void executeDelete(PersistenceManager pm, Collection<Long> accessLogsIds) {
		pm.currentTransaction().begin();
		for (Long id : accessLogsIds) {
			AccessLog accessLog = pm.getObjectById(AccessLog.class, id);
			StoreManager.unref(accessLog.getRequestHeaderDigest());
			StoreManager.unref(accessLog.getRequestBodyDigest());
			StoreManager.unref(accessLog.getResponseHeaderDigest());
			StoreManager.unref(accessLog.getResponseBodyDigest());
			pm.deletePersistent(accessLog);
		}
		pm.currentTransaction().commit();
	}

	public void onTimer(Object userContext) {
		try {
			doJobs();
		} catch (Throwable t) {
			logger.error("doJobs error.", t);
		}
	}

	private PersistenceManager doJob(PersistenceManager pm, Object req) {
		JSONObject response=null;
		if (req instanceof AccessLog) {
			executeInsert(pm, (AccessLog) req);
			return pm;
		}
		RequestInfo requestInfo = (RequestInfo) req;
		switch (requestInfo.type) {
		case TYPE_QUERY_DELTE:
			requestInfo.ids=queryAccessLog(pm,requestInfo.query);
		case TYPE_LIST_DELTE:
			response=new JSONObject();
			response.element("command", "listDelete");
			response.element("result", "success");
			executeDelete(pm, requestInfo.ids);
			break;
		case TYPE_QUERY_EXPORT:
			requestInfo.ids=queryAccessLog(pm,requestInfo.query);
		case TYPE_LIST_EXPORT:
			try {
				File exportFile=executeExport(pm, requestInfo.ids);
				exportFile.deleteOnExit();
				Blob exportBlob=Blob.create(exportFile);
				requestInfo.peer.download(exportBlob);
			} catch (IOException e) {
				response=new JSONObject();
				response.element("command", "listExport");
				response.element("result", "fail");
				logger.warn("failt to export", e);
			} catch (Throwable t){
				response=new JSONObject();
				response.element("command", "listExport");
				response.element("result", "fail");
				logger.error("failt to export", t);
			}
			break;
		case TYPE_IMPORT:
			try {
				pm=null;
				executeImport(requestInfo.importBlob,requestInfo.peer);
			} catch (IOException e) {
				logger.warn("failt to import", e);
				response=new JSONObject();
				response.element("command", "import");
				response.element("result", "fail");
			}
			break;
		}
		if (requestInfo.peer != null && response!=null) {
			requestInfo.peer.message(response);
		}
		return pm;
	}

	private void doJobs() {
		PersistenceManager pm = JdoUtil.getPersistenceManager();
		while (true) {
			Object req = null;
			synchronized (requestQueue) {
				if (requestQueue.size() == 0) {
					break;
				}
				req = requestQueue.remove(0);
			}
			try{
				pm=doJob(pm, req);
			} finally {
				if(pm!=null && pm.currentTransaction().isActive()){
					pm.currentTransaction().rollback();
				}
			}
		}
	}

	private void queue(Object obj) {
		synchronized (requestQueue) {
			requestQueue.add(obj);
			if (isTerm) {
				doJobs();//timerが止められていたら、queueと同時に処理
			}
		}
	}

	/**
	 * 本当にinsertするかどうかはaccessLogの状態に依存する
	 * 
	 * @param accessLog
	 */
	public void insertAccessLog(AccessLog accessLog) {
		queue(accessLog);
	}

	private static final int TYPE_QUERY_DELTE = 1;
	private static final int TYPE_LIST_DELTE = 2;
	private static final int TYPE_QUERY_EXPORT = 3;
	private static final int TYPE_LIST_EXPORT = 4;
	private static final int TYPE_IMPORT = 5;

	private static class RequestInfo {
		RequestInfo(int type, String query, PaPeer peer) {
			this.type = type;
			this.query = query;
			this.peer = peer;
		}

		RequestInfo(int type, Collection<Long> ids, PaPeer peer) {
			this.type = type;
			this.ids = ids;
			this.peer = peer;
		}

		RequestInfo(int type, Blob importBlob, PaPeer peer) {
			this.type = type;
			this.importBlob = importBlob;
			this.peer = peer;
		}

		int type;
		String query;
//		String chId;
		PaPeer peer;
		Collection<Long> ids;
		Blob importBlob;
	}

	// 条件に一致したaccessLogを削除
	public void deleteAccessLog(String query,PaPeer peer) {
		queue(new RequestInfo(TYPE_QUERY_DELTE, query, peer));
	}

	// id列挙されたaccessLogを削除
	public void deleteAccessLog(Collection<Long> ids,PaPeer peer) {
		queue(new RequestInfo(TYPE_LIST_DELTE, ids, peer));
	}

	// 条件に一致したaccessLogを移出
	public void exportAccessLog(String query, PaPeer peer) {
		queue(new RequestInfo(TYPE_QUERY_EXPORT, query, peer));
	}

	// id列挙されたaccessLogを移出
	public void exportAccessLog(Collection<Long> ids, PaPeer peer) {
		queue(new RequestInfo(TYPE_LIST_EXPORT, ids, peer));
	}

	public void importAccessLog(Blob importFile, PaPeer peer) {
		queue(new RequestInfo(TYPE_IMPORT, importFile, peer));
	}
}
