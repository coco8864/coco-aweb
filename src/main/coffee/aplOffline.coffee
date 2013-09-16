
AUTH_URL_LS_KEY='aplOfflineAuthUrl'
CHECK_XHR_QUERY='?__PH_AUTH__=__XHR_CHECK__'
CHECK_QUERY='?__PH_AUTH__=__CD_CHECK__'
CHECK_WS_QUERY='?__PH_AUTH__=__CD_WS_CHECK__'

authFrame=null
workFrame=null
cdr={isIn:false,req:null}

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
 localStorage.setItem(AUTH_URL_LS_KEY,res.authUrl)
 loadAuthFrame(res.authUrl)

onlineCheckAuthInfoError=(res)->
 #xhr呼び出しに失敗した.offlineでの接続を試みる
 aplInfo.isOffline=true
 authUrl=localStorage.getItem(AUTH_URL_LS_KEY)
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
  authFrame.height(res.offsetHeight)
  _response(res)
 if res.type=='offlineAuth' #offlineAuth
  _response({type:'hideFrame'});
  if res.result
   aplInfo.loginId=res.authInfo.user.loginId
   aplInfo.maxAge=30
   res.aplInfo=aplInfo
   res.authInfo=res.authInfo
  response(res)
 else if res.type=='encrypt' || res.type=='decrypt' || res.type=='logout'
  response(res)
 else if res.type=='authInfo'
  authInfo=res.authInfo
  response({type:'onlineAuth',result:res.result,aplInfo:aplInfo,authInfo:res.authInfo})

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
  aplInfo.token=res.token
  requestToAuthFrame({type:'authInfo',aplInfo:aplInfo})
 else
  res.type='onlineAuth'
  response(res)

onlineAuthAuthUrlRes=(res)->
 if res.result=='redirect'
  loadWorkFrame(res.location,onlineAuthResponse)
## else if res.result=='redirectForAuthorizer' ##authUrlでも認証されていなければ
##  location.href=res.location
 else
  onlineAuthResponse(res)

onlineAuthAplUrlRes=(res)=>
 if res.result=='redirect' ##apl未認証の場合uthUrlにリダイレクト
  loadWorkFrame(res.location+'&originUrl='+encodeURIComponent(@originUrl),onlineAuthAuthUrlRes)
 else
  onlineAuthResponse(res)

onlineAuth=(isWs,originUrl)-> ##aplUrlをチェ繝?
 if isWs
  url=aplInfo.aplUrl+CHECK_WS_QUERY
 else
  url=aplInfo.aplUrl+CHECK_QUERY
 if originUrl
   @originUrl=originUrl
##  url+='&originUrl='+encodeURIComponent(originUrl)
 loadWorkFrame(url,onlineAuthAplUrlRes)
### online auth end ###

onRequest=(req)->
## if cdr.isIn
##  throw "duplicate request"
 cdr.isIn=true
 cdr.req=req
 if req.type=='onlineAuth'
  onlineAuth(req.isWs,req.originUrl)
 else if req.type=='offlineAuth'
  _response({type:'showFrame',height:document.height});
 else if req.type=='encrypt'
  requestToAuthFrame(req)
 else if req.type=='decrypt'
  requestToAuthFrame(req)
 else if req.type=='logout'
  requestToAuthFrame(req)
 else
  throw 'unkown type:'+req.type

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
