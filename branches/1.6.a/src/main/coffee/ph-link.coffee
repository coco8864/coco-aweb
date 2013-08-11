#-------------------Link-------------------
class Link extends ph.Deferred
 constructor:(@keyUrl,@isOffline,@isWs,@storeOnly)->
  super
  @status='init'
  @reqseq=0
  @reqcb={}
  if !@isOffline && !@storeOnly
   @connection=new Connection(@)
  ph.on('message',@_onMessage)
  @_frame=ph.jQuery(
    "<iframe " +
    "style='frameborder:no;background-color:#CFCFCF;overflow:auto;height:0px;width:0px;position:absolute;top:0%;left:0%;' " +
    "name='AplFrame#{@keyUrl}' " +
    "src='#{@keyUrl}/~ph.vsp'>" + 
    "</iframe>")
  ph.jQuery("body").append(@_frame)
  @
 _connect:->
  alert('connect start')
  @ppStorage=new PrivateSessionStorage(@)
  con=@connection
  @ppStorage.onLoad(->con.init())
  con
 _requestToAplFrame:(msg,cb)->
  if cb
    @reqseq++
    msg.reqseq=@reqseq
    @reqcb[msg.reqseq]=cb
  jsonMsg=ph.JSON.stringify(msg)
  @_frame[0].contentWindow.postMessage(jsonMsg,'*')
 _onMessage:(qjev)=>
  ev=qjev.originalEvent
  if !@_frame || ev.source!=@_frame[0].contentWindow
   return
  res=ph.JSON.parse(ev.data)
  if res.reqseq
   cb=@reqcb[res.reqseq]
   delete @reqcb[res.reqseq]
   if !res.result
    cb(null)
   else if res.type=='encrypt'
    cb(res.encryptText)
   else if res.type=='decrypt'
    cb(res.plainText)
   return
  if res.type=='showFrame'
   @_frame.css({'height':"#{res.height}px",'width':'500px','top':'100px','left':'50%','margin-top':'0px','margin-left':'-250px'})
   return
  if res.type=='hideFrame'
   @_frame.css({'height':'0px','width':'0px','top':'0%','left':'0%'})
   return
  if res.type=='loadAplFrame'
   if res.loginId #認証済みなら
    @aplInfo=res.aplInfo
    @trigger('onlineAuth',@)
    @trigger('auth',@)
    @_connect()
   else if res.isOffline || @isOffline
    @_requestToAplFrame({type:'offlineAuth'})
   else
    @_requestToAplFrame({type:'onlineAuth',isWs:@isWs,originUrl:location.href})
  if res.type=='onlineAuth'
   if res.result=='redirectForAuthorizer'
     location.href=res.location
     return
   else if res.result==true
    @aplInfo=res.aplInfo
    @authInfo=res.authInfo
    @trigger('onlineAuth',@)
    @trigger('auth',@)
    @_connect()
   else
    @cause='fail to onlineAuth'
    @trigger('failToAuth',@)
    @unload()
  if res.type=='offlineAuth'
   if res.result==true
    @authInfo=res.authInfo
    @aplInfo={loginId:@authInfo.user.loginId}
    @trigger('onfflineAuth',@)
    @trigger('auth',@)
   else
    @cause='fail to offlineAuth'
    @trigger('failToAuth',@)
    @unload()
 _storDecrypt:(storage,key,cb)->
  encText=storage.getItem(key)
  if encText
   @decrypt(encText,(decText)->cb(decText))
  else
   cb(null)
 _encryptStor:(storage,key,value,cb)->
  @encrypt(value,(encText)->
    storage.setItem(key,encText)
    if cb
     cb(encText)
    )
 subscribe:(qname,subname)->
  @connection.subscribe(qname,subname)
 publish:(qname,msg)->
  @connection.publish(qname,msg)
 publishForm:(formId,qname,subname)->
  @connection.publishForm(formId,qname,subname)
 qnames:(cb)->
  @connection.qnames(cb)
 deploy:(qname,className)->
  @connection.deploy(qname,className)
 undeploy:(qname)->
  @connection.undeploy(qname)
 store:(scope)->
 encrypt:(plainText,cb)->
  @_requestToAplFrame({type:'encrypt',plainText:plainText},cb)
 decrypt:(encryptText,cb)->
  @_requestToAplFrame({type:'decrypt',encryptText:encryptText},cb)
 unlink:->
  if @connection
   @connection.close()
  @ppStorage.close()
  @_requestToAplFrame({type:'logout'})

URL_PTN=/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
ph._links=[]
ph.link=(aplUrl,isOffline)->
 isXhr=false
 aplUrl.match(URL_PTN)
 protocol=RegExp.$1
 aplDomain=RegExp.$2
 aplPath=RegExp.$3
 if protocol=='ws:'
  keyUrl="http://#{aplDomain}#{aplPath}"
 else if protocol=='wss:'
  keyUrl="https://#{aplDomain}#{aplPath}"
 else if protocol==null || protocol==''
  if ph.isSsl
   keyUrl="https://#{ph.domain}#{aplPath}"
  else
   keyUrl="http://#{ph.domain}#{aplPath}"
 else #http or https
  isXhr=true
  keyUrl=aplUrl
 link=ph._links[keyUrl]
 if link
  return link
 link=new Link(keyUrl,isOffline,!isXhr)
 ph._links[keyUrl]=pa
 link.onUnload(->delete ph._links[keyUrl])
 return link

#strage scope
#  SCOPE_PAGE_PRIVATE:'pagePrivate' ...そのページだけ、reloadを挟んで情報を維持するため
#  SCOPE_SESSION_PRIVATE:'sessionPrivate'...開いている同一セションのwindow間で情報を共有
#  SCOPE_APL_PRIVATE:'aplPrivate'...当該apl当該userの情報を保持
#  SCOPE_APL_LOCAL:'aplLocal'...当該aplの情報を保持
#  SCOPE_APL:'apl'
#  SCOPE_QNAME:'qname'
#  SCOPE_SUBNAME:'subname'
#  SCOPE_USER:'user'

#-------------------PagePrivateStorage-------------------
class PrivateSessionStorage extends ph.Deferred
 constructor:(@link)->
  super
  @status='init'
  aplInfo=@link.aplInfo
  @_paPss="_paPss:#{@link.keyUrl}:#{aplInfo.loginId}:#{aplInfo.appSid}"
  ##不要なsessionStorageの刈り取り
  sameLoginIdKey="_paPss:#{@link.keyUrl}:"
  i=sessionStorage.length
  while (i-=1) >=0
   key=sessionStorage.key(i)
   if key.lastIndexOf(sameLoginIdKey,0)==0 && key!=@_paPss
    sessionStorage.removeItem(key)
  s=@
  @link._storDecrypt(sessionStorage,@_paPss,(decText)->
    if decText
     s.data=ph.JSON.parse(decText)
    else
     s.data={}
    s.load()
  )
  ph.one('unload',@_unload) #pageがunloadされるときsessionStorageに残す
  @onUnload(@_unload)
 getItem:(key)->
  @data[key]
 setItem:(key,value)->
  oldValue=@data[key]
  @data[key]=value
  @trigger(key,{key:key,oldValue:oldValue,newValue:value})
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 removeItem:(key)->
  oldValue=@data[key]
  if !oldValue
   return
  delete @data[key]
  @trigger(key,{key:key,oldValue:oldValue})
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 close:->
  @unload()
  @
 _unload:=>
  ph.off('unload',@_unload)
  if @encText
   sessionStorage.setItem(@_paPss,@encText)
  @trigger('save',@)

