package naru.aweb.http;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import naru.async.pool.PoolBase;
import naru.async.pool.PoolManager;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.LogoutEvent;
import naru.aweb.config.AccessLog;
import naru.aweb.handler.KeepAliveContext;
import naru.aweb.mapping.MappingResult;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;
/**
 * 
 * @author Naru
 *
 */
public class RequestContext extends PoolBase {
	private static Logger logger = Logger.getLogger(RequestContext.class);
	
	private KeepAliveContext keepAliveContext;
	private AccessLog accessLog;
	private MappingResult mapping;
//	private ChunkContext chunkContext;//�g��Ȃ�
	private ChunkContext requestChunkContext=new ChunkContext();
	private GzipContext gzipContext;//�K�v�ȏꍇ�ɐݒ�
	private AuthSession authSession;
	private HeaderParser requestHeader=new HeaderParser();
	private ParameterParser parameterParser=new ParameterParser();
	private LogoutEvent logountEvent=null;
	
	private Map<String,Object> attribute=new HashMap<String,Object>();//request�ɕt�����鑮��
	
	public void recycle() {
		setKeepAliveContext(null);
		if(mapping!=null){
			mapping.unref();
			mapping=null;
		}
		if(accessLog!=null){
			accessLog.decTrace();//RequestContext����̎Q�Ƃ�؂�
			accessLog=null;
		}
		if(gzipContext!=null){
			gzipContext.unref();
			gzipContext=null;
		}
		requestHeader.recycle();
		parameterParser.recycle();
		Iterator itr=attribute.values().iterator();
		while(itr.hasNext()){
			Object v=itr.next();
			if(v instanceof PoolBase){
				((PoolBase)v).unref();
			}
		}
		attribute.clear();
		if(authSession!=null){
			if(logountEvent!=null){
				authSession.removeLogoutEvent(logountEvent);
			}
			authSession.unref();//requestContext����|�C���g����Ă���Ԃ͊J������Ȃ�
			authSession=null;
		}
		logountEvent=null;		
		super.recycle();
	}

	public KeepAliveContext getKeepAliveContext() {
		return keepAliveContext;
	}
	
	/*
	 * �g���ĂȂ��H
	 * @param keepAliveContext
	 */
	public void setKeepAliveContext(KeepAliveContext keepAliveContext) {
		if(keepAliveContext!=null){
			keepAliveContext.ref();
		}
		if(this.keepAliveContext!=null){
			this.keepAliveContext.unref();
		}
		this.keepAliveContext = keepAliveContext;
	}
	
	
	public AuthSession getAuthSession() {
		return authSession;
	}
	//authSession��ref�J�E���^��DispatchHandler�ŁA���F�،n�̃��C�t�T�C�N���ɂ��킹�ăJ�E���g�A�b�v����Ă���
	public void registerAuthSession(AuthSession authSession) {
		this.authSession = authSession;
	}

	/**
	 * ���N�G�X�g���I������Ǝ����I��event����������
	 * @param event
	 * @return
	 */
	public boolean registerLogoutEvnet(LogoutEvent event){
		if(authSession==null){
			return false;
		}
		if(!authSession.addLogoutEvent(event)){
			return false;
		}
		logountEvent=event;
		return true;
	}
	
	public AccessLog allocAccessLog() {
		accessLog=(AccessLog)PoolManager.getInstance(AccessLog.class);
		return accessLog;
//		logger.info("$$$"+accessLog,new Exception());
	}

	public AccessLog getAccessLog() {
		return accessLog;
	}

	public HeaderParser getRequestHeader() {
		return requestHeader;
	}

	public ParameterParser getParameterParser() {
		return parameterParser;
	}

	public GzipContext getGzipContext() {
		return gzipContext;
	}

	public void setGzipContext(GzipContext gzipContext) {
		this.gzipContext = gzipContext;
	}

	public MappingResult getMapping() {
		return mapping;
	}

	public void setMapping(MappingResult mapping) {
		this.mapping = mapping;
	}
	
	public Object getAttribute(String name){
		return attribute.get(name);
	}
	
	public void setAttribute(String name, Object value) {
		if(value instanceof PoolBase){
			((PoolBase)value).ref();
		}
		attribute.put(name, value);
	}
	
	public Iterator<String> getAttributeNames(){
		return attribute.keySet().iterator();
	}

	public ChunkContext getRequestChunkContext() {
		return requestChunkContext;
	}
}
