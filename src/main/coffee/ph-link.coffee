#-------------------Link-------------------
class Link extends ph.Deferred
 constructor:(@param)->
  super
  @keyUrl=@param.keyUrl
  @status='init'
  @reqseq=0
  @reqcb={}
  link=@
  if !@param.useOffline && !@param.useConnection
   @connection=new Connection(@)
   @connection.onLoad(->
     link.load()
    )
   @connection.onUnload(->
     if link.ppStorage
      link.ppStorage.close()
      link.ppStorage=null
    )
  ph.on('message',@_onMessage)
  @_frame=ph.jQuery(
    "<iframe " +
    "style='frameborder:no;background-color:#CFCFCF;overflow:auto;height:0px;width:0px;position:absolute;top:0%;left:0%;' " +
    "name='AplFrame#{@keyUrl}' " +
    "src='#{@keyUrl}/~ph.vsp'>" + 
    "</iframe>")
  ph.jQuery("body").append(@_frame)
  @_frameTimerId=setTimeout((->
    link._frameTimerId=null
    link._frame.remove()
    link.trigger('failToAuth',link)
    link.unload())
   ,10000
   )
  @
 _connect:->
#  alert('connect start2')
  @ppStorage=new PrivateSessionStorage(@)
  link=@
  @ppStorage.onUnload(->link._logout())
  if @param.useConnection==false
   @isConnect=false
   @ppStorage.onLoad(->link.trigger('ppStorage',link))
   return
  @isConnect=true
  isWs=@param.useWs
  con=@connection
  @ppStorage.onLoad(->
    con.init(isWs)
    link.trigger('ppStorage',link)
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
#   else if res.aplInfo.loginId #TODO:�F�؍ς݂Ȃ�,�œK��
#    @aplInfo=res.aplInfo
#    @authInfo=res.authInfo
#    @trigger('onlineAuth',@)
#    @trigger('auth',@)
#    @_connect()
   else if res.isOffline==false && @param.useOffline==true
    @cause='cannot use offline'
    @trigger('failToAuth',@)
    @unload()
   else if res.isOffline==true && @param.useOffline==false
    @cause='cannot use online'
    @trigger('failToAuth',@)
    @unload()
   else if res.isOffline==true || @param.useOffline==true
    @isOffline=true
    @_requestToAplFrame({type:'offlineAuth'})
   else
    @isOffline=false
##isWs�́Aws�Ń`�F�b�N���邩http�Ń`�F�b�N���邩�����Amapping�ɗ��ғo�^���Ȃ���link�A�v���͐����������Ȃ�
##���܂�Ӗ��͂Ȃ�
    isWs=!(@param.useWs==false) 
    @_requestToAplFrame({type:'onlineAuth',isWs:isWs,originUrl:location.href})
  if res.type=='onlineAuth'
   if res.result=='redirectForAuthorizer'
     location.href=res.location
     return
   else if res.result==true
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
  if res.type=='offlineAuth'
   if res.result==true
    @isOffline=true
    @authInfo=res.authInfo
    @aplInfo={loginId:@authInfo.user.loginId,appSid:'offline'}
    @ppStorage=new PrivateSessionStorage(@)
    link=@
    @ppStorage.onLoad(->
      link.trigger('ppStorage',link)
      link.trigger('onfflineAuth',link)
      link.trigger('auth',link)
      link.load()
     )
    @ppStorage.onUnload(->link._logout())
   else
    @cause='fail to offlineAuth'
    @trigger('failToAuth',@)
    @unload()
  if res.type=='logout'
    @_frame[0].src='about:blank'
    @_frame.remove()
    @unload()
  return
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
 storage:(scope)->
  if scope==ph.SCOPE_PAGE_PRIVATE
   return @ppStorage
  else if scope==ph.SCOPE_SESSION_PRIVATE
   return @ppStorage
  else if scope==ph.SCOPE_APL_PRIVATE
   return @ppStorage
  else if scope==ph.SCOPE_APL_LOCAL
   return @ppStorage
  throw 'unkown scope:'+scope
 encrypt:(plainText,cb)->
  @_requestToAplFrame({type:'encrypt',plainText:plainText},cb)
 decrypt:(encryptText,cb)->
  @_requestToAplFrame({type:'decrypt',encryptText:encryptText},cb)
 unlink:->
  if @connection
   @connection.close()
   return
  @ppStorage.close()

URL_PTN=/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
ph._links=[]

# aplUrl:
# useOffline:ture=�K��offline,false=�K��online,undefined=online�Ŏ����Ă��߂Ȃ�offline
# useConnection:online�̏ꍇ�L�� true=�K��connect����,false=�K��connect���Ȃ�,undefined=connect���Ď��s����΂��̂܂�
# useWs:connect���������ꍇ�Atrue=�K��websocket���g��,false=�K��xhr���g���Aundefined=websocket�Ɏ��s�����xhr
ph.link=(aplUrl,useOffline,useConnection,useWs)->
 if ph.jQuery.isPlainObject(aplUrl)
  param=aplUrl
  if !param.aplUrl
    throw 'illegal argument'
  aplUrl=param.aplUrl
 else
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
  param.useWs=false
  keyUrl=aplUrl
 link=ph._links[keyUrl]
 if link
  return link
 param.keyUrl=keyUrl
 link=new Link(param)
 ph._links[keyUrl]=link
 link.onUnload(->delete ph._links[keyUrl])
 return link

#strage scope
#  SCOPE_PAGE_PRIVATE:'pagePrivate' ...���̃y�[�W�����Areload������ŏ����ێ����邽��
#  SCOPE_SESSION_PRIVATE:'sessionPrivate'...�J���Ă��铯��Z�V������window�Ԃŏ������L
#  SCOPE_APL_PRIVATE:'aplPrivate'...���Yapl���Yuser�̏���ێ�
#  SCOPE_APL_LOCAL:'aplLocal'...���Yapl�̏���ێ�
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
  ##�s�v��sessionStorage�̊�����
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
  ph.one('unload',@_unload) #page��unload�����Ƃ�sessionStorage�Ɏc��
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
