if window.ph.auth
  return

class PhAuth
  _authFrameName:'__phAuthFrame'
  _authEachFrameName:'__phEachAuthFrame_'
  _infoPath:'/info'
  _checkAplQuery:'?__PH_AUTH__=__CD_CHECK__'
  _checkWsAplQuery:'?__PH_AUTH__=__CD_WS_CHECK__'
  _urlPtn:/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
  _auths:{}
##/* �Ăяo����queue���ď�����������d�g�� */
  _authQueue:[]
  _processAuth:null #�F�؏��������ۂ�
  _reqUrl:null
  _reqCb:null
  _finAuth:(res)->
    auth=@_processAuth
    auth.result=res.result
    if res.result
      auth._setup(res)
    else
      auth._deferred.reject(auth)
    return
  _alwaysAsyncAuth:(auth)=>
    auth.trigger('done',auth)
    @_processAuth=null
    @_call()
    return
  _callAsyncAuth:(auth)->
    @_authQueue.push(auth)
    @_call()
    return
  _call:->
    if @_processAuth!=null || @_authQueue.length==0
      return
    auth=@_authQueue.pop()
    @_processAuth=auth
    @_reqestUrl(auth._checkAplUrl,@_aplCheckCb)
  _reqestUrl:(url,cb)->
##//ph.log("_reqestUrl:"+url +":"+(new Date()).getTime())
    @_reqUrl=url
    @_reqCb=cb
    @_frame[0].src=url
    return
  _reqestCallback:(res)->
    reqestCb=@_reqCb
    @_reqUrl=null
    @_reqCb=null
    reqestCb(res)
    return
  _init:->
    @_frame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' + @_authFrameName + '" ></iframe>')
##    @_frame.load(@_frameLoad)
    ph.jQuery("body").append(@_frame)
    return
  _onMessage:(ev)=>
    if !@_frame || ev.source!=@_frame[0].contentWindow
      return
    url=@_reqUrl
    if !url
      return
    if url.lastIndexOf(ev.origin, 0)!=0
      return
    res=ph.JSON.parse(ev.data)
    @_reqestCallback(res)
    return
  _authCheckCb:(res)=>
    if res.result=='redirect'
##      /* authUrl��session��₢���킹 */
      @_reqestUrl(res.location,@_authCheckCb)
    else if res.result=='redirectForAuthorizer'
      window.location.href=res.location
    else
      @_finAuth(res)
    return
  _aplCheckCb:(res)=>
    if res.result=='redirect'
##    /* authUrl��session��₢���킹 */
      redirectUrl=res.location+'&originUrl='+encodeURIComponent(window.location.href)
      @_reqestUrl(redirectUrl,@_authCheckCb);
    else
      @_finAuth(res)
    return

#----------auth outer api----------
#  /*authUrl�ŗL��appSid���擾����A�ȉ��̃p�^�[��������
#  1)secondary���ɔF����Ă���
#  2)primary�͔F�؍ς݂���secondary����=>�F����appSid���쐬
#  3)primary�͔F�ؖ�=>���̃��\�b�h�͕��A�����F�؉�ʂɑJ��
#  */
  auth:(aplUrl,cb)->
    aplUrl.match(@_urlPtn)
    protocol=RegExp.$1
    authDomain=RegExp.$2
    authPath=RegExp.$3
    checkAplUrl=null
    if protocol=='ws:'
      checkAplUrl='http://'+authDomain+authPath+@_checkWsAplQuery
    else if protocol=='wss:'
      checkAplUrl='https://'+authDomain+authPath+@_checkWsAplQuery
    else if protocol==null || protocol==''
      checkAplUrl=window.location.protocol+'//'+window.location.host+authPath+this._checkWsAplQuery
    else #http or https
      checkAplUrl=aplUrl+this._checkAplQuery
    authObj=@_auths[checkAplUrl]
    if authObj
      auth=authObj.promise
      if cb && auth.state()=='pending'
        auth.on('done',cb)
      else if cb
        cb(auth)
      return auth
    dfd=ph.jQuery.Deferred()
    auth=dfd.promise(new Auth(checkAplUrl,dfd))
    auth._checkAplUrl=checkAplUrl
    auth.on('done',cb)
    auth.always(@_alwaysAsyncAuth)
    @_auths[checkAplUrl]={deferred:dfd,promise:auth}
    @_callAsyncAuth(auth)
    auth
  info:(authUrl,cb)->
    if !authUrl #�w�肪�Ȃ���Ύ������_�E�����[�h����authUrl
      authUrl=ph.authUrl
    url=authUrl+@_infoPath;
    @_reqestUrl(url,cb);

window.ph.auth=new PhAuth()

#xhr�ʐM�p�̃C�x���g�o�^
ph.jQuery(->
  ph.auth._init()
  ph.event.on('message',ph.auth._onMessage)
)

#-------------------Auth-------------------
class Auth extends ph.EventModule
  constructor: (@_checkAplUrl,@_deferred) ->
    super
  _setup:(res)->
    @loginId=res.loginId
    @authUrl=res.authUrl
    @appSid=res.appSid
    @token=res.token
    @_frame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' + ph.auth._authEachFrameName + @_checkAplUrl + '" ></iframe>')
    @_frame.load(@_frameLoad)
    ph.jQuery("body").append(@_frame)
  _frameLoad:=>
    @_deferred.resolve(@)
  _onMessage:->
  info:(cb)->
  encrypt:(loginid,plainText,cb)->
  decrypt:(loginid,encryptText,cb)->
