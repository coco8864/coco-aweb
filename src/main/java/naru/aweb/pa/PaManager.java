package naru.aweb.pa;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PaManager {
	
	private static PaManager instance=new PaManager();
	public static PaManager getInstance(){
		return instance;
	}
	
	private Map<String,PaletWrapper> paletWrappers=new HashMap<String,PaletWrapper>();//qname->palet
	public synchronized PaletWrapper deploy(String qname,String paletName){
		try {
			Class clazz = Class.forName(paletName);
			Palet palet = (Palet) clazz.newInstance();
			PaletWrapper paletWrapper = new PaletWrapper(qname, palet);
			paletWrappers.put(qname, paletWrapper);
			return paletWrapper;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public synchronized PaletWrapper undeploy(String qname){
		PaletWrapper paletWrapper=paletWrappers.remove(qname);
		if(paletWrapper!=null){
			paletWrapper.terminate();
		}
		return paletWrapper;
	}
	
	public PaletWrapper getPaletWrapper(String qname){
		return paletWrappers.get(qname);
	}
	
	public synchronized Set<String> qnames(){
		return paletWrappers.keySet();
	}
}
