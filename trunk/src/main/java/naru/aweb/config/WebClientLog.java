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
	@Column(name="START_TIME",defaultValue="-1")
	private long startTime;//äÓèÄ
	
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
	
}
