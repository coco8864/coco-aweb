
AUTH_URL_LS_KEY='aplOfflineAuthUrl'
CHECK_XHR_QUERY='?__PH_AUTH__=__XHR_CHECK__'
CHECK_QUERY='?__PH_AUTH__=__CD_CHECK__'
CHECK_WS_QUERY='?__PH_AUTH__=__CD_WS_CHECK__'

authFrame=null
workFrame=null
cdr={isIn:false,req:null}

aplAuthInfo={
 type:'load'
 result:false
 aplUrl:null
 authUrl:null
 cause:''
 isOffline:false
 appSid:null
 loginId:null
 token:null
}
authFrameTimerId=null
workFrameTimerId=null
workFrameCb=null

loadAuthFrame=(authUrl)->
 authFrame[0].src=authUrl+'/~offline.html?origin='+location.protocol+'//'+location.host
 authFrameTimerId=setTimeout((->
  authFrameTimerId=null
  _response({type:'load',result:false,cause:'frameTimeout'}))
  ,1000
 )

requestToAuthFrame=(msg)->
 jsonMsg=ph.JSON.stringify(msg)
 authFrame[0].contentWindow.postMessage(jsonMsg,'*')

loadWorkFrame=(url,cb)->
 workFrame[0].src=url
 workFrameCb=cb
 workFrameTimerId=setTimeout((->
  workFrameTimerId=null
  alert('workFrameTimeout'))
  ,1000
 )

onlineCheckAuthInfoSuccess=(res)->
 aplAuthInfo.isOffline=false
 aplAuthInfo.appSid=res.appSid
 aplAuthInfo.authUrl=res.authUrl
 aplAuthInfo.loginId=res.loginId
 aplAuthInfo.token=res.token
 localStorage.setItem(AUTH_URL_LS_KEY,res.authUrl)
 loadAuthFrame(res.authUrl)
## requestToAuthFrame(aplAuthInfo)

onlineCheckAuthInfoError=(res)->
 aplAuthInfo.isOffline=true
 authUrl=localStorage.getItem(AUTH_URL_LS_KEY)
 if authUrl
  loadAuthFrame(authUrl)
 else
  aplAuthInfo.result=false
  aplAuthInfo.cause='cannot find authUrl'
  _response(aplAuthInfo)

onlineCheckAuthInfo=->
 jQuery.ajax({
  type:'POST',
  url:aplAuthInfo.aplUrl + CHECK_XHR_QUERY,
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
 if res.type=='load' #AuthFrameのロード完了
  clearTimeout(authFrameTimerId)
  aplAuthInfo.result=res.result
  aplAuthInfo.cause=res.cause
  _response(aplAuthInfo)
 if res.type=='offlineAuth' #offlineAuth
  _response({type:'hideFrame'});
  response(res)

onWorkResponse=(res)->
 clearTimeout(workFrameTimerId)
 workFrameTimerId=null
 workFrameCb(res)

### online auth start ###
onlineAuthResponse=(res)->
 if res.result==true
  aplAuthInfo.isOffline=false
  aplAuthInfo.appSid=res.appSid
  aplAuthInfo.authUrl=res.authUrl
  aplAuthInfo.loginId=res.loginId
  aplAuthInfo.token=res.token
  requestToAuthFrame(aplAuthInfo)
 response(res)

onlineAuthAuthUrlRes=(res)->
 if res.result=='redirect'
  loadWorkFrame(res.location,onlineAuthResponse)
## else if res.result=='redirectForAuthorizer' ##authUrlでも認証されていなければ
##  location.href=res.location
 else
  onlineAuthResponse(res)

onlineAuthAplUrlRes=(res)->
 if res.result=='redirect' ##apl未認証の場合authUrlにリダイレクト
  loadWorkFrame(res.location,onlineAuthAuthUrlRes)
 else
  onlineAuthResponse(res)

onlineAuth=(isWs,originUrl)-> ##aplUrlをチェック
 if isWs
  url=aplAuthInfo.aplUrl+CHECK_WS_QUERY
 else
  url=aplAuthInfo.aplUrl+CHECK_QUERY
 if originUrl
  url+='&originUrl='+encodeURIComponent(originUrl)
 loadWorkFrame(url,onlineAuthAplUrlRes)
### online auth end ###

onRequest=(req)->
 if cdr.isIn
  throw "duplicate request"
 if aplAuthInfo.result==false
  throw "fail to load"
 cdr.isIn=true
 cdr.req=req
 if req.type=='onlineAuth'
  onlineAuth(req.isWs,req.originUrl)
 else if req.type=='offlineAuth'
  _response({type:'showFrame'});
  alert('offlineAuth')
 else if req.type=='encrypt'
  requestToAuthFrame(req)
 else if req.type=='decrypt'
  requestToAuthFrame(req)
 else if req.type=='info'
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
 if window==parent #テスト時に自分には投げない処理
  alert('aplOffline response:'+jsonMsg)
 else
  parent.postMessage(jsonMsg,'*')

jQuery(->
 pos=location.pathname.lastIndexOf('/')
 aplAuthInfo.aplUrl=location.origin+location.pathname.substring(0,pos)
 jQuery(window).on('message',onMsg)
 authFrame=jQuery(
      "<iframe width='100%' height='512' frameborder='no' "+
      "name='aplOfflineAuth#{aplAuthInfo.aplUrl}' >"+
      "</iframe>")
 workFrame=jQuery(
      "<iframe width='0' height='0' frameborder='no' "+
      "name='aplOfflineWork#{aplAuthInfo.aplUrl}' >"+
      "</iframe>")
 jQuery("body").append(workFrame)
 jQuery("body").append(authFrame)
 onlineCheckAuthInfo()

 window.onlineAuth=onlineAuth
)
