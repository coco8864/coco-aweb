package naru.aweb.auth;

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

import naru.aweb.config.Config;
import naru.aweb.config.Mapping;
import naru.aweb.config.User;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.KeepAliveContext;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
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
	private ConsumerManager getConsumerManager(){
		if(consumerManager==null){
			ProxyProperties proxyProperties=new ProxyProperties();
			proxyProperties.setProxyHostName("proxy.gw.nic.fujitsu.com");
			proxyProperties.setProxyPort(8080);
			HttpClientFactory.setProxyProperties(proxyProperties);
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
	
	private void successAuth(SessionId temporaryId,User user){
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
	
	private void openIdReq(SessionId temporaryId){
		try {
			ConsumerManager manager=getConsumerManager();
			ParameterParser parameter = getParameterParser();
			String identifier=parameter.getParameter("identifier");
			List discoveries = manager.discover(identifier);			
			DiscoveryInformation discovered = manager.associate(discoveries);
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
			ConsumerManager manager=getConsumerManager();
			HeaderParser requestHeader = getRequestHeader();
			ParameterParser parameter = getParameterParser();
			ParameterList openidResp = new ParameterList(parameter.getParameterMapServlet());
			String receivingURL=requestHeader.getAddressBar(isSsl());
			DiscoveryInformation discovered = (DiscoveryInformation)temporaryId.getAttribute("discovered");
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
				User user=authenticator.getUserByLoginId(openid);
				if(user==null){
					user=authenticator.createUser(openid,"password","admin");//TODO 初期値
				}
				successAuth(temporaryId, user);
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
		}
		
	}
}
