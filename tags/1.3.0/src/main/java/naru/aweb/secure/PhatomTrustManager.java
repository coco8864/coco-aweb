package naru.aweb.secure;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.log4j.Logger;

public class PhatomTrustManager implements X509TrustManager {
	private static Logger logger=Logger.getLogger(PhatomTrustManager.class);
	/*
	 * The default X509TrustManager returned by SunX509. We'll delegate
	 * decisions to it, and fall back to the logic in this class if the default
	 * X509TrustManager doesn't trust it.
	 */
	private X509TrustManager sunJSSEX509TrustManager;

	public PhatomTrustManager(KeyStore ks) throws NoSuchAlgorithmException, NoSuchProviderException, KeyStoreException {
		// create a "default" JSSE X509TrustManager.
//		KeyStore ks = KeyStore.getInstance("JKS");
//		ks.load(new FileInputStream("trustedCerts"), "passphrase".toCharArray());
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509","SunJSSE");
		tmf.init(ks);
		TrustManager tms[] = tmf.getTrustManagers();
		/*
		 * Iterate over the returned trustmanagers, look for an instance of
		 * X509TrustManager. If found, use that as our "default" trust manager.
		 */
		for (int i = 0; i < tms.length; i++) {
			if (tms[i] instanceof X509TrustManager) {
				sunJSSEX509TrustManager = (X509TrustManager) tms[i];
				return;
			}
		}
		/*
		 * Find some other way to initialize, or else we have to fail the
		 * constructor.
		 */
		throw new IllegalStateException("Couldn't initialize");
	}

	public void checkClientTrusted(X509Certificate[] certs, String authType)
			throws CertificateException {
		if(!logger.isDebugEnabled()){//証明書がない事は承知している
			return;
		}
		try {
			sunJSSEX509TrustManager.checkClientTrusted(certs, authType);
		} catch (CertificateException e) {
			logger.debug("checkClientTrusted fail",e);
			//TODO 本当は、ユーザ毎に証明書の管理をすべき
			//Userテーブルには、証明書の格納場所は作ってある
//			throw e;
		}
	}

	public void checkServerTrusted(X509Certificate[] certs, String authType)
			throws CertificateException {
		if(!logger.isDebugEnabled()){//証明書がない事は承知している
			return;
		}
		try {
			sunJSSEX509TrustManager.checkServerTrusted(certs, authType);
		} catch (CertificateException e) {
			logger.warn("checkServerTrusted fail",e);
//			throw e;
		}
	}

	public X509Certificate[] getAcceptedIssuers() {
		return sunJSSEX509TrustManager.getAcceptedIssuers();
	}
}
