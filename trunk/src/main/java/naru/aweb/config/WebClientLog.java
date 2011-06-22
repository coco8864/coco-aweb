package naru.aweb.config;

import java.util.Arrays;

import naru.async.pool.PoolBase;

public class WebClientLog extends PoolBase {
	public static final int CHECK_POINT_START=0;
	public static final int CHECK_POINT_CONNECT=1;//startからconnect完了まで
	public static final int CHECK_POINT_SSL_PROXY=2;//sslProxy送信開始から、response受信まで
	public static final int CHECK_POINT_HANDSHAKE=3;//handshake開始からhandshake完了まで
	public static final int CHECK_POINT_REQUEST_HEADER=4;//requestHeader送信開始から、requestHeader送信完了通知まで
	public static final int CHECK_POINT_REQUEST_BODY=5;//requestBody送信開始から、requestBody送信完了通知まで
	public static final int CHECK_POINT_RESPONSE_HEADER=6;//requestBodyを送信完了から,responseHeaderを受信完了まで
	public static final int CHECK_POINT_RESPONSE_BODY=7;//responseHeaderを受信完了から、responseBodyを受信完了まで
	public static final int CHECK_POINT_END=8;//responseHeaderを受信完了から、responseBodyを受信完了まで
	public static final int CHECK_POINT_NUM=9;
	
	private long readLengths[]=new long[CHECK_POINT_NUM];
	private long writeLengths[]=new long[CHECK_POINT_NUM];
	private long processTimes[]=new long[CHECK_POINT_NUM];

	@Override
	public void recycle() {
		Arrays.fill(readLengths, -1L);
		Arrays.fill(writeLengths, -1L);
		Arrays.fill(processTimes, -1L);
	}

	private long lastData(long[] datas,int index){
		for(int i=index;i>=CHECK_POINT_START;i++){
			if(datas[i]>=0){
				return datas[i];
			}
		}
		return 0;
	}
	
	public void checkPoing(int checkPoint,long readLength,long writeLength){
		long now=System.currentTimeMillis();
		if(checkPoint==CHECK_POINT_START){
			processTimes[checkPoint]=now;
			readLengths[checkPoint]=readLength;
			writeLengths[checkPoint]=writeLength;
		}else{
			readLengths[checkPoint]=readLength-lastData(readLengths,checkPoint-1);
			writeLengths[checkPoint]=writeLength-lastData(writeLengths,checkPoint-1);
			if(processTimes[checkPoint]>=0){
				processTimes[checkPoint]=now-processTimes[checkPoint];
			}else{
				processTimes[checkPoint]=now-lastData(processTimes,checkPoint-1);
			}
		}
		processTimes[checkPoint+1]=now;//CHECK_POINT_ENDで呼ばれる事はない
	}
	public void setInTime(int checkPoint,long time){
		processTimes[checkPoint]=time;
	}
	
	public long getReadLength(int checkPoing){
		return readLengths[checkPoing];
	}
	public long getWriteLength(int checkPoing){
		return writeLengths[checkPoing];
	}
	public long getProcessTime(int checkPoing){
		return processTimes[checkPoing];
	}
}
