OFFLINE_KEY='PaOfflineAuth'
parentOrigin=null
aplAuthInfo=null
userAuthInfo=null
userInfoDfd=null

getUserAuthInfo=(cb)->
 userInfoDfd=jQuery.ajax({
  type:'POST',
  url:'userInfo',
  dataType:'json',
  data:aplAuthInfo
 })
 userInfoDfd.done((x)->
   userAuthInfo=x
   if cb
    cb(true,x))
 userInfoDfd.fail((x)->
   userAuthInfo=null
   if cb
    cb(false,x))

encrypt=(req)->
 if !userAuthInfo
  req.result=false
  response(req)
  return
 req.result=true
 if userAuthInfo.offlinePassHash
  req.encryptText=CryptoJS.AES.encrypt(req.plainText, userAuthInfo.offlinePassHash).toString()
 else
  req.encryptText=req.plainText
 response(req)

decrypt=(req)->
 if !userAuthInfo
  req.result=false
  response(req)
  return
 req.result=true
 if userAuthInfo.offlinePassHash
  req.plainText=CryptoJS.AES.decrypt(req.encryptText, userAuthInfo.offlinePassHash).toString(CryptoJS.enc.Utf8)
 else
  req.plainText=req.encryptText
 response(req)

onRequest=(req)->
 if req.type=="encrypt"
  if userInfoDfd
   userInfoDfd.always(->encrypt(req))
  else
   onlineInfo(->encrypt(req))
 else if req.type=="decrypt"
  if userInfoDfd
   userInfoDfd.always(->decrypt(req))
  else
   onlineInfo(->decrypt(req))
 else if req.type=="logout"
  jQuery.ajax({
    type:'GET',
    url:'ajaxLogout',
    dataType:'json',
    success:(x)->response(x),
    error:(x)->response({type:'logout',result:false})
   })
 else if req.type=="info"
  aplAuthInfo=req
  if userInfoDfd
   userInfoDfd.always(->response(userInfo.authInfo))
  else
   getUserAuthInfo(->response(userInfo.authInfo))
 else if req.type=="offlineAuth"
 else
   throw 'unkown type:'+req.type

onMsg=(qjev)->
 ev=qjev.originalEvent
 if !ev.data
  return
 if ev.source==parent
  req=ph.JSON.parse(ev.data)
  onRequest(req)

response=(msg)->
 jsonMsg=ph.JSON.stringify(msg)
 if window==parent #テスト時に自分には投げない処理
  alert('authOffline response:'+jsonMsg)
 else
  parent.postMessage(jsonMsg,'*')

offlineLogon=->
 loginId=jQuery('#loginId').val()
 passowrd=jQuery('#password').val()
 alert(loginId+':'+passowrd)
 response({type:'offlineAuth',result:true,loginId:loginId})
 jQuery('logonId').val("")
 jQuery('password').val("")

offlineCancel=->
 jQuery('logonId').val("")
 jQuery('password').val("")
 response({type:'offlineAuth',result:false})

jQuery(->
 parentOrigin=decodeURIComponent(location.search.substring('?origin='.length))
 jQuery('#logonBtn').on('click',offlineLogon)
 jQuery('#cancelBtn').on('click',offlineCancel)
 if parentOrigin=='file://'
  parentOrigin='*'
 jQuery(window).on('message',onMsg)
 response({type:'loadAuthFrame',result:true})
)

