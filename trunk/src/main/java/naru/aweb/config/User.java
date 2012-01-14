package naru.aweb.config;

import java.io.UnsupportedEncodingException;
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

import naru.async.store.DataUtil;
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
		jsonConfig.setExcludes(new String[]{"passHash","digestAuthPassHash","admin","guest","dummyPassword","rolesList"});
		jsonConfig.setNewBeanInstanceStrategy(new UserInstanceStrategy());
	}
	private static final String USER_QUERY="select from " + User.class.getName() + " ";
	
	public static Collection<User> query(String whereSection,int from,int to,String ordering) {
		String queryString=USER_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where句とは限らない
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.currentTransaction().begin();
		Query q=pm.newQuery(queryString);
		if(from>=0){
			q.setRange(from, to);
		}
		if(ordering!=null){
			q.setOrdering(ordering);
		}
		try{
			return (Collection<User>)pm.detachCopyAll((Collection<User>)q.execute());
		}finally{
			if(pm.currentTransaction().isActive()){
				pm.currentTransaction().rollback();
			}
		}
	}
	
	public static User getByLoginId(String loginId) {
		Collection<User>users=query("WHERE loginId=='"+loginId+"'",-1,0,null);
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
	
	public static JSON collectionToJson(Collection<User> mappings){
		return JSONSerializer.toJSON(mappings,jsonConfig);
	}

	public static User fromJson(String jsonString){
		JSON json=JSONObject.fromObject(jsonString);
		User user=(User)JSONSerializer.toJava(json,jsonConfig);
		return user;
	}
	
	public static void deleteById(Long id){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.currentTransaction().begin();
		try {
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
	
	public static String calcPassHashSha1(String username,String password){
		StringBuffer sb=new StringBuffer(username);
		sb.append(':');
		sb.append(password);
		try {
			return DataUtil.digestHexSha1(sb.toString().getBytes("iso8859_1"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String calcDigestAuthPassHash(String username,String password,String realm){
		StringBuilder sb=new StringBuilder();
		sb.append(username);
		sb.append(":");
		sb.append(realm);
		sb.append(":");
		sb.append(password);
		try {
			return DataUtil.digestHex(sb.toString().getBytes("iso8859_1"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	//確実に誤ったpasswordを返却する
	public String getDummyPassword(){
		String dummyPassword=Long.toString(System.currentTimeMillis());
		String credential=calcPassHashSha1(loginId,dummyPassword);
		if(credential.equals(this.passHash)){
			return dummyPassword+"#";
		}
		return dummyPassword;
	}
	
	public void changePassword(String newPassword,String realm){
		setPassHash(calcPassHashSha1(loginId,newPassword));
		//Digest認証は、MD5だから少し脆弱、Digest認証を使わない覚悟をするのであれば、あえてdigestAuthPassHashを設定する必要はない
		if(!config.getBoolean("isSkipDigestAuth", false)){
			setDigestAuthPassHash(calcDigestAuthPassHash(loginId, newPassword, realm));
		}
		setChangePass(new Date());
		logger.info("changePassword:"+loginId);
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
	
//	public void delete(){
//		logger.info("deleteUser:"+loginId);
//		JdoUtil.delete("SELECT FROM "+ User.class.getName() + " WHERE id=="+this.id);
//	}
	
	
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
	@Column(name="ROLES",jdbcType="VARCHAR", length=1024)
	private String roles;
	
	@NotPersistent
	private List<String> rolesList=null;
	
	@Persistent
	@Column(name="FIRST_NAME",jdbcType="VARCHAR", length=1024)
	private String firstName;
	
	@Persistent
	@Column(name="LAST_NAME",jdbcType="VARCHAR", length=1024)
	private String lastName;
	
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
	@Column(name="FOOT_SIZE")
	private int footSize;
	
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
	 * @return the firstName
	 */
	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	/**
	 * @return the lastName
	 */
	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
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
	 * @return the footSize
	 */
	public int getFootSize() {
		return footSize;
	}

	public void setFootSize(int footSize) {
		this.footSize = footSize;
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
}
