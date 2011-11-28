package naru.aweb.http;

import java.util.HashSet;
import java.util.Set;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.util.ServerParser;

public class CookieLocation extends PoolBase {
	private boolean isSecure=false;
	private ServerParser domain;
	private String path;
	private Set<String> equalsUrls=new HashSet<String>();
	
	public static CookieLocation parse(boolean isSecure,String domain,String path){
		CookieLocation cookieLocation=(CookieLocation)PoolManager.getInstance(CookieLocation.class);
		cookieLocation.setup(isSecure, domain, path);
		return cookieLocation;
	}
	
	@Override
	public void recycle() {
		if(domain!=null){
			domain.unref();
			domain=null;
		}
		path=null;
		equalsUrls.clear();
	}

	public void setup(boolean isSecure,String domain,String path){
		String proto;
		boolean isDefaultPort=false;
		this.isSecure=isSecure;
		this.path=path;
		if(domain==null){//必要ない場合
			this.domain=null;
			return;
		}
		if(isSecure){
			proto="https://";
			this.domain=ServerParser.parse(domain,443);
			if(this.domain.getPort()==443){
				isDefaultPort=true;
			}
		}else{
			proto="http://";
			this.domain=ServerParser.parse(domain,80);
			if(this.domain.getPort()==80){
				isDefaultPort=true;
			}
		}
		//URL表現のバリエーション
		if(path==null){
			equalsUrls.add(proto + this.domain.getHost()+":"+this.domain.getPort()+"/");
			equalsUrls.add(proto + this.domain.getHost()+":"+this.domain.getPort());
		}else{
			equalsUrls.add(proto + this.domain.getHost()+":"+this.domain.getPort()+ path);
		}
		if(isDefaultPort){
			if(path==null){
				equalsUrls.add(proto + this.domain.getHost() +"/");
				equalsUrls.add(proto + this.domain.getHost());
			}else{
				equalsUrls.add(proto + this.domain.getHost() + path);
			}
		}
	}
	
	//url表現と一致するかどうかをチェック
	public boolean isMatch(String authUrl){
		return equalsUrls.contains(authUrl);
	}
	
	public boolean isMatch(boolean isSecure,ServerParser domain,String path){
		if(this.isSecure!=isSecure){
			return false;
		}
		if(!this.domain.equals(domain)){
			return false;
		}
		if(this.path!=null){
			if( this.path.equals(path) ){
				return true;
			}
			if("/".equals(this.path) && path==null){
				return true;
			}
		}else{
			if("/".equals(path)||path==null){
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof CookieLocation)){
			return false;
		}
		CookieLocation cl=(CookieLocation)obj;
		return isMatch(cl.isSecure,cl.domain,cl.path);
	}

	@Override
	public int hashCode() {
		int hash=0;
		if(path!=null){
			hash+=path.hashCode();
		}
		if(isSecure){
			hash+=1;
		}
		if(domain!=null){
			hash+=domain.hashCode();
		}
		return hash;
	}

	public boolean isSecure() {
		return isSecure;
	}

	public String getDomain() {
		if(domain==null){
			return null;
		}
		return domain.toString();
	}

	public String getPath() {
		return path;
	}
}
