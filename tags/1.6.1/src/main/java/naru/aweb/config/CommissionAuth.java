package naru.aweb.config;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.PersistenceModifier;
import javax.jdo.annotations.Persistent;

import naru.aweb.auth.User;
import naru.aweb.util.CipherUtil;
import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

/**
 * ブラウザ側で管理する。
 * そのため、db保存内容とは乖離する可能性がある。
 * @author Naru
 *
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION,table="COMMISSION_AUTH",detachable="true")
public class CommissionAuth {
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(CommissionAuth.class);
		jsonConfig.setExcludes(new String[]{"user","authDataPlain","authData"});
	}
	
	public static final String BASIC="basic";
	public static final String FORM="form";
	public static final String COOKIE="cookie";
	
	public static JSON collectionToJson(Collection<CommissionAuth> commissionAuths){
		return JSONSerializer.toJSON(commissionAuths,jsonConfig);
	}
	
	private static final String COMMISSION_AUTH_QUERY="SELECT FROM " + CommissionAuth.class.getName() + " ";
	/**
	 * @param jdoql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<CommissionAuth> query(String whereSection,int from,int to,String ordering) {
		String queryString=COMMISSION_AUTH_QUERY;
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
		return (Collection<CommissionAuth>)pm.detachCopyAll((Collection<CommissionAuth>)q.execute());
	}
	
	public static Collection<CommissionAuth> getByKey(User user,String authUrl){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query = pm.newQuery(CommissionAuth.class, "user==:user && authUrl==:authUrl");
		Collection<CommissionAuth> list=(Collection<CommissionAuth>)query.execute(user,authUrl);
		return list;
	}
	public static Collection<CommissionAuth> getByUser(User user){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query = pm.newQuery(CommissionAuth.class, "user==:user");
		Collection<CommissionAuth> list=(Collection<CommissionAuth>)query.execute(user);
		return list;
	}
	
	public static CommissionAuth getById(Long id){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		return pm.getObjectById(CommissionAuth.class, id);
	}
	
	public void save(){
		save(false);
	}
	
	public void save(boolean isCommit){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		if(isCommit){
			pm.currentTransaction().begin();
		}
		try{
			pm.makePersistent(this);
			if(isCommit){
				pm.currentTransaction().commit();
			}
		}finally{
			if(isCommit&&pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
	}
	public void remove(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.deletePersistent(this);
	}
	
	public String toJson(){
		JSON json=JSONSerializer.toJSON(this,jsonConfig);
		return json.toString();
	}
	
	public CommissionAuth(User user,String authUrl){
		this.user=user;
		this.authUrl=authUrl;
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	private Long id;
	
	/**
	 * 認証情報を保持しているが一時的に使わない時に使用
	 * 保存しますかメッセージにnoと答えた場合も、falseに設定
	 * この場合、認証情報は保持しない。
	 */
	@Persistent
	@Column(name="ENABLED")
	private boolean enabled;

	/**
	 * form認証の実行が指示された際、
	 * trueの場合、authUrlをリクエストせず、直接actionUrlにpostする
	 * falseの場合、一旦authUrl表示後、actionUrlにpostする
	 */
	@Persistent
	@Column(name="FORCE_POST")
	private boolean forcePost;
	
	/**
	 * form認証について、
	 * trueの場合、authUrlを訪れた際、自動でactionUrlにpostする
	 * falseの場合、「よいですか」メッセージボックスを表示
	 */
	@Persistent
	@Column(name="AUTO_POST")
	private boolean autoPost;
	
	@Persistent
	@Column(name="AUTH_TYPE",jdbcType="VARCHAR", length=16)
	private String authType;
	
	@Persistent
	@Column(name="NOTES",jdbcType="VARCHAR", length=1024)
	private String notes;//表示名
	
	@Persistent
	@Column(name="REALM",jdbcType="VARCHAR", length=128)
	private String realm;//basicの場合realm,formの場合form画面のtitle
	
	@Persistent
	@Column(name="AUTH_METHOD",jdbcType="VARCHAR", length=8)
	private String authMethod;

	@Persistent
	@Column(name="AUTH_URL",jdbcType="VARCHAR", length=8192)
	@Index(name="COMMISSION_AUTH_IDX")
	private String authUrl;
	
	@Persistent
	@Column(name="AUTH_BODY",jdbcType="VARCHAR", length=8192)
	private String authBody;
	
	@Persistent
	@Column(name="ACTION_URL",jdbcType="VARCHAR", length=8192)
	private String actionUrl;	
	
	@Persistent(persistenceModifier=PersistenceModifier.PERSISTENT)
	@Index(name="COMMISSION_AUTH_IDX")
	@Column(name="USER_ID")
	private User user;
	
	/**
	 * 認証情報を保存しているか否か
	 * ブラウザにはformPost時にauthDataを渡す
	 */
	@Persistent
	@Column(name="ISDATA")
	private boolean isData;
	
	@Persistent
	@Column(name="AUTH_DATA",jdbcType="BLOB")
	private byte[] authData;
	
	public enum BASIC_PROCESS {
		INIT,
		PROCESSING,
		PROCESSED
	}
	/**
	 * 同一session内で当該commissionAuthのために401->200にするのは１回だけ
	 * 後は、ブラウザの回復力に任せる
	 */
	@NotPersistent
	private BASIC_PROCESS basicProcess=BASIC_PROCESS.INIT;
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getAuthUrl() {
		return authUrl;
	}
	public void setAuthUrl(String authUrl) {
		this.authUrl = authUrl;
	}
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	public byte[] getAuthData() {
		return authData;
	}
	public void setAuthData(byte[] authData) {
		if(authData==null){
			isData=false;
		}else{
			isData=true;
		}
		this.authData = authData;
	}
	
	private static final String DEFAULT_CHIPHER_PASSWORD="changeIt!!";
	private static final String CHIPHER_ENCODING="utf8";
	public void setAuthDataPlain(String authData){
		setAuthDataPlain(authData,DEFAULT_CHIPHER_PASSWORD);
	}
	
	public void setAuthDataPlain(String authData,String pass){
		if(authData==null){
			setAuthData(null);
			return;
		}
		//人によってpasswordを変化させる。
		//tableのdataをuserを跨いでmoveしても使えなくする
		User user=getUser();
		if(user==null){
			throw new RuntimeException("fail to setAuthDataPlain not set user.");
		}
		byte[] ciphertext=null;
		try {
			ciphertext = CipherUtil.encrypt(authData.getBytes(CHIPHER_ENCODING), pass+user.getId());
		} catch (UnsupportedEncodingException e) {
		}
		if(ciphertext==null){
			throw new RuntimeException("fail to setAuthDataPlain encrypt.");
		}
		setAuthData(ciphertext);
	}

	public String getAuthDataPlain(){
		return getAuthDataPlain(DEFAULT_CHIPHER_PASSWORD);
	}
	
	public String getAuthDataPlain(String pass){
		User user=getUser();
		if(user==null){
			throw new RuntimeException("fail to getAuthDataPlain not set user.");
		}
		byte[] ciphertext=getAuthData();
		if(ciphertext==null){
			return null;
//			throw new RuntimeException("fail to getAuthDataPlain not set authData.");
		}
		byte[] cleartext= CipherUtil.decrypt(ciphertext, pass+user.getId());
		if(cleartext==null){
			throw new RuntimeException("fail to getAuthDataPlain decrypt.");
		}
		String authData=null;
		try {
			authData=new String(cleartext,CHIPHER_ENCODING);
		} catch (UnsupportedEncodingException e) {
		}
		return authData;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getAuthType() {
		return authType;
	}

	public void setAuthType(String authType) {
		this.authType = authType;
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

	public String getRealm() {
		return realm;
	}

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public String getAuthMethod() {
		return authMethod;
	}

	public void setAuthMethod(String authMethod) {
		this.authMethod = authMethod;
	}

	public String getAuthBody() {
		return authBody;
	}

	public void setAuthBody(String authBody) {
		this.authBody = authBody;
	}

	public String getActionUrl() {
		return actionUrl;
	}

	public void setActionUrl(String actionUrl) {
		this.actionUrl = actionUrl;
	}

	public boolean isForcePost() {
		return forcePost;
	}

	public void setForcePost(boolean forcePost) {
		this.forcePost = forcePost;
	}

	public boolean isAutoPost() {
		return autoPost;
	}

	public void setAutoPost(boolean autoPost) {
		this.autoPost = autoPost;
	}

	public boolean isData() {
		return isData;
	}

	public void setData(boolean isData) {
		this.isData = isData;
	}
	
	public void initBasicProcess(){
		basicProcess=BASIC_PROCESS.INIT;
	}
	public synchronized boolean startBasicProcess(){
		switch(basicProcess){
		case INIT:
			basicProcess=BASIC_PROCESS.PROCESSING;
			return true;
		case PROCESSED:
			return false;
		}
		try {
			wait(3000);
		} catch (InterruptedException e) {
		}
		return false;
	}
	public synchronized void endBasicProcess(){
		basicProcess=BASIC_PROCESS.PROCESSED;
		notifyAll();
	}
}
