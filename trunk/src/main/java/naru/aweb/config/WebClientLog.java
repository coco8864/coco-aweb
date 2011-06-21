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
	private long handshakeRawRead;
	
	@Persistent
	@Column(name="HANDSHAKE_RAW_WRITE",defaultValue="-1")
	private long handshakeRawWrite;
	
	@Persistent
	@Column(name="REQUEST_HEADER_RAW",defaultValue="-1")
	private long requestHeaderRaw;
	
	@Persistent
	@Column(name="REQUEST_BODY_RAW",defaultValue="-1")
	private long requestBodyRaw;
	
	@Persistent
	@Column(name="RESPONSE_HEADER_RAW",defaultValue="-1")
	private long responseHeaderRaw;
	
	@Persistent
	@Column(name="RESPONSE_BODY_RAW",defaultValue="-1")
	private long responseBodyRaw;

	@Persistent
	@Column(name="START_TIME",defaultValue="-1")
	private long startTime;//äÓèÄ
	
	@Persistent
	@Column(name="CONNECT_END_TIME",defaultValue="-1")
	private long connectEndTime;
	
	@Persistent
	@Column(name="HANDSHAKE_START_TIME",defaultValue="-1")
	private long handshakeStartTime;
	
	@Persistent
	@Column(name="HANDSHAKE_END_TIME",defaultValue="-1")
	private long handshakeEndTime;
	
	@Persistent
	@Column(name="REQUEST_HEADER_START_TIME",defaultValue="-1")
	private long requestHeaderStartTime;
	
	@Persistent
	@Column(name="REQUEST_HEADER_END_TIME",defaultValue="-1")
	private long requestHeaderEndTime;
	
	@Persistent
	@Column(name="REQUEST_BODY_START_TIME",defaultValue="-1")
	private long requestBodyStartTime;
	
	@Persistent
	@Column(name="REQUEST_BODY_END_TIME",defaultValue="-1")
	private long requestBodyEndTime;
	
	@Persistent
	@Column(name="RESPONSE_HEADER_START_TIME",defaultValue="-1")
	private long responseHeaderStartTime;
	
	@Persistent
	@Column(name="RESPONSE_HEADER_END_TIME",defaultValue="-1")
	private long responseHeaderEndTime;
	
	@Persistent
	@Column(name="RESPONSE_BODY_START_TIME",defaultValue="-1")
	private long responseBodyStartTime;
	
	@Persistent
	@Column(name="RESPONSE_BODY_END_TIME",defaultValue="-1")
	private long responseBodyEndTime;
	
}
