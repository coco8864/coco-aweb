package naru.aweb.config;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Persistent;

import naru.async.pool.PoolBase;

public class WebClientLog extends PoolBase {
	//webClientÇ∆ÇµÇƒÇÃê´î\èÓïÒ
	@Persistent
	@Column(name="START_RAW_READ",defaultValue="-1")
	private long startRawRead;//äÓèÄ
	
	@Persistent
	@Column(name="START_RAW_WRITE",defaultValue="-1")
	private long startRawWrite;//äÓèÄ
	
	@Persistent
	@Column(name="HANDSHAKE_RAW_READ",defaultValue="-1")
	private int handshakeRawRead;
	
	@Persistent
	@Column(name="HANDSHAKE_RAW_WRITE",defaultValue="-1")
	private int handshakeRawWrite;
	
	@Persistent
	@Column(name="REQUEST_HEADER_RAW",defaultValue="-1")
	private int requestHeaderRaw;
	
	@Persistent
	@Column(name="REQUEST_BODY_RAW",defaultValue="-1")
	private int requestBodyRaw;
	
	@Persistent
	@Column(name="RESPONSE_HEADER_RAW",defaultValue="-1")
	private int responseHeaderRaw;
	
	@Persistent
	@Column(name="RESPONSE_BODY_RAW",defaultValue="-1")
	private int responseBodyRaw;
	
	@Persistent
	@Column(name="CONNECT_START_TIME",defaultValue="-1")
	private long connectStartTime;//äÓèÄ
	
	@Persistent
	@Column(name="CONNECT_END_TIME",defaultValue="-1")
	private int connectEndTime;
	
	@Persistent
	@Column(name="HANDSHAKE_START_TIME",defaultValue="-1")
	private int handshakeStartTime;
	
	@Persistent
	@Column(name="HANDSHAKE_END_TIME",defaultValue="-1")
	private int handshakeEndTime;
	
	@Persistent
	@Column(name="REQUEST_HEADER_START_TIME",defaultValue="-1")
	private int requestHeaderStartTime;
	
	@Persistent
	@Column(name="REQUEST_HEADER_END_TIME",defaultValue="-1")
	private int requestHeaderEndTime;
	
	@Persistent
	@Column(name="REQUEST_BODY_START_TIME",defaultValue="-1")
	private int requestBodyStartTime;
	
	@Persistent
	@Column(name="REQUEST_BODY_END_TIME",defaultValue="-1")
	private int requestBodyEndTime;
	
	@Persistent
	@Column(name="RESPONSE_HEADER_START_TIME",defaultValue="-1")
	private int responseHeaderStartTime;
	
	@Persistent
	@Column(name="RESPONSE_HEADER_END_TIME",defaultValue="-1")
	private int responseHeaderEndTime;
	
	@Persistent
	@Column(name="RESPONSE_BODY_START_TIME",defaultValue="-1")
	private int responseBodyStartTime;
	
	@Persistent
	@Column(name="RESPONSE_BODY_END_TIME",defaultValue="-1")
	private int responseBodyEndTime;
	
	/*--------------*/
	public long getStartRawRead() {
		return startRawRead;
	}

	public long getStartRawWrite() {
		return startRawWrite;
	}

	public int getHandshakeRawRead() {
		return handshakeRawRead;
	}

	public int getHandshakeRawWrite() {
		return handshakeRawWrite;
	}

	public int getRequestHeaderRaw() {
		return requestHeaderRaw;
	}

	public int getRequestBodyRaw() {
		return requestBodyRaw;
	}

	public int getResponseHeaderRaw() {
		return responseHeaderRaw;
	}

	public int getResponseBodyRaw() {
		return responseBodyRaw;
	}

	public long getConnectStartTime() {
		return connectStartTime;
	}

	public int getConnectEndTime() {
		return connectEndTime;
	}

	public int getHandshakeStartTime() {
		return handshakeStartTime;
	}

	public int getHandshakeEndTime() {
		return handshakeEndTime;
	}

	public int getRequestHeaderStartTime() {
		return requestHeaderStartTime;
	}

	public int getRequestHeaderEndTime() {
		return requestHeaderEndTime;
	}

	public int getRequestBodyStartTime() {
		return requestBodyStartTime;
	}

	public int getRequestBodyEndTime() {
		return requestBodyEndTime;
	}

	public int getResponseHeaderStartTime() {
		return responseHeaderStartTime;
	}

	public int getResponseHeaderEndTime() {
		return responseHeaderEndTime;
	}

	public int getResponseBodyStartTime() {
		return responseBodyStartTime;
	}

	public int getResponseBodyEndTime() {
		return responseBodyEndTime;
	}

	public void setStartRawRead(long startRawRead) {
		this.startRawRead = startRawRead;
	}

	public void setStartRawWrite(long startRawWrite) {
		this.startRawWrite = startRawWrite;
	}

	public void setHandshakeRawRead(int handshakeRawRead) {
		this.handshakeRawRead = handshakeRawRead;
	}

	public void setHandshakeRawWrite(int handshakeRawWrite) {
		this.handshakeRawWrite = handshakeRawWrite;
	}

	public void setRequestHeaderRaw(int requestHeaderRaw) {
		this.requestHeaderRaw = requestHeaderRaw;
	}

	public void setRequestBodyRaw(int requestBodyRaw) {
		this.requestBodyRaw = requestBodyRaw;
	}

	public void setResponseHeaderRaw(int responseHeaderRaw) {
		this.responseHeaderRaw = responseHeaderRaw;
	}

	public void setResponseBodyRaw(int responseBodyRaw) {
		this.responseBodyRaw = responseBodyRaw;
	}

	public void setConnectStartTime(long connectStartTime) {
		this.connectStartTime = connectStartTime;
	}

	public void setConnectEndTime(int connectEndTime) {
		this.connectEndTime = connectEndTime;
	}

	public void setHandshakeStartTime(int handshakeStartTime) {
		this.handshakeStartTime = handshakeStartTime;
	}

	public void setHandshakeEndTime(int handshakeEndTime) {
		this.handshakeEndTime = handshakeEndTime;
	}

	public void setRequestHeaderStartTime(int requestHeaderStartTime) {
		this.requestHeaderStartTime = requestHeaderStartTime;
	}

	public void setRequestHeaderEndTime(int requestHeaderEndTime) {
		this.requestHeaderEndTime = requestHeaderEndTime;
	}

	public void setRequestBodyStartTime(int requestBodyStartTime) {
		this.requestBodyStartTime = requestBodyStartTime;
	}

	public void setRequestBodyEndTime(int requestBodyEndTime) {
		this.requestBodyEndTime = requestBodyEndTime;
	}

	public void setResponseHeaderStartTime(int responseHeaderStartTime) {
		this.responseHeaderStartTime = responseHeaderStartTime;
	}

	public void setResponseHeaderEndTime(int responseHeaderEndTime) {
		this.responseHeaderEndTime = responseHeaderEndTime;
	}

	public void setResponseBodyStartTime(int responseBodyStartTime) {
		this.responseBodyStartTime = responseBodyStartTime;
	}

	public void setResponseBodyEndTime(int responseBodyEndTime) {
		this.responseBodyEndTime = responseBodyEndTime;
	}
}
