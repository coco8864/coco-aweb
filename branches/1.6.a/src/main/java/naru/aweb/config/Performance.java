package naru.aweb.config;

import java.util.Collection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

@PersistenceCapable(identityType = IdentityType.APPLICATION,table="PERFORMANCE",detachable="true")
public class Performance {
	private static final String PERFORMANCE_QUERY="SELECT FROM " + Performance.class.getName() + " ";
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(Performance.class);
	}
	
	public static JSON collectionToJson(Collection<Performance> accessLogs){
		return JSONSerializer.toJSON(accessLogs,jsonConfig);
	}
	
	/**
	 * 結果を使い終わったあとは、HibernateUtil.clearSession()する事
	 * @param hql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<Performance> query(String whereSection,int from,int to,String ordering) {
		String queryString=PERFORMANCE_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where句とは限らない
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query q=pm.newQuery(queryString);
		if(from>=0){
			q.setRange(from, to);
		}
		if(ordering!=null){
			q.setOrdering(ordering);
		}
		return (Collection<Performance>)pm.detachCopyAll((Collection<Performance>)q.execute());
	}
	
	public static long delete(String whereSection){
		String queryString=PERFORMANCE_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where句とは限らない
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
	
	private double ave(long sum){
		if(count==0){
			return Double.NaN;
		}
		return (double)sum/(double)count;
	}
	
//	private double stdev(long sum,long sumsq){
//	}
	
	public static String csvTitle(){
		StringBuilder sb=new StringBuilder();
		sb.append("id");
		sb.append(',');
		sb.append("name");
		sb.append(',');
		sb.append("isMaster");
		sb.append(',');
		sb.append("testBrowserCount");
		sb.append(',');
		sb.append("testRequestCount");
		sb.append(',');
		sb.append("testLoopCount");
		sb.append(',');
		sb.append("testThinkingTime");
		sb.append(',');
		sb.append("isSsl");
		sb.append(',');
		sb.append("requestLine");
		sb.append(',');
		sb.append("statusCode");
		sb.append(',');
		
		sb.append("startTime");
		sb.append(',');
		sb.append("lastTime");
		sb.append(',');
		
		sb.append("count");
		sb.append(',');
		sb.append("responseLengthSum");
		sb.append(',');
		sb.append("maxMemorySum");
		sb.append(',');
		sb.append("freeMemorySum");
		sb.append(',');
		sb.append("thinkingTimeSum");
		sb.append(',');
		
		sb.append("processTimeSum");
		sb.append(',');
		sb.append("requestHeaderTimeSum");
		sb.append(',');
		sb.append("requestBodyTimeSum");
		sb.append(',');
		sb.append("responseHeaderTimeSum");
		sb.append(',');
		sb.append("responseBodyTimeSum");
		sb.append(',');
		
		sb.append("processTimeSumsq");
		sb.append(',');
		sb.append("requestHeaderTimeSumsq");
		sb.append(',');
		sb.append("requestBodyTimeSumsq");
		sb.append(',');
		sb.append("responseHeaderTimeSumsq");
		sb.append(',');
		sb.append("responseBodyTimeSumsq");
		sb.append("\r\n");
		return sb.toString();
	}
	
	public String toCsv(){
		StringBuilder sb=new StringBuilder();
		sb.append(id);
		sb.append(',');
		sb.append(name);
		sb.append(',');
		sb.append(isMaster);
		sb.append(',');
		sb.append(testBrowserCount);
		sb.append(',');
		sb.append(testRequestCount);
		sb.append(',');
		sb.append(testLoopCount);
		sb.append(',');
		sb.append(testThinkingTime);
		sb.append(',');
		sb.append(isSsl);
		sb.append(',');
		sb.append(requestLine);
		sb.append(',');
		sb.append(statusCode);
		sb.append(',');
		
		sb.append(startTime);
		sb.append(',');
		sb.append(lastTime);
		sb.append(',');
		
		sb.append(count);
		sb.append(',');
		sb.append(responseLengthSum);
		sb.append(',');
		sb.append(maxMemorySum);
		sb.append(',');
		sb.append(freeMemorySum);
		sb.append(',');
		sb.append(thinkingTimeSum);
		sb.append(',');
		
		sb.append(processTimeSum);
		sb.append(',');
		sb.append(requestHeaderTimeSum);
		sb.append(',');
		sb.append(requestBodyTimeSum);
		sb.append(',');
		sb.append(responseHeaderTimeSum);
		sb.append(',');
		sb.append(responseBodyTimeSum);
		sb.append(',');
		
		sb.append(processTimeSumsq);
		sb.append(',');
		sb.append(requestHeaderTimeSumsq);
		sb.append(',');
		sb.append(requestBodyTimeSumsq);
		sb.append(',');
		sb.append(responseHeaderTimeSumsq);
		sb.append(',');
		sb.append(responseBodyTimeSumsq);
		sb.append("\r\n");
		return sb.toString();
	}
	
	public void insert(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Transaction tx=pm.currentTransaction();
		if(tx.isActive()){
			tx.rollback();
		}
		try{
			tx.begin();
			pm.makePersistent(this);
			tx.commit();
		}finally{
			if(tx.isActive()){
				tx.rollback();
			}
		}
	}
	
	public String toJson(){
		JSON json=JSONSerializer.toJSON(this,jsonConfig);
		return json.toString();
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Persistent
	@Column(name="NAME",jdbcType="VARCHAR", length=128)
	private String name;
	
	@Persistent
	@Column(name="START_TIME")
	private long startTime;
	
	@Persistent
	@Column(name="LAST_TIME")
	private long lastTime;
	
	@Persistent
	@Column(name="IS_MASTER")
	private boolean isMaster;
	
	@Persistent
	@Column(name="IS_Ssl",defaultValue="false")
	private boolean isSsl;
	
	@Persistent
	@Column(name="REQUEST_LINE",jdbcType="VARCHAR", length=8192)
	private String requestLine;
	
	@Persistent
	@Column(name="REQUEST_HEADER_DIGEST",jdbcType="VARCHAR", length=32)
	private String requestHeaderDigest;
	@Persistent
	@Column(name="REQUEST_BODY_DIGEST",jdbcType="VARCHAR", length=32)
	private String requestBodyDigest;
	@Persistent
	@Column(name="STATUS_CODE",jdbcType="VARCHAR", length=3)
	private String statusCode;
	@Persistent
	@Column(name="RESPONSE_BODY_DIGEST",jdbcType="VARCHAR", length=32)
	private String responseBodyDigest;
	
	@Persistent
	@Column(name="COUNT")
	private int count;
	
	@Persistent
	@Column(name="RESPONSE_LENGTH_SUM")
	private long responseLengthSum;
	
	@Persistent
	@Column(name="PROCESS_TIME_SUM")
	private long processTimeSum;
	@Persistent
	@Column(name="PROCESS_TIME_SUMSQ")
	private long processTimeSumsq;
	@Persistent
	@Column(name="REQUEST_HEADER_TIME_SUM")
	private long requestHeaderTimeSum;
	@Persistent
	@Column(name="REQUEST_HEADER_TIME_SUMSQ")
	private long requestHeaderTimeSumsq;
	@Persistent
	@Column(name="REQUEST_BODY_TIME_SUM")
	private long requestBodyTimeSum;
	@Persistent
	@Column(name="REQUEST_BODY_TIME_SUMSQ")
	private long requestBodyTimeSumsq;
	@Persistent
	@Column(name="RESPONSE_HEADER_TIME_SUM")
	private long responseHeaderTimeSum;
	@Persistent
	@Column(name="RESPONSE_HEADER_TIME_SUMSQ")
	private long responseHeaderTimeSumsq;
	@Persistent
	@Column(name="RESPONSE_BODY_TIME_SUM")
	private long responseBodyTimeSum;
	@Persistent
	@Column(name="RESPONSE_BODY_TIME_SUMSQ")
	private long responseBodyTimeSumsq;
	@Persistent
	@Column(name="MAX_MEMORY_SUM",defaultValue="0")
	private long maxMemorySum;
	@Persistent
	@Column(name="FREE_MEMORY_SUM",defaultValue="0")
	private long freeMemorySum;
	@Persistent
	@Column(name="THINKING_TIME_SUM",defaultValue="0")
	private long thinkingTimeSum;
	
	//テストの実行環境
	@Persistent
	@Column(name="TEST_THINKING_TIME",defaultValue="0")
	private long testThinkingTime;
	
	@Persistent
	@Column(name="TEST_BROWSER_COUNT")
	private int testBrowserCount;
	
	@Persistent
	@Column(name="TEST_REQUEST_COUNT")
	private int testRequestCount;
	
	@Persistent
	@Column(name="TEST_LOOP_COUNT")
	private int testLoopCount;
	
	
	@NotPersistent
//	private WebClientCollector webClientCollector=new WebClientCollector();
	
	public void init(boolean isMaster,String name,int browserCount,int requestCount,int loopCount,long thinkingTime,AccessLog accessLog){
		this.isMaster=isMaster;
		this.name=name;
		this.testBrowserCount=browserCount;
		this.testRequestCount=requestCount;
		this.testLoopCount=loopCount;
		this.testThinkingTime=thinkingTime;
		this.requestLine=accessLog.getRequestLine();
		this.isSsl=false;//Caller#startRequestでhttp/httpsをDestinationTypeに設定している
		if(accessLog.getDestinationType()==AccessLog.DESTINATION_TYPE_HTTPS){
			this.isSsl=true;
		}
		this.requestHeaderDigest=accessLog.getRequestHeaderDigest();
		this.requestBodyDigest=accessLog.getRequestBodyDigest();
		this.statusCode=accessLog.getStatusCode();
		this.responseBodyDigest=accessLog.getResponseBodyDigest();
		this.startTime=accessLog.getStartTime().getTime();
		
		this.count=1;
		this.lastTime=System.currentTimeMillis();
		this.responseLengthSum=accessLog.getResponseLength();
		long t;
		t=accessLog.getProcessTime();
		processTimeSum+=t;
		processTimeSumsq+=(t*t);
		t=accessLog.getRequestHeaderTime();
		requestHeaderTimeSum+=t;
		requestHeaderTimeSumsq+=(t*t);
		t=accessLog.getRequestBodyTime();
		requestBodyTimeSum+=t;
		requestBodyTimeSumsq+=(t*t);
		t=accessLog.getResponseHeaderTime();
		responseHeaderTimeSum+=t;
		responseHeaderTimeSumsq+=(t*t);
		t=accessLog.getResponseBodyTime();
		responseBodyTimeSum+=t;
		responseBodyTimeSumsq+=(t*t);
		
//		webClientCollector.add(accessLog.getWebClientLog());
	}
	
	public synchronized void add(AccessLog accessLog){
		if(statusCode!=null && !statusCode.equals(accessLog.getStatusCode())){
			statusCode=null;
		}
		if(responseBodyDigest!=null && !responseBodyDigest.equals(accessLog.getResponseBodyDigest())){
			responseBodyDigest=null;
		}
		if(requestHeaderDigest!=null&&!requestHeaderDigest.equals(accessLog.getRequestHeaderDigest())){
			requestLine=null;
			requestHeaderDigest=null;
			requestBodyDigest=null;
		}
		count++;
		this.lastTime=System.currentTimeMillis();
		responseLengthSum+=accessLog.getResponseLength();
		long t;
		t=accessLog.getProcessTime();
		processTimeSum+=t;
		processTimeSumsq+=(t*t);
		t=accessLog.getRequestHeaderTime();
		requestHeaderTimeSum+=t;
		requestHeaderTimeSumsq+=(t*t);
		t=accessLog.getRequestBodyTime();
		requestBodyTimeSum+=t;
		requestBodyTimeSumsq+=(t*t);
		t=accessLog.getResponseHeaderTime();
		responseHeaderTimeSum+=t;
		responseHeaderTimeSumsq+=(t*t);
		t=accessLog.getResponseBodyTime();
		responseBodyTimeSum+=t;
		responseBodyTimeSumsq+=(t*t);
		thinkingTimeSum+=accessLog.getThinkingTime();
		Runtime runtime=Runtime.getRuntime();
		maxMemorySum+=runtime.maxMemory();
		freeMemorySum+=runtime.freeMemory();
		
//		webClientCollector.add(accessLog.getWebClientLog());
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
	 * @return the id
	 */
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

	public String getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}

	public String getResponseBodyDigest() {
		return responseBodyDigest;
	}

	public void setResponseBodyDigest(String responseBodyDigest) {
		this.responseBodyDigest = responseBodyDigest;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public long getProcessTimeSum() {
		return processTimeSum;
	}

	public void setProcessTimeSum(long processTimeSum) {
		this.processTimeSum = processTimeSum;
	}

	public long getProcessTimeSumsq() {
		return processTimeSumsq;
	}

	public void setProcessTimeSumsq(long processTimeSumsq) {
		this.processTimeSumsq = processTimeSumsq;
	}

	public long getRequestHeaderTimeSum() {
		return requestHeaderTimeSum;
	}

	public void setRequestHeaderTimeSum(long requestHeaderTimeSum) {
		this.requestHeaderTimeSum = requestHeaderTimeSum;
	}

	public long getRequestHeaderTimeSumsq() {
		return requestHeaderTimeSumsq;
	}

	public void setRequestHeaderTimeSumsq(long requestHeaderTimeSumsq) {
		this.requestHeaderTimeSumsq = requestHeaderTimeSumsq;
	}

	public long getRequestBodyTimeSum() {
		return requestBodyTimeSum;
	}

	public void setRequestBodyTimeSum(long requestBodyTimeSum) {
		this.requestBodyTimeSum = requestBodyTimeSum;
	}

	public long getRequestBodyTimeSumsq() {
		return requestBodyTimeSumsq;
	}

	public void setRequestBodyTimeSumsq(long requestBodyTimeSumsq) {
		this.requestBodyTimeSumsq = requestBodyTimeSumsq;
	}

	public long getResponseHeaderTimeSum() {
		return responseHeaderTimeSum;
	}

	public void setResponseHeaderTimeSum(long responseHeaderTimeSum) {
		this.responseHeaderTimeSum = responseHeaderTimeSum;
	}

	public long getResponseHeaderTimeSumsq() {
		return responseHeaderTimeSumsq;
	}

	public void setResponseHeaderTimeSumsq(long responseHeaderTimeSumsq) {
		this.responseHeaderTimeSumsq = responseHeaderTimeSumsq;
	}

	public long getResponseBodyTimeSum() {
		return responseBodyTimeSum;
	}

	public void setResponseBodyTimeSum(long responseBodyTimeSum) {
		this.responseBodyTimeSum = responseBodyTimeSum;
	}

	public long getResponseBodyTimeSumsq() {
		return responseBodyTimeSumsq;
	}

	public void setResponseBodyTimeSumsq(long responseBodyTimeSumsq) {
		this.responseBodyTimeSumsq = responseBodyTimeSumsq;
	}

	public int getTestBrowserCount() {
		return testBrowserCount;
	}

	public void setTestBrowserCount(int testBrowserCount) {
		this.testBrowserCount = testBrowserCount;
	}

	public int getTestRequestCount() {
		return testRequestCount;
	}

	public void setTestRequestCount(int testRequestCount) {
		this.testRequestCount = testRequestCount;
	}

	public int getTestLoopCount() {
		return testLoopCount;
	}

	public void setTestLoopCount(int testLoopCount) {
		this.testLoopCount = testLoopCount;
	}

	public boolean isMaster() {
		return isMaster;
	}

	public void setMaster(boolean isMaster) {
		this.isMaster = isMaster;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getLastTime() {
		return lastTime;
	}

	public void setLastTime(long lastTime) {
		this.lastTime = lastTime;
	}

	public long getResponseLengthSum() {
		return responseLengthSum;
	}

	public void setResponseLengthSum(long responseLengthSum) {
		this.responseLengthSum = responseLengthSum;
	}

	public long getMaxMemorySum() {
		return maxMemorySum;
	}

	public long getFreeMemorySum() {
		return freeMemorySum;
	}

	public void setMaxMemorySum(long maxMemorySum) {
		this.maxMemorySum = maxMemorySum;
	}

	public void setFreeMemorySum(long freeMemorySum) {
		this.freeMemorySum = freeMemorySum;
	}

	public long getThinkingTimeSum() {
		return thinkingTimeSum;
	}

	public long getTestThinkingTime() {
		return testThinkingTime;
	}

	public void setThinkingTimeSum(long thinkingTimeSum) {
		this.thinkingTimeSum = thinkingTimeSum;
	}

	public void setTestThinkingTime(long testThinkingTime) {
		this.testThinkingTime = testThinkingTime;
	}

	public boolean isSsl() {
		return isSsl;
	}

	public void setSsl(boolean isSsl) {
		this.isSsl = isSsl;
	}
	
}
