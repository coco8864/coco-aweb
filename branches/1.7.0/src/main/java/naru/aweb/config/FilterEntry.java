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
import net.sf.json.JSON;
import net.sf.json.JSONSerializer;
import net.sf.json.JsonConfig;

@PersistenceCapable(identityType = IdentityType.APPLICATION,table="FILTER_ENTRY",detachable="true")
public class FilterEntry {
	private static JsonConfig jsonConfig;
	static{
		jsonConfig=new JsonConfig();
		jsonConfig.setRootClass(FilterCategory.class);
	}
	private static final String FILTERENTRY_QUERY="SELECT FROM " + FilterEntry.class.getName() + " ";

	public static JSON collectionToJson(Collection<FilterEntry> entrys){
		return JSONSerializer.toJSON(entrys,jsonConfig);
	}
	
	/**
	 * @param jdoql
	 * @param firstResult
	 * @param maxResults
	 * @return
	 */
	public static Collection<AccessLog> query(String whereSection,int from,int to,String ordering) {
		String queryString=FILTERENTRY_QUERY;
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
		return (Collection<AccessLog>)pm.detachCopyAll((Collection<AccessLog>)q.execute());
	}
	
	public static FilterEntry getByKey(FilterCategory category,String filter){
		PersistenceManager pm=JdoUtil.getPersistenceManager();
		Query query = pm.newQuery(FilterEntry.class, "filter==:filter && category == :category");		
		Collection<FilterEntry> list=(Collection<FilterEntry>)query.execute(filter,category);
		if(list.size()==0){
			return null;
		}
		return list.iterator().next();
	}
	
	public FilterEntry(FilterCategory category,String filter){
		this.category=category;
		this.filter=filter;
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
	
	@Persistent(persistenceModifier=PersistenceModifier.PERSISTENT)
	@Column(name="CATEGORY_ID")
	private FilterCategory category;
	
	@Persistent
	@Index(name="FILTER_IDX")	
	@Column(name="FILTER",jdbcType="VARCHAR", length=8192)
	private String filter;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public FilterCategory getCategory() {
		return category;
	}

	public void setCategory(FilterCategory category) {
		this.category = category;
	}

	public String getFilter() {
		return filter;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}
	
	
}
