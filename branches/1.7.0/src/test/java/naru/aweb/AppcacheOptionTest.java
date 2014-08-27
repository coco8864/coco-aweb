package naru.aweb;

import static org.junit.Assert.*;

import java.io.File;

import naru.aweb.config.AppcacheOption;
import net.sf.json.JSONObject;

import org.junit.Test;

public class AppcacheOptionTest {

	@Test
	public void test() {
		JSONObject json=new JSONObject();
		File destinationFile=new File("D:\\prj\\aweb\\ph\\docroot");
		AppcacheOption appcacheOption=new AppcacheOption(destinationFile,"/aaa",json);
	}

}
