package naru.aweb.filter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import naru.async.Timer;
import naru.async.pool.PoolManager;
import naru.async.timer.TimerManager;
import naru.aweb.config.Config;
import naru.aweb.http.HeaderParser;
import naru.aweb.http.WebClient;
import naru.aweb.http.WebClientConnection;
import naru.aweb.http.WebClientHandler;

public class ListGetter implements WebClient,Timer {
	private static Config config=Config.getConfig();
	private String source;
	private String chId;
	private File listFile;
	private FileChannel writeChannel;
	
	public void start(String source,String chId) throws IOException{
		this.source=source;
		this.chId=chId;
		if(!source.startsWith("http")){
			TimerManager.setTimeout(0, this, null);
			return;
		}
		listFile=File.createTempFile("list", ".tgz", config.getTmpDir());
		URL url=new URL(source);
		boolean isHttps="https".equals(url.getProtocol());
		String server=url.getHost();
		int port=url.getPort();
		if(port<=0){
			if(isHttps){
				port=443;
			}else{
				port=80;
			}
		}
		WebClientConnection connection=WebClientConnection.create(isHttps,server,port);
		WebClientHandler clientHandler=WebClientHandler.create(connection);
		HeaderParser requestHeader=(HeaderParser) PoolManager.getInstance(HeaderParser.class);
		requestHeader.setMethod("GET");
		requestHeader.setPath(url.getFile());
		requestHeader.setQuery(url.getQuery());
		requestHeader.setReqHttpVersion(HeaderParser.HTTP_VESION_11);
		requestHeader.setHeader("User-Agent", "Mozilla/4.0");
		String requestLine=connection.getRequestLine(requestHeader);
		ByteBuffer[] requestHeaderBuffer=connection.getRequestHeaderBuffer(requestLine,requestHeader, false);
		requestHeader.unref(true);
		clientHandler.startRequest(this, null, 1000, requestHeaderBuffer,0, false,0);
	}
	public void onWrittenRequestBody(Object userContext) {
	}

	public void onWrittenRequestHeader(Object userContext) {
	}

	public void onResponseHeader(Object userContext, HeaderParser responseHeader) {
		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(listFile,"rwd");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
		writeChannel=raf.getChannel();
	}
	public void onResponseBody(Object userContext, ByteBuffer[] buffer) {
		try {
			writeChannel.write(buffer);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}
	public void onRequestEnd(Object userContext,int stat) {
		try {
			writeChannel.close();
			Maintenance.addCategorys(source, listFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void onRequestFailure(Object userContext,int stat, Throwable t) {
	}
	public void onTimer(Object userContext) {
		try {
			Maintenance.addCategorys(source, new File(source));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void onWebConnected(Object userContext){
	}
	public void onWebHandshaked(Object userContext){
	}
	public void onWebProxyConnected(Object userContext) {
	}

}
