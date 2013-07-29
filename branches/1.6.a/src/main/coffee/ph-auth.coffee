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
##/* �Ăяo����queue���ď�����������d�g�� */
  _authQueue:[]
  _processAuth:null #�F�؏��������ۂ�
  _reqQueue:[]
  _processReq:null
  _finAuth:(res)->
    auth=@_processAuth
    auth.result=res.result
    if res.result
      auth._setup(res)
    else
      delete @_authQueue[auth.keyUrl]
      auth.deferred.reject(auth)
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
    @_requestUrl(auth.checkAplUrl,@_aplCheckCb)
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
    t=@ #�Ӑ}����url�łȂ��ꍇevent�����Ȃ�����timeout����
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
    if !@_frame || ev.originalEvent.source!=@_frame[0].contentWindow
      return
    @_frame[0].src='about:blank'
    req=@_processReq
    if !req
      return
    clearTimeout(req.timerId)
    req.timerId=null
    if req.url.lastIndexOf(ev.originalEvent.origin, 0)!=0
      @_requestCallback({result:false,reason:'domain error'})
      return
    res=ph.JSON.parse(ev.originalEvent.data)
    @_requestCallback(res)
    return
  _authCheckCb:(res)=>
    if res.result=='redirect'
##      /* authUrl��session��₢���킹 */
      @_requestUrl(res.location,@_authCheckCb)
    else if res.result=='redirectForAuthorizer'
      location.href=res.location
    else
      @_finAuth(res)
    return
  _aplCheckCb:(res)=>
    if res.result=='redirect'
##    /* authUrl��session��₢���킹 */
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
      ph.checkCall(->ph.auth.onlineAuth(cb,auth))
#.fail(->ph.auth.offlineAuth(cb,auth))
    auth.promise
  offlineAuth:(cb,auth)->

#----------auth outer api----------
#  /*authUrl�ŗL��appSid���擾����A�ȉ��̃p�^�[��������
#  1)secondary���ɔF����Ă���
#  2)primary�͔F�؍ς݂���secondary����=>�F����appSid���쐬
#  3)primary�͔F�ؖ�=>���̃��\�b�h�͕��A�����F�؉�ʂɑJ��
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
    @_auths[auth.keyUrl]=auth
    @_callAsyncAuth(auth)
    auth
  info:(cb,authUrl)->
    ph.checkCall(ph.auth._info,cb,authUrl)
  _info:(cb,authUrl)=>
    if !authUrl #�w�肪�Ȃ���Ύ������_�E�����[�h����authUrl
      authUrl=ph.authUrl
#      authUrl='https://192.168.1.30:1280/auth'
    url=authUrl+@_infoPath;
    @_requestUrl(url,cb);
    return

window.ph.auth=new PhAuth()

#xhr�ʐM�p�̃C�x���g�o�^
ph.jQuery(->
  ph.auth._init()
  ph.on('message',ph.auth._onMessage)
)

#-------------------Auth-------------------
class Auth extends ph.EventModule2
  _reqQueue:[]
  _processReq:null
  constructor:(aplUrl)->
    super
    @deferred=ph.jQuery.Deferred()
    @promise=@deferred.promise(@)
    aplUrl.match(ph.auth._urlPtn)
    protocol=RegExp.$1
    aplDomain=RegExp.$2
    aplPath=RegExp.$3
    if protocol=='ws:'
      @keyUrl="http://#{aplDomain}#{aplPath}"
      @checkAplUrl=@keyUrl+ph.auth._checkWsAplQuery
    else if protocol=='wss:'
      @keyUrl="https://#{aplDomain}#{aplPath}"
      @checkAplUrl=@keyUrl+ph.auth._checkWsAplQuery
    else if protocol==null || protocol==''
      if ph.isSsl
        @keyUrl="https//#{ph.domain}#{aplPath}"
      else
        @keyUrl="http//#{ph.domain}#{aplPath}"
      @checkAplUrl=@keyUrl+ph.auth._checkAplQuery
    else #http or https
      @keyUrl=aplUrl;
      @checkAplUrl=aplUrl+ph.auth._checkAplQuery
  _init:->
    ph.on('message',@_onMessage)
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
    if !@_frame || ev.originalEvent.source!=@_frame[0].contentWindow
      return
    if @_loadFrame
      @_loadFrame=false
      @_info=ph.JSON.parse(ev.originalEvent.data)
      @deferred.resolve(@)
      return
    if ev.originalEvent.data=='showFrame'
      @_frame.css({'height':'200px','width':'300px','top':'50%','left':'50%'})
      return
    if ev.originalEvent.data=='hideFrame'
      @_frame.css({'height':'0px','width':'0px','top':'0%','left':'0%'})
      return
    req=@_processReq
    if !req
      return
    res=ph.JSON.parse(ev.originalEvent.data)
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
