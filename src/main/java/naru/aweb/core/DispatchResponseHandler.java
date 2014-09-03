package naru.aweb.core;


import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthHandler;
import naru.aweb.config.Config;
import naru.aweb.handler.WebServerHandler;
import naru.aweb.handler.ServerBaseHandler.SCOPE;
import naru.aweb.mapping.Mapping;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

/**
 * DispatchHandlerでレスポンスが決定した場合に使われる
 * 
 * @author naru
 * 
 */
public class DispatchResponseHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(DispatchResponseHandler.class);
	private static Config config = Config.getConfig();
	private static final String TYPE = "type";
	private static final String MESSAGE = "message";
	private static final String STATUS_CODE = "statusCode";
	private static final String AUTH_HEADER_NAME = "authenticateHeaderName";
	private static final String AUTH_HEADER = "authenticateHeader";
	private static final String AJAX_RESPONSE = "ajaxResponse";
	private static final String RESPONSE = "response";

	private enum Type {
		FORBIDDEN, NOT_FOUND, REDIRECT,AJAX_ALEADY_AUTH,AUTHENTICATE,CROSS_DOMAIN_FRAME,JSON_RESPONSE
	}

	public static MappingResult forbidden() {
		return forbidden("Forbidden");
	}
	
	public static MappingResult authenticate(boolean isProxy,String authenticateHeader) {
		MappingResult mapping = createDispatchMapping(Type.AUTHENTICATE);
		if(isProxy){
			mapping.setAttribute(STATUS_CODE, "407");
			mapping.setAttribute(AUTH_HEADER_NAME, HeaderParser.PROXY_AUTHENTICATE_HEADER);
		}else{
			mapping.setAttribute(STATUS_CODE, "401");
			mapping.setAttribute(AUTH_HEADER_NAME, HeaderParser.WWW_AUTHENTICATE_HEADER);
		}
		mapping.setAttribute(AUTH_HEADER, authenticateHeader);
		return mapping;
	}

	public static MappingResult forbidden(String message) {
		MappingResult mapping = createDispatchMapping(Type.FORBIDDEN);
		mapping.setAttribute(MESSAGE, message);
		return mapping;
	}

	public static MappingResult notfound() {
		return forbidden("not found");
	}

	public static MappingResult notfound(String message) {
		MappingResult mapping = createDispatchMapping(Type.NOT_FOUND);
		mapping.setAttribute(MESSAGE, message);
		return mapping;
	}
	
	public static MappingResult crossDomainFrame(Object response) {
		MappingResult mapping = createDispatchMapping(Type.CROSS_DOMAIN_FRAME);
		mapping.setAttribute(RESPONSE, response);
		return mapping;
	}

	public static MappingResult jsonResponse(Object response) {
		MappingResult mapping = createDispatchMapping(Type.JSON_RESPONSE);
		mapping.setAttribute(RESPONSE, response);
		return mapping;
	}

	private static MappingResult createDispatchMapping(Type type) {
		MappingResult mapping = (MappingResult) PoolManager.getInstance(MappingResult.class);
		mapping.setHandlerClass(DispatchResponseHandler.class);
		mapping.setAttribute(TYPE, type);
		return mapping;
	}
	
	public static MappingResult authMapping() {
		MappingResult mapping = (MappingResult) PoolManager.getInstance(MappingResult.class);
		mapping.setHandlerClass(AuthHandler.class);
		return mapping;
	}

	public void onRequestBody() {
		MappingResult mapping = getRequestMapping();
		Type type = (Type) mapping.getAttribute(TYPE);
		String message;
		String location;
		String setCookieString;
		switch (type) {
		case FORBIDDEN:
			message = (String) mapping.getAttribute(MESSAGE);
			completeResponse("403", message);
			break;
		case NOT_FOUND:
			message = (String) mapping.getAttribute(MESSAGE);
			completeResponse("404", message);
			break;
		case REDIRECT:
			setCookieString = (String) mapping.getAttribute(HeaderParser.SET_COOKIE_HEADER);
			setHeader(HeaderParser.SET_COOKIE_HEADER, setCookieString);
			//IEでiframe内のcookieを有効にするヘッダ,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
			setHeader("P3P", "CP=\"CAO PSA OUR\"");
			location = (String) mapping.getAttribute(HeaderParser.LOCATION_HEADER);
			setHeader(HeaderParser.LOCATION_HEADER, location);
			completeResponse("302");
			break;
		case AJAX_ALEADY_AUTH:
			JSONObject json=new JSONObject();
			json.put("result", true);
			json.put(AuthHandler.APP_SID, mapping.getAttribute(AuthHandler.APP_SID));
			json.put(AuthHandler.LOGIN_ID, mapping.getAttribute(AuthHandler.LOGIN_ID));
			responseJson(json);
			break;
		case AUTHENTICATE:
			String authHeaderName=(String)mapping.getAttribute(AUTH_HEADER_NAME);
			String authHeader=(String)mapping.getAttribute(AUTH_HEADER);
			String statuCode=(String)mapping.getAttribute(STATUS_CODE);
			setHeader(authHeaderName,authHeader);
			completeResponse(statuCode);
			break;
		case CROSS_DOMAIN_FRAME:
			mapping.setResolvePath("/crossDomainFrame.vsp");
			mapping.setDesitinationFile(config.getAuthDocumentRoot());
			setAttribute(SCOPE.REQUEST,RESPONSE, mapping.getAttribute(RESPONSE));
			forwardHandler(Mapping.VELOCITY_PAGE_HANDLER);
			break;
		case JSON_RESPONSE:
			Object response=mapping.getAttribute(RESPONSE);
			responseJson(response);
			break;
		default:
			completeResponse("500", "type:" + type);
		}
	}

	public void onFailure(Object userContext, Throwable t) {
		if(logger.isDebugEnabled())logger.debug("#failer.cid:" + getChannelId() + ":" + t.getMessage());
		asyncClose(userContext);
		super.onFailure(t, userContext);
	}

	public void onTimeout(Object userContext) {
		if(logger.isDebugEnabled())logger.debug("#timeout.cid:" + getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}

	public void onFinished() {
		if(logger.isDebugEnabled())logger.debug("#finished.cid:" + getChannelId());
		super.onFinished();
	}
}
