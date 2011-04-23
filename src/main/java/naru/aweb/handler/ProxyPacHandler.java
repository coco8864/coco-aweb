package naru.aweb.handler;

import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebServerHandler;

import org.apache.log4j.Logger;

public class ProxyPacHandler extends WebServerHandler {
	private static Logger logger = Logger.getLogger(ProxyPacHandler.class);
	private static Config config=Config.getConfig();

	public void startResponseReqBody(){
		HeaderParser requestHeader=getRequestHeader();
		String localHost=requestHeader.getHeader(HeaderParser.HOST_HEADER);
		String pac=config.getProxyPac(localHost);
		setContentType("text/plain");
		completeResponse("200", pac);
	}

	public void onFailure(Object userContext, Throwable t) {
		logger.debug("#failer.cid:" +getChannelId() +":"+t.getMessage());
		asyncClose(userContext);
		super.onFailure(userContext, t);
	}

	public void onTimeout(Object userContext) {
		logger.debug("#timeout.cid:" +getChannelId());
		asyncClose(userContext);
		super.onTimeout(userContext);
	}
	
	public void onFinished() {
		logger.debug("#finished.cid:" +getChannelId());
		super.onFinished();
	}
	
}
