
OFFLINE_KEY='PaOfflineAuth'
userInfo=null
offlinePassHash=null
cdr={isIn:false,req:null}

loadRawUserInfo=(loginId)->
 userInfosText=localStorage.getItem(OFFLINE_KEY)
 if !userInfosText
  return null
 userInfos=ph.JSON.parse(userInfosText)
 userInfoTextEnc=userInfos[loginId]
 if !userInfoTextEnc
  return null
 userInfoText=decrypt(userInfoTextEnc)
 if !userInfoText
  return null
 return ph.JSON.parse(userInfoText)

saveUserInfo=(loginId,userInfo)->
 userInfosText=localStorage.getItem(OFFLINE_KEY)
 if userInfosText
  userInfos=ph.JSON.parse(userInfosText)
 else
  userInfos={}
 userInfoText=ph.JSON.stringify(userInfo)
 userInfoTextEnc=encrypt(userInfoText)
 if !userInfoTextEnc
  userInfoTextEnc=userInfoText
 userInfos[loginId]=userInfoTextEnc
 userInfosText=ph.JSON.stringify(userInfos)
 localStorage.setItem(OFFLINE_KEY,userInfosText)

getUserInfo=(loginId,passHash)->
 if offlinePassHash
  return
 offlinePassHash=passHash
 userInfo=loadRawUserInfo(loinId)
 if userInfo
  return true
 offlinePassHash=null
 return false

onRequest=(ev)->
 origin = ev.origin;
 req=ph.JSON.parse(ev.data);
 if parentOrigin!="*" && origin!=parentOrigin
  return
 if cdr.isIn
  return;##parent‚©‚çresponse‚ğ‘Ò‚½‚¸‚É‘±‚¯‚Ärequest‚³‚ê‚½iˆÙí)
 cdr.isIn=true
 cdr.req=req
 if req.type=="encrypt"
  decrypt(req)
 else if req.type=="decrypt"
  decrypt(req)
 else if req.type=="logout"
  logout()
 else if req.type=="userInfo"
  userInfo(req.appUrl,req.appSid,req.token)
 else if req.type=="offlineLogon"
 else
  resupnse({result:false,reason:'unknown type:'+req.type})

response=(msg)->
 cdr.isIn=false
 cdr.req=null
 jsonMsg=ph.JSON.stringify(msg)
 parent.postMessage(jsonMsg,parentOrigin)

jQuery(->
 parentOrigin=decodeURIComponent(location.search.substring('?origin='.length))
 if parentOrigin=='file://'
  parentOrigin='*'
 if window.addEventListener
  window.addEventListener('message',onRequest, false)
 else if window.attachEvent
  window.attachEvent('onmessage',onRequest)
)

userInfo=(appUrl,appSid,token)->
 jQuery.ajax({
  type:'POST'
  url:'userInfo'
  dataType:'json'
  data:{appUrl:appUrl,appSid:appSid,token:token}
 }).done((res)->
   if res.result
    msg.result=true
    userInfo=msg.userInfo=res.userInfo
    offlinePassHash=res.offlinePassHash
    saveUserInfo(userInfo.loginId,userInfo)
    response(msg)
   else
    response({result:false})
   return
  ).fail(->
   response({result:false})
   return
  )
 return

decrypt=(encryptText)->
 if offlinePassHash
  return CryptoJS.AES.decrypt(encryptText, offlinePassHash).toString(CryptoJS.enc.Utf8)
 return null

encrypt=(plainText)->
 if offlinePassHash
  return CryptoJS.AES.encrypt(plainText, offlinePassHash).toString()

logoff=->
 jQuery.ajax({
  type:'GET'
  url:'ajaxLogout'
  dataType:'json'
 }).done((res)->
   if res.result
    msg.result=true
    offlinePassHash=null
    userInfo=null
    response(msg)
   else
    response({result:false})
   return
  ).fail(->
   response({result:false})
   return
  )
 return


