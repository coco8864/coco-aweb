OFFLINE_KEY='PaOfflineAuth:'
parentOrigin=null
aplInfo=null
userInfo=null ##{authInfo:{},offlinePassHash:''}
userInfoDfd=null

checkPassword=(loginId,password)->
 encryptText=localStorage.getItem(OFFLINE_KEY+loginId)
 if !encryptText
  return false
 passHash=null
 if password
  passHash=CryptoJS.SHA256(offlinePassSalt+":"+loginId+":"+password).toString()
 try
  plainText=decEncryptText(encryptText,passHash)
 catch error
  return false
 if !plainText
  return false
 userInfo=ph.JSON.parse(plainText)
 return true

saveUserInfo=->
 loginId=userInfo.authInfo.user.loginId
 text=ph.JSON.stringify(userInfo)
 encText=encPlainText(text,userInfo.offlinePassHash)
 localStorage.setItem(OFFLINE_KEY+loginId,encText)

getUserInfo=(cb)->
 userInfoDfd=jQuery.ajax({
  type:'POST',
  url:'userInfo',
  dataType:'json',
  data:aplInfo
 })
 userInfoDfd.done((x)->
   userInfo=x
   if cb
    cb(true,x)
   saveUserInfo())
   
 userInfoDfd.fail((x)->
   userInfo=null
   if cb
    cb(false,x))

encPlainText=(plainText,passHash)->
 if passHash
  return CryptoJS.AES.encrypt(plainText,passHash).toString()
 else
  return plainText

decEncryptText=(encryptText,passHash)->
 if passHash
  return CryptoJS.AES.decrypt(encryptText, passHash).toString(CryptoJS.enc.Utf8)
 else
  return encryptText

encrypt=(req)->
 if !userInfo
  req.result=false
  response(req)
  return
 req.result=true
 req.encryptText=encPlainText(req.plainText,userInfo.offlinePassHash)
 response(req)

decrypt=(req)->
 if !userInfo
  req.result=false
  response(req)
  return
 req.result=true
 req.plainText=decEncryptText(req.encryptText,userInfo.offlinePassHash)
 response(req)

responseAuthInfo=->
 res={type:'authInfo'}
 if userInfo && userInfo.authInfo
  res.result=true
  res.authInfo=userInfo.authInfo
 else
  res.result=false
 response(res)

onRequest=(req)->
 if req.type=="encrypt"
  encrypt(req)
 else if req.type=="decrypt"
  decrypt(req)
 else if req.type=="logout"
  jQuery.ajax({
    type:'GET',
    url:'ajaxLogout',
    dataType:'json',
    success:(x)->response(x),
    error:(x)->response({type:'logout',result:false})
   })
 else if req.type=="authInfo"
  aplInfo=req.aplInfo
  if userInfoDfd
   userInfoDfd.always(responseAuthInfo)
  else
   getUserInfo(responseAuthInfo)
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
 password=jQuery('#password').val()
 if !checkPassword(loginId,password)
  alert(loginId+':認証情報に誤りがあります')
  return
 response({type:'offlineAuth',result:true,authInfo:userInfo.authInfo})
 jQuery('logonId').val('')
 jQuery('password').val('')
 return

offlineCancel=->
 jQuery('logonId').val('')
 jQuery('password').val('')
 response({type:'offlineAuth',result:false})
 return

jQuery(->
 parentOrigin=decodeURIComponent(location.search.substring('?origin='.length))
 jQuery('#logonBtn').on('click',offlineLogon)
 jQuery('#cancelBtn').on('click',offlineCancel)
 if parentOrigin=='file://'
  parentOrigin='*'
 jQuery(window).on('message',onMsg)
 response({type:'loadAuthFrame',result:true,offsetHeight:document.documentElement.offsetHeight})
)

