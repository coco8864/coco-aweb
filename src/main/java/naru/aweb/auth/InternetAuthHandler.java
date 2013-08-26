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
			consumerManager=new ConsumerManager();
		}
		return consumerManager;
	}
	
	private void redirect(String location) {
		setHeader(HeaderParser.LOCATION_HEADER, location);
		completeResponse("302");
	}
	
	private void openIdReq(SessionId temporaryId){
		try {
			ConsumerManager manager=getConsumerManager();
			List discoveries = manager.discover("https://www.google.com/accounts/o8/id");			
			DiscoveryInformation discovered = manager.associate(discoveries);
			temporaryId.setAttribute("discovered", discovered);
			
			FetchRequest fetch = FetchRequest.createFetchRequest();
			fetch.addAttribute("FirstName", "http://schema.openid.net/namePerson/first", true);
			fetch.addAttribute("LastName", "http://schema.openid.net/namePerson/last", true);
			fetch.addAttribute("Email", "http://schema.openid.net/contact/email", true);
			AuthRequest authReq = manager.authenticate(discovered, config.getAuthUrl()+"/internetAuth/openIdRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId());
			authReq.addExtension(fetch);			
//			AuthRequest authReq = manager.authenticate(discovered, config.getAuthUrl()+"/internetAuth/openIdRes?"+AuthHandler.AUTH_ID +"="+temporaryId.getAuthId());
			redirect(authReq.getDestinationUrl(true));
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
				MessageExtension ext = authSuccess.getExtension(AxMessage.OPENID_NS_AX);
				FetchResponse fetchResp = (FetchResponse) ext;
				String email = fetchResp.getAttributeValue("Email");
				Map map = fetchResp.getAttributes();
//		        if (ext != null){
//		            FetchResponse fetchResp =new FetchResponse(ext.getParameters());
//		            String email = fetchResp.getParameter("email");
//		        }
				completeResponse("200","success:"+verified.getIdentifier()+":"+email+":"+map);
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
