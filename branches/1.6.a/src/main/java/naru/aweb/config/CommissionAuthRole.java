package naru.aweb.config;

import java.util.Collection;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.PersistenceModifier;
import javax.jdo.annotations.Persistent;

import naru.aweb.util.JdoUtil;

@PersistenceCapable(identityType = IdentityType.APPLICATION,table="COMMISSION_AUTH_ROLE",detachable="true")
public class CommissionAuthRole {
	public static Collection<CommissionAuthRole> getByKey(String role){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query = pm.newQuery(CommissionAuthRole.class, "role == :role");
		Collection<CommissionAuthRole> list=(Collection<CommissionAuthRole>)query.execute(role);
		return list;
	}
	
	public CommissionAuthRole(String role,CommissionAuthUrl authUrl){
		this.role=role;
		this.authUrl=authUrl;
	}
	public void delete(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.deletePersistent(this);
	}
	
	public void save(){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		pm.makePersistent(this);
	}
	
	@Persistent(primaryKey="true",valueStrategy = IdGeneratorStrategy.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Persistent
	@Index(name="COMMISSION_AUTH_ROLE_IDX")	
	@Column(name="ROLE",jdbcType="VARCHAR", length=128)
	private String role;
	
	@Persistent(persistenceModifier=PersistenceModifier.PERSISTENT)
	@Column(name="AUTH_URL_ID")
	private CommissionAuthUrl authUrl;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public CommissionAuthUrl getAuthUrl() {
		return authUrl;
	}

	public void setAuthUrl(CommissionAuthUrl authUrl) {
		this.authUrl = authUrl;
	}
	
}
