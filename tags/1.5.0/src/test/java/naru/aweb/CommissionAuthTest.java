package naru.aweb;

import naru.aweb.config.CommissionAuth;
import naru.aweb.config.User;

import org.junit.Test;

public class CommissionAuthTest {
	@Test
	public void testJson(){
		User user=new User();
		user.setId(123l);
		CommissionAuth commissionAuth=new CommissionAuth(user,"http://");
		commissionAuth.setAuthDataPlain("data");
		System.out.println(commissionAuth.toJson());
	}

}
