package naru.aweb.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openid4java.association.AssociationException;
import org.openid4java.consumer.ConsumerException;
import org.openid4java.consumer.ConsumerManager;
import org.openid4java.consumer.VerificationResult;
import org.openid4java.discovery.Discovery;
import org.openid4java.discovery.DiscoveryException;
import org.openid4java.discovery.DiscoveryInformation;
import org.openid4java.discovery.Identifier;
import org.openid4java.message.AuthRequest;
import org.openid4java.message.AuthSuccess;
import org.openid4java.message.MessageException;
import org.openid4java.message.MessageExtension;
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
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.ServerParser;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * 設定項目
 * 1)セションタイムアウト時間
 * 2)logout後のurl
 * 3)認証方式[basic|digest|basicForm|digestForm]
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
	
	private void redirect(String location) {
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	private void setCookie(String setCookieString){
		if(setCookieString==null){
			return;
		}
		//IEでiframe内のcookieを有効にするヘッダ,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
		setHeader("P3P", "CP=\"CAO PSA OUR\"");
		setHeader(HeaderParser.SET_COOKIE_HEADER, setCookieString);
	}
	
	private void successAuth(SessionId temporaryId,String userId){
		User user=authenticator.getUserByLoginId(userId);
		if(user==null){
			user=authenticator.createUser(userId,"password","admin");//TODO 初期値
		}
		String url=temporaryId.getUrl();
		AuthSession authSession=authenticator.loginUser(user);
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
	
	public static String contents(URL url) throws IOException {
		InputStream is = url.openStream();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while (true) {
			int len = is.read(buf);
			if (len < 0) {
				break;
			}
			baos.write(buf, 0, len);
		}
		is.close();
		String contents = new String(baos.toByteArray(), /*"iso8859_1"*/"utf-8");
		baos.close();
		return contents;
	}

	private static String CONSUMER_KEY="rJyJQOaHKAzqLe4PX7VHtw";
	private static String CONSUMER_SECRET="TfIoMjsrKOqHARDDMs3W197xNIWd97zdpWdY6zhmUw";
	
	static TwitterFactory twitterFactory = new TwitterFactory();

/*
 * http://api.twitter.com/oauth/authenticate?oauth_token=lAqlzvHVCNNGKIXRlNNs16mGvFA7kGOuXiR4whwqE

https://127.0.0.1:1280/auth/internetAuth/twitterRes?oauth_token=lAqlzvHVCNNGKIXRlNNs16mGvFA7kGOuXiR4whwqE&oauth_verifier=tGASVyWV3KTBeFxjlcysQhWxato4SNpiKdoSKRZ6s
	
 */
	
	private void twitterReq(SessionId temporaryId){
		Twitter twitter=twitterFactory.getInstance();
		twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		try{
			// リクエストトークンの生成
			RequestToken reqToken = twitter.getOAuthRequestToken();
 			// RequestTokenとTwitterオブジェクトをセッションに保存
            temporaryId.setAttribute("RequestToken", reqToken);
            temporaryId.setAttribute("Twitter", twitter);
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
			AccessToken accessToken = accessToken = twitter.getOAuthAccessToken((RequestToken)temporaryId.getAttribute("RequestToken"), verifier);
			System.out.println("accessToken"+accessToken.getToken());
			System.out.println("accessToken"+accessToken.getUserId());
			System.out.println("accessToken"+accessToken.getScreenName());
		} catch (TwitterException e) {
			logger.error("twitterRes",e);
		}
		redirect(config.getPublicWebUrl());
	}
	
	private void fbReq(SessionId temporaryId){
		String redirectUrl="https://www.facebook.com/dialog/oauth";
		redirectUrl+="?client_id=495791087169690";
		redirectUrl+="&redirect_uri="+config.getAuthUrl()+"/internetAuth/fbRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId();
		redirect(redirectUrl);
	}
	private static String APP_ID="495791087169690";
	private static String APP_SECRET="07249541e046efba0611a9bc58e31f4e";
	
	private void fbRes(SessionId temporaryId){
		ParameterParser parameter = getParameterParser();
		System.out.println(parameter.getParameterMap());
		String code=parameter.getParameter("code");
//		https://graph.facebook.com/oauth/access_token?client_id=YOUR_APP_ID&redirect_uri=YOUR_URL&client_secret=YOUR_APP_SECRET&code=A_CODE_GENERATED_BY_SERVER
			
		String accessTokenUrl="https://graph.facebook.com/oauth/access_token?client_id="+APP_ID;
		accessTokenUrl+="&redirect_uri="+config.getAuthUrl()+"/internetAuth/fbRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId();
		accessTokenUrl+="&client_secret=" + APP_SECRET;
		accessTokenUrl+="&code=" + code;
		try {
			String accessToken=contents(new URL(accessTokenUrl));
			System.out.println("accessToken:"+accessToken);
			String meUrl="https://graph.facebook.com/me?"+accessToken;
			String me=contents(new URL(meUrl));
			JSONObject json=JSONObject.fromObject(me);
			System.out.println("me:"+me);
			System.out.println("id:"+json.getString("id"));
			successAuth(temporaryId, "https://www.facebook.com?id="+json.getString("id"));
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
			System.out.println("opendpoint:"+discovered.getOPEndpoint());
			FetchRequest fetch = FetchRequest.createFetchRequest();
			fetch.addAttribute("nickname", "http://axschema.org/namePerson/friendly", true);
//			fetch.addAttribute("email", "http://schema.openid.net/contact/email", true);
			AuthRequest authReq = manager.authenticate(discovered, config.getAuthUrl()+"/internetAuth/openIdRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId());
			
			SRegRequest sregReq = SRegRequest.createFetchRequest();

			sregReq.addAttribute("nickname", true);
////			sregReq.addAttribute("email", true);

//			AuthRequest req = _consumerManager.authenticate(discovered, return_to);
//			req.addExtension(sregReq);			
			
			authReq.addExtension(fetch);			
			authReq.addExtension(sregReq);
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
		completeResponse("500");
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
				completeResponse("403");
			}else{
				AuthSuccess authSuccess = AuthSuccess.createAuthSuccess(openidResp);
				if (authSuccess.hasExtension(SRegMessage.OPENID_NS_SREG)){
					SRegResponse sregResp = (SRegResponse)authSuccess.getExtension(SRegMessage.OPENID_NS_SREG);
			        String nickName = sregResp.getAttributeValue("nickname");
//					completeResponse("200","sreg success:"+verified.getIdentifier()+":"+nickName);
				}else if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)){
					FetchResponse fetchResp = (FetchResponse)authSuccess.getExtension(AxMessage.OPENID_NS_AX);
					String nickname = fetchResp.getAttributeValue("nickname");
//					completeResponse("200","AX success:"+verified.getIdentifier()+":"+nickname);
				}else{
//					completeResponse("200","success:"+verified.getIdentifier());
				}
				String openid=verified.getIdentifier();
				successAuth(temporaryId, openid);
			}
			return;
		} catch (DiscoveryException e) {
			logger.error("openIdRes",e);
		} catch (MessageException e) {
			logger.error("openIdRes",e);
		} catch (AssociationException e) {
			logger.error("openIdRes",e);
		}
		completeResponse("500");
	}
	
	public void startResponseReqBody() {
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		keepAliveContext.setKeepAlive(false);
		//keepAliveさせない,IEは、同一のポートでhttpとhttpsをやるとおかしな動きをするため
		
		MappingResult mapping=getRequestMapping();
		String path=mapping.getResolvePath();
		ParameterParser parameter = getParameterParser();
		String authId=parameter.getParameter(AuthHandler.AUTH_ID);
		if(authId==null){
			completeResponse("500");
			return;
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
		}
	}
}
