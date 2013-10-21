class Link extends PhObject
 constructor:(@param)->
  super
  @keyUrl=@param.keyUrl
  @reqseq=0
  @reqcb={}
  @storages={}
  @ppStorage=new PrivateSessionStorage(@)
  link=@
  if @param.useConnection
   @connection=new Connection(@)
   @connection.onLoad(->
     link.load(link)
     link.trigger('linked',link)
     return
    )
   @connection.onUnload(->
     if link.ppStorage
      link.ppStorage.unload()
      link.ppStorage=null
     return
    )
  ph.on('@message',@_onMessage)
  link=@
  ph.onLoad(->
   link._frame=ph.jQuery(
     "<iframe " +
     "style='frameborder:no;background-color:#CFCFCF;overflow:auto;height:0px;width:0px;position:absolute;top:0%;left:0%;' " +
     "name='AplFrame#{link.keyUrl}' " +
     "src='#{link.keyUrl}/~ph.vsp'>" + 
     "</iframe>")
   ph.jQuery("body").append(link._frame)
   link._frameTimerId=setTimeout((->
     link._frameTimerId=null
     link._frame.remove()
     link.trigger('failToAuth',link)
     link.unload(link)
     return)
    ,ph.authFrameTimeout*2
    )
   )
  ph.on('@unload',->link.unlink())
  @
 _connect:->
  # alert('connect start2')
  # @ppStorage=new PrivateSessionStorage(@)
  link=@
  # @ppStorage.onUnload(->link._logout())
  if @param.useConnection==false
   @isConnect=false
   @ppStorage.onLoad(->
    link.trigger('ppStorage',link)
    link.load()
   )
   return
  @isConnect=true
  isWs=@param.useWs
  if !ph.useWebSocket
   isWs=false
  con=@connection
  @ppStorage.onLoad(->
    con.init(isWs)
    link.trigger('ppStorage',link)
    return
   )
  return
 _logout:->
  @_requestToAplFrame({type:'logout'})
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
   # @_frame.focus()
   return
  if res.type=='hideFrame'
   @_frame.css({'height':'0px','width':'0px','top':'0%','left':'0%'})
   return
  if res.type=='loadAplFrame'
   clearTimeout(@_frameTimerId)
   @_frameTimerId=null
   if !res.result
    @cause='fail to onlineAuth'
    @trigger('failToAuth',@)
    @unload()
   # else if res.aplInfo.loginId #TODO:認証済みなら,最適化
   #  @aplInfo=res.aplInfo
   #  @authInfo=res.authInfo
   #  @trigger('onlineAuth',@)
   #  @trigger('auth',@)
   #  @_connect()
   else if res.aplInfo.isOffline==true && @param.useOffline==false
    # offlineなのにofflineは絶対使うな指定
    @isOffline=true
    @trigger('suspendAuth',@)
   else if @param.useOffline==true || res.aplInfo.isOffline==true
    # 必ずofflineを使う指定、もしくは実際にoffline
    @isOffline=true
    @_requestToAplFrame({type:'offlineAuth'})
   else
    # online
    @isOffline=false
    # isWsは、wsでチェックするかhttpでチェックするかだが、mappingに両者登録しないとlinkアプリは正しく動かない
    # あまり意味はない
    isWs=!(@param.useWs==false) && ph.useWebSocket
    @_requestToAplFrame({type:'onlineAuth',isWs:isWs,originUrl:location.href})
  if res.type=='onlineAuth'
   if res.result=='redirectForAuthorizer'
     location.href=res.location
     return
   else if res.result==true
    # jQuery UIでdialogをopenするとaplFrameがreloadされる。http://bugs.jqueryui.com/ticket/9166
    # その延長でonlineAuthが再度通知される動作に対応
    if @aplInfo
     return
    @isOffline=false
    @aplInfo=res.aplInfo
    @authInfo=res.authInfo
    @trigger('onlineAuth',@)
    @trigger('auth',@)
    @_connect()
   else
    @cause='fail to onlineAuth'
    @trigger('failToAuth',@)
    @unload()
  else if res.type=='offlineAuth'
   if res.result==true
    @isOffline=true
    @authInfo=res.authInfo
    @aplInfo={loginId:@authInfo.user.loginId,appSid:'offline'}
    # @ppStorage=new PrivateSessionStorage(@)
    link.trigger('offlineAuth',link)
    link.trigger('auth',link)
    #link=@
    #@ppStorage.onLoad(->
    #  link.trigger('offlineAuth',link)
    #  link.trigger('auth',link)
    # )
    # @ppStorage.onUnload(->link._logout())
    @load()
   else
    @cause='fail to offlineAuth'
    @trigger('failToAuth',@)
  else if res.type=='logout'
    @_frame[0].src='about:blank'
    @_frame.remove()
    @unload()
  else if res.type=='offlineLogout'
   if res.result==true
    @trigger('suspendAuth',link)
  else if res.type=='getItem' || res.type=='changeItem' || res.type=='keys'
   storage=@storages[res.scope]
   if storage
    storage._storageTrigger(res)
  return
 offlineAuth:()->
  ### offlineで認証します。###
  @_requestToAplFrame({type:'offlineAuth'})
 offlineLogout:()->
  @_requestToAplFrame({type:'offlineLogout'})
 userProfile:()->
  @_requestToAplFrame({type:'userProfile'})
 subscribe:(qname,subname,cb)->
  @connection.subscribe(qname,subname,cb)
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
 storage:(scope)->
  if scope==ph.SCOPE.PAGE_PRIVATE || !scope
   return @ppStorage
  stor=@storages[scope]
  if !stor
    stor=new PhLocalStorage(@,scope)
    @storages[scope]=stor
  return stor
 encrypt:(plainText,cb)->
  @_requestToAplFrame({type:'encrypt',plainText:plainText},cb)
 decrypt:(encryptText,cb)->
  @_requestToAplFrame({type:'decrypt',encryptText:encryptText},cb)
 unlink:->
  if @connection
   @connection.unload()
  if @ppStorage
   @ppStorage.unload()
  for scope, storage of @storages
   storage.unload()
  @_frame.remove()
  @unload()

URL_PTN=/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
ph._links={}

# aplUrl:
# useOffline:ture=必ずoffline,false=必ずonline,undefined=onlineで試してだめならoffline
# useConnection:onlineの場合有効 true=必ずconnectする,false=必ずconnectしない,undefined=connectして失敗すればそのまま
# useWs:connect成功した場合、true=必ずwebsocketを使う,false=必ずxhrを使う、undefined=websocketに失敗すればxhr
ph.link=(aplUrl,useOffline,useConnection,useWs)->
 if !aplUrl
  pos=location.href.lastIndexOf("/")
  aplUrl=location.href.substring(0,pos)
  useOffline=false
  useConnection=false
  useWs=false
 if typeof aplUrl=='Object'
  param=aplUrl
  if !param.aplUrl
    throw 'aplUrl is null'
  aplUrl=param.aplUrl
 else
  if useConnection!=false
   useConnection=true
  if useWs!=false
   useWs=true
 param={aplUrl:aplUrl,useOffline:useOffline,useConnection:useConnection,useWs:useWs}
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
  keyUrl=aplUrl
 link=ph._links[keyUrl]
 if link
  return link
 param.keyUrl=keyUrl
 link=new Link(param)
 ph._links[keyUrl]=link
 link.onUnload(->
   link.trigger('unlinked',link)
   delete ph._links[keyUrl]
  )
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
class PrivateSessionStorage extends PhObject
 constructor:(@link)->
  super
  @link.on('auth',@_init)
 _init:=>
  aplInfo=@link.aplInfo
  @_linkPss="&#{@link.keyUrl}:#{aplInfo.loginId}:#{aplInfo.appSid}"
  ##不要なsessionStorageの刈り取り
  sameLoginIdKey="&#{@link.keyUrl}:"
  i=sessionStorage.length
  while (i-=1) >=0
   key=sessionStorage.key(i)
   if key.lastIndexOf(sameLoginIdKey,0)==0 && key!=@_linkPss
    sessionStorage.removeItem(key)
  #_storDecrypt:(storage,key,cb)->
  @encText=sessionStorage.getItem(@_linkPss)
  if @encText
   s=@
   @link.decrypt(@encText,(decText)->
    if decText
     s.data=ph.JSON.parse(decText)
    else
     s.data={}
    s.load()
    s.onUnload(s._onUnload)
   )
  else
   @data={}
   @load()
   @onUnload(@_onUnload)
  #ph.one('@unload',@_onUnload) #pageがunloadされるときsessionStorageに残す
 getItem:(key,ctx)->
  s=@
  @onLoad(->s._getItem(key,ctx))
 _getItem:(key,ctx)->
  data={key:key,scope:ph.SCOPE.PAGE_PRIVATE,value:@data[key]}
  if typeof(ctx)=='function'
    ctx(data)
  else
    @trigger(key,data,ctx)
    @trigger('@getItem',data,ctx)
  @data[key]
 keys:(ctx)->
  s=@
  @onLoad(->s._keys(ctx))
 _keys:(ctx)->
  if typeof(Object.keys)=='function'
   keys=Object.keys(@data)
  else
   keys=[]
   for key,value of @data
    keys.push(key)
  data={scope:ph.SCOPE.PAGE_PRIVATE,keys:keys}
  if typeof(ctx)=='function'
    ctx(data)
  else
    @trigger('@keys',data,ctx)
  keys
 setItem:(key,value)->
  s=@
  @onLoad(->s._setItem(key,value))
 _setItem:(key,value)->
  oldValue=@data[key]
  @data[key]=value
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 removeItem:(key)->
  s=@
  @onLoad(->s._removeItem(key))
 _removeItem:(key)->
  oldValue=@data[key]
  if !oldValue
   return
  delete @data[key]
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 _onUnload:=>
  # ph.off('unload',@_unload)
  if @encText
   sessionStorage.setItem(@_linkPss,@encText)
  @trigger('@save',@)

#-------------------PageLocalStorage-------------------
class PhLocalStorage extends PhObject
  constructor:(@link,@scope)->
    super
    @ctxs={}
    @ctxIdx=0
    if @link.isLoading()
      s=@
      link.onLoad(->s.load())
    else
      @load()
  getItem:(key,ctx)->
    s=@
    @onLoad(->s._getItem(key,ctx))
  _getItem:(key,ctx)->
    @ctxs[@ctxIdx]=ctx
    @link._requestToAplFrame({type:'getItem',scope:@scope,key:key,ctxIdx:@ctxIdx,via:0})
    @ctxIdx++;
    return
  setItem:(key,value)->
    s=@
    @onLoad(->s._setItem(key,value))
  _setItem:(key,value)->
    @link._requestToAplFrame({type:'setItem',scope:@scope,key:key,value:value,via:0})
    return
  removeItem:(key)->
    s=@
    @onLoad(->s._removeItem(key))
  _removeItem:(key)->
    @link._requestToAplFrame({type:'removeItem',scope:@scope,key:key,via:0})
    return
  keys:(ctx)->
    s=@
    @onLoad(->s._keys(ctx))
  _keys:(ctx)->
    @ctxs[@ctxIdx]=ctx
    @link._requestToAplFrame({type:'keys',scope:@scope,ctxIdx:@ctxIdx,via:0})
    @ctxIdx++;
    return
  _storageTrigger:(data)->
    ctx=@ctxs[data.ctxIdx]
    delete @ctxs[data.ctxIdx]
    if typeof(ctx)=='function'
      ctx(data)
    else if data.type=='getItem'
      @trigger('@getItem',data,ctx)
      @trigger(data.key,data,ctx)
    else if data.type=='keys'
      @trigger('@keys',data,ctx)
    else if data.type=='changeItem'
      data.value=data.newValue
      @trigger('@changeItem',data)
      @trigger(data.key,data)

