if window.ph.auth
  return

class PhAuth
  _authFrameName:'__phAuthFrame'
  _authEachFrameName:'__phAuthEachFrame_'
  _infoPath:'/info'
  _checkAplQuery:'?__PH_AUTH__=__CD_CHECK__'
  _checkWsAplQuery:'?__PH_AUTH__=__CD_WS_CHECK__'
  _urlPtn:/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
  _auths:{}
##/* 呼び出しをqueueして順次処理する仕組み */
  _authQueue:[]
  _processAuth:null #認証処理中か否か
  _reqQueue:[]
  _processReq:null
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
    return
  _reqestUrl:(url,cb)->
    req={url:url,cb:cb}
    @_reqQueue.push(req)
    @_reqest()
    return
  _reqest:->
    if @_processReq!=null || @_reqQueue.length==0
      return
    req=@_reqQueue.pop()
    @_processReq=req
    @_frame[0].src=req.url
    return
  _reqestCallback:(res)->
    reqestCb=@_processReq.cb
    @_processReq=null
    reqestCb(res)
    @_reqest()
    return
  _init:->
    @_frame=ph.jQuery("<iframe width='0' height='0' frameborder='no' name='#{@_authFrameName}' ></iframe>")
    ph.jQuery("body").append(@_frame)
    return
  _onMessage:(ev)=>
    if !@_frame || ev.source!=@_frame[0].contentWindow
      return
    @_frame[0].src='about:blank'
    req=@_processReq
    if !req
      return
    if req.url.lastIndexOf(ev.origin, 0)!=0
      return
    res=ph.JSON.parse(ev.data)
    @_reqestCallback(res)
    return
  _authCheckCb:(res)=>
    if res.result=='redirect'
##      /* authUrlにsessionを問い合わせ */
      @_reqestUrl(res.location,@_authCheckCb)
    else if res.result=='redirectForAuthorizer'
      window.location.href=res.location
    else
      @_finAuth(res)
    return
  _aplCheckCb:(res)=>
    if res.result=='redirect'
##    /* authUrlにsessionを問い合わせ */
      redirectUrl=res.location+'&originUrl='+encodeURIComponent(window.location.href)
      @_reqestUrl(redirectUrl,@_authCheckCb);
    else
      @_finAuth(res)
    return

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
    keyUrl=checkAplUrl=null
    if protocol=='ws:'
      keyUrl="http://#{authDomain}#{authPath}"
      checkAplUrl=keyUrl+@_checkWsAplQuery
    else if protocol=='wss:'
      keyUrl="https://#{authDomain}#{authPath}"
      checkAplUrl=keyUrl+@_checkWsAplQuery
    else if protocol==null || protocol==''
      keyUrl="#{window.location.protocol}//#{window.location.host}#{authPath}"
      checkAplUrl=keyUrl+@_checkAplQuery
    else #http or https
      keyUrl=aplUrl;
      checkAplUrl=aplUrl+@_checkAplQuery
    authObj=@_auths[keyUrl]
    if authObj
      auth=authObj.promise
      if cb && auth.state()=='pending'
        auth.on('done',cb)
      else if cb
        cb(auth)
      return auth
    dfd=ph.jQuery.Deferred()
    auth=dfd.promise(new Auth(keyUrl,checkAplUrl,dfd))
    auth._checkAplUrl=checkAplUrl
    auth.on('done',cb)
    auth.always(@_alwaysAsyncAuth)
    @_auths[keyUrl]={deferred:dfd,promise:auth}
    @_callAsyncAuth(auth)
    auth
  info:(authUrl,cb)->
    if !authUrl #指定がなければ自分をダウンロードしたauthUrl
      authUrl=ph.authUrl
    url=authUrl+@_infoPath;
    @_reqestUrl(url,cb);
    return

window.ph.auth=new PhAuth()

#xhr通信用のイベント登録
ph.jQuery(->
  ph.auth._init()
  ph.event.on('message',ph.auth._onMessage)
)

#-------------------Auth-------------------
class Auth extends ph.EventModule
  _reqQueue:[]
  _processReq:null
  constructor: (@_keyUrl,@_checkAplUrl,@_deferred) ->
    super
    ph.event.on('message',@_onMessage)
  _setup:(res)->
    @loginId=res.loginId
    @authUrl=res.authUrl
    @appSid=res.appSid
    @token=res.token
    @_frame=ph.jQuery("<iframe width='0' height='0' frameborder='no' name='#{ph.auth._authEachFrameName}#{@_keyUrl}' ></iframe>")
    @_frame.load(@_frameLoad)
    ph.jQuery("body").append(@_frame)
  _frameLoad:=>
    @_deferred.resolve(@)
  _reqestUrl:(url,cb)->
    req={url:url,cb:cb}
    @_reqQueue.push(req)
    @_reqest()
    return
  _reqest:->
    if @_processReq!=null || @_reqQueue.length==0
      return
    req=@_reqQueue.pop()
    @_processReq=req
    @_frame[0].src=req.url
    return
  _reqestCallback:(res)->
    reqestCb=@_processReq.cb
    @_processReq=null
    reqestCb(res)
    @_reqest()
    return
  _onMessage:->
    if !@_frame || ev.source!=@_frame[0].contentWindow
      return
    @_frame[0].src='about:blank'
    req=@_processReq
    if !req
      return
    if req.url.lastIndexOf(ev.origin, 0)!=0
      return
    res=ph.JSON.parse(ev.data)
    @_reqestCallback(res)
    return
  logout:(url)->
  info:(cb)->
  encrypt:(loginid,plainText,cb)->
  decrypt:(loginid,encryptText,cb)->
