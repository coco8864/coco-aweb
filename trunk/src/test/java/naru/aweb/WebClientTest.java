package naru.aweb;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientHandler;
import naru.queuelet.test.TestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class WebClientTest extends TestBase{
	@BeforeClass
	public static void beforClass() throws IOException {
		setupContainer("phantom2");
	}
	@Before
	public void beforeTest() {
	}
	@After
	public void afterClass() {
		stopContainer();
		System.out.println("Queuelet container stop");
	}
	
	private static class TestWebClient implements WebClient{
		@Override
		public void onRequestEnd(Object userContext) {
		}

		@Override
		public void onRequestFailure(Object userContext, Throwable t) {
		}

		@Override
		public void onResponseBody(Object userContext, ByteBuffer[] buffer) {
			System.out.println("onResponseBody." +buffer.length);
		}

		@Override
		public void onResponseHeader(Object userContext,
				HeaderParser responseHeader) {
			System.out.println("onResponseHeader");
		}

		@Override
		public void onWrittenRequestBody(Object userContext) {
			System.out.println("onWrittenRequestBody");
		}

		@Override
		public void onWrittenRequestHeader(Object userContext) {
			System.out.println("onWrittenRequestHeader");
		}
	}
	
	
	@Test
	public void test0() throws Throwable{
		callTest("qtest0",Long.MAX_VALUE);
	}
	public void qtest0() throws Throwable{
		WebClientHandler handler=WebClientHandler.create(false,"127.0.0.1", 1280);
		HeaderParser requestHeader=new HeaderParser();
		requestHeader.setMethod("GET");
		requestHeader.setReqHttpVersion("/");
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
		TestWebClient testWebClient=new TestWebClient();
		handler.startRequest(testWebClient, handler, 1000, requestHeader, true, 15000);
		
		System.out.println("qtest0");
	}
}
