package naru.aweb.config;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.Unique;

import naru.aweb.auth.Authenticator;
import naru.aweb.util.DatePropertyFilter;
import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;
import net.sf.json.util.NewBeanInstanceStrategy;

import org.apache.log4j.Logger;

/**
 * @author naru hayashi
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION,table="PH_USER",detachable="true")
public class User {
	private static Logger logger=Logger.getLogger(User.class);
	private static Config config=Config.getConfig();
	public static final String ROLE_ADMIN="admin";
	public static final String ROLE_GUEST="guest";
	public static final String ROLE_USER="user";
	
	private static class UserInstanceStrategy extends NewBeanInstanceStrategy{
		@Override
		public Object newInstance(Class clazz, JSONObject json)
				throws InstantiationException, IllegalAccessException,
				SecurityException, NoSuchMethodException,
				InvocationTargetException {
			if(clazz==User.class){
				Long id=json.optLong("id",-1);
				if(id>=0){
					return getById(id);
				}
			}
			return clazz.newInstance();
		}
	}
	
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(User.class);
		DatePropertyFilter dpf=new DatePropertyFilter();
		jsonConfig.setJavaPropertyFilter(dpf);
		jsonConfig.setJsonPropertyFilter(dpf);
		jsonConfig.setExcludes(new String[]{"passHash","digestAuthPassHash","offlinePassHash","certificateStoreId","extStoreId","admin","guest","dummyPassword","rolesList"});
		jsonConfig.setNewBeanInstanceStrategy(new UserInstanceStrategy());
	}
	private static final String USER_QUERY="select from " + User.class.getName() + " ";
	
	public static Collection<User> query(String whereSection,int from,int to,String ordering) {
		String queryString=USER_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where句とは限らない
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		try{
			pm.currentTransaction().begin();
			Query q=pm.newQuery(queryString);
			if(from>=0){
				q.setRange(from, to);
			}
			if(ordering!=null){
				q.setOrdering(ordering);
			}
			return (Collection<User>)pm.detachCopyAll((Collection<User>)q.execute());
		}finally{
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
	}
	
	public static User getByLoginId(String loginId) {
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query=pm.newQuery(User.class);
		query.setFilter("loginId==:loginId && revoked==false");
		Collection<User>users=pm.detachCopyAll((Collection<User>)query.execute(loginId));
		if(users==null){
			return null;
		}
		for(User user:users){
			return user;
		}
		return null;
	}

	public static User getById(Long id) {
		PersistenceManager pm=JdoUtil.currentPm();
		return pm.detachCopy(pm.getObjectById(User.class,id));
	}
	
	public static JSON collectionToJson(Collection<User> users){
		return JSONSerializer.toJSON(users,jsonConfig);
	}

	public static User fromJson(String jsonString){
		JSON json=JSONObject.fromObject(jsonString);
		User user=(User)JSONSerializer.toJava(json,jsonConfig);
		return user;
	}
	
	public static void deleteById(Long id){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		try {
			pm.currentTransaction().begin();
			User user = pm.getObjectById(User.class, id);
			if (user == null) {
				return;
			}
			pm.deletePersistent(user);
			pm.currentTransaction().commit();
			logger.info("deleteUser:"+user.getLoginId());
		} finally {
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
	}
	
	//確実に誤ったpasswordを返却する
	public String getDummyPassword(){
		String dummyPassword=Long.toString(System.currentTimeMillis());
		Authenticator authenticator=config.getAuthenticator();
		String credential=authenticator.calcPassHash(loginId,dummyPassword);
		if(credential.equals(this.passHash)){
			return dummyPassword+"#";
		}
		return dummyPassword;
	}
	
	public void changePassword(String newPassword,String realm){
		Authenticator authenticator=config.getAuthenticator();
		changePassword(authenticator, newPassword, realm);
	}
	
	/*　admin作成時は、初期化中なのでAuthenticatorから呼び出される*/
	public void changePassword(Authenticator authenticator,String newPassword,String realm){
		setPassHash(authenticator.calcPassHash(loginId,newPassword));
		//Digest認証は、MD5だから少し脆弱、Digest認証を使わない覚悟をするのであれば、あえてdigestAuthPassHashを設定する必要はない
		if(!config.getBoolean("isSkipDigestAuth", false)){
			setDigestAuthPassHash(authenticator.calcDigestAuthPassHash(loginId, newPassword, realm));
		}
		setChangePass(new Date());
		logger.info("changePassword:"+loginId);
	}
	
	public void changeOfflinePassword(String newPassword){
		Authenticator authenticator=config.getAuthenticator();
		setOfflinePassHash(authenticator.calcOfflinePassHash(loginId, newPassword));
		logger.info("changeOfflinePassword:"+loginId);
	}
	
	public JSON toJson(){
		JSON json=JSONSerializer.toJSON(this,jsonConfig);
		return json;
	}
	
	public void save() {
		logger.info("cleateUser:"+loginId);
		JdoUtil.insert(this);
	}
	
	public void update() {
		JdoUtil.update(this);
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Persistent
	@Unique(name="PH_USER_LOGIN_ID_IDX")	
	@Column(name="LOGIN_ID" ,jdbcType="VARCHAR", length=1024)
	private String loginId;
	
	@Persistent
	@Column(name="PASS_HASH")
	private String passHash;
	
	/**
	 * digest認証を行う可能性があるなら設定
	 */
	@Persistent
	@Column(name="DIGEST_AUTH_PASS_HASH")
	private String digestAuthPassHash;
	
	@Persistent
	@Column(name="OFFLINE_PASS_HASH")
	private String offlinePassHash;
	
	@Persistent
	@Column(name="CREATE_DATE")
	private Date createDate;//作成日付
	
	@Persistent
	@Column(name="CHANGE_PASS")
	private Date changePass;//パスワード変更日付
	
	@Persistent
	@Column(name="LAST_LOGIN")
	private Date lastLogin;//最終ログイン日付
	
	@Persistent
	@Column(name="REVOKED")
	private boolean revoked;//無効化
	
	@Persistent
	@Column(name="ORIGIN")
	private String origin;//fbなど他サーバで認証した結果作られた場合
	
	@Persistent
	@Column(name="ROLES",jdbcType="VARCHAR", length=1024)
	private String roles;
	
	@NotPersistent
	private List<String> rolesList=null;
	
	@Persistent
	@Column(name="NICKNAME",jdbcType="VARCHAR", length=1024)
	private String nickname;

	@Persistent
	@Column(name="CERTIFICATE_STORE_ID")
	private long certificateStoreId;
	
	@Persistent
	@Column(name="EXT_STORE_ID")
	private long extStoreId;
	
	@NotPersistent
	private int errorPassCount;//失敗回数、メモリのみで管理
	@NotPersistent
	private int loginCount;//現在ログイン数、メモリのみで管理
	
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
	 * @return the name
	 */
	public String getLoginId() {
		return loginId;
	}
	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}
	
	/**
	 * @return the role
	 */
	public String getRoles() {
		return roles;
	}
	public void setRoles(String roles) {
		if(rolesList!=null &&(roles==null || (roles!=null && !roles.equals(this.roles)))){
			rolesList=null;
		}
		this.roles = roles;
	}

	public boolean isAdmin() {
		List<String> list=getRolesList();
		return list.contains(ROLE_ADMIN);
	}
	
	
	public boolean isGuest() {
		List<String> list=getRolesList();
		return list.contains(ROLE_GUEST);
	}
	
	public List<String> getRolesList(){
		if(rolesList==null){
			rolesList=new ArrayList<String>();
			if(roles==null){
				return rolesList;
			}
			String[] rolesArray=roles.split(",");
			for(int i=0;i<rolesArray.length;i++){
				rolesList.add(rolesArray[i]);
			}
		}
		return  rolesList;
	}

	/**
	 * @return the createDate
	 */
	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	/**
	 * @return the changePass
	 */
	public Date getChangePass() {
		return changePass;
	}

	public void setChangePass(Date changePass) {
		this.changePass = changePass;
	}

	/**
	 * @return the lastLogin
	 */
	public Date getLastLogin() {
		return lastLogin;
	}

	public void setLastLogin(Date lastLogin) {
		this.lastLogin = lastLogin;
	}

	/**
	 * @return the certificateStoreId
	 */
	public long getCertificateStoreId() {
		return certificateStoreId;
	}

	public void setCertificateStoreId(long certificateStoreId) {
		this.certificateStoreId = certificateStoreId;
	}

	/**
	 * @return the extStoreId
	 */
	public long getExtStoreId() {
		return extStoreId;
	}

	public void setExtStoreId(long extStoreId) {
		this.extStoreId = extStoreId;
	}
	
	/**
	 * @return the revoked
	 */
	public boolean isRevoked() {
		return revoked;
	}

	public void setRevoked(boolean revoked) {
		this.revoked = revoked;
	}
	
	public int getErrorPassCount() {
		return errorPassCount;
	}

	public void setErrorPassCount(int errorPassCount) {
		this.errorPassCount = errorPassCount;
	}

	public int getLoginCount() {
		return loginCount;
	}

	public void login(){
		logger.info("login:"+loginId);
		loginCount++;
	}
	
	public boolean logout(){
		logger.info("logout:"+loginId);
		loginCount--;
		if(loginCount!=0){
			return false;
		}
		return true;
	}

	public String getPassHash() {
		return passHash;
	}

	public String getDigestAuthPassHash() {
		return digestAuthPassHash;
	}

	public void setPassHash(String passHash) {
		this.passHash = passHash;
	}

	public void setDigestAuthPassHash(String digestAuthPassHash) {
		this.digestAuthPassHash = digestAuthPassHash;
	}
	
	public String getOfflinePassHash() {
		return offlinePassHash;
	}

	public void setOfflinePassHash(String offlinePassHash) {
		this.offlinePassHash = offlinePassHash;
	}
	
	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}
}
