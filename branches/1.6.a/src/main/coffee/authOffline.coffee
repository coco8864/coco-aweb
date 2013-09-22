OFFLINE_KEY='LinkOffInfo:'
OFFLINE_USERS_KEY='LinkOffUsers'
parentOrigin=null
aplInfo=null
userInfo=null ##{authInfo:{},offlinePassHash:''}
userInfoDfd=null
isOffline=false
isAuth=false

checkPassword=(loginId,password)->
 userInfo=null
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

getUsers=->
 usersText=localStorage.getItem(OFFLINE_USERS_KEY)
 users={}
 if usersText
  users=ph.JSON.parse(usersText)
 return users

saveUserInfo=->
 if !userInfo
  return
 #publicの場合localstorageに覚える必要なし
 if !userInfo.authInfo.user.loginId
  return
 loginId=userInfo.authInfo.user.loginId
 text=ph.JSON.stringify(userInfo)
 encText=encPlainText(text,userInfo.offlinePassHash)
 localStorage.setItem(OFFLINE_KEY+loginId,encText)
 users=getUsers()
 users[loginId]=userInfo.authInfo.user.nickname
 usersText=ph.JSON.stringify(users)
 localStorage.setItem(OFFLINE_USERS_KEY,usersText)

getUserInfo=(cb)->
 userInfoDfd=jQuery.ajax({
  type:'POST',
  url:'userInfo',
  dataType:'json',
  data:aplInfo
 })
 userInfoDfd.done((x)->
   userInfo=null
   userInfo=x
   if cb
    cb()
   isOffline=false
   isAuth=true
   saveUserInfo()
  )
   
 userInfoDfd.fail((x)->
   userInfo=null
   if cb
    cb()
   isAuth=false
  )

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
    success:(x)->response({type:'logout',result:true,x:x}),
    error:(x)->response({type:'logout',result:false})
   })
 else if req.type=="authInfo"
  aplInfo=req.aplInfo
  if userInfoDfd
   userInfoDfd.always(responseAuthInfo)
  else
   getUserInfo(responseAuthInfo)
 else if req.type=="offlineAuth"
  users=getUsers()
  count=0
  select=jQuery('#loginIds')
  select.empty()
  for loginid of users
   nickname=users[loginid]
   select.append('<option value="'+loginid+'">' + nickname+'</option>')
   count++
  if count==0
   alert('there is no offline authentication user')
   response({type:'offlineAuth',result:false,cause:'no user'})
   return
  select.focus()
  jQuery('#offlineLogon').show()
  jQuery('#userProfile').hide()
  jQuery('#loginId').val(select.val())
 else if req.type=="offlineLogout"
  result=false
  if userInfo
   result=true
  userInfo=null
  isOffline=true
  isAuth=false
  response({type:'offlineLogout',result:result})
 else if req.type=="userProfile"
  jQuery('#orgPassword').val('')
  jQuery('#password1').val('')
  jQuery('#password2').val('')
  jQuery('#orgOfflinePass').val('')
  jQuery('#offlinePass1').val('')
  jQuery('#offlinePass2').val('')
  if !isAuth
   response({type:'userProfile',result:false,cause:'not login'})
   return
  user=userInfo.authInfo.user
  jQuery('#pfLoginId').val(user.loginId)
  jQuery('#pfNickname').val(user.nickname)
  jQuery('#passwordInputs').show()
  jQuery('#offlinePassInputs').show()
  jQuery('#pfNickname').attr('readonly', false)
  jQuery('#pfUpdateBtn').show()
  jQuery('#pfCancelBtn').show()
  if isOffline
   jQuery('#passwordInputs').hide()
   jQuery('#offlinePassInputs').hide()
   jQuery('#pfMessage').text('now offline refrence only')
   jQuery('#pfNickname').attr('readonly', true)
   jQuery('#pfUpdateBtn').hide()
  else if user.origin
   jQuery('#pfMessage').text('please update your profile')
   jQuery('#passwordInputs').hide()
  else
   jQuery('#pfMessage').text('please update your profile')
  jQuery('#offlineLogon').hide()
  jQuery('#userProfile').show()
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
 isOffline=true
 if !checkPassword(loginId,password)
  alert(loginId+':認証情報に誤りがあります')
  jQuery('#loginIds').focus()
  isAuth=false
  return
 isAuth=true
 response({type:'offlineAuth',result:true,authInfo:userInfo.authInfo})
 jQuery('logonId').val('')
 jQuery('password').val('')
 return

offlineCancel=->
 jQuery('logonId').val('')
 jQuery('password').val('')
 response({type:'offlineAuth',result:false,cause:'cancel'})
 return

profileUpdate=->
 if isOffline
  response({type:'userProfile',result:false,cause:'offline'})
  return
 user=userInfo.authInfo.user
 req={token:userInfo.authInfo.token}
 req.nickname=jQuery('#pfNickname').val()
 if !user.origin
  req.orgPassword=jQuery('#orgPassword').val()
  req.password1=jQuery('#password1').val()
  req.password2=jQuery('#password2').val()
 req.orgOfflinePass=jQuery('#orgOfflinePass').val()
 req.offlinePass1=jQuery('#offlinePass1').val()
 req.offlinePass2=jQuery('#offlinePass2').val()

 updateUserProfileDfd=jQuery.ajax({
  type:'POST',
  url:'updateUserProfile',
  dataType:'json',
  data:req
 })
 updateUserProfileDfd.done((res)->
   if res.result
    userInfo.authInfo.user=res.user
    if res.offlinePassHash
     userInfo.offlinePassHash=res.offlinePassHash
    response({type:'userProfile',result:true})
   else
    jQuery('#pfMessage').text(res.cause)
  )
 updateUserProfileDfd.fail((res)->
   jQuery('#pfMessage').text('communication error')
  )
 return

profileCancel=->
 response({type:'userProfile',result:false,cause:'cancel'})
 return

jQuery(->
 parentOrigin=decodeURIComponent(location.search.substring('?origin='.length))
 jQuery('#logonBtn').on('click',offlineLogon)
 jQuery('#cancelBtn').on('click',offlineCancel)

 jQuery('#pfUpdateBtn').on('click',profileUpdate)
 jQuery('#pfCancelBtn').on('click',profileCancel)

 if parentOrigin=='file://'
  parentOrigin='*'
 jQuery(window).on('message',onMsg)
 response({type:'loadAuthFrame',result:true,offsetHeight:document.documentElement.offsetHeight})
)

