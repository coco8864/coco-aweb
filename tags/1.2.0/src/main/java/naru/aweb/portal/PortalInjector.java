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
		/* portal�@�\�̏ꍇpath����portal�@�\�p�̏�񂪒����Ă���ꍇ������B
		 * ���̏��͍폜����proxy�ΏۃT�[�o�Ƀ��N�G�X�g
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
		 * proxy��Authrization�w�b�_��t��������̏ꍇ
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
				return;//Digest�͂����Ń`�F�b�N������
			}
			String realm=matcher.group(1);
			
			//�����̎����Ă���㗝���O�C�����ŁAdomain,realm�ɍ��v������̂͂Ȃ����H
			String resolveDomain=mapping.getResolveDomain();
			CommissionAuth basicCommissionAuth=portalSession.getBasicAuth(resolveDomain,realm);
			if(WebAuthReplaceMark==null && !portalSession.startBasicProcess(resolveUrl, basicCommissionAuth)){
				return;
			}
			if(basicCommissionAuth==null||basicCommissionAuth.isEnabled()){
				String authrization=requestHeader.getHeader(HeaderParser.WWW_AUTHORIZATION_HEADER);
				if(WebAuthReplaceMark==null){//�u���E�U���璼�ڏo���ꂽ���N�G�X�g
					responseHeader.setStatusCode("200");
					proxyHandler.removeResponseHeader(HeaderParser.WWW_AUTHENTICATE_HEADER);
					portalSession.putRealm(resolveUrl, realm);
					proxyHandler.setReplace(true);
					injectContext=helper.getReplaceContext("WebAuthReplace.html");
					proxyHandler.addResponseHeader(HeaderParser.CONTENT_TYPE_HEADER,"text/html; charset=utf-8");
					proxyHandler.addResponseHeader("Pragma", "no-cache");
					proxyHandler.addResponseHeader("Cache-Control", "no-cache");
					proxyHandler.addResponseHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
				}else if(authrization!=null){//ajax����user/pass�����Ă���̂�401���ԋp���ꂽ=>�F�؏�񂪖���
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
