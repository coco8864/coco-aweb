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
			ParameterParser parameter = getParameterParser();
			String identifier=parameter.getParameter("identifier");
			List discoveries = manager.discover(identifier);			
			DiscoveryInformation discovered = manager.associate(discoveries);
			temporaryId.setAttribute("discovered", discovered);
			
			FetchRequest fetch = FetchRequest.createFetchRequest();
			fetch.addAttribute("firstName", "http://axschema.org/namePerson/first", true);
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
			        String email = sregResp.getAttributeValue("email");
					completeResponse("200","sreg success:"+verified.getIdentifier()+":"+nickName+":"+email);
				}else if (authSuccess.hasExtension(AxMessage.OPENID_NS_AX)){
					FetchResponse fetchResp = (FetchResponse)authSuccess.getExtension(AxMessage.OPENID_NS_AX);
					String firstName = fetchResp.getAttributeValue("firstName");
					String email = fetchResp.getAttributeValue("email");
					completeResponse("200","AX success:"+verified.getIdentifier()+":"+firstName+":"+email);
				}else{
					completeResponse("200","success:"+verified.getIdentifier());
				}
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
