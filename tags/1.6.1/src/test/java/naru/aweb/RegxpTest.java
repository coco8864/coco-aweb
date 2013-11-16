package naru.aweb;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import naru.aweb.util.StreamUtil;

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
	
	@Test
	public void test2() {
		String text="zz\"/root/x.js'/aaa.js\"zz'/root/js/bbb.jpg'xx'/yyy.html'xxxxxxx";
		String reqex="[\"|'](?:(/root/[^(?:\"|')]*(?:(?:\\.js)|(?:\\.css)|(?:\\.gif)|(?:\\.png)|(?:\\.jpg)))|(/[^(?:\"|')]*(?:(?:\\.html)|(?:\\.htm))))[\"|']";
		Pattern pattern=Pattern.compile(reqex);
		
		Matcher matcher=null;
		synchronized(pattern){
			matcher=pattern.matcher(text);
		}
		while(matcher.find()){
			for(int i=0;i<(matcher.groupCount()+1);i++){
				System.out.println("i:"+i +" "+matcher.group(i));
			}
		}
	}
	@Test
	public void test3() throws IOException {
//		byte[] contentsBytes=StreamUtil.readFile(new File("ph/apps/admin/admin.vsp"));
		byte[] contentsBytes=StreamUtil.readFile(new File("ph/docroot/index.html"));
		String contents=new String(contentsBytes,"utf-8");
		String reqex="[\"|'](?:(/pub/[^(?:\"|')]*(?:(?:\\.js)|(?:\\.css)|(?:\\.gif)|(?:\\.png)|(?:\\.jpg)))|(/[^(?:\"|')]*(?:(?:\\.html)|(?:\\.htm))))[\"|']";
		Pattern pattern=Pattern.compile(reqex);
		Matcher matcher=null;
		synchronized(pattern){
			matcher=pattern.matcher(contents);
		}
		while(matcher.find()){
			for(int i=0;i<(matcher.groupCount()+1);i++){
				System.out.println("i:"+i +" "+matcher.group(i));
			}
		}
		
	}
	

}
