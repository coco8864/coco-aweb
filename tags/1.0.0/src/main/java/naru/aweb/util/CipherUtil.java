package naru.aweb.util;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

public class CipherUtil {
	// Salt
	private static byte[] CIPHER_SALT = { (byte) 0xa7, (byte) 0x74, (byte) 0x31, (byte) 0x8d,
			(byte) 0x8e, (byte) 0xd8, (byte) 0xef, (byte) 0xa9 };
	// åJÇËï‘Çµêî
	private static int CIPHER_COUNT = 2048;
	private static String CIPHER_METHOD="PBEWithSHA1AndRC2_40";
	
	public static byte[] encrypt(byte[] cleartext,String password) {
		PBEKeySpec pbeKeySpec;
		PBEParameterSpec pbeParamSpec;
		SecretKeyFactory keyFac;
		SecretKey pbeKey;
		Cipher pbeCipher;

		// PBE ÉpÉâÉÅÅ[É^ê∂ê¨
		// à√çÜâª
		byte[] ciphertext=null;
		try {
			pbeParamSpec = new PBEParameterSpec(CIPHER_SALT, CIPHER_COUNT);
			pbeKeySpec = new PBEKeySpec(password.toCharArray());

			keyFac = SecretKeyFactory.getInstance(CIPHER_METHOD);
			pbeKey = keyFac.generateSecret(pbeKeySpec);
			// ê∂ê¨
			pbeCipher = Cipher.getInstance(CIPHER_METHOD);
			// èâä˙âª
			pbeCipher.init(Cipher.ENCRYPT_MODE, pbeKey, pbeParamSpec);
			ciphertext = pbeCipher.doFinal(cleartext);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ciphertext;
	}

	public static byte[] decrypt(byte[] ciphertext,String password) {
		PBEParameterSpec pbeParamSpecDec = new PBEParameterSpec(CIPHER_SALT, CIPHER_COUNT);
		PBEKeySpec pbeKeySpecDec = new PBEKeySpec(password.toCharArray());
		byte[] cleartext=null;
		try {
			SecretKeyFactory keyFacDec = SecretKeyFactory.getInstance(CIPHER_METHOD);
			SecretKey pbeKeyDec = keyFacDec.generateSecret(pbeKeySpecDec);

			Cipher cDec;
			cDec = Cipher.getInstance(CIPHER_METHOD);
			cDec.init(Cipher.DECRYPT_MODE, pbeKeyDec, pbeParamSpecDec);
			cleartext = cDec.doFinal(ciphertext);
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return cleartext;
	}
}
