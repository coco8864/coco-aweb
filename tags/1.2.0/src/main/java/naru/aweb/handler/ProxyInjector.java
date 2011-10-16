package naru.aweb.handler;

import java.nio.ByteBuffer;

import naru.aweb.http.HeaderParser;

public interface ProxyInjector {
	public void init(ProxyHandler proxyHandler);
	public void term();
	public void onRequestHeader(HeaderParser requestHeader);
	public void onResponseHeader(HeaderParser responseHeader);
	public ByteBuffer[] onResponseBody(ByteBuffer[] buffer);
	public boolean isInject();
	public long getInjectLength();
}
