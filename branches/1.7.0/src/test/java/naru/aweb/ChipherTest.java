package naru.aweb;

import static org.junit.Assert.*;

import naru.aweb.util.CipherUtil;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ChipherTest {
	
	@Test
	public void testChipher(){
		byte[] cleartext="I am chipher test data".getBytes();
		byte[] ciphertext=CipherUtil.encrypt(cleartext,"password");
		byte[] calcCleartext=CipherUtil.decrypt(cleartext,"password");
		
		assertArrayEquals(cleartext, calcCleartext);
	}
	@Test
	public void testChipher2(){
		byte[] cleartext="I am chipher test data".getBytes();
		byte[] ciphertext=CipherUtil.encrypt(cleartext,"password");
		byte[] calcCleartext=CipherUtil.decrypt(cleartext,"password2");
		
		assertArrayEquals(cleartext, calcCleartext);
	}

}
