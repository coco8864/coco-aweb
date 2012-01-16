package naru.aweb.config;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import naru.async.BufferGetter;
import naru.async.pool.PoolBase;
import naru.async.store.DataUtil;
import naru.async.store.Store;
import naru.aweb.http.HeaderParser;
import naru.aweb.util.DatePropertyFilter;
import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

import org.apache.log4j.Logger;

/**
 * 
 * 
 * @author naru hayashi
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION,table="ACCESS_LOG")
public class AccessLog extends PoolBase implements BufferGetter{
	private static final String ACCESSLOG_COUNT_QUERY="SELECT count(id) FROM " + AccessLog.class.getName();
	private static final String ACCESSLOG_QUERY="SELECT FROM " + AccessLog.class.getName() + " ";
	
	private static Logger logger=Logger.getLogger(AccessLog.class);	
	private static Config config=Config.getConfig();
//	private static LogPersister logPersister;
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(AccessLog.class);
		DatePropertyFilter dpf=new DatePropertyFilter();
		jsonConfig.setJavaPropertyFilter(dpf);
		jsonConfig.setJsonPropertyFilter(dpf);
		jsonConfig.setExcludes(new String[]{"poolId","chId","persist","timeCheckPint","life","shortFormat","skipPhlog"});
	}
	private static SimpleDateFormat logDateFormat=null; 
	static{
		logDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
	}
	public static String fomatLogDate(Date date){
		synchronized(logDateFormat){
			return logDateFormat.format(date);
		}
	}
	
	public static String calcResolveDigest(String method,boolean isHttps,String origin,String path,String query){
		StringBuilder sb=new StringBuilder();
		sb.append(method);
		if(isHttps){
			sb.append("https://");
		}else{
			sb.append("http://");
		}
		sb.append(origin);
		sb.append(path);
		if(query!=null){
			sb.append("?");
			sb.append(query);
		}
		String resolveString=sb.toString();
		try {
			logger.debug("calcResolveDigest resolveString:"+resolveString);
			String digest=DataUtil.digest(resolveString.getBytes(HeaderParser.HEADER_ENCODE));
			return digest;
		} catch (UnsupportedEncodingException e) {
			logger.error("getDigest error.",e);
		}
		return null;
	}
	
	public static JSON collectionToJson(Collection<AccessLog> accessLogs){
		return JSONSerializer.toJSON(accessLogs,jsonConfig);
	}
	
	public static AccessLog fromJson(String jsonString){
		AccessLog accessLog;
		try {
			JSON json=JSONObject.fromObject(jsonString);
			accessLog = (AccessLog)JSONSerializer.toJava(json,jsonConfig);
		} catch (RuntimeException e) {
			logger.error("fail to fromJson.jsonString:"+jsonString,e);
			return null;
		}
		return accessLog;
	}
	
	public static AccessLog getById(Long id){
		PersistenceManager pm=JdoUtil.currentPm();
		try{
			return (AccessLog) pm.detachCopy(pm.getObjectById(AccessLog.class,id));
		}catch(JDOObjectNotFoundException e){
			return null;
		}
	}
	
	public static long delete(String whereSection){
		String queryString=ACCESSLOG_QUERY;
		if(whereSection!=null){
			queryString+= " where "+whereSection;
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		long count=0;
		try{
			tx.begin();
			Query q=pm.newQuery(queryString);
			count=q.deletePersistentAll();
			tx.commit();
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
		return count;
	}
	
	public static long countOfAccessLog(String whereSection) {
		String queryString=ACCESSLOG_COUNT_QUERY;
		if(whereSection!=null){
			queryString+= " where "+whereSection;
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query q=pm.newQuery(queryString);
		Long count=(Long)q.execute();
		return count.longValue();
	}
	
	public static Collection<AccessLog> query(String whereSection) {
		return query(whereSection,-1,Integer.MAX_VALUE,null);
	}
	
	public static Collection<AccessLog> query(String whereSection,int from,int to) {
		return query(whereSection,from,to,null);
	}
	
	/**
	 * ���ʂ��g���I��������Ƃ́AHibernateUtil.clearSession()���鎖
	 * @param hql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<AccessLog> query(String whereSection,int from,int to,String ordering) {
		String queryString=ACCESSLOG_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where��Ƃ͌���Ȃ�
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query q=pm.newQuery(queryString);
		if(from>=0){
			q.setRange(from, to);
		}
		if(ordering!=null){
			q.setOrdering(ordering);
		}
		return (Collection<AccessLog>)pm.detachCopyAll((Collection<AccessLog>)q.execute());
	}
	
	private void insert(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		if(tx.isActive()){
			tx.rollback();
		}
		try{
			tx.begin();
			pm.makePersistent(this);
			tx.commit();
			pm.makeTransient(this);//�ė��p���邽�߂ɕK�v?
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
	}
	
	/* db�ɕۑ����邩�ۂ� */
	private boolean isPersist=false;
	public void setPersist(boolean isPersist){
		this.isPersist=isPersist;
	}
	public boolean isPersist(){
		return this.isPersist;
	}
	
	public void persist(){
		if(isPersist){
			insert();
		}
	}
	/* �ۑ����ɒʒm����Queue��channelId */
	private String chId;
	public void setChId(String chId){
		this.chId=chId;
	}
	public String getChId(){
		return chId;
	}
	
	public void delete(){
		PersistenceManager pm=JdoUtil.currentPm();
		pm.deletePersistent(this);
		JdoUtil.commit();
	}
	
	public String toJson(){
		JSON json=JSONSerializer.toJSON(this,jsonConfig);
		return json.toString();
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Persistent
	@Column(name="START_TIME")
	private Date startTime;
	
	@Persistent
	@Column(name="LOCAL_IP",jdbcType="VARCHAR", length=16)
	private String localIp;
	
	@Persistent
	@Index(name="USER_ID_IDX")	
	@Column(name="USER_ID",jdbcType="VARCHAR", length=16)
	private String userId;//user����16�����ȉ�!!!
	
	@Persistent
	@Column(name="IPADDRESS",jdbcType="VARCHAR", length=16)
	private String ip;
	
	@Persistent
	@Index(name="REQUEST_LINE_IDX")	
	@Column(name="REQUEST_LINE",jdbcType="VARCHAR", length=8192)
	private String requestLine;

	@Persistent
	@Column(name="REQUEST_HEADER_LENGTH")
	private long requestHeaderLength;//���N�G�X�g�w�b�_��
	
	@Persistent
	@Column(name="STATUS_CODE",jdbcType="CHAR", length=3)
	private String statusCode;
	
	@Persistent
	@Column(name="RESPONSE_HEADER_LENGTH")
	private long responseHeaderLength;//���X�|���X�w�b�_��
	
	@Persistent
	@Column(name="RESPONSE_LENGTH")
	private long responseLength;//�����M���X�|���X��(body)
	
	@Persistent
	@Column(name="PROCESS_TIME")
	private long processTime;
	
	/**
	 * requestHeader���������I��������ԁBstartTime����̍���
	 */
	@Persistent
	@Column(name="REQUEST_HEADER_TIME")
	private long requestHeaderTime;
	
	/**
	 * request���������I��������ԁBstartTime����̍���
	 */
	@Persistent
	@Column(name="REQUEST_BODY_TIME")
	private long requestBodyTime;
	
	/**
	 * response���������n�߂����ԁBstartTime����̍���
	 */
	@Persistent
	@Column(name="RESPONSE_HEADER_TIME")
	private long responseHeaderTime;
	
	/**
	 * response body���������n�߂����ԁBstartTime����̍���
	 */
	@Persistent
	@Column(name="RESPONSE_BODY_TIME")
	private long responseBodyTime;
	
	@Persistent
	@Column(name="REQUEST_HEADER_DIGEST",jdbcType="VARCHAR", length=32)
	private String requestHeaderDigest;//SSL�ʐM�̏ꍇ�A�������������N�G�X�g�d��
	
	@Persistent
	@Column(name="REQUEST_BODY_DIGEST",jdbcType="VARCHAR", length=32)
	private String requestBodyDigest;//SSL�ʐM�̏ꍇ�A�������������N�G�X�g�d��
	
	@Persistent
	@Column(name="RESPONSE_HEADER_DIGEST",jdbcType="VARCHAR", length=32)
	private String responseHeaderDigest;//SSL�ʐM�̏ꍇ�A�������������N�G�X�g�d��
	
	@Persistent
	@Column(name="RESPONSE_BODY_DIGEST",jdbcType="VARCHAR", length=32)
	@Index(name="RESPONSE_BODY_IDX")	
	private String responseBodyDigest;//SSL�ʐM�̏ꍇ�A�������������N�G�X�g�d��
	
	@Persistent
	@Column(name="PLAIN_RESPONSE_LENGTH")
	private long plainResponseLength;//�����X�|���X��(gzip�̏ꍇ���k�O�Achunk�̏ꍇchunk�O)
	
	@Persistent
	@Column(name="CONTENT_ENCODING",jdbcType="VARCHAR", length=128)
	private String contentEncoding;//gzip���k�������X�|���X���ۂ��H
	
	@Persistent
	@Column(name="TRANSFER_ENCODING",jdbcType="VARCHAR", length=128)
	private String transferEncoding;//chunk�������ۂ��H
	
	@Persistent
	@Column(name="CONTENT_TYPE",jdbcType="VARCHAR", length=128)
	private String contentType;//���X�|���X��contentType
	
	@Persistent
	@Column(name="CHANNEL_ID")
	private long channelId;//�ċN�������ނƏ���������邽�߈�ӂł͂Ȃ��AkeepAlive�������ɓ���������ۂ��𔻒f���邽��

	@Persistent
	@Column(name="ORIGINAL_LOG_ID")
	private long originalLogId;//simurate��response�ɎQ�Ƃ���id�Areplay��request�ɎQ�Ƃ���id
	
	public static final char SOURCE_TYPE_PLAIN_WEB='w';
	public static final char SOURCE_TYPE_SSL_WEB='W';
	public static final char SOURCE_TYPE_PLAIN_PROXY='p';
	public static final char SOURCE_TYPE_SSL_PROXY='P';
	public static final char SOURCE_TYPE_SIMULATE='s';
	public static final char SOURCE_TYPE_EDIT='E';
	@Persistent
	@Column(name="SOURCE_TYPE")
	private char sourceType;
	
	@Persistent
	@Column(name="REAL_HOST",jdbcType="VARCHAR", length=64)
	private String realHost;//localPort�����܂�
	
	public static final char DESTINATION_TYPE_HTTP='H';
	public static final char DESTINATION_TYPE_HTTPS='S';
	public static final char DESTINATION_TYPE_FILE='F';
	public static final char DESTINATION_TYPE_HANDLER='A';//aplication
	public static final char DESTINATION_TYPE_REPLAY='R';
	public static final char DESTINATION_TYPE_EDIT='E';
	@Persistent
	@Column(name="DESTINATION_TYPE")
	private char destinationType;
	
	@Persistent
	@Column(name="RESOLVE_ORIGIN",jdbcType="VARCHAR", length=128)
	private String resolveOrigin;//http,https�̏ꍇServre,file�̏ꍇfullpath,handler�̏ꍇ�A�N���X��
	
	/**
	 * ���N�G�X�g�����ʂ��邽�߂�digest�l
	 * ProxyHandler,Caller��response��trace����ꍇ�ݒ�
	 * proxy�o�R�Ń��N�G�X�g���邩�ۂ��ɂ���Ēl�͕ω����Ȃ��B
	 * 
	 * proxy���N�G�X�g�����A http://ph.domain/xxx�@�̏ꍇ http://domain/xxx�Ƃ��ċL�^
	 *�@sslProxy,Web���N�G�X�g�� /index.html �Ƀz�X�g����t�����ċL�^
	 */
	@Persistent
	@Index(name="RESOLVE_DIGEST_IDX")	
	@Column(name="RESOLVE_DIGEST",jdbcType="VARCHAR", length=32)
	private String resolveDigest;//destinationType+method+resolve+resolvePath?query

	@Persistent
	@Column(name="THINKING_TIME",defaultValue="0")
	private long thinkingTime;//���N�G�X�g����O�ɑ҂�������,stress�e�X�g�̂���
	
	public AccessLog(){
	}
	
	public void recycle(){
		id=null;
		startTime=null;
		userId=localIp=ip=requestLine=statusCode=null;
		contentEncoding=transferEncoding=null;
		channelId=-1;
		processTime=0;
		requestHeaderLength=responseHeaderLength=responseLength=0;
		requestHeaderTime=requestBodyTime=responseHeaderTime=responseBodyTime=0;
//		requestHeaderTrace=requestBodyTrace=responseHeaderTrace=responseBodyTrace=-1;
		contentEncoding=transferEncoding=contentType=null;
		originalLogId=-1;
		
		sourceType='-';
		realHost=null;
		destinationType='-';
		resolveOrigin=null;
		resolveDigest=null;
		traceCount=1;//ReqestContext����̎Q�ƕ�
		chId=null;
		isSkipPhlog=isShortFormat=false;
		thinkingTime=0;
//		requestHeaderStore=requestBodyStore=responseHeaderStore=responseBodyStore;
	}
	@NotPersistent
	private boolean isSkipPhlog=false;
	@NotPersistent
	private boolean isShortFormat=false;//TODO apache�������ɓ��I�ɑg�ݗ��Ă�
	
	public void log(boolean debug){
		if(!debug&&isSkipPhlog){//'/queue'�̂悤�ɑ�ʂɏo�͂����log�͏o�͂�}�~����
			return;
		}
		StringBuffer sb=new StringBuffer(256);
		if(startTime!=null){
			sb.append(fomatLogDate(startTime));
		}else{
			sb.append(fomatLogDate(new Date(0)));
		}
		sb.append(" ");
		if(ip!=null){
			sb.append(ip);
		}else{
			sb.append("-");
		}
		sb.append(" ");
		if(userId!=null){
			sb.append(userId);
		}else{
			sb.append("-");
		}
		sb.append(" \"");
		sb.append(requestLine);
		sb.append("\" ");
		sb.append(statusCode);
		sb.append(" ");
		sb.append(responseLength);
		sb.append(" ");
		sb.append(processTime);
		
		if(!debug && !isShortFormat){
			sb.append("#");
			sb.append(getRealHost());
			sb.append(",");
			switch(getSourceType()){
			case SOURCE_TYPE_PLAIN_WEB:
				sb.append("plainWeb");
				break;
			case SOURCE_TYPE_SSL_WEB:
				sb.append("sslWeb");
				break;
			case SOURCE_TYPE_PLAIN_PROXY:
				sb.append("plainProxy");
				break;
			case SOURCE_TYPE_SSL_PROXY:
				sb.append("sslProxy");
				break;
			default:
				sb.append("-");
			}
			sb.append(",");
			sb.append(getDestinationType());
			sb.append(",");
			sb.append(getContentEncoding());
			sb.append(",");
			sb.append(getTransferEncoding());
			sb.append(",");
			sb.append(getRequestHeaderTime());//request�w�b�_���������I���������
			sb.append(",");
			sb.append(getRequestBodyTime());//request���������I���������
			sb.append(",");
			sb.append(getResponseHeaderTime());//response���J�n��������
			sb.append(",");
			sb.append(getResponseBodyTime());//response body�������J�n��������
			sb.append(",");
			sb.append(getChannelId());
		}
		logger.info(sb.toString());
	}
	
	/**
	 * @return the id
	 */
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	
	/**
	 * @return the userId
	 */
	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	
	/**
	 * @return the startTime
	 */
	public Date getStartTime() {
		return startTime;
	}
	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}
	
	/**
	 * @return the ip
	 */
	public String getIp() {
		return ip;
	}
	public void setIp(String ip) {
		this.ip = ip;
	}
	
	/**
	 * @return the requestLine
	 */
	public String getRequestLine() {
		return requestLine;
	}
	public void setRequestLine(String requestLine) {
		this.requestLine = requestLine;
	}
	
	/**
	 * @return the statusCode
	 */
	public String getStatusCode() {
		return statusCode;
	}
	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}
	
	/**
	 * @return the responsLength
	 */
	public long getResponseLength() {
		return responseLength;
	}
	public void setResponseLength(long responseLength) {
		this.responseLength = responseLength;
	}
	
	/**
	 * @return the requestHeaderLength
	 */
	public long getRequestHeaderLength() {
		return requestHeaderLength;
	}

	public void setRequestHeaderLength(long requestHeaderLength) {
		this.requestHeaderLength = requestHeaderLength;
	}
	
	/**
	 * @return the responseHeaderLength
	 */
	public long getResponseHeaderLength() {
		return responseHeaderLength;
	}

	public void setResponseHeaderLength(long responseHeaderLength) {
		this.responseHeaderLength = responseHeaderLength;
	}
	
	/**
	 * @return the processTime
	 */
	public long getProcessTime() {
		return processTime;
	}
	public void setProcessTime(long processTime) {
		this.processTime = processTime;
	}
	
	public enum TimePoint{
		requestHeader,
		requestBody,
		responseHeader,
		responseBody
	}
	
	public void setTimeCheckPint(TimePoint timePoint) {
		if(startTime==null){
			startTime=new Date(System.currentTimeMillis());
		}
		long start=startTime.getTime();
		long diff=System.currentTimeMillis()-start;
		switch(timePoint){
		case requestHeader:
			setRequestHeaderTime(diff);
			break;
		case requestBody:
			setRequestBodyTime(diff);
			break;
		case responseHeader:
			setResponseHeaderTime(diff);
			break;
		case responseBody:
			setResponseBodyTime(diff);
			break;
		}
	}
	
	//�����I����̒��ߍ�Ƃ��s��
	//request top(startTime) -> requestHeader end(0)
	//requestHeader end -> requestBody top(x)=(0)
	//requestBody top -> requestBody end(1)
	//requestBody end -> response top(2)
	//response top -> responseHeader end(y)=(2)
	//responseHeader end -> responseBody top(3)
	//responseBody top -> responseBody end(endProcess now)
	public void endProcess(){//�����I�����ɌĂяo��
		if(startTime==null){
			setProcessTime(-1);
			return;
		}
		long start=startTime.getTime();
		long responseBodyEnd=System.currentTimeMillis();
		setProcessTime(responseBodyEnd-start);
	}
	
	/**
	 * @return the timeRecode
	 */
	/*
	public String getTimeRecode() {
		return timeRecode;
	}
	public void setTimeRecode(String timeRecode) {
		this.timeRecode = timeRecode;
	}
	*/
	
	/**
	 * @return the plainResponseLength
	 */
	public long getPlainResponseLength() {
		return plainResponseLength;
	}

	public void setPlainResponseLength(long plainResponseLength) {
		this.plainResponseLength = plainResponseLength;
	}

	/**
	 * @return the contentEncoding
	 */
	public String getContentEncoding() {
		return contentEncoding;
	}

	public void setContentEncoding(String contentEncoding) {
		this.contentEncoding = contentEncoding;
	}

	/**
	 * @return the transferEncoding
	 */
	public String getTransferEncoding() {
		return transferEncoding;
	}

	public void setTransferEncoding(String transferEncoding) {
		this.transferEncoding = transferEncoding;
	}

	/**
	 * @return the channelId
	 */
	public long getChannelId() {
		return channelId;
	}

	public void setChannelId(long channelId) {
		this.channelId = channelId;
	}

	/**
	 * @return the localIp
	 */
	public String getLocalIp() {
		return localIp;
	}

	public void setLocalIp(String localIp) {
		this.localIp = localIp;
	}
	
	/**
	 * @return the originalLogId
	 */
	public long getOriginalLogId() {
		return originalLogId;
	}

	public void setOriginalLogId(long originalLogId) {
		this.originalLogId = originalLogId;
	}

	/**
	 * @return the sourceType
	 */
	public char getSourceType() {
		return sourceType;
	}

	public void setSourceType(char sourceType) {
		this.sourceType = sourceType;
	}

	/**
	 * @return the hostHeader
	 */
	public String getRealHost() {
		return realHost;
	}

	public void setRealHost(String realHost) {
		this.realHost = realHost;
	}

	/**
	 * @return the destinationType
	 */
	public char getDestinationType() {
		return destinationType;
	}

	public void setDestinationType(char destinationType) {
		this.destinationType = destinationType;
	}

	/**
	 * @return the resolveOrigin
	 */
	public String getResolveOrigin() {
		return resolveOrigin;
	}

	public void setResolveOrigin(String resolveOrigin) {
		this.resolveOrigin = resolveOrigin;
	}

	/**
	 * @return the responseTraceLength
	 */
	public String getResolveDigest() {
		return resolveDigest;
	}

	public void setResolveDigest(String resolveDigest) {
		this.resolveDigest = resolveDigest;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	
	/*�@�ȉ��̏�������������LogPersister�ɑ���
	1)���N�G�X�g���I������
	2)requestHeaderTrace���I������
	3)requestBodyTrace���I������
	4)responseHeaderTrace���I������
	5)responseBodyTrace���I������
	*/
	@NotPersistent
	private int traceCount=1;
	public synchronized void incTrace(){
		traceCount++;
	}
	public synchronized void decTrace(){
		traceCount--;
		if(traceCount==0){
			LogPersister logPersister=config.getLogPersister();
			if(logPersister==null){
				logger.debug("logPersister is null");
				log(true);
				return;//��~���̏ꍇ
			}
			logPersister.insertAccessLog(this);
		}
	}
	
	public boolean onBuffer(Object userContext, ByteBuffer[] buffers) {
		throw new RuntimeException("never use");
	}

	public void onBufferEnd(Object userContext) {
		Store store=(Store)userContext;
		logger.debug("store:"+store.getStoreId());
		decTrace();
	}

	public void onBufferFailure(Object userContext, Throwable failure) {
		logger.warn("AccessLog onBufferFailure.",failure);
		decTrace();
	}

	public String getRequestHeaderDigest() {
		return requestHeaderDigest;
	}

	public void setRequestHeaderDigest(String requestHeaderDigest) {
		this.requestHeaderDigest = requestHeaderDigest;
	}

	public String getRequestBodyDigest() {
		return requestBodyDigest;
	}

	public void setRequestBodyDigest(String requestBodyDigest) {
		this.requestBodyDigest = requestBodyDigest;
	}

	public String getResponseHeaderDigest() {
		return responseHeaderDigest;
	}

	public void setResponseHeaderDigest(String responseHeaderDigest) {
		this.responseHeaderDigest = responseHeaderDigest;
	}

	public String getResponseBodyDigest() {
		return responseBodyDigest;
	}

	public void setResponseBodyDigest(String responseBodyDigest) {
		this.responseBodyDigest = responseBodyDigest;
	}

	public long getRequestHeaderTime() {
		return requestHeaderTime;
	}

	public void setRequestHeaderTime(long requestHeaderTime) {
		this.requestHeaderTime = requestHeaderTime;
	}

	public long getRequestBodyTime() {
		return requestBodyTime;
	}

	public void setRequestBodyTime(long requestBodyTime) {
		this.requestBodyTime = requestBodyTime;
	}

	public long getResponseHeaderTime() {
		return responseHeaderTime;
	}

	public void setResponseHeaderTime(long responseHeaderTime) {
		this.responseHeaderTime = responseHeaderTime;
	}

	public long getResponseBodyTime() {
		return responseBodyTime;
	}

	public void setResponseBodyTime(long responseBodyTime) {
		this.responseBodyTime = responseBodyTime;
	}

	public void setSkipPhlog(boolean isSkipPhlog) {
		this.isSkipPhlog = isSkipPhlog;
	}

	public void setShortFormat(boolean isShortFormat) {
		this.isShortFormat = isShortFormat;
	}

	public long getThinkingTime() {
		return thinkingTime;
	}

	public void setThinkingTime(long thinkingTime) {
		this.thinkingTime = thinkingTime;
	}
}