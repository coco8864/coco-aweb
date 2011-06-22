package naru.aweb;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.ByteBuffer;

import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientHandler;
import naru.aweb.robot.CallScheduler;
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
		public void onRequestEnd(Object userContext,int stat) {
			System.out.println("onRequestEnd.stat:"+stat +":" +(System.currentTimeMillis()%10000));
		}

		@Override
		public void onRequestFailure(Object userContext,int stat, Throwable t) {
			System.out.println("onRequestFailure.stat:"+stat+":" +(System.currentTimeMillis()%10000));
			t.printStackTrace();
		}

		@Override
		public void onResponseBody(Object userContext, ByteBuffer[] buffer) {
			System.out.println("onResponseBody." +buffer.length);
		}

		@Override
		public void onResponseHeader(Object userContext,
				HeaderParser responseHeader) {
			System.out.println("onResponseHeader."+(System.currentTimeMillis()%10000) +":statusCode:"+responseHeader.getStatusCode());
		}

		@Override
		public void onWrittenRequestBody(Object userContext) {
			System.out.println("onWrittenRequestBody");
		}

		@Override
		public void onWrittenRequestHeader(Object userContext) {
			System.out.println("onWrittenRequestHeader:"+(System.currentTimeMillis()%10000));
		}

		@Override
		public void onWebConnected(Object userContext) {
			WebClientHandler handler=(WebClientHandler)userContext;
			System.out.println("onWebConnected:"+(System.currentTimeMillis()%10000));
			System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
			System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
		}

		@Override
		public void onWebHandshaked(Object userContext) {
			WebClientHandler handler=(WebClientHandler)userContext;
			System.out.println("onWebHandshaked:"+(System.currentTimeMillis()%10000));
			System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
			System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
		}

		@Override
		public void onWebProxyConnected(Object userContext) {
			// TODO Auto-generated method stub
			
		}
	}
	
	
	@Test
	public void test0() throws Throwable{
		callTest("qtest0",Long.MAX_VALUE);
	}
	public void qtest0() throws Throwable{
		WebClientHandler handler=WebClientHandler.create(false,"www.asahi.com", 80);
		handler.setHeaderSchedule(1000,0);
		
		HeaderParser requestHeader=new HeaderParser();
		requestHeader.setMethod("GET");
		requestHeader.setReqHttpVersion("/");
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
		TestWebClient testWebClient=new TestWebClient();
		handler.startRequest(testWebClient, handler, 1000, requestHeader, true, 15000);
		System.out.println((System.currentTimeMillis()%10000));
		while(true){
			Thread.sleep(100);
			if(handler.isConnect()==false){
				break;
			}
		}
		System.out.println("headerWrite:"+(handler.getHeaderActualWriteTime()%10000));
		System.out.println("bodyWrite:"+(handler.getBodyActualWriteTime()%10000));
	}
	
	@Test
	public void test1() throws Throwable{
		callTest("qtest1",Long.MAX_VALUE);
	}
	public void qtest1() throws Throwable{
		WebClientHandler handler=WebClientHandler.create(false,"ph-sample.appspot.com", 80);
//		CallScheduler cs=CallScheduler.create(handler, 100000,Long.MIN_VALUE, 0, 0);
		handler.setHeaderSchedule(10000,Long.MIN_VALUE);
		
		HeaderParser requestHeader=new HeaderParser();
		requestHeader.setMethod("GET");
		requestHeader.setReqHttpVersion("/");
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
		TestWebClient testWebClient=new TestWebClient();
		handler.startRequest(testWebClient, handler, 1000, requestHeader, true, 15000);
		System.out.println((System.currentTimeMillis()));
		while(true){
			Thread.sleep(100);
			if(handler.isConnect()==false){
				break;
			}
		}
		System.out.println((System.currentTimeMillis()));
		System.out.println("headerWrite:"+(handler.getHeaderActualWriteTime()%10000));
		System.out.println("bodyWrite:"+(handler.getBodyActualWriteTime()%10000));
	}
	
	@Test
	public void test2() throws Throwable{
		callTest("qtest2",Long.MAX_VALUE);
	}
	public void qtest2() throws Throwable{
		WebClientHandler handler=WebClientHandler.create(false,"ph-sample.appspot.com", 80);
		CallScheduler cs=CallScheduler.create(handler);
		
		HeaderParser requestHeader=new HeaderParser();
		requestHeader.setMethod("GET");
		requestHeader.setReqHttpVersion("/");
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
		TestWebClient testWebClient=new TestWebClient();
		handler.startRequest(testWebClient, handler, 1000, requestHeader, false, 15000);
		System.out.println((System.currentTimeMillis()));
		System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
		System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
		while(true){
			Thread.sleep(100);
			System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
			System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
			if(handler.isConnect()==false){
				break;
			}
		}
		System.out.println((System.currentTimeMillis()));
		System.out.println("headerWrite:"+(cs.getHeaderActualWriteTime()%10000));
		System.out.println("bodyWrite:"+(cs.getBodyActualWriteTime()%10000));
		System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
		System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
	}
	
	@Test
	public void test3() throws Throwable{
		callTest("qtest3",Long.MAX_VALUE);
	}
	public void qtest3() throws Throwable{
//		WebClientHandler handler=WebClientHandler.create(true,"ph-sample.appspot.com", 443);
//		WebClientHandler handler=WebClientHandler.create(false,"ph-sample.appspot.com", 80);
//		WebClientHandler handler=WebClientHandler.create(false,"judus.soft.fujitsu.com", 1280);
		WebClientHandler handler=WebClientHandler.create(true,"10.124.175.29", 1280);
		handler.ref();
		handler.setHeaderSchedule(0,0);
		
		HeaderParser requestHeader=new HeaderParser();
		requestHeader.setMethod("GET");
		requestHeader.setPath("/");
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
		long startTime=System.currentTimeMillis();
		TestWebClient testWebClient=new TestWebClient();
		handler.startRequest(testWebClient, handler, 10000, requestHeader, false, 5000);
		System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
		System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
		while(true){
			Thread.sleep(100);
//			System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
//			System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
			if(handler.isConnect()==false){
				break;
			}
		}
		System.out.println((System.currentTimeMillis()-startTime));
		System.out.println("sslProxy:"+(handler.getSslProxyActualWriteTime()-startTime));
		System.out.println("headerWrite:"+(handler.getHeaderActualWriteTime()-startTime));
		System.out.println("bodyWrite:"+(handler.getBodyActualWriteTime()-startTime));
		System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
		System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
		handler.unref();
	}
	
	
	private static long count=0;
	
	private static class TestWebConnectClient implements WebClient{
		private long startTime;
		TestWebConnectClient(long startTime){
			this.startTime=startTime;
		}
		
		@Override
		public void onRequestEnd(Object userContext,int stat) {
			System.out.println("onRequestEnd.stat:"+stat +":" +(System.currentTimeMillis()-startTime));
			synchronized(this){
				count--;
			}
		}

		@Override
		public void onRequestFailure(Object userContext,int stat, Throwable t) {
			System.out.println("onRequestFailure.stat:"+stat+":" +(System.currentTimeMillis()-startTime));
			t.printStackTrace();
			synchronized(this){
				count--;
			}
		}

		@Override
		public void onResponseBody(Object userContext, ByteBuffer[] buffer) {
			System.out.println("onResponseBody." +buffer.length);
		}

		@Override
		public void onResponseHeader(Object userContext,
				HeaderParser responseHeader) {
			System.out.println("onResponseHeader."+(System.currentTimeMillis()-startTime) +":statusCode:"+responseHeader.getStatusCode());
		}

		@Override
		public void onWrittenRequestBody(Object userContext) {
			System.out.println("onWrittenRequestBody");
		}

		@Override
		public void onWrittenRequestHeader(Object userContext) {
			System.out.println("onWrittenRequestHeader:"+(System.currentTimeMillis()%10000));
		}
		
		private boolean connected=false;

		@Override
		public void onWebConnected(Object userContext) {
			WebClientHandler handler=(WebClientHandler)userContext;
			System.out.println("onWebConnected:"+(System.currentTimeMillis()-startTime));
			synchronized(this){
				connected=true;
				count++;
				notify();
			}
		}

		@Override
		public void onWebHandshaked(Object userContext) {
			WebClientHandler handler=(WebClientHandler)userContext;
			System.out.println("onWebHandshaked:"+(System.currentTimeMillis()%10000));
			System.out.println("getTotalReadLength:"+handler.getTotalReadLength());
			System.out.println("getTotalWriteLength:"+handler.getTotalWriteLength());
		}

		@Override
		public void onWebProxyConnected(Object userContext) {
			// TODO Auto-generated method stub
			
		}
	}
	
	@Test
	public void test4() throws Throwable{
		callTest("qtest4",Long.MAX_VALUE);
	}
	public void qtest4() throws Throwable{
		WebClientHandler[] handlers=new WebClientHandler[2000];
		for(int i=0;i<handlers.length;i++){
			System.out.println("i:"+i);
			WebClientHandler handler=handlers[i]=WebClientHandler.create(false,"ph-sample.appspot.com", 80);
			handler.ref();
			handler.setHeaderSchedule(100000,Long.MIN_VALUE);
			
			HeaderParser requestHeader=new HeaderParser();
			requestHeader.setMethod("GET");
			requestHeader.setPath("/");
			requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
			long startTime=System.currentTimeMillis();
			TestWebConnectClient testWebClient=new TestWebConnectClient(startTime);
			handler.startRequest(testWebClient, handler, 10000, requestHeader, false, 5000);
			/*
			synchronized(testWebClient){
				while(true){
					if(testWebClient.connected){
						break;
					}
					testWebClient.wait();
				}
			}
			*/
		}
		System.out.println("count:"+count);
	}
	
	@Test
	public void test5() throws Throwable{
		callTest("qtest5",Long.MAX_VALUE);
	}
	public void qtest5() throws Throwable{
		WebClientHandler[] handlers=new WebClientHandler[2000];
		for(int i=0;i<handlers.length;i++){
			WebClientHandler handler=handlers[i]=WebClientHandler.create(false,"ph-sample.appspot.com", 80);
			handler.ref();
			handler.setHeaderSchedule(200000,Long.MIN_VALUE);
		}
		HeaderParser requestHeader=new HeaderParser();
		requestHeader.setMethod("GET");
		requestHeader.setPath("/");
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
		for(int i=0;i<handlers.length;i++){
			System.out.println("i:"+i);
			long startTime=System.currentTimeMillis();
			TestWebConnectClient testWebClient=new TestWebConnectClient(startTime);
			handlers[i].startRequest(testWebClient, handlers[i], 10000, requestHeader, false, 5000);
			synchronized(testWebClient){
				while(true){
					if(testWebClient.connected){
						break;
					}
					testWebClient.wait();
				}
			}
		}
		
		System.out.println("count:"+count);
	}
	
	@Test
	public void test6() throws Throwable{
		callTest("qtest6",Long.MAX_VALUE);
	}
	public void qtest6() throws Throwable{
		WebClientHandler[] handlers=new WebClientHandler[2000];
		for(int i=0;i<handlers.length;i++){
			WebClientHandler handler=handlers[i]=WebClientHandler.create(false,"ph-sample.appspot.com", 80);
			handler.ref();
			handler.setHeaderSchedule(200000,Long.MIN_VALUE);
		}
		HeaderParser requestHeader=new HeaderParser();
		requestHeader.setMethod("GET");
		requestHeader.setPath("/");
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_10);
		for(int i=0;i<handlers.length;i++){
			System.out.println("i:"+i);
			long startTime=System.currentTimeMillis();
			Caller testWebClient=new TestWebConnectClient(startTime);
			handlers[i].startRequest(testWebClient, handlers[i], 10000, requestHeader, false, 5000);
			synchronized(testWebClient){
				while(true){
					if(testWebClient.connected){
						break;
					}
					testWebClient.wait();
				}
			}
		}
		
		System.out.println("count:"+count);
	}
	
	
}
