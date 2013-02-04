window.ph.pa={
  STAT_INIT:'INIT'
  STAT_AUTH:'AUTH'
  STAT_IDLE:'IDLE'
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
  TYPE_CONNECTION_CLOSE:'connectionClose'
#response type
  TYPE_RESPONSE:'response'
  TYPE_MESSAGE:'message'
  RESULT_ERROR:'error'
  RESULT_OK:'ok'
  _INTERVAL:1000
  _DEFAULT_SUB_ID:'@'
  _XHR_FRAME_NAME_PREFIX:'__pa_'
  _XHR_FRAME_URL:'/xhrPaFrame.vsp'
  _RETRY_COUNT:3
  _BROWSERID:'bid.'
  _connections:{} #key:url value:{deferred:dfd,promise:prm}
  _getBid:(appid)->
    str=sessionStorage[this._BROWSERID + appid] ? '0'
    parseInt(str,10)
  _setBid:(appid,bid)->
    sessionStorage[this._BROWSERID + appid]=String(bid)
  connect:(url)->
    if url.lastIndexOf('ws://',0)==0||url.lastIndexOf('wss://',0)==0
      if !ph.useWebSocket
        #webSocketが使えなくてurlがws://だったらhttp://に変更
        url='http' + url.substring(2);
    else if url.lastIndexOf('http://',0)==0||url.lastIndexOf('https://',0)==0
    else
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
      url=scm+ph.domain+url
    ph.log('url:' + url)
    if this._connections[url]
      return this._connections[url].promise
    dfd=ph.jQuery.Deferred()
    prm=dfd.promise(new CD(url))
    this._connections[url]={deferred:dfd,promise:prm}
    prm
  _xhrOnMessage:(event)->
    for url,con of ph.pa._connections
      tmpCd=con.promise
      if !tmpCd._xhrFrame
        continue
      if event.source=tmpCd._xhrFrame[0].contentWindow
          cd=tmpCd
          break
    if !cd
      #他のイベント
      return
    res=ph.JSON.parse(event.data)
    if !(cd.stat==ph.pa.STAT_LOADING)
      cd._onXhrOpen(res)
    else
      cd._onXhrMessage(res)
  _onTimer:->
}
#xhr通信用のイベント登録
if window.addEventListener
  window.addEventListener('message',ph.pa._xhrOnMessage, false)
else if window.attachEvent
  window.attachEvent('onmessage',ph.pa._xhrOnMessage)
setTimeout(ph.wsq._onTimer,ph.pa._INTERVAL)

class EventModule
  on: (name, callback) ->
    @_callback ={} unless @_callback?
    if !@_callback[name]? then @_callback[name]=[]
    @_callback[name].push(callback)
    this
  trigger: (name,args...) ->
    list = @_callback[name]
    return @ unless list
    for callback in list
      callback.apply(@,args)
    this

#Connection Deferred
class CD extends EventModule
  constructor: (@url) ->
    @_callback={}
    @_subscribes={}
    @isWs=(url.lastIndexOf('ws',0)==0)
    @stat=ph.pa.STAT_AUTH
    @_sendMsgs=[]
    con=@
    ph.auth.auth(url,con._doneAuth)
#  _callback:{}
#  _subscribes:{} #key:qname@subname value:{deferred:dfd,promise:prm}
#  stat:ph.pa.STAT_INIT
  _doneAuth:(auth)=>
    if !auth.result
      ph.pa._connections[@url]=null
      @trigger("error",@)#fail to auth
      return
    @trigger("auth",@)#success to auth
    @_appId=auth.appId
    @stat=ph.pa.STAT_IDLE
    if @isWs
      @_openWebSocket()
    else
      @_openXhr()
  _flushMsg:->
    if @stat!=ph.pa.STAT_CONNECT
      return
    if @_sendMsgs.length==0
      return
    msg=@_sendMsgs
    @_sendMsgs=[]
    jsonText=ph.JSON.stringify(msg)
    ph.log('send:'+jsonText)
    if @isWs
      @_ws.send(jsonText)
    else
      @_xhrFrame[0].contentWindow.postMessage(jsonText,"*")#TODO origin
  _send:(msg)->
    @_sendMsgs.unshift(msg)
    @_flushMsg()
  _onWsOpen:=>
    ph.log('Pa _onWsOpen')
    this._onOpen()
  _onWsClose:=>
    ph.log('Pa _onWsClose')
  _onWsMessage:(msg)=>
    ph.log('Pa _onWsMessage:'+msg.data)
    obj=ph.JSON.parse(msg.data)
    this._onMessage(obj)
  _openWebSocket:=>
    ph.log('Pa WebSocket start')
    @stat=ph.pa.STAT_OPEN
    ws=new WebSocket(@url)
    ws.onopen=@_onWsOpen
    ws.onmessage=@_onWsMessage
    ws.onclose=@_onWsClose
    ws.onerror=@_onWsClose
    ws._connection=@
    @_ws=ws
  _openXhr:->
    ph.log('Pa _openXhr')
    @stat=ph.pa.STAT_OPEN
    @_xhrFrameName=ph.pa._XHR_FRAME_NAME_PREFIX + this.url
    @_xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      this._xhrFrameName + 
      '" src="' + 
      @url + ph.pa._XHR_FRAME_URL +
      '"></iframe>')
    con=@
    @_xhrFrame.load(->con._onXhrLoad())
    ph.jQuery("body").append(@_xhrFrame)
  _onXhrLoad:=>
    ph.log('Pa _onXhrLoad')
    this.stat=ph.pa.LOADING
  _onXhrOpen:(res)->
    ph.log('Pa _onXhrOpened')
    if res.load
      this._onOpen()
    else
      ph.log('_onXhrOpened error.'+ph.JSON.stringify(res));
  _onXhrMessage:(obj)->
    ph.log('Pa _onXhrMessage')
    this._onMessage(obj)
  _onOpen:->
    @stat=ph.pa.STAT_CONNECT
    @_send({type:ph.pa.TYPE_NEGOTIATE,bid:ph.pa._getBid(@_appId)})
  __onMsgNego:(msg)->
    if msg.bid!=ph.pa._getBid(@_appId)
      ph.pa._setBid(@_appId,msg.bid)
      @_send({type:ph.pa.TYPE_NEGOTIATE,bid:msg.bid})
  __onMsgMessage:(msg)->
  __onMsgResOk:(msg)->
    ph.log('__onMsgResOk requestType:'+msg.requestType)
    if msg.requestType==ph.pa.TYPE_SUBSCRIBE
      key=msg.qname + '@' +msg.subname
      if @_subscribes[key]
        @_subscribes[key].deferred.resolve(msg,@_subscribes[key].promise)
        @_subscribes[key]=null
  __onMsgResError:(msg)->
    ph.log('__onMsgResError requestType:'+msg.requestType)
    if msg.requestType==ph.pa.TYPE_SUBSCRIBE
      key=msg.qname + '@' +msg.subname
      if @_subscribes[key]
        @_subscribes[key].deferred.resolve(msg,@_subscribes[key].promise)
        @_subscribes[key]=null
  _onMessage:(msg)->
    if msg.type==ph.pa.TYPE_NEGOTIATE
      @__onMsgNego(msg)
    else if msg.type==ph.pa.TYPE_MESSAGE
      @__onMsgMessage(msg)
    else if msg.type==ph.pa.TYPE_RESPONSE && msg.result==ph.pa.RESULT_OK
      @__onMsgResOk(msg)
    else if msg.type==ph.pa.TYPE_RESPONSE && msg.result==ph.pa.RESULT_ERROR
      @__onMsgResError(msg)
#  _onMessageText:(textMsg)->
#  _onMessageBlob:(blobMsg)->
#  _callback:()->
#  _onTimer:->
#  _getOnlySubId:(qname)->
  close:->
  subscribe:(qname,subname)->
    if !subname
      subname='@'
    key=qname + '@' +subname
    if @_subscribes[key]
      return @_subscribes[key].promise
    dfd=ph.jQuery.Deferred()
    prm=dfd.promise(new SD(this,qname,subname))
    @_subscribes[key]={deferred:dfd,promise:prm}
    prm
  publish:(qname,msg)->
    @_send({type:'publish',qname:qname,message:msg})
  qnames:->
    @_send({type:'qnames'})
  deploy:(qname,className)->
    @_send({type:'deploy',qname:qname,paletClassName:className})
  undeploy:(qname)->
    @_send({type:'undeploy',qname:qname})

#Subscribe Deferred
class SD extends EventModule
  constructor: (@_cd,@qname,@subname)->
    @_callback={}
    @_cd._send({type:'subscribe',qname:@qname,subname:@subname})
#  _callback:{}
  unsubscribe:->
    @_cd._send({type:'unsubscribe',qname:qname,subname:subname})
  publish:(msg)->
    @_cd._send({type:'publish',qname:@qname,subname:@subname,message:msg})

