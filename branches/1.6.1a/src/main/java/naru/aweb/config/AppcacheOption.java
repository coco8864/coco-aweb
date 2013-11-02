package naru.aweb.config;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import naru.aweb.handler.WebServerHandler;
import naru.aweb.handler.ServerBaseHandler.SCOPE;
import naru.aweb.http.Cookie;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.StreamUtil;
import net.sf.json.JSONObject;

/**
 * /apl配下の *.vsp,*.htmlの中から
 *   /pub/??/?.js,?.css,?.gif,?.png,?.jpg /??/?.html,?.htm
 * /docroot配下に存在確認
 * 
 * apl.htmlおよびみつかった、/??/?.html,?.htmから
 *   /pub/??/?.js,?.css,?.gif,?.png,?.jpg /??/?.html,?.htm
 * 
 * ph.jsは以下が含まれている
 * 'jquery-1.8.3.min.js','ph-jqnoconflict.js','ph-json2.js','ph-link.js'
 * 
 * ~ph.htmlは以下が含まれている
 * 'jquery-1.8.3.min.js','ph-json2.js',/pub/js/aes.js,/pub/js/aplOffline.js
 * 
 * cache対象は、docroot配下になくてはならない
 * 
 * option:{appcache:{enabled:true,manifestPath:'/phoffline.appcache',cacheFilePath:'/phoffline.html',cacheFilePattern:'...'}}
 * @author naru
 *
 */
public class AppcacheOption {
	public static final String APPCACHE_KEY="appcacheVersion";
	private static final String HTML_PATTERN=".*\\.html$|.*\\.htm$|.*\\.vsp$";
	
	private static final String DEFAULT_CACHE_FILE_PATTERN=".*\\.html$|.*\\.htm$|.*\\.css$|.*\\.js$|.*\\.jpg$|.*\\.png$|.*\\.gif$";
	private static final String DEFAULT_MANIFEST_PATH="/~ph.appcache";
	private static final String DEFAULT_CACHE_HTML_PATH="/~ph.vsp";
	private static final String[] PH_JS_INCLUDE_JS={"ph.js","jquery-1.8.3.min.js","ph-jqnoconflict.js","ph-json2.js","ph-link.js"};
	private static final String[] PH_HTML_INCLUDE_JS={"jquery-1.8.3.min.js","ph-json2.js","aes.js","aplOffline.js"};
	
	private static Logger logger = Logger.getLogger(AppcacheOption.class);
	private static Config config = Config.getConfig();
	
	private boolean enabled=false;
	private String manifestPath;
	private String manifestAbsPath;
	private String cacheHtmlPath;
	private File destinationFile;
	private String sourcePath;
	private String cacheFilePattern;
	private Set<String> cachePaths=new HashSet<String>();
	private int currentAppacheVersion=0;
	private String homePath;
	
	public AppcacheOption(File destinationFile,String sourcePath,JSONObject options){
		this.destinationFile=destinationFile;
		this.sourcePath=sourcePath;
		this.enabled=options.optBoolean("enabled",true);
		this.manifestPath=options.optString("manifestPath",DEFAULT_MANIFEST_PATH);
		this.cacheHtmlPath=options.optString("cacheHtmlPath",DEFAULT_CACHE_HTML_PATH);
		this.cacheFilePattern=options.optString("cacheFilePattern",DEFAULT_CACHE_FILE_PATTERN);
		if("/".equals(sourcePath)){
			manifestAbsPath=manifestPath;
			this.homePath=options.optString("homePath","/index.html");
		}else{
			manifestAbsPath=sourcePath+manifestPath;
			// offline対応する場合、/apl.htmlを起点とする
			this.homePath=options.optString("homePath",sourcePath+".html");
		}
		currentAppacheVersion=config.getInt(APPCACHE_KEY, 0);
		setup();
	}
	
	private void setup(){
		cachePaths.clear();
		Pattern htmlPattern=Pattern.compile(HTML_PATTERN);
		Set<File>htmlFiles=new HashSet<File>();
		for(String path:PH_JS_INCLUDE_JS){
			addPath("/pub/js/"+path,cachePaths);
		}
		for(String path:PH_HTML_INCLUDE_JS){
			addPath("/pub/js/"+path,cachePaths);
		}
		if(destinationFile==null){
			return;
		}
		collectFile(destinationFile,htmlPattern,htmlFiles);
		Iterator<File> itr=htmlFiles.iterator();
		Set<String>uncheckedHtmlFiles=new HashSet<String>();
		addPath(homePath,uncheckedHtmlFiles);
		while(itr.hasNext()){
			File file=itr.next();
			collectCacheFile(file,uncheckedHtmlFiles,cachePaths);
		}
		Set<String>checkHtmlFiles=new HashSet<String>();
		collectCacheFileR(uncheckedHtmlFiles,checkHtmlFiles,cachePaths);
		for(String path:checkHtmlFiles){
			addPath(path,cachePaths);
		}
		logger.info("sourcePath:"+sourcePath +" currentAppacheVersion:"+currentAppacheVersion);
		for(String path:cachePaths){
			logger.info(path);
		}
	}
	
	private void collectFile(File file,Pattern pattern,Set<File>files){
		if(file.isFile()){
			String fileName=file.getAbsolutePath();
			Matcher m=pattern.matcher(fileName);
			if(m.matches()){
				files.add(file);
			}
		}if(file.isDirectory()){
			for(File f:file.listFiles()){
				collectFile(f,pattern,files);
			}
		}
	}
	
	/*
	 * 存在を確認してからlistに追加する
	 */
	private void addPath(String path,Set<String>htmlFiles){
		if(path==null){
			return;
		}
		File f=new File(config.getPublicDocumentRoot(),path);
		if(f.exists()){
			htmlFiles.add(path);
		}
	}
	
	private void collectCacheFile(File file,Set<String>htmlFiles,Set<String>embeddedFiles){
		String contents=null;
		try {
			contents = new String(StreamUtil.readFile(file),"utf-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String reqex="[\"|'](?:(/pub/[^(?:\"|')]*(?:(?:\\.js)|(?:\\.css)|(?:\\.gif)|(?:\\.png)|(?:\\.jpg)))|(/[^(?:\"|')]*(?:(?:\\.html)|(?:\\.htm))))[\"|']";
		Pattern pattern=Pattern.compile(reqex);
		Matcher matcher=null;
		synchronized(pattern){
			matcher=pattern.matcher(contents);
		}
		while(matcher.find()){
			String embededFile=matcher.group(1);
			addPath(embededFile,embeddedFiles);
			String htmlFile=matcher.group(2);
			addPath(htmlFile,htmlFiles);
		}
	}
	
	private void collectCacheFileR(Set<String>uncheckHtmlFiles,Set<String>checkHtmlFiles,Set<String>embeddedFiles){
		Set<String>addCacheHtmlFiles=new HashSet<String>();
		Iterator<String> itr=uncheckHtmlFiles.iterator();
		while(itr.hasNext()){
			String path=itr.next();
			collectCacheFile(new File(config.getPublicDocumentRoot(),path),addCacheHtmlFiles,embeddedFiles);
			checkHtmlFiles.add(path);
		}
		itr=addCacheHtmlFiles.iterator();
		while(itr.hasNext()){
			String path=itr.next();
			if(checkHtmlFiles.contains(path)){
				itr.remove();
			}
		}
		if(addCacheHtmlFiles.size()!=0){
			collectCacheFileR(addCacheHtmlFiles,checkHtmlFiles,embeddedFiles);
		}
	}
	
	
	private void checkAppcacheManifest(WebServerHandler handler,String phappcache,String cookieKey,int appcacheVersion){
		if("unused".equals(phappcache)){
			handler.completeResponse("404", "file not exists");
		}else{
			forwardAppcacheTemplate(handler, phappcache, "~ph.appcache",cookieKey,appcacheVersion);
		}
	}
	
	private static Set<String> NON_PATHS=new HashSet<String>();
	
	private void forwardAppcacheTemplate(WebServerHandler handler,String phappcache,String template,String cookieKey,int appcacheVersion){
		boolean useAppcache=!"unused".equals(phappcache);
		boolean appcacheMode=!"off".equals(phappcache);
		Set<String> cachePaths=NON_PATHS;
		if(useAppcache && appcacheMode ){
			cachePaths=this.cachePaths;
		}
		handler.setAttribute(SCOPE.REQUEST,"phappcacheCookieKey", cookieKey);
		handler.setAttribute(SCOPE.REQUEST,"manifest", manifestAbsPath);
		handler.setAttribute(SCOPE.REQUEST,"useAppcache", useAppcache);
		handler.setAttribute(SCOPE.REQUEST,"appcacheMode", appcacheMode);
		handler.setAttribute(SCOPE.REQUEST,"cachePaths", cachePaths);
		handler.setAttribute(SCOPE.REQUEST,APPCACHE_KEY, appcacheVersion);
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
		int appcacheVersion=config.getInt(APPCACHE_KEY, 0);
		if(currentAppacheVersion!=appcacheVersion){
			setup();
			currentAppacheVersion=appcacheVersion;
		}
		String cookieKey="phappcache"+sourcePath.replaceAll("/", "_");
		String cookie=handler.getRequestHeader().getHeader(HeaderParser.COOKIE_HEADER);
		String phappcache=Cookie.parseHeader(cookie, cookieKey, null);
		if(path.equals(manifestPath)){
			checkAppcacheManifest(handler,phappcache,cookieKey,appcacheVersion);
			return true;
		}
		if(path.equals(cacheHtmlPath)){
			forwardAppcacheTemplate(handler,phappcache,path.substring(1),cookieKey,appcacheVersion);//先頭の"/"を削除
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
