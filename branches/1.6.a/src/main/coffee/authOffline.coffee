OFFLINE_KEY='PaOfflineAuth'
parentOrigin=null
aplAuthInfo=null
userAuthInfo=null
userInfoDfd=null

onlineInfo=(cb)->
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
  req.plainText=CryptoJS.AES.decrypt(req.encryptText, offlinePassHash).toString(CryptoJS.enc.Utf8)
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
   onlineInfo(->response(userInfo.authInfo))

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

jQuery(->
 parentOrigin=decodeURIComponent(location.search.substring('?origin='.length))
 if parentOrigin=='file://'
  parentOrigin='*'
 jQuery(window).on('message',onMsg)
 response({type:'load',result:true})
)

