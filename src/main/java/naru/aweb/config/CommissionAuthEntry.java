package naru.aweb.config;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Iterator;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.PersistenceModifier;
import javax.jdo.annotations.Persistent;

import naru.aweb.auth.User;
import naru.aweb.util.CipherUtil;
import naru.aweb.util.JdoUtil;

@PersistenceCapable(identityType = IdentityType.APPLICATION,table="COMMISSION_AUTH_ENTRY",detachable="true")
public class CommissionAuthEntry {
	private static final String COMMISSION_AUTH_DEF_QUERY="SELECT FROM " + CommissionAuthEntry.class.getName() + " ";
	/**
	 * @param jdoql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<CommissionAuthEntry> query(String whereSection,int from,int to,String ordering) {
		String queryString=COMMISSION_AUTH_DEF_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//whereãÂÇ∆ÇÕå¿ÇÁÇ»Ç¢
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query q=pm.newQuery(queryString);
		if(from>=0){
			q.setRange(from, to);
		}
		if(ordering!=null){
			q.setOrdering(ordering);
		}
		return (Collection<CommissionAuthEntry>)pm.detachCopyAll((Collection<CommissionAuthEntry>)q.execute());
	}
	
	public static CommissionAuthEntry getByKey(User user,CommissionAuthUrl authUrl){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query = pm.newQuery(CommissionAuthEntry.class, "user == :user && authUrl==:authUrl");		
		Collection<CommissionAuthEntry> list=(Collection<CommissionAuthEntry>)query.execute(user,authUrl);
		Iterator<CommissionAuthEntry> itr=list.iterator();
		if(itr.hasNext()){
			return itr.next();
		}
		return null;
	}
	
	public void save(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.makePersistent(this);
	}
	public void remove(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.deletePersistent(this);
	}
	
	public CommissionAuthEntry(User user,CommissionAuthUrl authUrl){
		this.user=user;
		this.authUrl=authUrl;
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	private Long id;
	
	@Persistent(persistenceModifier=PersistenceModifier.PERSISTENT)
	@Index(name="COMMISSION_AUTH_ENTRY_IDX")	
	@Column(name="AUTH_URL_ID")
	private CommissionAuthUrl authUrl;
	
	@Persistent(persistenceModifier=PersistenceModifier.PERSISTENT)
	@Index(name="COMMISSION_AUTH_ENTRY_IDX")	
	@Column(name="USER_ID")
	private User user;
	
	@Persistent
	@Column(name="AUTH_DATA",jdbcType="BLOB")
	private byte[] authData;
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public CommissionAuthUrl getAuthUrl() {
		return authUrl;
	}
	public void setAuthUrl(CommissionAuthUrl authUrl) {
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
		this.authData = authData;
	}
	
	private static final String DEFAULT_CHIPHER_PASSWORD="changeIt!!";
	private static final String CHIPHER_ENCODING="utf8";
	public void setAuthDataPlain(String authData){
		setAuthDataPlain(authData,DEFAULT_CHIPHER_PASSWORD);
	}
	
	public void setAuthDataPlain(String authData,String pass){
		//êlÇ…ÇÊÇ¡ÇƒpasswordÇïœâªÇ≥ÇπÇÈÅB
		//tableÇÃdataÇuserÇå◊Ç¢Ç≈moveÇµÇƒÇ‡égÇ¶Ç»Ç≠Ç∑ÇÈ
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
			throw new RuntimeException("fail to getAuthDataPlain not set authData.");
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
}
