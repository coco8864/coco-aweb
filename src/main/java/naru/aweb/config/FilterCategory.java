package naru.aweb.config;

import java.util.Collection;
import java.util.Date;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.annotations.Column;
import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.Index;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import naru.aweb.util.DatePropertyFilter;
import naru.aweb.util.JdoUtil;
import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

@PersistenceCapable(identityType = IdentityType.APPLICATION,table="FILTER_CATEGORY",detachable="true")
public class FilterCategory {
	private static final String FILTERCATEGORY_QUERY="SELECT FROM " + FilterCategory.class.getName() + " ";
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(FilterCategory.class);
		DatePropertyFilter dpf=new DatePropertyFilter();
		jsonConfig.setJavaPropertyFilter(dpf);
		jsonConfig.setJsonPropertyFilter(dpf);
	}
	
	public static JSON collectionToJson(Collection<FilterCategory> categorys){
		return JSONSerializer.toJSON(categorys,jsonConfig);
	}
	/**
	 * @param jdoql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<FilterCategory> query(String whereSection,int from,int to,String ordering) {
		String queryString=FILTERCATEGORY_QUERY;
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
		return (Collection<FilterCategory>)pm.detachCopyAll((Collection<FilterCategory>)q.execute());
	}
	
	public static FilterCategory getByKey(String source,String category,boolean isUrl){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query = pm.newQuery(FilterCategory.class, "source ==:source && category==:category && isUrl==:isUrl");		
		Collection<FilterCategory> list=(Collection<FilterCategory>)query.execute(source,category,isUrl);
		if(list.size()==0){
			return null;
		}
		return list.iterator().next();
	}
	
	public static FilterCategory getById(Long id){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		return pm.getObjectById(FilterCategory.class, id);
	}
	
	public FilterCategory(String source,String category,boolean isUrl){
		this.source=source;
		this.category=category;
		this.isUrl=isUrl;
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
	@Index(name="CATEGORY_IDX")	
	@Column(name="SOURCE",jdbcType="VARCHAR", length=1024)
	private String source;
	
	@Persistent
	@Index(name="CATEGORY_IDX")	
	@Column(name="CATEGORY",jdbcType="VARCHAR", length=1024)
	private String category;
	
	@Persistent
	@Index(name="CATEGORY_IDX")	
	@Column(name="IS_URL")
	private boolean isUrl;
	
	@Persistent
	@Column(name="LAST_UPDATE")
	private Date lastUpdate;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public boolean isUrl() {
		return isUrl;
	}

	public void setUrl(boolean isUrl) {
		this.isUrl = isUrl;
	}

	public Date getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(Date lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
}
