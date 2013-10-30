package naru.aweb.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;


public class AuthenticationTest {
	private static Pattern authenticationPattern=Pattern.compile("\\s*Basic\\s+realm\\s*=\\s*\"(.[^\"]*)\"",Pattern.CASE_INSENSITIVE);
	
	@Test
	public void testAuth(){
		String a[]={ "Basic realm=\"WallyWorld\"",
				"BASIC  realm=\"WallyWorld\"",
				" BASIC  realm=\" WallyWorld\""
				};
		
		for(int i=0;i<a.length;i++){
			Matcher matcher=authenticationPattern.matcher(a[i]);
			matcher.find();
			System.out.println(matcher.groupCount() + ":" + matcher.group(1));
		}
		
	}
	
}
