package naru.aweb.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.openid4java.association.AssociationException;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.MessageException;
import org.openid4java.message.ParameterList;
import org.openid4java.message.ax.AxMessage;
import org.openid4java.message.ax.FetchRequest;
import org.openid4java.message.ax.FetchResponse;
import org.openid4java.message.sreg.SRegMessage;
import org.openid4java.message.sreg.SRegRequest;
import org.openid4java.message.sreg.SRegResponse;
import org.openid4java.util.HttpClientFactory;
import org.openid4java.util.ProxyProperties;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

import naru.aweb.config.Config;
import naru.aweb.handler.KeepAliveContext;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.HttpUtil;
import naru.aweb.util.ParameterParser;
import naru.aweb.util.ServerParser;
import net.sf.json.JSONObject;

/**
 * 設定項目
 * 1)セションタイムアウト時間
 * 2)logout後のurl
 * 3)認証方式[basic|digest|basicForm|digestForm|internetAuth]
 * 
 * googleの登録URL
 * https://cloud.google.com/console
 * 
 * @author naru
 *
 */
public class InternetAuthHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(InternetAuthHandler.class);
	private static Config config = Config.getConfig();
	private static Authenticator authenticator = config.getAuthenticator();
	private static Authorizer authorizer=config.getAuthorizer();
	
	private static ConsumerManager consumerManager=null;
	private ConsumerManager getConsumerManager(String uri){
		if(consumerManager==null){
			//TODO このproxyの選び方は怪しい
			ServerParser proxyServer=config.findProxyServer(uri);
			if(proxyServer!=null){
				ProxyProperties proxyProperties=new ProxyProperties();
				proxyProperties.setProxyHostName(proxyServer.getHost());
				proxyProperties.setProxyPort(proxyServer.getPort());
				HttpClientFactory.setProxyProperties(proxyProperties);
			}
			consumerManager=new ConsumerManager();
		}
		return consumerManager;
	}
	
	private void setCookie(String setCookieString){
		if(setCookieString==null){
			return;
		}
		//IEでiframe内のcookieを有効にするヘッダ,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
		setHeader("P3P", "CP=\"CAO PSA OUR\"");
		setHeader(HeaderParser.SET_COOKIE_HEADER, setCookieString);
	}
	
	private void successAuth(SessionId temporaryId,String userId,String nickname,String accessToken,String origin){
		User user=authenticator.getUserByLoginId(userId);
		if(user==null){
			String role=config.getString("authInternetRole");
			authenticator.createUser(userId,nickname,"password",role);
			user=authenticator.getUserByLoginId(userId);
			user.setOrigin(origin);
		}
		String url=temporaryId.getUrl();
		AuthSession authSession=authenticator.loginUser(user);
		if(accessToken!=null){
			authSession.setAttribute("accessToken", accessToken);
		}
		authorizer.setAuthSessionToTemporaryId(temporaryId.getAuthId(), authSession);
		SessionId primaryId = authorizer.createPrimaryId(authSession);
		String setCookieString = primaryId.getSetCookieString();
		setCookie(setCookieString);
		if(url==null){
			redirect(config.getPublicWebUrl());//doneResponse("200","authentication success.");
			return;
		}
		SessionId pathOnceId=authorizer.createPathOnceIdByPrimary(url, primaryId.getId());
		if(temporaryId.isDirectUrl()){
			//directUrlの場合は即座にredirectする
			pathOnceId.unref();
			redirect(url);
			return;
		}
		String encodeUrl = pathOnceId.encodeUrl();
		//元のurlに戻るところ
		redirect(encodeUrl);
	}

	static TwitterFactory twitterFactory = new TwitterFactory();
	private static Map<String,String> twitterToken=new HashMap<String,String>();
	
	private void twitterReq(SessionId temporaryId){
		Twitter twitter=twitterFactory.getInstance();
		String consumerKey=config.getString("authTwitterConsumerKey");
		String consumerSecret=config.getString("authTwitterConsumerSecret");
		twitter.setOAuthConsumer(consumerKey, consumerSecret);
		try{
			// リクエストトークンの生成
			RequestToken reqToken = twitter.getOAuthRequestToken();
 			// RequestTokenとTwitterオブジェクトをセッションに保存
            temporaryId.setAttribute("RequestToken", reqToken);
            temporaryId.setAttribute("Twitter", twitter);
            twitterToken.put(reqToken.getToken(), temporaryId.getAuthId());
            //System.out.println("token:"+reqToken.getToken());
            //System.out.println("getAuthenticationURL:"+reqToken.getAuthenticationURL());
    		redirect(reqToken.getAuthenticationURL());
    		return;
		}catch (TwitterException e){
			logger.error("twitterReq",e);
		}
		redirect(config.getPublicWebUrl());
	}
	
	private void twitterRes(SessionId temporaryId){
		Twitter twitter = (Twitter)temporaryId.getAttribute("Twitter");
		ParameterParser parameter = getParameterParser();
		String verifier = parameter.getParameter("oauth_verifier");
		try {
			AccessToken accessToken = twitter.getOAuthAccessToken((RequestToken)temporaryId.getAttribute("RequestToken"), verifier);
			//System.out.println("accessToken"+accessToken.getToken());
			//System.out.println("accessToken"+accessToken.getUserId());
			//System.out.println("accessToken"+accessToken.getScreenName());
			successAuth(temporaryId,
					"https://twitter.com?id="+accessToken.getUserId(),
					accessToken.getScreenName(),
					accessToken.getToken(),
					"https://twitter.com");
			return;
		} catch (TwitterException e) {
			logger.error("twitterRes",e);
		}
		redirect(config.getPublicWebUrl());
	}
	
	private void fbReq(SessionId temporaryId){
		String authFbAppId=config.getString("authFbAppId");
		String redirectUrl="https://www.facebook.com/dialog/oauth";
		redirectUrl+="?client_id=" + authFbAppId;
		redirectUrl+="&redirect_uri="+config.getAuthUrl()+"/internetAuth/fbRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId();
		redirect(redirectUrl);
	}
	
	private void fbRes(SessionId temporaryId){
		String authFbAppId=config.getString("authFbAppId");
		String authFbAppSecret=config.getString("authFbAppSecret");
		
		ParameterParser parameter = getParameterParser();
		String code=parameter.getParameter("code");
//		https://graph.facebook.com/oauth/access_token?client_id=YOUR_APP_ID&redirect_uri=YOUR_URL&client_secret=YOUR_APP_SECRET&code=A_CODE_GENERATED_BY_SERVER
		String accessTokenUrl="https://graph.facebook.com/oauth/access_token?client_id="+authFbAppId;
		accessTokenUrl+="&redirect_uri="+config.getAuthUrl()+"/internetAuth/fbRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId();
		accessTokenUrl+="&client_secret=" + authFbAppSecret;
		accessTokenUrl+="&code=" + code;
		try {
			String accessToken=HttpUtil.get(accessTokenUrl);
			String meUrl="https://graph.facebook.com/me?"+accessToken;
			String me=HttpUtil.get(meUrl);
			JSONObject json=JSONObject.fromObject(me);
			String nickname=json.optString("first_name");
			successAuth(temporaryId, 
					"https://www.facebook.com?id="+json.getString("id"),
					nickname,
					accessToken,
					"https://www.facebook.com");
			return;
		} catch (MalformedURLException e) {
			logger.error("accessToken",e);
		} catch (IOException e) {
			logger.error("accessToken",e);
		}
		redirect(config.getPublicWebUrl());
	}
	
	private void googleReq(SessionId temporaryId){
		String authGoogleClientId=config.getString("authGoogleClientId");
		String redirectUrl="https://accounts.google.com/o/oauth2/auth?response_type=code";
		redirectUrl+="&client_id=" + authGoogleClientId;
		redirectUrl+="&redirect_uri="+config.getAuthUrl()+"/internetAuth/googleRes";
		redirectUrl+="&scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.profile";
		redirectUrl+="&state="+temporaryId.getAuthId();
		redirect(redirectUrl);
	}
	
	private void googleRes(SessionId temporaryId){
		ParameterParser parameter = getParameterParser();
		String code=parameter.getParameter("code");
		
		String authGoogleClientId=config.getString("authGoogleClientId");
		String authGoogleClientSecret=config.getString("authGoogleClientSecret");
		Map<String,String> req=new HashMap<String,String>();
		req.put("client_id",authGoogleClientId);
		req.put("client_secret",authGoogleClientSecret);
		req.put("redirect_uri",config.getAuthUrl()+"/internetAuth/googleRes");
		req.put("grant_type","authorization_code");
		req.put("code",code);

		try {
			String accessJson=HttpUtil.post("https://accounts.google.com/o/oauth2/token",req);
			JSONObject access=JSONObject.fromObject(accessJson);
			String accessToken=access.getString("access_token");
			String meUrl="https://www.googleapis.com/oauth2/v1/userinfo?access_token="+accessToken;
			String me=HttpUtil.get(meUrl);
			JSONObject profile=JSONObject.fromObject(me);
			String nickname=profile.optString("given_name");
			successAuth(temporaryId,
					"https://accounts.google.com?id="+profile.getString("id"),
					nickname,
					accessToken,
					"https://accounts.google.com");
			return;
		} catch (MalformedURLException e) {
			logger.error("accessToken",e);
		} catch (IOException e) {
			logger.error("accessToken",e);
		}
		redirect(config.getPublicWebUrl());
	}
	
	private void openIdReq(SessionId temporaryId){
		try {
			ParameterParser parameter = getParameterParser();
			String identifier=parameter.getParameter("identifier");
			ConsumerManager manager=getConsumerManager(identifier);
			List discoveries = manager.discover(identifier);
			DiscoveryInformation discovered = manager.associate(discoveries);
			temporaryId.setAttribute("manager", manager);
			temporaryId.setAttribute("discovered", discovered);
			temporaryId.setAttribute("reqIdentifier", identifier);
			//System.out.println("opendpoint:"+discovered.getOPEndpoint());
			//returnToUrlにはportも含めないとopenid4javaがverifyエラーを投げる
			String authUrl=config.getAuthUrl();
			if(authUrl.indexOf(":",6)<0){
				if(authUrl.startsWith("https://")){
					authUrl=authUrl.replaceFirst("(https://[^/]*)/", "$1:443/");
				}else{
					authUrl=authUrl.replaceFirst("(http://[^/]*)/)", "$1:80/");
				}
			}
			AuthRequest authReq = manager.authenticate(discovered, authUrl+"/internetAuth/openIdRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId());
			SRegRequest sregReq = SRegRequest.createFetchRequest();
			sregReq.addAttribute("nickname", false);
//			sregReq.addAttribute("fullname", false);
			authReq.addExtension(sregReq);
			FetchRequest fetch = FetchRequest.createFetchRequest();
			fetch.addAttribute("nickname", "http://axschema.org/namePerson/friendly", false);
//			fetch.addAttribute("first", "http://axschema.org/namePerson/first", false);
//			fetch.addAttribute("email", "http://axschema.org/contact/email", false); googole openidではどれも取れない？
			authReq.addExtension(fetch);			
			redirect(authReq.getDestinationUrl(true));
			temporaryId.setLastAccessTime();
			return;
		} catch (DiscoveryException e) {
			logger.error("openIdReq",e);
		} catch (MessageException e) {
			logger.error("openIdReq",e);
		} catch (ConsumerException e) {
			logger.error("openIdReq",e);
		}
		redirect(config.getPublicWebUrl());
	}

	private void openIdRes(SessionId temporaryId){
		try {
			HeaderParser requestHeader = getRequestHeader();
			ParameterParser parameter = getParameterParser();
			ParameterList openidResp = new ParameterList(parameter.getParameterMapServlet());
			String receivingURL=requestHeader.getAddressBar(isSsl());
			DiscoveryInformation discovered = (DiscoveryInformation)temporaryId.getAttribute("discovered");
			ConsumerManager manager=(ConsumerManager)temporaryId.getAttribute("manager");
			// verify the response
			VerificationResult verification = manager.verify(receivingURL, openidResp, discovered);
			Identifier verified = verification.getVerifiedId();
			if(verified==null){
				redirect(config.getPublicWebUrl());
			}else{
				AuthSuccess authSuccess = AuthSuccess.createAuthSuccess(openidResp);
				String nickname=null;
				if (authSuccess.hasExtension(SRegMessage.OPENID_NS_SREG)){
					SRegResponse sregResp = (SRegResponse)authSuccess.getExtension(SRegMessage.OPENID_NS_SREG);
					nickname = sregResp.getAttributeValue("nickname");
				}else if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)){
					FetchResponse fetchResp = (FetchResponse)authSuccess.getExtension(AxMessage.OPENID_NS_AX);
					nickname = fetchResp.getAttributeValue("nickname");
					if(nickname==null){
						nickname = fetchResp.getAttributeValue("first");
					}
				}else{
				}
				String reqIdentifier=(String)temporaryId.getAttribute("reqIdentifier");
				String openid=verified.getIdentifier();
				successAuth(temporaryId,openid,nickname,null,reqIdentifier);
			}
			return;
		} catch (DiscoveryException e) {
			logger.error("openIdRes",e);
		} catch (MessageException e) {
			logger.error("openIdRes",e);
		} catch (AssociationException e) {
			logger.error("openIdRes",e);
		}
		redirect(config.getPublicWebUrl());
	}
	
	public void onRequestBody() {
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		keepAliveContext.setKeepAlive(false);
		//keepAliveさせない,IEは、同一のポートでhttpとhttpsをやるとおかしな動きをするため
		
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		ParameterParser parameter = getParameterParser();
		String authId=parameter.getParameter(AuthHandler.AUTH_ID);
		String oauthToken=parameter.getParameter("oauth_token");//twitterの場合
		String state=parameter.getParameter("state");//googole oauthの場合
		if(authId==null){//TODO きれいにする
			//twitterResには、authIdが設定できないため
			authId=twitterToken.remove(oauthToken);
			if(authId==null){
				authId=state;
				if(authId==null){
					completeResponse("500");
					return;
				}
			}
		}
		SessionId temporaryId=authorizer.getTemporaryId(authId);
		if(temporaryId==null){
			completeResponse("500");
			return;
		}
		
		if("/internetAuth/openIdReq".equals(path)){
			openIdReq(temporaryId);
			return;
		}else if("/internetAuth/openIdRes".equals(path)){
			openIdRes(temporaryId);
			return;
		}else if("/internetAuth/fbReq".equals(path)){
			fbReq(temporaryId);
			return;
		}else if("/internetAuth/fbRes".equals(path)){
			fbRes(temporaryId);
			return;
		}else if("/internetAuth/twitterReq".equals(path)){
			twitterReq(temporaryId);
			return;
		}else if("/internetAuth/twitterRes".equals(path)){
			twitterRes(temporaryId);
			return;
		}else if("/internetAuth/googleReq".equals(path)){
			googleReq(temporaryId);
			return;
		}else if("/internetAuth/googleRes".equals(path)){
			googleRes(temporaryId);
			return;
		}
	}
}
