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
	private static final String CA_PASSWORD = "caPassword";
	private static final String TRUST_STORE_DIR = "trustStoreDir";
	private static final String KEYTOOL = "keytool";
	private static final String OPENSSL = "openssl";
	
	private File trustStoreDir;
	private String password;
	private Map<String,SSLContext> sslContexts;
	private String keytool;
	private String openssl=null;
	private String caPassword;
	private Provider sslProvider;
	private File caDir=null;
	
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
		openssl=config.getString(OPENSSL);
		if(openssl==null){
			openssl="/usr/bin/openssl";
		}
		File test=new File(openssl);
		if(!test.exists()){
			openssl=null;
			logger.info("openssl not found");
		}else{
			caPassword=config.getString(CA_PASSWORD);
			caDir=new File(trustStoreDir,"CA");
			logger.info("openssl command:"+openssl);
		}
	}
	
	public SSLContext getSSLContext(String domain){
		SSLContext sslContext=sslContexts.get(domain);
		if(sslContext!=null){
			return sslContext;
		}
		InputStream storeStream=null;
		try {
			storeStream=createTrustStore(domain, password,caPassword);
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
	
	private void exec(String title,String[] args){
		Process p;
		try {
			p = Runtime.getRuntime().exec(args,null,caDir);
			p.getInputStream().close();
			p.getOutputStream().close();
			p.waitFor();
			logger.info(title +" exitValue:"+p.exitValue());
		} catch (Exception e) {
			logger.warn("fail to " +title,e);
			throw new RuntimeException("fail to " +title,e);
		}
	}
	
	private void runKeytoolGenkey(File trustStoreFile,String cn,String password){
		String args[]={
/*
* keytool -genkey -alias phantom -keystore cacerts_127.0.0.1 -storepass changeit -keypass changeit  -validity 1461 -dname "OU=phantom Project, O=coco8864, L=Numazu-shi, ST=Shizuoka, C=JP, CN=127.0.0.1"
*/
//				"C:\\jdk1.5.0_18\\bin\\keytool.exe",
				keytool,
				"-genkey",
				"-keyalg","RSA",
				"-keysize","1024",
				"-validity","1461",
				"-alias","phantom",
				"-keypass",password,
				"-storepass",password,
				"-dname","C=JP, ST=shizuoka, L=numazu, O=coco8864, OU=phantom ,CN="+cn,
				"-keystore",trustStoreFile.getAbsolutePath()
		};
		exec("generate key cn:"+cn,args);
	}
	
	private void runImportCA(File trustStoreFile,String password){
		File caFile=new File(trustStoreDir,"CA/cacert.pem");
		String args[]={
/*
keytool -keystore cacerts_192.168.1.30.jks -storepass changeit -import -noprompt -trustcacerts -alias phantomCA -file D:\prj\aweb\CA\cacert.pem
*/
				keytool,
				"-import",
				"-noprompt",
				"-trustcacerts",
				"-alias","phantomCA",
				"-file",caFile.getAbsolutePath(),
				"-storepass",password,
				"-keystore",trustStoreFile.getAbsolutePath()
		};
		exec("importCA",args);
	}
	
	private void runCertreq(File trustStoreFile,String cn,String password){
		File certreqFile=new File(trustStoreDir,cn+".csr");//.csr: èêñºóvãÅ (PEM-encoded X.509 Certificate Signing Request)
		String args[]={
/*
keytool -keystore cacerts_192.168.1.30.jks -storepass changeit -certreq -alias phantom -file 192.168.1.30_certreq.csr
*/
				keytool,
				"-certreq",
				"-noprompt",
				"-alias","phantom",
				"-file",certreqFile.getAbsolutePath(),
				"-storepass",password,
				"-keystore",trustStoreFile.getAbsolutePath()
		};
		exec("certreq",args);
	}
	
	private void runExportCert(File trustStoreFile,String cn,String password){
		File certFile=new File(trustStoreDir,cn+".cer");
		String args[]={
/*
keytool -export -storepass changeit -keystore D:\prj\aweb\ph\security\ph.login.yahoo.co.jp.jks -alias phantom -file ph.login.yahoo.co.jp.cer
*/
				keytool,
				"-export",
				"-noprompt",
				"-alias","phantom",
				"-file",certFile.getAbsolutePath(),
				"-storepass",password,
				"-keystore",trustStoreFile.getAbsolutePath()
		};
		exec("exportCert",args);
	}
	
	private void runServerCert(File trustStoreFile,String cn,String caPassword){
		File confFile=new File(trustStoreDir,"CA/openssl-ca.cnf");
		File keyFile=new File(trustStoreDir,"CA/private/cakey.pem");
		File casertFile=new File(trustStoreDir,"CA/cacert.pem");
		File certreqFile=new File(trustStoreDir,cn+".csr");//.csr: èêñºóvãÅ (PEM-encoded X.509 Certificate Signing Request)
		File certFile=new File(trustStoreDir,cn+".crt");//.crt: èÿñæèë(X509ÇÃPEM encoded)
		String args[]={
/*
openssl ca -config /home/naru/phantom3/ph/security/CA/openssl-ca.cnf -keyfile /home/naru/phantom3/ph/security/CA/private/cakey.pem -cert /home/naru/phantom3/ph/security/CA/cacert.pem -in /home/naru/phantom3/ph/security/49.212.107.199_certreq.csr -out /home/naru/phantom3/ph/security/49.212.107.199_cert.cer -passin pass:xxxxxx -batch
*/
				openssl,
				"ca",
				"-config",confFile.getAbsolutePath(),
				"-keyfile",keyFile.getAbsolutePath(),
				"-cert",casertFile.getAbsolutePath(),
				"-in",certreqFile.getAbsolutePath(),
				"-out",certFile.getAbsolutePath(),
				"-passin","pass:"+caPassword,
				"-batch"
		};
		exec("serverCert",args);
	}
	
	private void runImportCert(File trustStoreFile,String cn,String password){
		File certFile=new File(trustStoreDir,cn+".crt");//.crt: èÿñæèë(X509ÇÃPEM encoded)
		String args[]={
/*
keytool -keystore ph.www.google.com.jks -storepass changeit -import -noprompt -alias phantom -file D:\prj\aweb\ph\CA\ph.www.google.com_cert.csr
*/
				keytool,
				"-import",
				"-noprompt",
				"-alias","phantom",
				"-file",certFile.getAbsolutePath(),
				"-storepass",password,
				"-keystore",trustStoreFile.getAbsolutePath()
		};
		exec("certreq",args);
	}
	
	private InputStream createTrustStore(String cn,String trustStorePassword,String caPassword) throws FileNotFoundException{
		File trustStoreFile=new File(trustStoreDir,cn+".jks");
		if(trustStoreFile.exists()){
			return new FileInputStream(trustStoreFile);
		}
		runKeytoolGenkey(trustStoreFile,cn,trustStorePassword);
		if(openssl==null){
			runExportCert(trustStoreFile,cn,trustStorePassword);
		}else{
			runImportCA(trustStoreFile,trustStorePassword);
			runCertreq(trustStoreFile,cn,trustStorePassword);
			runServerCert(trustStoreFile,cn,caPassword);
			runImportCert(trustStoreFile,cn,trustStorePassword);
		}
		
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
		if(sslProvider!=null){
			sslContext = SSLContext.getInstance("TLSv1", sslProvider);
		}else{
			sslContext = SSLContext.getInstance("TLSv1");
		}
		sslContext.init(kmf.getKeyManagers(), tms, null);
		return sslContext;
	}

}
