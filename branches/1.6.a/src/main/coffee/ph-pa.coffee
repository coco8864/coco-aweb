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
#strage scope
  SCOPE_PAGE_PRIVATE:'pagePrivate'
  SCOPE_SESSION_PRIVATE:'sessionPrivate'
  SCOPE_APL_PRIVATE:'aplPrivate'
  SCOPE_APL_LOCAL:'aplLocal'
  SCOPE_APL:'apl'
  SCOPE_QNAME:'qname'
  SCOPE_SUBNAME:'subname'
  SCOPE_USER:'user'

#  _INTERVAL:1000
  _SEND_DATA_MAX:(1024*1024*2)
  _WS_RETRY_MAX:3
  _KEEP_MSG_BEFORE_SUBSCRIBE:true
  _KEEP_MSG_MAX:64
  _DEFAULT_SUB_ID:'@'
  _DOWNLOAD_FRAME_NAME_PREFIX:'__pa_dl_'
  _XHR_FRAME_NAME_PREFIX:'__pa_xhr_' #xhrPaFrame.vspに同じ定義あり
  _XHR_FRAME_URL:'/~xhrPaFrame'
  _connections:{} #key:url value:{deferred:dfd,promise:prm}
  connect:(url,conCb)->
    httpUrl=null
    if url.lastIndexOf('ws://',0)==0||url.lastIndexOf('wss://',0)==0
      httpUrl='http' + url.substring(2)
      if !ph.useWebSocket
        #webSocketが使えなくてurlがws://だったらhttp://に変更
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
      prm=dfd.promise(new Connection(url,httpUrl,dfd))
      @_connections[url]={deferred:dfd,promise:prm}
    prm._openCount++
    if conCb
      prm.on('connected',conCb)
    prm
  _onUnload:->
    ph.log('onUnload')
    for url,con of ph.pa._connections
      pms=con.promise
      pms.trigger('unload')
  _onStorage:(event)->
    ph.log('onStorage.key:'+event.key)
    for url,con of ph.pa._connections
      pms=con.promise
      pms.trigger('storage',event)
  _xhrOnMessage:(event)->
    for url,con of ph.pa._connections
      c=con.promise
      if !c._xhrFrame
        continue
      if event.source==c._xhrFrame[0].contentWindow
          cd=c
          break
    if !cd
      #他のイベント
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
  _storDecrypt:(storage,auth,key,cb)->
    encText=storage.getItem(key)
    if encText
      auth.decrypt(encText,(decText)->cb(decText))
    else
      cb(null)
  _encryptStor:(storage,auth,key,value,cb)->
    auth.encrypt(value,(encText)->
      strage.setItem(key,encText)
      if cb
        cb(encText)
      )
}
#xhr通信用のイベント登録
ph.jQuery(->
  ph.event.on('message',ph.pa._xhrOnMessage)
  ph.event.on('unload',ph.pa._onUnload)
  ph.event.on('storage',ph.pa._onStorage)
)
#setTimeout(ph.pa._onTimer,ph.pa._INTERVAL)
