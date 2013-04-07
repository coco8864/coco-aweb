package naru.aweb;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

import org.junit.Test;

public class CharsetDecoderTest {

	@Test
	public void test() {
		Charset c=Charset.forName("utf-8");
		CharsetDecoder cd=c.newDecoder();
		CharBuffer cb=CharBuffer.allocate(128);
		ByteBuffer bb=ByteBuffer.wrap("aaaa".getBytes());
		
		/*
		try {
			System.out.println(cd.decode(bb).toString());
		} catch (CharacterCodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		*/
		
		System.out.println(bb.remaining());
		CoderResult coderResult=cd.decode(bb, cb,false);
		System.out.println(coderResult.isError());
		coderResult=cd.decode(bb, cb,true);
		coderResult=cd.flush(cb);
		cb.flip();
		System.out.println(cb.toString());
		
		
		try {
			coderResult.throwException();
		} catch (CharacterCodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}

}
