if window.ph.auth2
  return
window.ph.auth2={
  _authFrameName:'__phAuthFrame2'
  _authEachFrameName:'__phEachAuthFrame_'
  _checkAplQuery:'?__PH_AUTH__=__CD_CHECK__'
  _checkWsAplQuery:'?__PH_AUTH__=__CD_WS_CHECK__'
  _urlPtn:/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
  _aplAuths:{}
  _auths:{}
##/* �Ăяo����queue���ď�����������d�g�� */
  _callQueue:[]
  _aplAuth:null
  _reqUrl:null
  _reqCb:null
  _timerId:null
  _finAuth:(res)->
    aplAuth=@_aplAuth
    if res.result
      aplAuth._setup(res)
    else
      aplAuth.trigger('auth',res)
      @_aplAuth=null
      @_call()
  _callRequest:(url,urlCb,aplAuth)->
    req={url:url,urlCb:urlCb,aplAuth:aplAuth}
    @_callQueue.push(req)
    @_call()
  _call:->
    if @_aplAuth!=null||@_callQueue.length==0
      return
    req=@_callQueue.pop()
    @_aplAuth=req.aplAuth
    @_reqestUrl(req.url,req.urlCb)
  _reqestUrl:(url,cb)->
##//ph.log("_reqestUrl:"+url +":"+(new Date()).getTime())
    @_reqUrl=url
    @_reqCb=cb
    ph.auth2._frame[0].src=url
  _reqestCallback:->
    reqestCb=@_reqCb
    res=@_res
    @_reqUrl=null
    @_reqCb=null
    @_res=null
    reqestCb(res)
  _frameLoad:(x)=> #�K�v�Ȃ�
    ph.log('_frameLoad:'+(new Date()).getTime())
    if ph.auth2._res
      @_reqestCallback()
      return
    if !ph.auth2._reqCb
      return
    @_timerId=setTimeout(@_frameLoad2,1000)
  _frameLoad2:=> #�K�v�Ȃ�
    if ph.auth2._res
      @_timerId=null
      @_reqestCallback()
      return
    reqestCb=@_reqCb
    if reqestCb!=null ##frame��load���ꂽ��message�����Ȃ�
      ph.log("_frameLoad timeout:"+this._reqUrl)
      @_reqCb=null
      @_reqUrl=null
      reqestCb({result:false,reason:'url error'})
  _init:->
    ph.auth2._frame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' + ph.auth2._authFrameName + '" ></iframe>')
##    ph.auth2._frame.load(ph.auth2._frameLoad)
    ph.jQuery("body").append(ph.auth2._frame)
  _onMessage:(ev)->
    if !ph.auth2._frame || ev.source!=ph.auth2._frame[0].contentWindow
      return
    url=ph.auth2._reqUrl
    if !url
      return
    if url.lastIndexOf(ev.origin, 0)!=0
      return
    ph.auth2._res=ph.JSON.parse(ev.data)
    ph.auth2._reqestCallback()
  _authCheckCb:(res)->
    if res.result=='redirect'
##      /* authUrl��session��₢���킹 */
      ph.auth2._reqestUrl(res.location,ph.auth2._authCheckCb)
    else if res.result=='redirectForAuthorizer'
      window.location.href=res.location
    else
      ph.auth2._finAuth(res)
  _aplCheckCb:(res)->
    if res.result=='redirect'
##    /* authUrl��session��₢���킹 */
      redirectUrl=res.location+'&originUrl='+encodeURIComponent(window.location.href)
      ph.auth2._reqestUrl(redirectUrl,ph.auth2._authCheckCb);
    else
      ph.auth2._finAuth(res)
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
    aplAuth=@_aplAuths[checkAplUrl]
    if aplAuth
      return aplAuth
    aplAuth=new AplAuth(checkAplUrl)
    if cb
      aplAuth.on('auth',cb)
    @_aplAuths[checkAplUrl]=aplAuth
    @_callRequest(checkAplUrl,@_aplCheckCb,aplAuth)
    prm
}

#auth�h���C���Ƃ̒ʐM�p
if window.addEventListener
  window.addEventListener('message',ph.auth2._onMessage, false)
else if window.attachEvent
  window.attachEvent('onmessage',ph.auth2._onMessage)
ph.jQuery(ph.auth2._init);

#-------------------EventModule-------------------
class EventModule
  constructor:->
    @_callback ={}
  on: (name, callback) ->
    if !@_callback[name]? then @_callback[name]=[]
    @_callback[name].push(callback)
    @
  trigger: (name,args...) ->
    list = @_callback[name]
    return @ unless list
    for callback in list
      callback.apply(@,args)
    @

#-------------------Apl Auth-------------------
class AplAuth extends EventModule
  _auth:null
  constructor: (@checkAplUrl) ->
    super
  _setup:(res)->
    @_auth=ph.auth2._auths[res.authUrl]
    if @_auth
      @trigger('auth',@)
      return
    @_auth=new Auth(res.authUrl)
    @_auth._setup(res)
  info:->
    @_auth.info(@)
  encrypt:(loginid,plainText)->
    @_auth.encrypt(loginid,plainText,@)
  decrypt:(loginid,encryptText)->
    @_auth.decrypt(loginid,encryptText,@)

#-------------------Auth-------------------
class Auth
  constructor: (@authUrl) ->
    super
  _setup:(res,@_aplAuth)->
    @loginId=res.loginId
    @authUrl=res.authUrl
    @appSid=res.appSid
    @token=res.token
    @_frame=ph.jQuery(
      "<iframe width='0' height='0' frameborder='no' " +
      "name='#{ph.auth2._authEachFrameName}#{@authUrl}'" +
      "src='#{@authUrl}/info' ></iframe>")
    @_frame.load(@_frameLoad)
    ph.jQuery("body").append(@_frame)
  _frameLoad:=>
    ph.auth2._aplAuth=null
    ph.auth2._call()
    @authUrl.trigger('auth',@)
    @authUrl=null
    ph.auth2._auths[@authUrl]=@
  _onMessage:->
  info:(@_aplAuth)->
  encrypt:(loginid,plainText,@_aplAuth)->
  decrypt:(loginid,encryptText,@_aplAuth)->

