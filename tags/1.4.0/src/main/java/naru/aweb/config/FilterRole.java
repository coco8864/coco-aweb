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

@PersistenceCapable(identityType = IdentityType.APPLICATION,table="FILTER_ROLE",detachable="true")
public class FilterRole {
	
	public static Collection<FilterRole> getByKey(String role,boolean isBlack){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query = pm.newQuery(FilterRole.class, "role == :role && isBlack==:isBlack");		
		Collection<FilterRole> list=(Collection<FilterRole>)query.execute(role,isBlack);
		return list;
	}
	
	public FilterRole(String role,boolean isBlack,FilterCategory category){
		this.role=role;
		this.isBlack=isBlack;
		this.category=category;
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
	@Index(name="ROLE_IDX")	
	@Column(name="ROLE",jdbcType="VARCHAR", length=128)
	private String role;
	
	@Persistent
	@Index(name="ROLE_IDX")	
	@Column(name="IS_BLACK")
	private boolean isBlack;//blackList‚©whileList‚©‚Ìƒtƒ‰ƒO
	
	@Persistent(persistenceModifier=PersistenceModifier.PERSISTENT)
	@Column(name="CATEGORY_ID")
	private FilterCategory category;

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

	public boolean isBlack() {
		return isBlack;
	}

	public void setBlack(boolean isBlack) {
		this.isBlack = isBlack;
	}

	public FilterCategory getCategory() {
		return category;
	}

	public void setCategory(FilterCategory category) {
		this.category = category;
	}
	
}
