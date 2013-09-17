package naru.aweb.config;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import naru.aweb.handler.WebServerHandler;
import naru.aweb.http.Cookie;
import naru.aweb.http.HeaderParser;
import net.sf.json.JSONObject;

/**
 * option:{appcache:{enabled:true,manifestPath:'/phoffline.appcache',cacheFilePath:'/phoffline.html',cacheFilePattern:'...'}}
 * @author naru
 *
 */
public class AppcacheOption {
	private static final String DEFAULT_CACHE_FILE_PATTERN=".*\\.html$|.*\\.htm$|.*\\.css$|.*\\.js$|.*\\.jpg$|.*\\.png$|.*\\.gif$";
	private static final String DEFAULT_MANIFEST_PATH="/~ph.appcache";
	private static final String DEFAULT_CACHE_HTML_PATH="/~ph.vsp";
	private static Logger logger = Logger.getLogger(AppcacheOption.class);
	private static Config config = Config.getConfig();
	
	private boolean enabled=false;
	private String manifestPath;
	private String manifestAbsPath;
	private String cacheHtmlPath;
	private File destinationFile;
	private String sourcePath;
	private String cacheFilePattern;
	private List<String> cachePaths=new ArrayList<String>();
	
	public AppcacheOption(File destinationFile,String sourcePath,JSONObject options){
		this.destinationFile=destinationFile;
		this.sourcePath=sourcePath;
		this.enabled=options.optBoolean("enabled",true);
		this.manifestPath=options.optString("manifestPath",DEFAULT_MANIFEST_PATH);
		this.cacheHtmlPath=options.optString("cacheHtmlPath",DEFAULT_CACHE_HTML_PATH);
		this.cacheFilePattern=options.optString("cacheFilePattern",DEFAULT_CACHE_FILE_PATTERN);
		
		if("/".equals(sourcePath)){
			manifestAbsPath=manifestPath;
		}else{
			manifestAbsPath=sourcePath+manifestPath;
		}
		setup();
	}
	
	private void setup(){
		/* TODO 必須コンテンツ */
		cachePaths.add("/pub/js/jquery-1.8.3.min.js");
		cachePaths.add("/pub/js/ph-json2.js");
		cachePaths.add("/pub/js/aes.js");
		cachePaths.add("/pub/js/aplOffline.js");
		cachePaths.add("/pub/js/sha256.js");
		cachePaths.add("/pub/js/authOffline.js");
		if(destinationFile==null){
			return;
		}
		cachePaths.add("");
		cachePaths.add("/");
		Pattern pattern=Pattern.compile(cacheFilePattern);
		Set<String>files=new HashSet<String>();
		collectFile(destinationFile,files,pattern);
		String base=destinationFile.getAbsolutePath();
		for(String abs:files){
			if(!abs.startsWith(base)){
				continue;
			}
			String filePath=abs.substring(base.length());
			filePath=filePath.replaceAll("\\\\", "/");
			if(!filePath.startsWith("/")){
				filePath="/"+filePath;
			}
			String path=null;
			if("/".equals(sourcePath)){
				path=filePath;
			}else{
				path=sourcePath + filePath;
			}
			cachePaths.add(path);
		}
	}
	
	private void collectFile(File file,Set<String>files,Pattern pattern){
		if(file.isFile()){
			String fileName=file.getAbsolutePath();
			Matcher m=pattern.matcher(fileName);
			if(m.matches()){
				files.add(fileName);
			}
		}if(file.isDirectory()){
			for(File f:file.listFiles()){
				collectFile(f,files,pattern);
			}
		}
	}
	
	private void checkAppcacheManifest(WebServerHandler handler,String phappcache,String cookieKey){
		if("unused".equals(phappcache)){
			handler.completeResponse("404", "file not exists");
		}else{
			forwardAppcacheTemplate(handler, phappcache, "~ph.appcache",cookieKey);
		}
	}
	
	private static List<String> NON_PATHS=new ArrayList<String>();
	
	private void forwardAppcacheTemplate(WebServerHandler handler,String phappcache,String template,String cookieKey){
		boolean useAppcache=!"unused".equals(phappcache);
		boolean appcacheMode=!"off".equals(phappcache);
		List<String> cachePaths=NON_PATHS;
		if(useAppcache && appcacheMode ){
			cachePaths=this.cachePaths;
		}
		handler.setRequestAttribute("phappcacheCookieKey", cookieKey);
		handler.setRequestAttribute("manifest", manifestAbsPath);
		handler.setRequestAttribute("useAppcache", useAppcache);
		handler.setRequestAttribute("appcacheMode", appcacheMode);
		handler.setRequestAttribute("cachePaths", cachePaths);
		config.forwardVelocityTemplate(handler,template);
	}
	
	/**
	 * パラメタが冗長だが早く計算するため
	 * モードは、on/off/clean/refreshの4つ
	 * on:一般コンテンツでofflineを利用
	 * off:一般コンテンツでは利用しない
	 * clean:offline機能自身を利用しない（隠し）
	 * refresh:コンテンツを取り直してonと同じ(admin roleだけに許可)（隠し）TODO roleがとれない
	 * 
	 * @param handler
	 * @param path
	 * @return
	 */
	public boolean checkAndForward(WebServerHandler handler,String path,String sourcePath){
		if(!enabled){
			return false;
		}
		String cookieKey="phappcache"+sourcePath.replaceAll("/", "_");
		String cookie=handler.getRequestHeader().getHeader(HeaderParser.COOKIE_HEADER);
		String phappcache=Cookie.parseHeader(cookie, cookieKey, null);
		if(path.equals(manifestPath)){
			checkAppcacheManifest(handler,phappcache,cookieKey);
			return true;
		}
		if(path.equals(cacheHtmlPath)){
			forwardAppcacheTemplate(handler,phappcache,path.substring(1),cookieKey);//先頭の"/"を削除
			return true;
		}
		return false;
	}
	public boolean isAppachePath(String path){
		if(path!=null && (path.startsWith(manifestPath)||path.startsWith(cacheHtmlPath))){
			return true;
		}
		return false;
	}
}
