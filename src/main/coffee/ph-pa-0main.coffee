if window.ph.pa
  return
window.ph.pa={
  STAT_INIT:'INIT'
  STAT_AUTH:'AUTH'
  STAT_IDLE:'IDLE'
  STAT_NEGOTIATION:'NEGOTIATION'
  STAT_OPEN:'OPEN',
  STAT_LOADING:'LOADING'
  STAT_CONNECT:'CONNECT'
  STAT_CLOSE:'CLOSE'
  CB_INFO:'INFO'
  CB_ERROR:'ERROR'
  CB_MESSAGE:'MESSAGE'
#request type
  TYPE_NEGOTIATE:'negotiate'
  TYPE_PUBLISH:'publish'
  TYPE_SUBSCRIBE:'subscribe'
  TYPE_UNSUBSCRIBE:'unsubscribe'
  TYPE_DEPLOY:'deploy'
  TYPE_UNDEPLOY:'undeploy'
  TYPE_QNAMES:'qnames'
  TYPE_CLOSE:'close'
#response type
  TYPE_RESPONSE:'response'
  TYPE_MESSAGE:'message'
  TYPE_DOWNLOAD:'download'
  RESULT_ERROR:'error'
  RESULT_SUCCESS:'success'
#  _INTERVAL:1000
  _SEND_DATA_MAX:(1024*1024*2)
  _WS_RETRY_MAX:3
  _KEEP_MSG_BEFORE_SUBSCRIBE:true
  _KEEP_MSG_MAX:64
  _DEFAULT_SUB_ID:'@'
  _DOWNLOAD_FRAME_NAME_PREFIX:'__pa_dl_'
  _XHR_FRAME_NAME_PREFIX:'__pa_xhr_' #xhrPaFrame.vsp�ɓ�����`����
  _XHR_FRAME_URL:'/!xhrPaFrame'
  _connections:{} #key:url value:{deferred:dfd,promise:prm}
  connect:(url)->
    httpUrl=null
    if url.lastIndexOf('ws://',0)==0||url.lastIndexOf('wss://',0)==0
      httpUrl='http' + url.substring(2)
      if !ph.useWebSocket
        #webSocket���g���Ȃ���url��ws://��������http://�ɕύX
        url='http' + url.substring(2)
    else if url.lastIndexOf('http://',0)!=0&&url.lastIndexOf('https://',0)!=0
      if ph.useWebSocket
        if ph.isSsl
          scm='wss://'
        else
          scm='ws://'
      else
        if ph.isSsl
          scm='https://'
        else
          scm='http://'
      if ph.isSsl
        httpUrl='https://' + ph.domain+url
      else
        httpUrl='http://' + ph.domain+url
      url=scm+ph.domain+url
    else
      httpUrl=url
    ph.log('url:' + url+':httpUrl:'+httpUrl)
    if @_connections[url]
      prm=@_connections[url].promise
    else
      dfd=ph.jQuery.Deferred()
      prm=dfd.promise(new CD(url,httpUrl,dfd))
      @_connections[url]={deferred:dfd,promise:prm}
    prm._openCount++
    prm
  _xhrOnMessage:(event)->
    for url,con of ph.pa._connections
      tmpCd=con.promise
      if !tmpCd._xhrFrame
        continue
      if event.source==tmpCd._xhrFrame[0].contentWindow
          cd=tmpCd
          break
    if !cd
      #���̃C�x���g
      return
    ress=ph.JSON.parse(event.data)
    if !ph.jQuery.isArray(ress)
      if ress.load
        cd._onXhrOpen(ress)
      return
    for res in ress
      cd._onXhrMessage(res)
    return
#  _onTimer:->
}
#xhr�ʐM�p�̃C�x���g�o�^
if window.addEventListener
  window.addEventListener('message',ph.pa._xhrOnMessage, false)
else if window.attachEvent
  window.attachEvent('onmessage',ph.pa._xhrOnMessage)
#setTimeout(ph.pa._onTimer,ph.pa._INTERVAL)

#-------------------EventModule-------------------
class EventModule
  constructor:->
    @_callback ={}
  on: (name, callback) ->
    if !@_callback[name]? then @_callback[name]=[]
    @_callback[name].push(callback)
    @
  trigger: (name,args...) ->
    list = @_callback[name]
    return @ unless list
    for callback in list
      callback.apply(@,args)
    @
  checkState:->
    if @deferred.state()!='pending'
      throw 'state error:'+@deferred.state()
