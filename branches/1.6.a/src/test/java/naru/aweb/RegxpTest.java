package naru.aweb;

import static org.junit.Assert.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class RegxpTest {

	@Test
	public void test() {
		String reqex=".*\\.vsp$|.*\\.vsf$|/ph\\.js|/ph\\.json";
		Pattern pattern=Pattern.compile(reqex);
		Matcher matcher=null;
		synchronized(pattern){
			matcher=pattern.matcher("/ph.json");
		}
		System.out.println(matcher.matches());

		synchronized(pattern){
			matcher=pattern.matcher("/xxx.vsp");
		}
		System.out.println(matcher.matches());
		
		synchronized(pattern){
			matcher=pattern.matcher("/xxx.vsx");
		}
		System.out.println(matcher.matches());
		
		
		/*
		 * .*\.vsv$|.*\.vsv$|/ph\.js|/ph\.json
		 */
	}

}
