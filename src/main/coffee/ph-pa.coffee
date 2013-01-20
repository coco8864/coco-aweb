window.ph.pa={
  STAT_INIT:'INIT'
  STAT_AUTH:'AUTH'
  STAT_IDLE:'IDLE'
  STAT_OPEN:'OPEN',
  STAT_CONNECT:'CONNECT'
  STAT_CLOSE:'CLOSE'
  CB_INFO:'INFO'
  CB_ERROR:'ERROR'
  CB_MESSAGE:'MESSAGE'
  _INTERVAL:1000
  _DEFAULT_SUB_ID:'@'
  _XHR_FRAME_NAME_PREFIX:'__pa_'
  _XHR_FRAME_URL:'/xhrPaFrame.vsp'
  _RETRY_COUNT:3
  _APPID_PREFIX:'appid.'
  _connections:{} #key:url value:{deferred:dfd,promise:prm}
  connect:(url)->
    if url.lastIndexOf('ws://',0)==0||url.lastIndexOf('wss://',0)==0
      if !ph.useWebSocket
        #webSocket‚ªŽg‚¦‚È‚­‚Äurl‚ªws://‚¾‚Á‚½‚çhttp://‚É•ÏX
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
}

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
  bid:''
  _doneAuth:(auth)=>
    if !auth.result
      ph.pa._connections[@url]=null
      @trigger("error",@)#fail to auth
      return
    @trigger("auth",@)#success to auth
    @_appId=auth.appId
    bid=sessionStorage[ph.pa._APPID_PREFIX+@_appId]
    if bid
      @bid=bid
    else
      @bid=''
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
    @_sendMsgs.push(msg)
    @_flushMsg()
  _onWsOpen:=>
    ph.log('Pa _onWsOpen')
    @stat=ph.pa.STAT_CONNECT
    @_send({type:'negotiate',bid:@bid})
  _onWsClose:=>
    ph.log('Pa _onWsClose')
  _onWsMessage:(msg)=>
    ph.log('Pa _onWsMessage')
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
  _onXhrLoad:->
    ph.log('Pa _onXhrLoad')
    @stat=ph.pa.STAT_CONNECT
  _onXhrMessage:(data)->
    ph.log('Pa _onXhrMessage')
#  _onMessage:(msg)->
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
#  _callback:{}
  unsubscribe:->
    @_cd._send({type:'unsubscribe',qname:qname,subname:subname})
  publish:(msg)->
    @_cd._send({type:'publish',qname:@qname,subname:@subname,message:msg})

