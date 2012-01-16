package naru.aweb.core;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthHandler;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.ParameterParser;
import naru.aweb.http.WebServerHandler;
import naru.aweb.mapping.MappingResult;
import net.sf.json.JSON;
import net.sf.json.JSONObject;

import org.apache.log4j.Logger;

/**
 * DispatchHandler�Ń��X�|���X�����肵���ꍇ�Ɏg����
 * 
 * @author naru
 * 
 */
public class DispatchResponseHandler extends WebServerHandler {
	private static Logger logger = Logger
			.getLogger(DispatchResponseHandler.class);
	private static Config config = Config.getConfig();
	private static final String TYPE = "type";
	private static final String MESSAGE = "message";
	private static final String STATUS_CODE = "statusCode";
	private static final String AUTH_HEADER_NAME = "authenticateHeaderName";
	private static final String AUTH_HEADER = "authenticateHeader";
	private static final String AJAX_RESPONSE = "ajaxResponse";

	private enum Type {
		FORBIDDEN, NOT_FOUND, REDIRECT,AJAX_ALEADY_AUTH,AUTHENTICATE
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
	
	public static MappingResult ajaxAleadyAuth() {
		MappingResult mapping = createDispatchMapping(Type.AJAX_ALEADY_AUTH);
		return mapping;
	}

	public static MappingResult redirectOrgPath(String location,String setCookieString) {
		MappingResult mapping=createDispatchMapping(Type.REDIRECT);
		mapping.setAttribute(HeaderParser.LOCATION_HEADER, location);
		mapping.setAttribute(HeaderParser.SET_COOKIE_HEADER, setCookieString);
		return mapping;
	}

	private static MappingResult createDispatchMapping(Type type) {
		MappingResult mapping = (MappingResult) PoolManager.getInstance(MappingResult.class);
		mapping.setHandlerClass(DispatchResponseHandler.class);
		mapping.setAttribute(TYPE, type);
		return mapping;
	}

	public void startResponseReqBody() {
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
			//IE��iframe����cookie��L���ɂ���w�b�_,http://d.hatena.ne.jp/satoru_net/20090506/1241545178
			setHeader("P3P", "CP=\"CAO PSA OUR\"");
			location = (String) mapping.getAttribute(HeaderParser.LOCATION_HEADER);
			setHeader(HeaderParser.LOCATION_HEADER, location);
			completeResponse("302");
			break;
		case AJAX_ALEADY_AUTH:
			JSONObject json=new JSONObject();
			json.put("result", true);
			responseJson(json);
			break;
		case AUTHENTICATE:
			String authHeaderName=(String)mapping.getAttribute(AUTH_HEADER_NAME);
			String authHeader=(String)mapping.getAttribute(AUTH_HEADER);
			String statuCode=(String)mapping.getAttribute(STATUS_CODE);
			setHeader(authHeaderName,authHeader);
			completeResponse(statuCode);
			break;
		default:
			completeResponse("500", "type:" + type);
		}
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" + getChannelId() + ":" + t.getMessage());
		asyncClose(userContext);
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" + getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}

	public void onFinished() {
		logger.debug("#finished.cid:" + getChannelId());
		super.onFinished();
	}
}