package naru.aweb.spdy;

import java.security.Provider;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;

public class SpdyConfig {
	private static Logger logger=Logger.getLogger(SpdyConfig.class);
	
	private Config config;
	private boolean isSpdyAvailable;
	private Provider provider=null;
	
	public SpdyConfig(Config config){
		this.config=config;
		isSpdyAvailable=false;
		//設定がSpdyを使う事を指示していること
		boolean useSslStdProvider=config.getBoolean("useSslStdProvider", true);
		if(!useSslStdProvider){
			return;
		}
		//JDK7以上で動作していること
//		String vesion=System.getProperty("java.version");
		//sslnpn.net.ssl.internal.ssl.Providerが読み込める事
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
//			String[] protocols=config.getStringArray("spdyProtocols");
			String[] protocols=new String[]{SpdyFrame.PROTOCOL_V3,SpdyFrame.PROTOCOL_V2,SpdyFrame.PROTOCOL_HTTP_11};
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
