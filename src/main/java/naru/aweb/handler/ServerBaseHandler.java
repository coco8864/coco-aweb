package naru.aweb.handler;

import java.util.Iterator;

import javax.net.ssl.SSLEngine;

import org.apache.log4j.Logger;

import naru.async.ChannelHandler;
import naru.async.pool.PoolManager;
import naru.async.ssl.SslHandler;
import naru.aweb.auth.AuthSession;
import naru.aweb.auth.User;
import naru.aweb.config.AccessLog;
import naru.aweb.config.Config;
import naru.aweb.http.GzipContext;
import naru.aweb.http.RequestContext;
import naru.aweb.mapping.Mapping;
import naru.aweb.mapping.MappingResult;
import naru.aweb.spdy.SpdySession;
import naru.aweb.util.HeaderParser;
import naru.aweb.util.ParameterParser;
import naru.aweb.util.ServerParser;

/**
 * ���N�G�X�g�P�ʂɍ쐬�����handler�̊��N���X<br/>
 * �e�X�R�[�v�ϐ��ւ̃A�N�Z�X���@��񋟂���B<br/>
 * ���ڌp�����ė��p���Ȃ��B<br/>
 * handler�I�u�W�F�N�g�͍ė��p�����B�ė��p�����ۂɂ́Arecycle���\�b�h�Œʒm�����B
 * @author naru
 *
 */
public abstract class ServerBaseHandler extends SslHandler {
	private static Logger logger = Logger.getLogger(ServerBaseHandler.class);
	private static Config config=Config.getConfig();
	
	public static final String ATTRIBUTE_RESPONSE_STATUS_CODE="responseStatusCode";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_TYPE="responseContentType";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_DISPOSITION = "reponseContentDisposition";
	public static final String ATTRIBUTE_RESPONSE_FILE = "responseFile";
	public static final String ATTRIBUTE_RESPONSE_CONTENT_LENGTH = "responseContentLength";
	public static final String ATTRIBUTE_STORE_OFFSET = "responseOffset";
	//cache���g��Ȃ��ꍇ�ɐݒ�
	public static final String ATTRIBUTE_RESPONSE_FILE_NOT_USE_CACHE = "responseFileNotUseCache";
	
	public static final String ATTRIBUTE_VELOCITY_TEMPLATE="velocityTemplate";
	public static final String ATTRIBUTE_VELOCITY_REPOSITORY="velocityRepository";
	public static final String ATTRIBUTE_VELOCITY_ENGINE="velocityEngine";
	public static final String ATTRIBUTE_KEEPALIVE_CONTEXT=KeepAliveContext.class.getName();
	public static final String ATTRIBUTE_SPDY_SESSION=SpdySession.class.getName();
	public static final String ATTRIBUTE_USER=User.class.getName();
	
	public enum SCOPE{
		/**
		 * handler�X�R�[�v
		 */
		HANDLER,
		/**
		 * ���N�G�X�g�X�R�[�v
		 */
		REQUEST,
		/**
		 * keepAlive�X�R�[�v<br/>
		 * ����Socket�͈̔�,spdy�ʐM�̏ꍇ��REQUEST�Ɠ��`
		 */
		KEEP_ALIVE,
		/**
		 * SessionStorage�X�R�[�v<br/>
		 * ����session�ł��u���E�U���قȂ�ƕʂ̃X�R�[�v�ƂȂ�BLink�̋@�\�Ƃ��Ē񋟁B<br/>
		 */
		BROWSER,/* ���N�G�X�g���ׂĂ�bid��U��K�v������Alink�̋@�\�Ƃ��Ē񋟁Aaweb�̋@�\�Ƃ��Ă͒񋟂��Ȃ� */
		/**
		 * session�X�R�[�v<br/>
		 * ���O�C���A�P��A�v���P�ʂ͈̔�<br/>
		 */
		SESSION,
		/**
		 * authSession�X�R�[�v<br/>
		 * ���O�C���A�����A�v���P�ʂ͈̔�<br/>
		 */
		AUTH_SESSION,
		/**
		 * mapping�X�R�[�v<br/>
		 * mapping��`�͈̔�<br/>
		 */
		MAPPING,/* mapping��`�P�ʂ܂�Aapplication�X�R�[�v */
		/**
		 * application�X�R�[�v<br/>
		 * �A�v���P�[�V�����Ƃ��ĈӖ��̂��镡����mapping��`�͈̔́Aservlet�A�v���P�[�V�����Ƃ��ẮAapplication�X�R�[�v����<br/>
		 * ������<br/>
		 */
		APPLICATION,
		/**
		 * config�X�R�[�v<br/>
		 * phantom server�͈́@�ݒ���<br/>
		 */
		CONFIG
	}
	
	/**
	 * ��handler�ɏ����������p���܂��B<br/>
	 */
	@Override
	public ChannelHandler forwardHandler(SslHandler handler) {
		SpdySession spdySession=getSpdySession();
		if(spdySession!=null){
			spdySession.setServerHandler((ServerBaseHandler)handler);
		}
		return super.forwardHandler(handler);
	}
	
	/**
	 * ���Y���N�G�X�g��requestContext���擾���܂��B<br/>
	 */
	public RequestContext getRequestContext(){
		return getRequestContext(false);
	}
	public RequestContext getRequestContext(boolean isNew){
		KeepAliveContext keepAliveContext=getKeepAliveContext();
		if(keepAliveContext==null){
			return null;
		}
		RequestContext requestContext=keepAliveContext.getRequestContext(isNew);
		return requestContext;
	}
	
	/**
	 * ���Y���N�G�X�g��accessLog�I�u�W�F�N�g���擾���܂��B<br/>
	 */
	public AccessLog getAccessLog(){
		return getRequestContext().getAccessLog();
	}
	
	/**
	 * ���Y���N�G�X�g��requestHeader�I�u�W�F�N�g���擾���܂��B<br/>
	 */
	public HeaderParser getRequestHeader(){
		RequestContext requestContext=getRequestContext();
		if(requestContext==null){
			logger.warn("getRequestHeader requestContext is null.",new Throwable());
			return null;
		}
		return requestContext.getRequestHeader();
	}
	
	/**
	 * ���A�v���P�[�V�����Ƃ����L����session�I�u�W�F�N�g���擾���܂��B<br/>
	 */
	private AuthSession getRootAuthSession(){
		AuthSession session=getAuthSession();
		if(session==null){
			return null;
		}
		return session.getSessionId().getPrimaryId().getAuthSession();
	}
	
	/**
	 * �e�X�R�[�v��key&value�`���Œl��ݒ肵�܂��B<br/>
	 * HANDLER,REQUEST,SESSION,AUTH_SESSION,MAPPING,CONFIG���T�|�[�g<br/>
	 * @param scope�@�X�R�[�v
	 * @param name �L�[��
	 * @param value �l
	 */
	public void setAttribute(SCOPE scope,String name,Object value){
		AuthSession session=null;
		switch(scope){
		case HANDLER:
			setAttribute(name, value);
			break;
		case REQUEST:
			RequestContext requestContext=getRequestContext();
			if(requestContext!=null){
				requestContext.setAttribute(name, value);
			}
			break;
		case SESSION:
			session=getAuthSession();
			if(session!=null){
				session.setAttribute(name, value);
			}
			break;
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				session.setAttribute(name, value);
			}
			break;
		case MAPPING:
			MappingResult mappingResult=getRequestMapping();
			Mapping mapping=mappingResult.getMapping();
			mapping.setAttribute(name,value);
			break;
		case CONFIG:
			config.setProperty(name, value);
			break;
		default:
			throw new IllegalArgumentException("not supoert scope:"+scope);
		}
	}
	
	/**
	 * �e�X�R�[�v�ɐݒ肳�ꂽ�l���擾���܂��B<br/>
	 * HANDLER,REQUEST,SESSION,AUTH_SESSION,MAPPING,CONFIG���T�|�[�g<br/>
	 * @param scope�@�X�R�[�v
	 * @param name �L�[��
	 * @return �l
	 */
	public Object getAttribute(SCOPE scope,String name){
		AuthSession session=null;
		switch(scope){
		case HANDLER:
			return getAttribute(name);
		case REQUEST:
			RequestContext requestContext=getRequestContext();
			if(requestContext!=null){
				return requestContext.getAttribute(name);
			}
			return null;
		case SESSION:
			session=getAuthSession();
			if(session!=null){
				return session.getAttribute(name);
			}
			return null;
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				return session.getAttribute(name);
			}
			return null;
		case MAPPING:
			MappingResult mappingResult=getRequestMapping();
			Mapping mapping=mappingResult.getMapping();
			return mapping.getAttribute(name);
		case CONFIG:
			return config.getProperty(name);
		default:
			throw new IllegalArgumentException("not supoert scope:"+scope);
		}
	}
	
	/**
	 * �e�X�R�[�v�ɐݒ肳��Ă�����̃L�[���̈ꗗ���擾���܂��B<br/>
	 * HANDLER,REQUEST,SESSION,AUTH_SESSION,MAPPING,CONFIG���T�|�[�g<br/>
	 * @param scope�@�X�R�[�v
	 * @return�@�L�[���ꗗ
	 */
	public Iterator<String> getAttributeNames(SCOPE scope){
		switch(scope){
		case HANDLER:
			throw new UnsupportedOperationException("handler scope getAttrNames");
		case REQUEST:
			RequestContext requestContext=getRequestContext();
			if(requestContext!=null){
				return requestContext.getAttributeNames();
			}
			return null;
		case SESSION:
			AuthSession session=getAuthSession();
			if(session!=null){
				return session.getAttributeNames();
			}
			return null;
		case AUTH_SESSION:
			session=getRootAuthSession();
			if(session!=null){
				return session.getAttributeNames();
			}
			return null;
		case MAPPING:
			MappingResult mappingResult=getRequestMapping();
			Mapping mapping=mappingResult.getMapping();
			return mapping.getAttributeNames();
		case CONFIG:
			return config.getConfiguration(null).getKeys();
		default:
			throw new IllegalArgumentException("not supoert scope:"+scope);
		}
	}
	
	/**
	 * ���Y�n���h���[��keepAliveContext���擾���܂��B<br/>
	 * ����socket�̃n���h���[�͓����context��ԋp���܂��B�������Aspdy����̏ꍇ��request�P�ʂƂȂ�܂��B<br/>
	 * @return�@keepAliveContext
	 */
	public KeepAliveContext getKeepAliveContext(){
		return getKeepAliveContext(false);
	}

	protected KeepAliveContext getKeepAliveContext(boolean isCreate){
		if(getChannelId()<0){//ChannelContext�����Ȃ��ꍇ
			return null;
		}
		KeepAliveContext keepAliveContext=(KeepAliveContext)getChannelAttribute(ATTRIBUTE_KEEPALIVE_CONTEXT);
		if(isCreate && keepAliveContext==null){
			keepAliveContext=(KeepAliveContext)PoolManager.getInstance(KeepAliveContext.class);
			keepAliveContext.setAcceptServer(ServerParser.create(getLocalIp(), getLocalPort()));
			setKeepAliveContext(keepAliveContext);
		}
		return keepAliveContext;
	}
	
	/**
	 * �A�v���P�[�V�����͎g�p���Ȃ�
	 * @param keepAliveContext
	 */
	public void setKeepAliveContext(KeepAliveContext keepAliveContext){
		endowChannelAttribute(ATTRIBUTE_KEEPALIVE_CONTEXT,keepAliveContext);
	}
	
	/**
	 * ���Y���N�G�X�g�̃p�����^���擾���܂��B<br/>
	 * parameter�́Akey&value�`�������łȂ��A�b�v���[�h���ꂽ�t�@�C����JSON�I�u�W�F�N�g�`���Ŏ擾�ł��܂��B
	 * @return parameter
	 */
	public ParameterParser getParameterParser() {
		return getRequestContext().getParameterParser();
	}
	
	protected GzipContext getGzipContext() {
		return getRequestContext().getGzipContext();
	}
	protected void setGzipContext(GzipContext gzipContext) {
		getRequestContext().setGzipContext(gzipContext);
	}
	
	/**
	 * ���Yrequest�̃}�b�s���O���(�ǂ�mapping��`�Ń��N�G�X�g���ꂽ��?)���擾���܂��B
	 * @return mapping���
	 */
	public MappingResult getRequestMapping() {
		return getRequestContext().getMapping();
	}
	protected void setRequestMapping(MappingResult mapping) {
		getRequestContext().setMapping(mapping);
	}
	
	/**
	 * ���Y�Z�V���������擾���܂��B<br/>
	 * @return�@�Z�V�������
	 */
	public AuthSession getAuthSession(){
		RequestContext requestContext=getRequestContext();
		if(requestContext==null){
			return null;
		}
		return requestContext.getAuthSession();
	}
	
	/**
	 * ���Yhandler�ŏ��u���ɉ����������ꂽ�ꍇ�ɒʒm����܂��B<br/>
	 * override����ꍇ�́A�����\�b�h���Ăяo���Ă��������B<br/>
	 */
	public void onFinished() {
		if(logger.isDebugEnabled())logger.debug("#finished.cid:"+getChannelId());
		super.onFinished();
	}
	
	/* DsipatchHandler��override���鑼�n���h���͕K�v�Ȃ� */
	/**
	 * ���p�s��
	 */
	@Override
	public SSLEngine getSSLEngine() {
		return null;
	}

	/**
	 * spdy�ʐM���Ă���ꍇ�Aspdy�Z�V���������擾���܂��B<br/>
	 * @return�@spdy�Z�V�������
	 */
	public SpdySession getSpdySession() {
		return (SpdySession)getAttribute(SCOPE.REQUEST, ATTRIBUTE_SPDY_SESSION);
	}
	
	/**
	 * ���Y���N�G�X�g��SSL�ʐM�𗘗p���Ă��邩�ۂ����擾���܂��B<br/>
	 */
	@Override
	public boolean isSsl() {
		if(getSpdySession()!=null){
			return true;
		}
		return	super.isSsl();
	}
	
}
