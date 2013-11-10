OFFLINE_KEY='LinkOffInfo:'
OFFLINE_USERS_KEY='LinkOffUsers'
OFFLINE_SALT_KEY='@offlinePassSalt'
parentOrigin=null
aplInfo=null
userInfo=null ##{authInfo:{},offlinePassHash:''}
userInfoDfd=null
isOffline=false
isAuth=false
isDisplay=false
offlineTargetAplUrl=null

sessionPrivatePrefix=null #'#userid.sid.'
authPrivatePrefix=null #'$userid.'
authLocalPrefix='$.'

calcPassHash=(loginId,password)->
  return CryptoJS.SHA256(offlinePassSalt+":"+loginId+":"+password).toString()

checkPassword=(loginId,password)->
  userInfo=null
  encryptText=localStorage.getItem(OFFLINE_KEY+loginId)
  if !encryptText
    return false
  passHash=calcPassHash(loginId,password)
  plainText=decryptText(encryptText,passHash)
  if plainText==null
    return false
  try
    userInfo=ph.JSON.parse(plainText)
    userInfo.offlinePassHash=passHash #passwordを省略された場合違うかもしれない
    sessionPrivatePrefix=null
    authPrivatePrefix='$'+userInfo.authInfo.user.loginId+'@'
  catch error
    return false
  return true

deleteUserItem=(loginId)->
  i=localStorage.length
  while (i-=1) >=0
    key=localStorage.key(i)
    if key.lastIndexOf('$'+loginId+'@')==0
      localStorage.removeItem(key)
  localStorage.removeItem(OFFLINE_KEY+loginId)

checkItems=(users)->
  i=localStorage.length
  while (i-=1) >=0
    key=localStorage.key(i)
    pos=key.indexOf('@')
    loginId=null
    if key.lastIndexOf('$')==0 && pos>0
      loginId=key.substring(1,pos)
    else if key.lastIndexOf('OFFLINE_KEY')==0
      loginId=key.substring(pos+1)
    if loginId && users[loginId]==undefined
      localStorage.removeItem(key)

getUsers=->
  usersText=localStorage.getItem(OFFLINE_USERS_KEY)
  if usersText
    users=ph.JSON.parse(usersText)
    salt=users[OFFLINE_SALT_KEY]
    delete users[OFFLINE_SALT_KEY]
    if salt==offlinePassSalt
      return users
    for loginId,nickname of users
      deleteUserItem(loginId)
  return {}

saveUsers=(users)->
  users[OFFLINE_SALT_KEY]=offlinePassSalt
  usersText=ph.JSON.stringify(users)
  localStorage.setItem(OFFLINE_USERS_KEY,usersText)

saveUserInfo=->
  if !userInfo
    return
  #publicの場合localstorageに覚える必要なし
  if !userInfo.authInfo.user.loginId
    return
  loginId=userInfo.authInfo.user.loginId
  text=ph.JSON.stringify(userInfo)
  encText=encryptText(text,userInfo.offlinePassHash)
  localStorage.setItem(OFFLINE_KEY+loginId,encText)
  users=getUsers()
  users[loginId]=userInfo.authInfo.user.nickname
  saveUsers(users)

getUserInfo=(cb)->
  userInfoDfd=jQuery.ajax({
    type:'POST',
    url:'userInfo',
    dataType:'json',
    data:aplInfo
    })
  userInfoDfd.done((x)->
    userInfo=x
    loginId=userInfo.authInfo.user.loginId
    if !userInfo.offlinePassHash
      userInfo.offlinePassHash=calcPassHash(loginId,"")
    sessionPrivatePrefix='#'+userInfo.authSid+'@'
    authPrivatePrefix='$'+loginId+'@'
    ##不要なsessionPrivateの刈り取り
    i=localStorage.length
    while (i-=1) >=0
      key=localStorage.key(i)
      if key.lastIndexOf('#')==0 && key.lastIndexOf(sessionPrivatePrefix)!=0
        localStorage.removeItem(key)
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

encryptText=(plainText,passHash)->
  if passHash && plainText!=null && typeof(plainText)!='undefined'
    return CryptoJS.AES.encrypt(plainText,passHash).toString()
  else
    return plainText

decryptText=(encryptText,passHash)->
  if passHash && encryptText!=null && typeof(encryptText)!='undefined'
    try
      return CryptoJS.AES.decrypt(encryptText, passHash).toString(CryptoJS.enc.Utf8)
    catch error
      return null
  else
    return encryptText

encrypt=(req)->
  if !userInfo
    req.result=false
    response(req)
    return
  req.result=true
  req.encryptText=encryptText(req.plainText,userInfo.offlinePassHash)
  response(req)

decrypt=(req)->
  if !userInfo
    req.result=false
    response(req)
    return
  req.result=true
  req.plainText=decryptText(req.encryptText,userInfo.offlinePassHash)
  response(req)

responseAuthInfo=->
  res={type:'authInfo'}
  res.result=true
  res.authInfo=userInfo.authInfo
  # aplとuserの同期をとるため登録されているuserを付加
  res.users=getUsers()
  ###
  if userInfo.authInfo.user.loginId!=""
    res.result=true
    res.authInfo=userInfo.authInfo
    # aplとuserの同期をとるため登録されているuserを付加
    res.users=getUsers()
  else
    res.authInfo=userInfo.authInfo
    res.result=false
  ###
  response(res)

SCOPE={
  PAGE_PRIVATE:'pagePrivate'
  SESSION_PRIVATE:'sessionPrivate' #auth localstorage key:sessionid.key=value
  APL_PRIVATE:'aplPrivate' #apl localstorage key:loginid.key=value
  APL_LOCAL:'aplLocal' #apl localstorage key:@.key=value  (no enc)
  AUTH_PRIVATE:'authPrivate' #auth localstorage key:loginid.key=value
  AUTH_LOCAL:'authLocal' #auth localstorage key:@.key=value (no enc)
}

scopePrefix=(scope)->
  if scope==SCOPE.SESSION_PRIVATE
    return sessionPrivatePrefix
  else if scope==SCOPE.AUTH_PRIVATE
    return authPrivatePrefix
  else if scope==SCOPE.AUTH_LOCAL
    return authLocalPrefix
  throw 'unkown scope:'+scope

scopeKey=(scope,key)->
  prefix=scopePrefix(scope)
  if prefix==null
    return null
  return scopePrefix(scope)+key

onGetItemRequest=(data)->
  data.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE
    # key=encryptText(data.key,userInfo.offlinePassHash)
    realKey=scopeKey(data.scope,data.key)
    if realKey!=null
      value=localStorage.getItem(realKey)
      data.value=decryptText(value,userInfo.offlinePassHash)
  else if data.scope==SCOPE.APL_PRIVATE
    if typeof(data.encKey)=='undefined'
      # data.encKey=encryptText(data.key,userInfo.offlinePassHash)
      data.encKey=data.key
    else
      data.value=decryptText(data.encValue,userInfo.offlinePassHash)
  else if data.scope==SCOPE.AUTH_LOCAL
    realKey=scopeKey(data.scope,data.key)
    if realKey!=null
      data.value=localStorage.getItem(realKey)
  else
    throw 'scope error:'+data.scope
  response(data)

onSetItemRequest=(data)->
  data.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE
    # key=encryptText(data.key,userInfo.offlinePassHash)
    realKey=scopeKey(data.scope,data.key)
    value=encryptText(data.value,userInfo.offlinePassHash)
    if realKey!=null
      localStorage.setItem(realKey,value)
  else if data.scope==SCOPE.APL_PRIVATE
    # data.encKey=encryptText(data.key,userInfo.offlinePassHash)
    data.encKey=data.key
    data.encValue=encryptText(data.value,userInfo.offlinePassHash)
    response(data)
  else if data.scope==SCOPE.AUTH_LOCAL
    realKey=scopeKey(data.scope,data.key)
    if realKey!=null
      localStorage.setItem(realKey,data.value)
  else
    throw 'scope error:'+data.scope

onRemoveItemRequest=(data)->
  data.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE
    # key=encryptText(data.key,userInfo.offlinePassHash)
    realKey=scopeKey(data.scope,data.key)
    if realKey!=null
      localStorage.removeItem(realKey)
  else if data.scope==SCOPE.APL_PRIVATE
    # data.encKey=encryptText(data.key,userInfo.offlinePassHash)
    data.encKey=data.key
    response(data)
  else if data.scope==SCOPE.AUTH_LOCAL
    realKey=scopeKey(data.scope,data.key)
    if realKey!=null
      localStorage.removeItem(realKey)
  else
    throw 'scope error:'+data.scope

enumScopeKey=(scope)->
  prefix=scopePrefix(scope)
  keys=[]
  i=localStorage.length
  while (i-=1) >=0
    key=localStorage.key(i)
    if key.lastIndexOf(prefix,0)==0
      keys.push(key.substring(prefix.length))
  return keys

decryptKeys=(keys)->
  if keys.length==0
    return
  for i in [0..keys.length-1]
    key[i]=decryptText(key[i],userInfo.offlinePassHash)

onkeysRequest=(data)->
  data.via++
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE
    data.keys=enumScopeKey(data.scope)
    # decryptKeys(data.keys)
  else if data.scope==SCOPE.APL_PRIVATE
    # decryptKeys(data.keys)
  else if data.scope==SCOPE.AUTH_LOCAL
    data.keys=enumScopeKey(data.scope)
  else
    throw 'scope error:'+data.scope
  response(data)

onChangeItemRequest=(data)->
  data.via++
  if data.scope==SCOPE.APL_PRIVATE
    # data.key=decryptText(data.encKey,userInfo.offlinePassHash)
    data.key=data.encKey
    data.newValue=decryptText(data.encNewValue,userInfo.offlinePassHash)
    data.oldValue=decryptText(data.encOldValue,userInfo.offlinePassHash)
  else
    throw 'scope error:'+data.scope
  response(data)

onRequest=(req)->
  if req.type=="encrypt"
    encrypt(req)
  else if req.type=="decrypt"
    decrypt(req)
  else if req.type=="getItem"
    onGetItemRequest(req)
  else if req.type=="setItem"
    onSetItemRequest(req)
  else if req.type=="removeItem"
    onRemoveItemRequest(req)
  else if req.type=="keys"
    onkeysRequest(req)
  else if req.type=="changeItem"
    onChangeItemRequest(req)
  else if req.type=="logout"
    jQuery.ajax({
      type:'POST',
      url:'ajaxLogout',
      dataType:'json',
      success:((res)->
        userInfo=null
        aplInfo=null
        response({type:'logout',result:true,detail:res})),
      error:(x)->response({type:'logout',result:false})
    })
  else if req.type=="authInfo"
    aplInfo=req.aplInfo
    if userInfoDfd
      userInfoDfd.always(responseAuthInfo)
    else
      getUserInfo(responseAuthInfo)
  else if req.type=="offlineAuth"
    if isDisplay
      # response({type:'offlineAuth',result:false,cause:'aleady display'})
      return
    offlineTargetAplUrl=req.aplUrl
    jQuery('#ofMessage').text('please enter password')
    jQuery('#password').val('')
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
    isDisplay=true
    response({type:'showFrame',height:document.body.clientHeight});
  else if req.type=="offlineLogout"
    result=false
    if userInfo
      result=true
    userInfo=null
    sessionPrivatePrefix=null
    authPrivatePrefix=null
    isOffline=true
    isAuth=false
    response({type:'offlineLogout',result:result})
  else if req.type=="userProfile"
    if isDisplay
      # response({type:'userProfile',result:false,cause:'aleady display'})
      return
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
    isDisplay=true
    response({type:'showFrame',height:document.body.clientHeight});
  else
    throw 'unkown type:'+req.type

onMsg=(qjev)->
  ev=qjev.originalEvent
  if !ev.data
    return
  if ev.source==parent
    req=ph.JSON.parse(ev.data)
    onRequest(req)

setupScope=(data,realKey)->
  if sessionPrivatePrefix && realKey.lastIndexOf(sessionPrivatePrefix,0)==0
    data.scope=SCOPE.SESSION_PRIVATE
    realKey=realKey.substring(sessionPrivatePrefix.length)
    # data.key=decryptText(realKey,userInfo.offlinePassHash)
    data.key=realKey
  else if authPrivatePrefix && realKey.lastIndexOf(authPrivatePrefix,0)==0
    data.scope=SCOPE.AUTH_PRIVATE
    realKey=realKey.substring(authPrivatePrefix.length)
    # data.key=decryptText(realKey,userInfo.offlinePassHash)
    data.key=realKey
  else if realKey.lastIndexOf(authLocalPrefix,0)==0
    data.scope=SCOPE.AUTH_LOCAL
    data.key=realKey.substring(authLocalPrefix.length)
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
  if data.scope==SCOPE.SESSION_PRIVATE || data.scope==SCOPE.AUTH_PRIVATE
    data.newValue=decryptText(ev.newValue,userInfo.offlinePassHash)
    data.oldValue=decryptText(ev.oldValue,userInfo.offlinePassHash)
    response(data)
  else if data.scope==SCOPE.AUTH_LOCAL
    data.newValue=ev.newValue
    data.oldValue=ev.oldValue
    response(data)

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
    # alert(loginId+':wrong password')
    jQuery('#ofMessage').text('wrong password')
    jQuery('#loginIds').focus()
    isAuth=false
    return
  #利用可能なaplUrlかチェック
  authInfo=userInfo.authInfo
  check=false
  for allow in authInfo.allowUrls
    if allow.url==offlineTargetAplUrl
      check=true
      break
  if !check
    # alert(loginId+':lack of right')
    jQuery('#ofMessage').text('lack of right')
    jQuery('#loginIds').focus()
    isAuth=false
    return
  isAuth=true
  response({type:'offlineAuth',result:true,authInfo:userInfo.authInfo})
  response({type:'hideFrame'})
  isDisplay=false
  jQuery('logonId').val('')
  jQuery('password').val('')
  return

offlineCancel=->
  jQuery('logonId').val('')
  jQuery('password').val('')
  response({type:'offlineAuth',result:false,cause:'cancel'})
  response({type:'hideFrame'})
  isDisplay=false
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
      response({type:'hideFrame'})
      isDisplay=false
    else
      jQuery('#pfMessage').text(res.cause)
  )
  updateUserProfileDfd.fail((res)->
    jQuery('#pfMessage').text('communication error')
  )
  return

profileCancel=->
  response({type:'userProfile',result:false,cause:'cancel'})
  response({type:'hideFrame'})
  isDisplay=false
  return

removeOfflineUser=->
  loginId=prompt('enter remove loginId')
  if loginId==null || loginId==''
    return
  users=getUsers()
  if !users[loginId]
    return
  deleteUserItem(loginId)
  delete users[loginId]
  saveUsers(users)

jQuery(->
  parentOrigin=decodeURIComponent(location.search.substring('?origin='.length))
  jQuery('#logonBtn').on('click',offlineLogon)
  jQuery('#cancelBtn').on('click',offlineCancel)
  jQuery('#pfUpdateBtn').on('click',profileUpdate)
  jQuery('#pfCancelBtn').on('click',profileCancel)
  jQuery('#pfRemoveUser').on('click',removeOfflineUser)
  if parentOrigin=='file://'
    parentOrigin='*'
  jQuery(window).on('message',onMsg)
  jQuery(window).on('storage',onStorage)
  users=getUsers()
  checkItems(users)
  response({type:'loadAuthFrame',result:true,offsetHeight:document.documentElement.offsetHeight})
)

