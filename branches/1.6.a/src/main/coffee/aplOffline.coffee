AUTH_URL_KEY='LinkOffAuthUrl'
CHECK_XHR_QUERY='?__PH_AUTH__=__XHR_CHECK__'
CHECK_QUERY='?__PH_AUTH__=__CD_CHECK__'
CHECK_WS_QUERY='?__PH_AUTH__=__CD_WS_CHECK__'

authFrame=null
workFrame=null
cdr={isIn:false,req:null}

aplPrivatePrefix=null
aplLocalPrefix='@.'

aplInfo={
  aplUrl:null
  authUrl:null
  isOffline:false
  appSid:null
  loginId:null
  token:null
  maxAge:-1
}
authFrameTimerId=null
workFrameTimerId=null
workFrameCb=null
authInfo=null

loadAuthFrame=(authUrl)->
  authFrame[0].src=authUrl+'/~ph.vsp?origin='+location.protocol+'//'+location.host
  authFrameTimerId=setTimeout((->
      authFrameTimerId=null
      _response({type:'loadAplFrame',result:false,cause:'frameTimeout'}))
    ,authFrameTimeout
    )

requestToAuthFrame=(msg)->
  jsonMsg=ph.JSON.stringify(msg)
  authFrame[0].contentWindow.postMessage(jsonMsg,'*')

loadWorkFrame=(url,cb)->
  workFrame[0].src=url
  workFrameCb=cb
  workFrameTimerId=setTimeout((->
     workFrameTimerId=null
     alert('aplOffline workFrameTimeout'))
   ,authFrameTimeout
  )

onlineCheckAuthInfoSuccess=(res)->
  aplInfo.isOffline=false
  aplInfo.appSid=res.appSid
  aplInfo.authUrl=res.authUrl
  aplInfo.loginId=res.loginId
  aplInfo.token=res.token
  aplPrivatePrefix='@'+aplInfo.loginId+'.'
  localStorage.setItem(AUTH_URL_KEY,res.authUrl)
  loadAuthFrame(res.authUrl)

onlineCheckAuthInfoError=(res)->
  #xhr呼び出しに失敗した.offlineでの接続を試みる
  aplInfo.isOffline=true
  authUrl=localStorage.getItem(AUTH_URL_KEY)
  if authUrl
    loadAuthFrame(authUrl)
  else
    aplInfo.result=false
    aplInfo.cause='cannot find authUrl'
    _response(aplInfo)

onlineCheckAuthInfo=->
  jQuery.ajax({
    type:'POST',
    url:aplInfo.aplUrl + CHECK_XHR_QUERY,
    dataType:'json',
    success:onlineCheckAuthInfoSuccess,
    error:onlineCheckAuthInfoError
  })

onMsg=(qjev)->
  ev=qjev.originalEvent
  if !ev.data
    return
  if ev.source==parent
    req=ph.JSON.parse(ev.data)
    onRequest(req)
  else if ev.source==authFrame[0].contentWindow
    res=ph.JSON.parse(ev.data)
    onAuthResponse(res)
  else if ev.source==workFrame[0].contentWindow
    res=ph.JSON.parse(ev.data)
    onWorkResponse(res)

onAuthResponse=(res)->
  if res.type=='loadAuthFrame' #AuthFrameのロード完了
    clearTimeout(authFrameTimerId)
    authFrameTimerId=null
    res.type='loadAplFrame'
    res.aplInfo=aplInfo
    _response(res)
  if res.type=='offlineAuth' #offlineAuth
    ##  _response({type:'hideFrame'})
    if res.result
      aplInfo.loginId=res.authInfo.user.loginId
      aplPrivatePrefix='@'+aplInfo.loginId+'.'
      aplInfo.maxAge=30
      res.aplInfo=aplInfo
      res.authInfo=res.authInfo
    response(res)
  else if res.type=='encrypt' || res.type=='decrypt' || res.type=='logout'
    response(res)
  else if res.type=='authInfo'
    authInfo=res.authInfo
    response({type:'onlineAuth',result:res.result,aplInfo:aplInfo,authInfo:res.authInfo})
  else if res.type=='offlineLogout'
    response(res)
  else if res.type=='userProfile'
    ##  _response({type:'hideFrame'})
    response(res)
  else if res.type=='showFrame'
    authFrame.height(res.height+20)
    res.height=document.body.clientHeight
    _response(res)
  else if res.type=='hideFrame'
    _response(res)
  else if res.type=='getItem'
    onGetItemResponse(res)
  else if res.type=='setItem'
    onSetItemResponse(res)
  else if res.type=='removeItem'
    onRemoveItemResponse(res)
  else if res.type=='enumKey'
    onEnumKeyResponse(res)
  else if res.type=='changeItem'
    onChangeItemResponse(res)

onWorkResponse=(res)->
  clearTimeout(workFrameTimerId)
  workFrameTimerId=null
  workFrameCb(res)

### online auth start ###
onlineAuthResponse=(res)->
  if res.result==true
    aplInfo.isOffline=false
    aplInfo.appSid=res.appSid
    aplInfo.authUrl=res.authUrl
    aplInfo.loginId=res.loginId
    aplPrivatePrefix='@'+aplInfo.loginId+'.'
    aplInfo.token=res.token
    requestToAuthFrame({type:'authInfo',aplInfo:aplInfo})
  else
    res.type='onlineAuth'
    response(res)

onlineAuthAuthUrlRes=(res)->
  if res.result=='redirect'
    loadWorkFrame(res.location,onlineAuthResponse)
  else
    onlineAuthResponse(res)

onlineAuthAplUrlRes=(res)=>
  if res.result=='redirect' ##apl未認証の場合uthUrlにリダイレクト
    loadWorkFrame(res.location+'&originUrl='+encodeURIComponent(@originUrl),onlineAuthAuthUrlRes)
  else
    onlineAuthResponse(res)

onlineAuth=(isWs,originUrl)-> ##aplUrlをチェック
  if isWs
    url=aplInfo.aplUrl+CHECK_WS_QUERY
  else
    url=aplInfo.aplUrl+CHECK_QUERY
  if originUrl
    @originUrl=originUrl
  loadWorkFrame(url,onlineAuthAplUrlRes)
### online auth end ###

SCOPE={
  PAGE_PRIVATE:'pagePrivate'
  SESSION_PRIVATE:'sessionPrivate' #auth localstorage key:sessionid.key=value
  APL_PRIVATE:'aplPrivate' #apl localstorage key:loginid.key=value
  APL_LOCAL:'aplLocal' #apl localstorage key:@.key=value  (no enc)
  AUTH_PRIVATE:'authPrivate' #auth localstorage key:loginid.key=value
  AUTH_LOCAL:'authLocal' #auth localstorage key:@.key=value (no enc)
}

scopePrefix=(scope)->
  if scope==SCOPE.APL_LOCAL
    return aplLocalPrefix
  else if scope==SCOPE.APL_PRIVATE
    return aplPrivatePrefix
  throw 'unkown scope:'+scope

scopeKey=(scope,key)->
  return scopePrefix(scope)+key

onGetItemRequest=(data)->
  date.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE || data.scope==SCOPE.AUTH_LOCAL
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_PRIVATE
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_LOCAL
    realKey=scopeKey(data.scope,date.key)
    date.value=localStorage.getItem(realKey)
    response(date)
  else
    throw 'scope error:'+date.scope

onSetItemRequest=(data)->
  date.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE || data.scope==SCOPE.AUTH_LOCAL
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_PRIVATE
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_LOCAL
    realKey=scopeKey(date.key)
    date.value=localStorage.setItem(realKey,date.value)
  else
    throw 'scope error:'+date.scope

onRemoveItemRequest=(data)->
  date.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE || data.scope==SCOPE.AUTH_LOCAL
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_PRIVATE
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_LOCAL
    realKey=scopeKey(date.key)
    date.value=localStorage.removeItem(realKey,date.value)
  else
    throw 'scope error:'+date.scope

enumScopeKey=(scope)->
  prefix=scopePrefix(scope)
  keys=[]
  i=localStorage.length
  while (i-=1) >=0
    key=localStorage.key(i)
    if key.lastIndexOf(prefix,0)==0
      keys.push(key.substring(prefix.length))
  return keys

onEnumKeyRequest=(data)->
  date.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE || data.scope==SCOPE.AUTH_LOCAL
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_PRIVATE
    date.keys=enumScopeKey(data.scope)
    if date.keys.length==0
      response(date)
    else
      requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_LOCAL
    date.keys=enumScopeKey(data.scope)
    response(date)
  else
    throw 'scope error:'+date.scope

onGetItemResponse=(data)->
  date.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE || data.scope==SCOPE.AUTH_LOCAL
    response(data)
  else if data.scope==SCOPE.APL_PRIVATE
    if typeof(data.value)!='undefined'
      response(data)
    else
      realKey=scopeKey(date.encKey)
      data.encValue=localStorage.getItem(realKey)
      requestToAuthFrame(data)
  else
    throw 'scope error:'+date.scope

onSetItemResponse=(data)->
  date.via++
  if data.scope==SCOPE.APL_PRIVATE
    realKey=scopeKey(date.encKey)
    data.encValue=localStorage.setItem(realKey,data.encValue)
  else
    throw 'scope error:'+date.scope

onRemoveItemResponse=(data)->
  date.via++
  if data.scope==SCOPE.APL_PRIVATE
    realKey=scopeKey(date.encKey)
    localStorage.removeItem(realKey)
  else
    throw 'scope error:'+date.scope

onEnumKeyResponse=(data)->
  date.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE || data.scope==SCOPE.AUTH_LOCAL
    response(data)
  else if data.scope==SCOPE.APL_PRIVATE
    response(data)
  else
    throw 'scope error:'+date.scope

onChangeItemResponse=(data)->
  date.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE || data.scope==SCOPE.AUTH_LOCAL
    response(data)
  else
    throw 'scope error:'+date.scope

onRequest=(req)->
  cdr.isIn=true
  cdr.req=req
  if req.type=='onlineAuth'
    onlineAuth(req.isWs,req.originUrl)
  else if req.type=='offlineAuth'
    requestToAuthFrame({type:'offlineAuth'})
    authFrame.focus()
  else if req.type=='offlineLogout'
    requestToAuthFrame({type:'offlineLogout'})
  else if req.type=='userProfile'
    requestToAuthFrame({type:'userProfile'})
    authFrame.focus()
  else if req.type=='encrypt'
    requestToAuthFrame(req)
  else if req.type=='decrypt'
    requestToAuthFrame(req)
  else if req.type=='logout'
    requestToAuthFrame(req)
  else if req.type=="setItem"
    onSetItemRequest(req)
  else if req.type=="getItem"
    onGetItemRequest(req)
  else if req.type=="removeItem"
    onRemoveItemRequest(req)
  else if req.type=="enumKey"
    onEnumKeyRequest(req)
  else
    throw 'unkown type:'+req.type

setupScope=(data,realKey)->
  if realKey.lastIndexOf(aplPrivatePrefix,0)==0
    data.scope=SCOPE.APL_PRIVATE
    data.encKey=realKey.substring(aplPrivatePrefix.length)
  else if realKey.lastIndexOf(aplLocalPrefix,0)==0
    data.scope=SCOPE.APL_LOCAL
    data.key=realKey.substring(aplLocalPrefix.length)
  else
    return false
  return true

onStorage=(qjev)->
  ev=qjev.originalEvent
  if !ev
    return
  data={type:'changeItem',via:0}
  if setupScope(data,ev.key)==false
    return
  if data.scope==SCOPE.APL_PRIVATE
    date.encNewValue=ev.newValue
    date.encOldValue=ev.oldValue
    requestToAuthFrame(data)
  else if data.scope==SCOPE.APL_LOCAL
    date.newValue=ev.newValue
    date.oldValue=ev.oldValue
    response(data)

response=(msg)->
  cdr.isIn=false
  cdr.req=null
  _response(msg)

_response=(msg)->
  jsonMsg=ph.JSON.stringify(msg)
  if window==parent #自分にはpostしない対処
    alert('aplOffline response:'+jsonMsg)
  else
    parent.postMessage(jsonMsg,'*')

jQuery(->
  if window==parent #直接呼び出された場合は、appcache controle画面
    return
  #子として呼び出された場合認証用
  jQuery('body').text('')
  href=location.href
  pos=href.lastIndexOf('/')
  aplInfo.aplUrl=href.substring(0,pos)
  jQuery(window).on('message',onMsg)
  jQuery(window).on('storage',onStorage)
  authFrame=jQuery(
      "<iframe width='100%' height='512' frameborder='no' "+
      "name='aplOfflineAuth#{aplInfo.aplUrl}' >"+
      "</iframe>")
  workFrame=jQuery(
      "<iframe width='0' height='0' frameborder='no' "+
      "name='aplOfflineWork#{aplInfo.aplUrl}' >"+
      "</iframe>")
  jQuery("body").append(workFrame)
  jQuery("body").append(authFrame)
  onlineCheckAuthInfo()
  window.onlineAuth=onlineAuth
)
