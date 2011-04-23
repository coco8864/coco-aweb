package naru.aweb.portal;

import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.async.pool.PoolBase;
import naru.aweb.config.CommissionAuth;
import naru.aweb.config.Config;
import naru.aweb.handler.InjectionHelper;
import naru.aweb.handler.ProxyHandler;
import naru.aweb.handler.ProxyInjector;
import naru.aweb.http.HeaderParser;
import naru.aweb.mapping.MappingResult;

public class PortalInjector extends PoolBase implements ProxyInjector {
	private static Pattern portalPathInfoPattern=Pattern.compile(";phportal=([^\\s;/?]*)");
	public static final String PORTAL_PATHINFO_KEY="portalPathInfo";
	public static final String REPLACE_MARK_HEADER="X-PH-WebAuthReplaceMark";
	private static Config config=Config.getConfig();
	
	private ProxyHandler proxyHandler;
	private PortalSession portalSession;
	private Object injectContext=null;

	public void init(ProxyHandler proxyHandler) {
		this.proxyHandler=proxyHandler;
		this.portalSession=PortalSession.getPortalSession(proxyHandler);
		injectContext=null;
	}

	public void onRequestHeader(HeaderParser requestHeader) {
		/* portal機能の場合path情報にportal機能用の情報が着いている場合がある。
		 * この情報は削除してproxy対象サーバにリクエスト
		 */
		MappingResult mapping=proxyHandler.getRequestMapping();
		String path=mapping.getResolvePath();
		Matcher matcher=null;
		synchronized(portalPathInfoPattern){
			matcher=portalPathInfoPattern.matcher(path);
		}
		StringBuffer sb=null;
		String portalPathInfo=null;
		if(matcher.find()){
			sb=new StringBuffer();
			matcher.appendReplacement(sb, "");
			portalPathInfo=matcher.group(1);
			matcher.appendTail(sb);
			path=sb.toString();
			mapping.setResolvePath(path);
			requestHeader.setRequestUri(mapping.getResolvePath());
			proxyHandler.setRequestAttribute(PORTAL_PATHINFO_KEY, portalPathInfo);
		}
		/*
		 * proxyでAuthrizationヘッダを付加する作戦の場合
		String basicAuthHeader = getBasicAuthHeader(mapping.isResolvedHttps(),mapping.getResolveServer());
		if (basicAuthHeader != null) {
			requestHeader.addHeader(HeaderParser.WWW_AUTHRIZATION_HEADER, basicAuthHeader);
			proxyHandler.setRequestAttribute(HeaderParser.WWW_AUTHRIZATION_HEADER, basicAuthHeader);
		}
		 */
	}

	private static Pattern authenticationPattern=Pattern.compile("\\s*Basic\\s+realm\\s*=\\s*\"(.[^\"]*)\"",Pattern.CASE_INSENSITIVE);
	
	public void onResponseHeader(HeaderParser responseHeader) {
		InjectionHelper helper=config.getInjectionHelper();
		
		MappingResult mapping=proxyHandler.getRequestMapping();
		HeaderParser requestHeader=proxyHandler.getRequestHeader();
		String WebAuthReplaceMark=requestHeader.getHeader(REPLACE_MARK_HEADER);
		String resolveUrl=mapping.getResolveUrl();
		if(WebAuthReplaceMark==null){
			portalSession.endBasicProcess(resolveUrl);
		}
		String statusCode=responseHeader.getStatusCode();
		if("401".equals(statusCode)/*&&injectContext==null*/){
			mapping.getResolveDomain();
			String authentication=responseHeader.getHeader(HeaderParser.WWW_AUTHENTICATE_HEADER);
			if(authentication==null){
				return;
			}
			Matcher matcher;
			synchronized(authenticationPattern){
				matcher=authenticationPattern.matcher(authentication);
			}
			if(!matcher.find()){
				return;//Digestはここでチェックあうと
			}
			String realm=matcher.group(1);
			
			//自分の持っている代理ログイン情報で、domain,realmに合致するものはないか？
			String resolveDomain=mapping.getResolveDomain();
			CommissionAuth basicCommissionAuth=portalSession.getBasicAuth(resolveDomain,realm);
			if(WebAuthReplaceMark==null && !portalSession.startBasicProcess(resolveUrl, basicCommissionAuth)){
				return;
			}
			if(basicCommissionAuth==null||basicCommissionAuth.isEnabled()){
				String authrization=requestHeader.getHeader(HeaderParser.WWW_AUTHORIZATION_HEADER);
				if(WebAuthReplaceMark==null){//ブラウザから直接出されたリクエスト
					responseHeader.setStatusCode("200");
					proxyHandler.removeResponseHeader(HeaderParser.WWW_AUTHENTICATE_HEADER);
					portalSession.putRealm(resolveUrl, realm);
					proxyHandler.setReplace(true);
					injectContext=helper.getReplaceContext("WebAuthReplace.html");
					proxyHandler.addResponseHeader(HeaderParser.CONTENT_TYPE_HEADER,"text/html; charset=utf-8");
					proxyHandler.addResponseHeader("Pragma", "no-cache");
					proxyHandler.addResponseHeader("Cache-Control", "no-cache");
					proxyHandler.addResponseHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
				}else if(authrization!=null){//ajaxからuser/passをつけているのに401が返却された=>認証情報が無効
					responseHeader.setStatusCode("200");
					proxyHandler.removeResponseHeader(HeaderParser.WWW_AUTHENTICATE_HEADER);
					proxyHandler.addResponseHeader("WebAuthRealm", realm);
					proxyHandler.setReplace(true);
					injectContext=helper.getReplaceContext("WebAuthFail.html");
					proxyHandler.addResponseHeader(HeaderParser.CONTENT_TYPE_HEADER,"text/plain");
					proxyHandler.addResponseHeader("Pragma", "no-cache");
					proxyHandler.addResponseHeader("Cache-Control", "no-cache");
					proxyHandler.addResponseHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
				}
			}
		}else if("200".equals(statusCode)||"404".equals(statusCode)){
			String contentType=responseHeader.getContentType();
			if(contentType!=null && contentType.startsWith("text/html")){
				injectContext=helper.getInsertContext("PortalInject.txt");
			}
		}
	}
	public ByteBuffer[] onResponseBody(ByteBuffer[] body) {
		InjectionHelper helper=config.getInjectionHelper();
		return helper.inject(injectContext,body);
	}
	
	public void term() {
		unref();
	}

	public long getInjectLength() {
		if(injectContext!=null){
			InjectionHelper helper=config.getInjectionHelper();
			return helper.getInjectContentsLength(injectContext);
		}
		return 0;
	}

	public boolean isInject() {
		return (injectContext!=null);
	}

	
}
