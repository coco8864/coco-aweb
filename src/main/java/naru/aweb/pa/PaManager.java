package naru.aweb.pa;

import java.util.HashMap;
import java.util.Map;

public class PaManager {
	
	private static PaManager instance=new PaManager();
	public static PaManager getInstance(){
		return instance;
	}
	
	private Map<String,PaletWrapper> paletWrappers=new HashMap<String,PaletWrapper>();//qname->palet
	public PaletWrapper deploy(String qname,String paletName){
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
	
	public PaletWrapper undeploy(String qname){
		PaletWrapper paletWrapper=paletWrappers.get(qname);
		if(paletWrapper!=null){
			paletWrapper.terminate();
		}
		return paletWrapper;
	}
	
	public PaletWrapper getPaletWrapper(String qname){
		return paletWrappers.get(qname);
	}
}
