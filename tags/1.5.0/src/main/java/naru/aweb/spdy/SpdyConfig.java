package naru.aweb.spdy;

import java.security.Provider;
import javax.net.ssl.SSLEngine;
import org.apache.log4j.Logger;
import naru.aweb.config.Config;

public class SpdyConfig {
	private static Logger logger=Logger.getLogger(SpdyConfig.class);
	private static final String SPDY_PROTOCOLS = "spdyProtocols";
	private static final String SPDY_TIMEOUT = "spdyTimeout";
	private static final String SPDY_FRAME_LIMIT = "spdyFrameLimit";
	
	private Config config;
	private boolean isSpdyAvailable;
	private Provider provider=null;
	private String spdyProtocols;
	private long spdyTimeout;
	private long spdyFrameLimit;//8192以上が必須
	private String[] protocols;
	
	public boolean isSpdyAvailable(){
		return isSpdyAvailable;
	}
	
	public String getSpdyProtocols() {
		return spdyProtocols;
	}

	public long getSpdyTimeout() {
		return spdyTimeout;
	}

	public long getSpdyFrameLimit() {
		return spdyFrameLimit;
	}

	public void setSpdyProtocols(String spdyProtocols) {
		config.setProperty(SPDY_PROTOCOLS, spdyProtocols);
		protocols=spdyProtocols.split(",");
		this.spdyProtocols = spdyProtocols;
	}

	public void setSpdyTimeout(long spdyTimeout) {
		config.setProperty(SPDY_TIMEOUT, spdyTimeout);
		this.spdyTimeout = spdyTimeout;
	}

	public void setSpdyFrameLimit(long spdyFrameLimit) {
		config.setProperty(SPDY_FRAME_LIMIT, spdyFrameLimit);
		this.spdyFrameLimit = spdyFrameLimit;
	}

	public SpdyConfig(Config config){
		this.config=config;
		isSpdyAvailable=false;
		spdyProtocols = config.getString(SPDY_PROTOCOLS);
		if(spdyProtocols==null){
			spdyProtocols=SpdyFrame.PROTOCOL_V3+","+SpdyFrame.PROTOCOL_V2+","+SpdyFrame.PROTOCOL_HTTP_11;
		}
		protocols=spdyProtocols.split(",");
		spdyTimeout=config.getLong(SPDY_TIMEOUT,60000);
		spdyFrameLimit=config.getLong(SPDY_FRAME_LIMIT,2048000);
		
		//設定がSpdyを使う事を指示していること
		boolean useSslStdProvider=config.getBoolean("useSslStdProvider", false);
		if(useSslStdProvider){//標準sslプロバイダを使う場合は、spdyできない
			return;
		}
		try {
			Class providerClass=Class.forName("sslnpn.net.ssl.internal.ssl.Provider");
			provider=(Provider)providerClass.newInstance();
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (UnsupportedClassVersionError e){
		}
		if(provider!=null){
			isSpdyAvailable=true;
			logger.info("SPDY available.");
		}
	}
	
	public Provider getSslProvider(){
		return provider;
	}
	
	public void setNextProtocols(SSLEngine engine){
		if(isSpdyAvailable && engine instanceof sslnpn.ssl.SSLEngineImpl){
			sslnpn.ssl.SSLEngineImpl sslNpnEngine=(sslnpn.ssl.SSLEngineImpl)engine;
			sslNpnEngine.setAdvertisedNextProtocols(protocols);
		}
	}
	
	/* 選択された次のプロトコル */
	public String getNextProtocol(SSLEngine engine){
		if(isSpdyAvailable && engine instanceof sslnpn.ssl.SSLEngineImpl){
			sslnpn.ssl.SSLEngineImpl sslNpnEngine=(sslnpn.ssl.SSLEngineImpl)engine;
			String nextProtocol=sslNpnEngine.getNegotiatedNextProtocol();
			if(nextProtocol!=null){
				return nextProtocol;
			}
		}
		return SpdyFrame.PROTOCOL_HTTP_11;
	}
	
	/* spdyプロトコルが選択されたか否か */
	public boolean isSpdySelect(SSLEngine engine){
		if(isSpdyAvailable && engine instanceof sslnpn.ssl.SSLEngineImpl){
			sslnpn.ssl.SSLEngineImpl sslNpnEngine=(sslnpn.ssl.SSLEngineImpl)engine;
			String nextProtocol=sslNpnEngine.getNegotiatedNextProtocol();
			if(nextProtocol!=null&&nextProtocol.startsWith("spdy/")){
				return true;
			}
		}
		return false;
	}
}
