package naru.aweb.secure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.log4j.Logger;

import naru.aweb.config.Config;


public class SslContextPool {
	private static Logger logger = Logger.getLogger(SslContextPool.class);
	private static final String TRUST_STORE_PASSWORD = "trustStorePassword";
	private static final String TRUST_STORE_DIR = "trustStoreDir";
	private static final String KEYTOOL = "keytool";
	
	private File trustStoreDir;
	private String password;
	private Map<String,SSLContext> sslContexts;
	private String keytool;
	private Provider sslProvider;
	
	public SslContextPool(Config config,Provider sslProvider){
		this.sslProvider=sslProvider;
		sslContexts=new HashMap<String,SSLContext>();
		trustStoreDir=new File(config.getString(TRUST_STORE_DIR));
		if(!trustStoreDir.exists()){
			trustStoreDir.mkdirs();
		}
		password=config.getString(TRUST_STORE_PASSWORD);
		keytool=config.getString(KEYTOOL);
		if(keytool==null){
			String javaHome=System.getProperty("java.home");//JAVA_HOME
			keytool=javaHome +"/bin/keytool";
		}
		logger.info("keytool command:"+keytool);
	}
	
	public SSLContext getSSLContext(String domain){
		SSLContext sslContext=sslContexts.get(domain);
		if(sslContext!=null){
			return sslContext;
		}
		InputStream storeStream=null;
		try {
			storeStream=createTrustStore(domain, password);
			sslContext=createSSLContext(storeStream,password);
			synchronized(sslContexts){
				sslContexts.put(domain, sslContext);
			}
		} catch (Exception e) {
			throw new RuntimeException("fail to getSSLContext",e);
		}finally{
			if(storeStream!=null){
				try {
					storeStream.close();
				} catch (IOException ignore) {
				}
			}
		}
		return sslContext;
	}
	
	private void runKeytoolGenkey(File trustStoreFile,String cn,String password){
		Process p;
		try {
			String args[]={
//					"C:\\jdk1.5.0_18\\bin\\keytool.exe",
					keytool,
//					"keytool",
					"-genkey",
					"-keyalg","RSA",
					"-keysize","512",
					"-validity","365",
					"-storetype","JKS",
					"-alias","ssltest",
					"-keypass",password,
					"-storepass",password,
					"-dname","CN="+cn,
					"-keystore",trustStoreFile.getAbsolutePath()
			};
			p = Runtime.getRuntime().exec(args);
			p.getInputStream().close();
			p.getOutputStream().close();
			p.waitFor();
			System.out.println("generate key cn:"+cn +" exitValue:"+p.exitValue());
		} catch (Exception e) {
			throw new RuntimeException("fail to runKeytoolGenkey",e);
		}
	}
	
	private InputStream createTrustStore(String cn,String trustStorePassword) throws FileNotFoundException{
		File trustStoreFile=new File(trustStoreDir,"cacerts_"+cn);
		if(trustStoreFile.exists()){
			return new FileInputStream(trustStoreFile);
		}
		runKeytoolGenkey(trustStoreFile,cn,trustStorePassword);
		if(trustStoreFile.exists()){
			return new FileInputStream(trustStoreFile);
		}
		throw new RuntimeException("fail to createTrustStore");
	}
	
	private SSLContext createSSLContext(InputStream storeStream,String trustStorePassword) throws NoSuchAlgorithmException, CertificateException, IOException, KeyStoreException, UnrecoverableKeyException, KeyManagementException, NoSuchProviderException {
		KeyStore ks = KeyStore.getInstance("JKS");
		char[] keystorePass = trustStorePassword.toCharArray();
		ks.load(storeStream, keystorePass);
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(ks, keystorePass);
		TrustManager[] tms=new TrustManager[]{new PhatomTrustManager(ks)};
		
		SSLContext sslContext=null;
		sslContext = SSLContext.getInstance("TLSv1", sslProvider);
		sslContext.init(kmf.getKeyManagers(), tms, null);
		return sslContext;
	}

}
