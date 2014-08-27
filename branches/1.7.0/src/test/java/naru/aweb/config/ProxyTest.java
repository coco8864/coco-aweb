package naru.aweb.config;
import static org.junit.Assert.*;	

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;

import naru.aweb.util.ServerParser;

import org.junit.Test;

public class ProxyTest {
	@Test
	public void testProxy1(){
	}
	@Test
	public void testProxy4() throws IOException{
		new ProxyFinder();
		ProxyFinder finder=ProxyFinder.create(null, 
				"localhost:8080","localhosts:8080","aaa.*;*.bbb;ccc");
		System.out.println(finder.getProxyPac("localhost:9090"));
		System.out.println(finder.findProxyServer(true, "aaa.xxx"));
		System.out.println(finder.findProxyServer(true, "yyy.bbb"));
		System.out.println(finder.findProxyServer(true, "ccc.zzz"));
	}
	@Test
	public void testProxy5() throws IOException{
		ProxyFinder finder=ProxyFinder.create("file:///E:/coco/aweb/test.pac", 
				null,null,null);
		System.out.println(finder.getProxyPac("localhost:9090"));
		System.out.println(finder.findProxyServer(true, "aaa.xxx"));
		System.out.println(finder.findProxyServer(true, "bbb.yyy"));
		System.out.println(finder.findProxyServer(true, "ccc.zzz"));
	}
	/* test.pac 
function FindProxyForURL(url,host) {
 if( shExpMatch(host,"aaa.*") ){
  return "PROXY aaas.proxy:8080";
 }
 if( shExpMatch(host,"bbb.*") ){
  return "PROXY bbbs.proxy:8080";
 }
 return "DIRECT";
}
	 */
	
}
