package naru.aweb;

import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

public class EncTest {
	
	public static void test(String enctype) throws UnsupportedEncodingException {
		OutputStreamWriter osw = new OutputStreamWriter( System.out, enctype );
		String cname = osw.getEncoding();
		System.out.println("enctype:"+enctype+":cname:"+cname);
	}
	

	/**
	 * @param args
	 * @throws UnsupportedEncodingException 
	 */
	public static void main(String[] args) throws UnsupportedEncodingException {
		test("Shift_JIS");
		test("Windows-31J");
		test("MS932");
	}

}
