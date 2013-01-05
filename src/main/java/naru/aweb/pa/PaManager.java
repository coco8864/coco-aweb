package naru.aweb.pa;

import java.util.Map;

public class PaManager {
	
	private static PaManager instance=new PaManager();
	public static PaManager getInstance(){
		return instance;
	}
	
	private Map<String,PaletWrapper> paletWrappers;//qname->palet
	
	public PaletWrapper deploy(String qname,Palet palet){
		return null;
	}
	
	public PaletWrapper undeploy(String qname){
		return null;
	}
	
	public PaletWrapper getPaletWrapper(String qname){
		return null;
	}
	
	

}
