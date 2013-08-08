#-------------------PaHandler-------------------
class PaHandler extends ph.Deferred
 constructor:(@keyUrl,@isOffline,@isWs)->
  super
  @status='init'
  ph.on('message',@_onMessage)
  @_frame=ph.jQuery(
    "<iframe " +
    "style='frameborder:no;background-color:#CFCFCF;overflow:auto;height:0px;width:0px;position:absolute;top:0%;left:0%;margin-top:-100px;margin-left:-150px;' " +
    "name='AplFrame#{@keyUrl}' " +
    "src='#{@keyUrl}/~ph.html'>" + 
    "</iframe>")
  ph.jQuery("body").append(@_frame)
  @
 _connect:->
  alert('connect start')
 _requestToAplFrame:(msg)->
  jsonMsg=ph.JSON.stringify(msg)
  @_frame[0].contentWindow.postMessage(jsonMsg,'*')
 _onMessage:(qjev)=>
  ev=qjev.originalEvent
  if !@_frame || ev.source!=@_frame[0].contentWindow
   return
  res=ph.JSON.parse(ev.data)
  if res.type=='showFrame'
   @_frame.css({'height':'200px','width':'300px','top':'50%','left':'50%'})
   return
  if res.type=='hideFrame'
   @_frame.css({'height':'0px','width':'0px','top':'0%','left':'0%'})
   return
  if res.type=='loadAplFrame'
   if res.loginId #”FØÏ‚Ý‚È‚ç
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
    @trigger('onlineAuth',@)
    @trigger('auth',@)
    @_connect()
   else
    @cause='fail to onlineAuth'
    @trigger('failToAuth',@)
  if res.type=='offlineAuth'
   if res.result==true
    @aplInfo=res.aplInfo
    @trigger('onfflineAuth',@)
    @trigger('auth',@)
   else
    @cause='fail to offlineAuth'
    @trigger('failToAuth',@)
 subscribe:(qname,subname)->
 publish:(qname,msg)->
 store:(scope)->
 logout:->
  @_requestToAplFrame({type:'logout'})
 encrypt:(text,cb)->
  @_requestToAplFrame({type:'encrypt',text:text})
 decrypt:(encText,cb)->
  @_requestToAplFrame({type:'decrypt',encText:encText})
 aplInfo:(cb)->
 authInfo:(cb)->

URL_PTN=/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
ph._pas=[]
ph.pa2=(aplUrl,isOffline)->
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
 pa=ph._pas[keyUrl]
 if pa
  return pa
 pa=new PaHandler(keyUrl,isOffline,!isXhr)
 ph._pas[keyUrl]=pa
 return pa

ph.PaHandler=PaHandler

