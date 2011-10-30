package naru.aweb.config;

import java.util.Arrays;

import naru.async.pool.PoolBase;
import naru.aweb.http.HeaderParser;

public class WebClientLog extends PoolBase {
	public static final int CHECK_POINT_START=0;
	public static final int CHECK_POINT_CONNECT=1;//startからconnect完了まで
	public static final int CHECK_POINT_SSL_PROXY=2;//sslProxy送信開始から、response受信まで
	public static final int CHECK_POINT_HANDSHAKE=3;//handshake開始からhandshake完了まで
	public static final int CHECK_POINT_REQUEST_HEADER=4;//requestHeader送信開始から、requestHeader送信完了通知まで
	public static final int CHECK_POINT_REQUEST_BODY=5;//requestBody送信開始から、requestBody送信完了通知まで
	public static final int CHECK_POINT_RESPONSE_HEADER=6;//requestBodyを送信完了から,responseHeaderを受信完了まで
	public static final int CHECK_POINT_RESPONSE_BODY=7;//responseHeaderを受信完了から、responseBodyを受信完了まで
	public static final int CHECK_POINT_END=8;
	public static final int CHECK_POINT_NUM=9;

	private int checkPoint;
	private String statusLine;
	private String serverHeader;
	private String connectionHeader;
	private String proxyConnectionHeader;
	private String keepAliveHeader;
	
	private long readLengths[]=new long[CHECK_POINT_NUM];
	private long writeLengths[]=new long[CHECK_POINT_NUM];
	private long processTimes[]=new long[CHECK_POINT_NUM];

	@Override
	public void recycle() {
		checkPoint=CHECK_POINT_START;
		statusLine=serverHeader=proxyConnectionHeader=keepAliveHeader=null;
		Arrays.fill(readLengths, -1L);
		Arrays.fill(writeLengths, -1L);
		Arrays.fill(processTimes, -1L);
	}

	private long lastData(long[] datas,int index){
		for(int i=index;i>=CHECK_POINT_START;i--){
			if(datas[i]>=0){
				return datas[i];
			}
		}
		return 0;
	}
	public void checkPoing(int checkPoint,long readLength,long writeLength){
		checkPoing(checkPoint, readLength, writeLength,-1L);
	}
	
	public void checkPoing(int checkPoint,long readLength,long writeLength,long inTime){
//		System.out.println("checkPoint:"+checkPoint +":" +inTime);
		long now=System.currentTimeMillis();
		if(checkPoint==CHECK_POINT_START){
			processTimes[checkPoint]=now;
			readLengths[checkPoint]=readLength;
			writeLengths[checkPoint]=writeLength;
		}else{
			readLengths[checkPoint]=readLength-lastData(readLengths,checkPoint-1);
			writeLengths[checkPoint]=writeLength-lastData(writeLengths,checkPoint-1);
			if(inTime>=0){
				processTimes[checkPoint]=now-inTime;
			}else{
				processTimes[checkPoint]=now-lastData(processTimes,checkPoint);
			}
		}
		processTimes[checkPoint+1]=now;//CHECK_POINT_ENDで呼ばれる事はない
		synchronized(this){
			this.checkPoint=checkPoint;
			notify();
		}
	}
	
	public long getReadLength(int checkPoing){
		return readLengths[checkPoing];
	}
	public long getWriteLength(int checkPoing){
		return writeLengths[checkPoing];
	}
	public long getProcessTime(int checkPoing){
		if(readLengths[checkPoing]<0){
			return -1;
		}
		return processTimes[checkPoing];
	}

	public void responseHeader(HeaderParser responseHeader) {
		statusLine=responseHeader.getStatusLine();
		serverHeader=responseHeader.getHeader(HeaderParser.SERVER_HEADER);
		connectionHeader=responseHeader.getHeader(HeaderParser.CONNECTION_HEADER);
		proxyConnectionHeader=responseHeader.getHeader(HeaderParser.PROXY_CONNECTION_HEADER);
		keepAliveHeader=responseHeader.getHeader(HeaderParser.KEEP_ALIVE_HEADER);
	}

	public int getCheckPoint() {
		return checkPoint;
	}
	
	public String toString(){
		StringBuilder sb=new StringBuilder();
		sb.append("statusLine:");
		sb.append(statusLine);
		sb.append("\n");
		sb.append("serverHeader:");
		sb.append(serverHeader);
		sb.append("\n");
		sb.append("connectionHeader:");
		sb.append(connectionHeader);
		sb.append("\n");
		sb.append("proxyConnectionHeader:");
		sb.append(proxyConnectionHeader);
		sb.append("\n");
		sb.append("keepAliveHeader:");
		sb.append(keepAliveHeader);
		sb.append("\n");
		for(int i=0;i<CHECK_POINT_NUM;i++){
			sb.append(i);
			sb.append(":");
			sb.append(getReadLength(i));
			sb.append(":");
			sb.append(getWriteLength(i));
			sb.append(":");
			sb.append(getProcessTime(i));
			sb.append("\n");
		}
		return sb.toString();
	}
	
	public String getStatusLine() {
		return statusLine;
	}

	public String getServerHeader() {
		return serverHeader;
	}

	public String getConnectionHeader() {
		return connectionHeader;
	}

	public String getProxyConnectionHeader() {
		return proxyConnectionHeader;
	}

	public String getKeepAliveHeader() {
		return keepAliveHeader;
	}
}
