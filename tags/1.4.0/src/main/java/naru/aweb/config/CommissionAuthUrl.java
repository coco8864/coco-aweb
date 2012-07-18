package naru.aweb.config;

import java.util.Collection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

@PersistenceCapable(identityType = IdentityType.APPLICATION,table="COMMISSION_AUTH_URL",detachable="true")
public class CommissionAuthUrl {
	private static final String COMMISSION_AUTH_DEF_QUERY="SELECT FROM " + CommissionAuthUrl.class.getName() + " ";
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(CommissionAuthUrl.class);
	}
	
	public static JSON collectionToJson(Collection<CommissionAuthUrl> categorys){
		return JSONSerializer.toJSON(categorys,jsonConfig);
	}
	/**
	 * @param jdoql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<CommissionAuthUrl> query(String whereSection,int from,int to,String ordering) {
		String queryString=COMMISSION_AUTH_DEF_QUERY;
		if(whereSection!=null){
			queryString+= whereSection;//where‹å‚Æ‚ÍŒÀ‚ç‚È‚¢
		}
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query q=pm.newQuery(queryString);
		if(from>=0){
			q.setRange(from, to);
		}
		if(ordering!=null){
			q.setOrdering(ordering);
		}
		return (Collection<CommissionAuthUrl>)pm.detachCopyAll((Collection<CommissionAuthUrl>)q.execute());
	}
	public static CommissionAuthUrl getById(Long id){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		return pm.getObjectById(CommissionAuthUrl.class, id);
	}
	
	public CommissionAuthUrl(String notes,String realm,String authType,String authMethod,String authUrl,String authBody){
		this.notes=notes;
		this.realm=realm;
		this.authType=authType;
		this.authMethod=authMethod;
		this.authUrl=authUrl;
		this.authBody=authBody;
	}
	
	public String toJson(){
		JSON json=JSONSerializer.toJSON(this,jsonConfig);
		return json.toString();
	}
	
	public void save(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.makePersistent(this);
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Persistent
	@Column(name="NOTES",jdbcType="VARCHAR", length=1024)
	private String notes;
	
	@Persistent
	@Column(name="RELME",jdbcType="VARCHAR", length=128)
	private String realm;
	
	@Persistent
	@Column(name="AUTH_TYPE",jdbcType="VARCHAR", length=16)
	private String authType;

	@Persistent
	@Column(name="AUTH_METHOD",jdbcType="VARCHAR", length=8)
	private String authMethod;

	@Persistent
	@Column(name="AUTH_URL",jdbcType="VARCHAR", length=8192)
	private String authUrl;
	
	@Persistent
	@Column(name="AUTH_BODY",jdbcType="VARCHAR", length=8192)
	private String authBody;

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getAuthType() {
		return authType;
	}
	public void setAuthType(String authType) {
		this.authType = authType;
	}
	public String getAuthMethod() {
		return authMethod;
	}
	public void setAuthMethod(String authMethod) {
		this.authMethod = authMethod;
	}
	public String getAuthUrl() {
		return authUrl;
	}
	public void setAuthUrl(String authUrl) {
		this.authUrl = authUrl;
	}
	public String getAuthBody() {
		return authBody;
	}
	public void setAuthBody(String authBody) {
		this.authBody = authBody;
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
}
