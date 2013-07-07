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
      delete @_authQueue[auth._keyUrl]
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
    @_requestUrl(auth._checkAplUrl,@_aplCheckCb)
    return
  _requestUrl:(url,cb)->
    req={url:url,cb:cb}
    @_reqQueue.push(req)
    @_request()
    return
  _request:->
    if @_processReq!=null || @_reqQueue.length==0
      return
    req=@_reqQueue.pop()
    @_processReq=req
    @_frame[0].src=req.url
    t=@ #意図したurlでない場合eventがこないからtimeoutする
    req.timerId=setTimeout((->
      t._requestCallback({result:false,reason:'timeout'})
      t._frame[0].src='about:blank'
      req.timerId=null
      return)
      ,1000)
    return
  _requestCallback:(res)->
    reqestCb=@_processReq.cb
    @_processReq=null
    reqestCb(res)
    @_request()
    return
  _init:->
    @_frame=ph.jQuery(
      "<iframe width='0' height='0' frameborder='no' "+
      "name='#{@_authFrameName}' >"+
      "</iframe>")
    ph.jQuery("body").append(@_frame)
    return
  _onMessage:(ev)=>
    if !@_frame || ev.source!=@_frame[0].contentWindow
      return
    @_frame[0].src='about:blank'
    req=@_processReq
    if !req
      return
    clearTimeout(req.timerId)
    req.timerId=null
    if req.url.lastIndexOf(ev.origin, 0)!=0
      @_reqestCallback({result:false,reason:'domain error'})
      return
    res=ph.JSON.parse(ev.data)
    @_requestCallback(res)
    return
  _authCheckCb:(res)=>
    if res.result=='redirect'
##      /* authUrlにsessionを問い合わせ */
      @_requestUrl(res.location,@_authCheckCb)
    else if res.result=='redirectForAuthorizer'
      location.href=res.location
    else
      @_finAuth(res)
    return
  _aplCheckCb:(res)=>
    if res.result=='redirect'
##    /* authUrlにsessionを問い合わせ */
      redirectUrl=res.location+'&originUrl='+encodeURIComponent(location.href)
      @_requestUrl(redirectUrl,@_authCheckCb);
    else
      @_finAuth(res)
    return
  auth:(aplUrl,cb)->
    auth=new Auth(aplUrl)
    orgAuth=@_auths[auth.keyUrl]
    if orgAuth
      auth=orgAuth
      auth.checkCall(cb)
    else
      @_auths[auth.keyUrl]=auth
      ph.load.done(->ph.auth.onlineAuth(cb,auth)).fail(->ph.auth.offlineAuth(cb,auth))
    auth.promise
  offlineAuth:(cb,auth)->

#----------auth outer api----------
#  /*authUrl固有のappSidを取得する、以下のパターンがある
#  1)secondary既に認可されている
#  2)primaryは認証済みだがsecondaryが未=>認可してappSidを作成
#  3)primaryは認証未=>このメソッドは復帰せず認証画面に遷移
#  */
  onlineAuth:(cb,auth)->
#    state=ph.load.state()
#    if state=='pending'
#      ph.load.done(->ph.auth.onlineAuth(aplUrl,cb,auth))
#      return auth.promise
##    authObj=@_auths[keyUrl]
#    if authObj
#      auth=authObj.promise
#      if cb && auth.state()=='pending'
#        auth.on('done',cb)
#      else if cb
#        cb(auth)
#      return auth
#    auth=prmAuth
    auth._init()
    if cb
      auth.on('done',cb)
    auth.always(@_alwaysAsyncAuth)
    @_auths[keyUrl]=auth
    @_callAsyncAuth(auth)
    auth
  info:(cb,authUrl)->
    if !authUrl #指定がなければ自分をダウンロードしたauthUrl
      authUrl=ph.authUrl
    url=authUrl+@_infoPath;
    @_requestUrl(url,cb);
    return

window.ph.auth=new PhAuth()

#xhr通信用のイベント登録
ph.jQuery(->
  ph.auth._init()
  ph.event.on('message',ph.auth._onMessage)
)

#-------------------Auth-------------------
class Auth extends ph.EventModule2
  _reqQueue:[]
  _processReq:null
  constructor:(aplUrl)->
    super
    @deferred=ph.jQuery.Deferred()
    @promise=@deferred.promise(@)
    aplUrl.match(ph.auth_urlPtn)
    protocol=RegExp.$1
    aplDomain=RegExp.$2
    aplPath=RegExp.$3
    keyUrl=checkAplUrl=null
    if protocol=='ws:'
      @keyUrl="http://#{aplDomain}#{aplPath}"
      @checkAplUrl=keyUrl+ph.auth._checkWsAplQuery
    else if protocol=='wss:'
      @keyUrl="https://#{aplDomain}#{aplPath}"
      @checkAplUrl=keyUrl+ph.auth._checkWsAplQuery
    else if protocol==null || protocol==''
      if ph.isSsl
        @keyUrl="https//#{ph.domain}#{aplPath}"
      else
        @keyUrl="http//#{ph.domain}#{aplPath}"
      @checkAplUrl=keyUrl+ph.auth._checkAplQuery
    else #http or https
      @keyUrl=aplUrl;
      @checkAplUrl=aplUrl+ph.auth._checkAplQuery
  _init:->
    ph.event.on('message',@_onMessage)
    @
  _setup:(res)->
    @loginId=res.loginId
    @authUrl=res.authUrl
    @appSid=res.appSid
    @token=res.token
    @_loadFrame=true
    @_frame=ph.jQuery(
      "<iframe " +
      "style='frameborder:no;background-color:#CFCFCF;overflow:auto;height:0px;width:0px;position:absolute;top:0%;left:0%;margin-top:-100px;margin-left:-150px;' " +
      "name='#{ph.auth._authEachFrameName}#{@_keyUrl}' " +
      "src='#{@authUrl}/authFrame?origin=#{location.protocol}//#{location.host}'>" + 
      "</iframe>")
    ph.jQuery("body").append(@_frame)
#    @_frame.css({'background-color':'#CFCFCF','overflow':'auto','height':'200px','width':'300px','position':'absolute','top':'50%','left':'50%','margin-top':'-100px','margin-left':'-150px'})
  _requestQ:(req,cb)->
    reqObj={req:req,cb:cb}
    @_reqQueue.push(reqObj)
    @_request()
    return
  _request:->
    if @_processReq!=null || @_reqQueue.length==0
      return
    reqObj=@_reqQueue.pop()
    @_processReq=reqObj
    reqText=ph.JSON.stringify(reqObj.req)
    @_frame[0].contentWindow.postMessage(reqText,'*')
    return
  _requestCallback:(res)->
    reqestCb=@_processReq.cb
    @_processReq=null
    reqestCb(res)
    @_request()
    return
  _onMessage:(ev)=>
    if !@_frame || ev.source!=@_frame[0].contentWindow
      return
    if @_loadFrame
      @_loadFrame=false
      @_info=ph.JSON.parse(ev.data)
      @_deferred.resolve(@)
      return
    if ev.data=='showFrame'
      @_frame.css({'height':'200px','width':'300px','top':'50%','left':'50%'})
      return
    if ev.data=='hideFrame'
      @_frame.css({'height':'0px','width':'0px','top':'0%','left':'0%'})
      return
    req=@_processReq
    if !req
      return
    res=ph.JSON.parse(ev.data)
    @_requestCallback(res)
    return
  logout:(cb)->
    @_requestQ({type:'logout'},(res)=>
      cb(res)
      @_frame.remove()
      delete ph.auth._auths[@_keyUrl])
    @
  info:(cb)->
    @_requestQ({type:'info'},cb)
    @
  encrypt:(plainText,cb)->
    @_requestQ({type:'encrypt',loginId:@loginId,plainText:plainText},(res)->cb(res.encryptText))
    @
  decrypt:(encryptText,cb)->
    @_requestQ({type:'decrypt',loginId:@loginId,encryptText:encryptText},(res)->cb(res.plainText))
    @
