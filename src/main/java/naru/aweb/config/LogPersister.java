package naru.aweb.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.apache.log4j.Logger;

import naru.async.Timer;
import naru.async.store.Store;
import naru.async.store.StoreManager;
import naru.async.store.StoreStream;
import naru.async.timer.TimerManager;
import naru.aweb.pa.Blob;
import naru.aweb.pa.PaPeer;
import naru.aweb.queue.QueueManager;
import naru.aweb.util.JdoUtil;

public class LogPersister implements Timer {
	private static Logger logger = Logger.getLogger(LogPersister.class);
//	private static QueueManager queueManager = QueueManager.getInstance();

	private long intervalTimeout = 1000;
//	private long timerId = -1;
	private Object interval=null;

	private List requestQueue = new LinkedList();
	// private List<AccessLog> insertQueue=new LinkedList<AccessLog>();
	// private List<AccessLog> deleteQueue=new LinkedList<AccessLog>();
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
		String chId = accessLog.getChId();
		if (accessLog.isPersist()) {
			pm.currentTransaction().begin();
			pm.makePersistent(accessLog);
			pm.currentTransaction().commit();
			pm.makeTransient(accessLog);// オブジェクトを再利用するために必要
		}
		if (chId != null) {
//			queueManager.complete(chId, accessLog.getId());
		}
		accessLog.unref();
	}

	/**
	 * /store id1 id2 /accessLog id1 id2 id3
	 * 
	 * @param pm
	 * @throws IOException
	 */
	// TODO非同期
	public void executeImport(PersistenceManager pm, Blob importBlob)
			throws IOException {
		ZipInputStream zis = null;
		if(true){
			return;
		}
		File importFile=null;
		InputStream fis=new FileInputStream(importFile);
		zis = new ZipInputStream(fis);
		Set<String> addDigests = new HashSet<String>();
		List<String> refDigests = new ArrayList<String>();
		// byte[] accessLogBytes=new byte[4096];
		while (true) {
			ZipEntry ze = zis.getNextEntry();
			if (ze == null) {
				break;
			}
			int size = (int) ze.getSize();
			String fileName = ze.getName();
			if (fileName.startsWith("/store")) {
				String digest = StoreStream.streamToStore(zis);
				addDigest(addDigests, digest);

			} else if (fileName.startsWith("/accessLog")) {
				InputStreamReader reader = new InputStreamReader(zis, "utf-8");
				StringBuilder sb = new StringBuilder();
				char[] buf = new char[1024];
				while (true) {
					int len = reader.read(buf);
					if (len <= 0) {
						break;
					}
					sb.append(buf, 0, len);
				}
				AccessLog accessLog = AccessLog.fromJson(sb.toString());
				if (accessLog == null) {
					continue;
				}
				addDigest(refDigests, accessLog.getRequestHeaderDigest());
				addDigest(refDigests, accessLog.getRequestBodyDigest());
				addDigest(refDigests, accessLog.getResponseHeaderDigest());
				addDigest(refDigests, accessLog.getResponseBodyDigest());

				accessLog.setId(null);
				accessLog.setPersist(true);
				executeInsert(pm, accessLog);
			}
		}

		Iterator<String> itr = refDigests.iterator();
		while (itr.hasNext()) {
			StoreManager.ref(itr.next());
		}
		itr = addDigests.iterator();
		while (itr.hasNext()) {
			StoreManager.unref(itr.next());
		}
		if(fis!=null){
			fis.close();
		}
	}

	private void addDigest(Collection<String> digests, String digest) {
		if (digest != null) {
			digests.add(digest);
		}
	}
	
	//private static Map<String,Blob>exportBlobs=new HashMap<String,Blob>();
	
	//public File popExportBlob(String cid){
	//	return exportBlobs.remove(cid);
	//}

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
			String json = accessLog.toJson();
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

	private void doJob(PersistenceManager pm, Object req) {
		if (req instanceof AccessLog) {
			executeInsert(pm, (AccessLog) req);
			return;
		}
		RequestInfo requestInfo = (RequestInfo) req;
		String message = null;
		switch (requestInfo.type) {
		case TYPE_QUERY_DELTE:
			requestInfo.ids=queryAccessLog(pm,requestInfo.query);
		case TYPE_LIST_DELTE:
			message = "delete count:" + requestInfo.ids.size();
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
				logger.warn("failt to export", e);
				message = "fail to export";
			}
			break;
		case TYPE_IMPORT:
			try {
				executeImport(pm, requestInfo.importBlob);
			} catch (IOException e) {
				logger.warn("failt to import", e);
				message = "fail to import";
			}
			break;
		}
		if (requestInfo.peer != null) {
			requestInfo.peer.message(message);
		}

	}

	private void doJobs() {
		PersistenceManager pm = JdoUtil.getPersistenceManager();
		// pm.currentTransaction().begin();
		// try {
		while (true) {
			Object req = null;
			synchronized (requestQueue) {
				if (requestQueue.size() == 0) {
					break;
				}
				req = requestQueue.remove(0);
			}
			try{
				doJob(pm, req);
			} finally {
				if(pm.currentTransaction().isActive()){
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
