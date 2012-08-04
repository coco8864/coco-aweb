package naru.aweb.spdy;

import java.security.Provider;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;

public class SpdyConfig {
	private static Logger logger=Logger.getLogger(SpdyConfig.class);
	
	private boolean isSpdyAvailable;
	
	public SpdyConfig(Config config){
		isSpdyAvailable=false;
		//設定がSpdyを使う事を指示していること
		boolean useSslStdProvider=config.getBoolean("useSslStdProvider", false);
		if(!useSslStdProvider){
			return;
		}
		//JDK7以上で動作していること
		System.getProperty("");
		
		isSpdyAvailable=true;
		logger.info("SPDY available.");
	}
	
	public Provider getSslProvider(){
		if(isSpdyAvailable){
			//sslnpn.net.ssl.internal.ssl.Providerが読み込める事
			try {
				Class providerClass=Class.forName("sslnpn.net.ssl.internal.ssl.Provider");
				Provider provider=(Provider)providerClass.newInstance();
				return provider;
			} catch (ClassNotFoundException e) {
			} catch (InstantiationException e) {
			} catch (IllegalAccessException e) {
			}
		}
		return null;
	}
	
	public void setNextProtocols(SSLEngine engine){
		if(isSpdyAvailable && engine instanceof sslnpn.ssl.SSLEngineImpl){
			sslnpn.ssl.SSLEngineImpl sslNpnEngine=(sslnpn.ssl.SSLEngineImpl)engine;
			sslNpnEngine.setAdvertisedNextProtocols(SpdyFrame.PROTOCOL_V2,SpdyFrame.PROTOCOL_HTTP_11);
		}
	}
	
	public boolean isSpdyNextProtocol(SSLEngine engine){
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
