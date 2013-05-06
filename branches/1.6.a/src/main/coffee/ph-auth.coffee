if window.ph.auth2
  return
window.ph.auth2={
  _authFrameName:'__phAuthFrame2'
  _authEachFrameName:'__phEachAuthFrame_'
  _checkAplQuery:'?__PH_AUTH__=__CD_CHECK__'
  _checkWsAplQuery:'?__PH_AUTH__=__CD_WS_CHECK__'
  _urlPtn:/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
  _auths:{}
##/* 呼び出しをqueueして順次処理する仕組み */
  _callQueue:[]
  _auth:null
  _reqUrl:null
  _reqCb:null
  _timerId:null
  _finAuth:(res)->
    auth=@_auth
    if res.result
      auth._setup(res)
    else
      auth.reject('fail to auth')
      auth.trigger('auth',res)
      @_auth=null
      @_call()
  _callRequest:(url,urlCb,auth)->
    req={url:url,urlCb:urlCb,auth:auth}
    @_callQueue.push(req)
    @_call()
  _call:->
    if @_auth!=null||@_callQueue.length==0
      return
    req=@_callQueue.pop()
    @_auth=req.auth
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
  _frameLoad:(x)=>
##//alert('ph-auth _frameLoad res:' + ph.auth2._res +' cb:' + ph.auth2._reqCb +' url:' +ph.auth2._reqUrl)
    ph.log('_frameLoad:'+(new Date()).getTime())
    if ph.auth2._res
      @_reqestCallback()
      return
    if !ph.auth2._reqCb
      return
    @_timerId=setTimeout(@_frameLoad2,1000)
  _frameLoad2:=>
##//ph.log("_frameLoad2:"+(new Date()).getTime());
    if ph.auth2._res
      @_timerId=null
      @_reqestCallback()
      return
    reqestCb=@_reqCb
    if reqestCb!=null ##frameにloadされたがmessageが来ない
      ph.log("_frameLoad timeout:"+this._reqUrl)
      @_reqCb=null
      @_reqUrl=null
      reqestCb({result:false,reason:'url error'})
  _init:->
    ph.auth2._frame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' + ph.auth2._authFrameName + '" ></iframe>')
    ph.auth2._frame.load(ph.auth2._frameLoad)
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
##      /* authUrlにsessionを問い合わせ */
      ph.auth2._reqestUrl(res.location,ph.auth2._authCheckCb)
    else if res.result=='redirectForAuthorizer'
      window.location.href=res.location
    else
      ph.auth2._finAuth(res)
  _aplCheckCb:(res)->
    if res.result=='redirect'
##    /* authUrlにsessionを問い合わせ */
      redirectUrl=res.location+'&originUrl='+encodeURIComponent(window.location.href)
      ph.auth2._reqestUrl(redirectUrl,ph.auth2._authCheckCb);
    else
      ph.auth2._finAuth(res)
#----------auth outer api----------
#  /*authUrl固有のappSidを取得する、以下のパターンがある
#  1)secondary既に認可されている
#  2)primaryは認証済みだがsecondaryが未=>認可してappSidを作成
#  3)primaryは認証未=>このメソッドは復帰せず認証画面に遷移
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
      return authObj.promise
    dfd=ph.jQuery.Deferred()
    prm=dfd.promise(new Auth(checkAplUrl,dfd))
    prm.checkAplUrl=checkAplUrl
    if cb
      prm.on('auth',cb)
    @_auths[checkAplUrl]={deferred:dfd,promise:prm}
    @_callRequest(checkAplUrl,@_aplCheckCb,prm)
    prm
}

#xhr通信用のイベント登録
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
  checkState:->
    if @deferred.state()!='pending'
      throw 'state error:'+@deferred.state()

#-------------------Auth-------------------
class Auth extends EventModule
  constructor: (@checkAplUrl,@deferred) ->
    super
  _setup:(res)->
    @loginId=res.loginId
    @authUrl=res.authUrl
    @appSid=res.appSid
    @token=res.token
    @_frame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' + ph.auth2._authEachFrameName + @authUrl + '" ></iframe>')
    @_frame.load(@_frameLoad)
    @trigger('setupAuth',res)
    ph.jQuery("body").append(@_frame)
  _frameLoad:=>
    ph.auth2._auth=null
    ph.auth2._call()
    @trigger('auth',@)
  _onMessage:->
  info:->
  encrypt:(loginid,plainText)->
  decrypt:(loginid,encryptText)->
