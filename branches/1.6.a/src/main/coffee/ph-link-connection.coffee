#-------------------Connection-------------------
class Connection extends ph.Deferred
  constructor:(@link) ->
    super
    @_subscribes={}
    @_openCount=1
    @stat=ph.STAT_INIT
    @_sendMsgs=[]
    @_reciveMsgs=[] #未subcriveで配信できなったmessage todo:溜まりすぎ
  init:(@useWs)->
    @_loginId=@link.aplInfo.loginId
    @_appSid=@link.aplInfo.appSid
    @_token=@link.aplInfo.token
    @connectXhrUrl=@link.keyUrl
    @isWs=!(@useWs==false)
    if @isWs
      @connectWsUrl='ws'+@connectXhrUrl.substring(4) #http->wsに
      @_openWebSocket()
    else
      @_openXhr()
    @_downloadFrameName=ph._DOWNLOAD_FRAME_NAME_PREFIX + @connectXhrUrl
    @_downloadFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      @_downloadFrameName + 
      '"></iframe>')
    ph.jQuery('body').append(@_downloadFrame)
    con=@
    @onUnload(->con._downloadFrame.remove())
  _flushMsg:->
    if @stat!=ph.STAT_CONNECT
      return
    if @_sendMsgs.length==0
      return
    msgs=@_sendMsgs
    @_sendMsgs=[]
    if @isWs
#WebSocketの場合は、1メッセージづつ送る
      for msg in msgs
        env=new Envelope()
        env.pack(msg,(protocolData)=>
          if ph.useBlob && protocolData instanceof Blob && protocolData.size>=ph._SEND_DATA_MAX
            ph.log('blob size:'+protocolData.size)
            @__onError({requestType:msg.type,qname:msg.qname,subname:msg.subname,message:'too long size:'+protocolData.size})
            return
          @_ws.send(protocolData)
          ph.log('ws send:'+protocolData)
        )
    else
#xhrの場合は、配列で一度に送る
      ptcMsgs=[]
      for msg in msgs
        env=new Envelope()
        ptcMsgs.push(env.pack(msg,null))
      jsonText=ph.JSON.stringify(ptcMsgs)
      ph.log('xhr send:'+jsonText)
      @_xhrFrame[0].contentWindow.postMessage(jsonText,"*")#TODO origin
  _sendNego:(msg)->
    if @isWs
      jsonText=ph.JSON.stringify(msg)
      @_ws.send(jsonText)
    else
      jsonText=ph.JSON.stringify([msg])
      @_xhrFrame[0].contentWindow.postMessage(jsonText,"*")#TODO origin
  _send:(msg)->
    if msg.type==ph.TYPE_NEGOTIATE
      @_sendMsgs.unshift(msg)
    else
      @_sendMsgs.push(msg)
    @_flushMsg()
  _onWsOpen:=>
    ph.log('Pa _onWsOpen')
    @_onOpen()
  _onWsClose: (event)=>
    ph.log('Pa _onWsClose.code:'+event.code+':reason:'+event.reason+':wasClean:'+event.wasClean)
#    @__onClose(event.reason)
    if @stat==ph.STAT_OPEN
# 接続直後に切れるのはwebsocketが使えないと考える
      if @useWs==true
        @trigger('unload')
        @unload()
        @stat=ph.INIT
        return
      @isWs=false
      @_openXhr()
      return
    else if @stat!=ph.STAT_CONNECT
      @trigger('unload')
      @unload()
      @stat=ph.INIT
      return
    @stat=ph.STAT_IDLE
    @_openWebSocket()
    return
  _onWsMessage:(msg)=>
    ph.log('Pa _onWsMessage:'+msg.data)
    @useWs=true #一回websocketでつながれば、xhrのスイッチを許さない
    envelope=new Envelope()
    envelope.unpack(msg.data,@_onMessage)
  _openWebSocket:=>
    ph.log('Pa WebSocket start')
    @stat=ph.STAT_OPEN
    ws=new WebSocket(@connectWsUrl)
    ws.onopen=@_onWsOpen
    ws.onmessage=@_onWsMessage
    ws.onclose=@_onWsClose
    ws.onerror=@_onWsClose
    ws._connection=@
    @_ws=ws
    con=@
    @onUnload(->
      if con._ws
        try
          con._ws.close(1000)
        catch error
        con._ws=null
     )
  _openXhr:->
    ph.log('Pa _openXhr')
    if @_xhrFrameName
      return
    @stat=ph.STAT_OPEN
    ##TODO:aplFrameを使って効率化
    @_xhrFrameName=ph._XHR_FRAME_NAME_PREFIX + @connectXhrUrl
    @_xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      @_xhrFrameName + 
      '" src="' + 
      @connectXhrUrl + ph._XHR_FRAME_URL +
      '"></iframe>')
    ph.jQuery('body').append(@_xhrFrame)
    ph.on('message',@_xhrOnMessage)
    con=@
    @onUnload(->
      if con._xhrFrame
       con._xhrFrame.remove()
       con._xhrFrame=null
      ph.off('message',con._xhrOnMessage)
     )
  _xhrOnMessage:(ev)=>
    if ev.originalEvent.source!=@_xhrFrame[0].contentWindow
      return
    ress=ph.JSON.parse(ev.originalEvent.data)
    if !ph.jQuery.isArray(ress)
      if ress.load
        @_onXhrOpen(ress)
      return
    for res in ress
      @_onXhrMessage(res)
    return
  _onXhrOpen:(res)->
    ph.log('Pa _onXhrOpen')
    if res.load
      @_onOpen()
    else
      ph.log('_onXhrOpened error.'+ph.JSON.stringify(res))
  _onXhrMessage:(obj)->
    ph.log('Pa _onXhrMessage')
    envelope=new Envelope()
    envelope.unpack(obj,@_onMessage)
#   @_onMessage(obj)
  _getBid:->
    @link.ppStorage.getItem('bid') ? 0
  _setBid:(bid)->
    if bid
      @link.ppStorage.setItem('bid',bid)
    else
      @link.ppStorage.removeItem('bid')
  _onOpen:->
    @stat=ph.STAT_NEGOTIATION
    @_sendNego({type:ph.TYPE_NEGOTIATE,bid:@_getBid(),token:@_token,needRes:true})
  __onMsgNego:(msg)->
    @stat=ph.STAT_CONNECT
    if msg.bid!=@_getBid()
      @_setBid(msg.bid)
      @_send({type:ph.TYPE_NEGOTIATE,bid:msg.bid,token:@_token,needRes:false})
    if @isLoading()
      @trigger('connected',@) #success to connect
      @load()
    return
  __onClose:(msg)->
    if @isUnload()
      ph.log('aleady closed')
      return
    @_setBid(null)
    @trigger('unload')
    @unload()
    @stat=ph.STAT_CLOSE
#----------for response event----------
  __getSd:(msg)->
    key="#{msg.qname}@#{msg.subname}"
    return @_subscribes[key]
  __endOfSubscribe:(msg)->
    key="#{msg.qname}@#{msg.subname}"
    sd=@_subscribes[key]
    if !sd
      return false
    sd.trigger('done',msg,sd)
    delete @_subscribes[key]
    true
  __onSuccess:(msg)->
    if msg.requestType==ph.TYPE_SUBSCRIBE
      @__endOfSubscribe(msg)
    else if msg.requestType==ph.TYPE_CLOSE
      @__onClose(msg)
    else if msg.requestType==ph.TYPE_QNAMES
      @trigger(ph.TYPE_QNAMES,msg.message)
    else
      sd=@__getSd(msg)
      if sd
        sd.trigger(ph.RESULT_SUCCESS,msg.requestType,msg)
      else
        @trigger(ph.RESULT_SUCCESS,msg.requestType,msg)
  __onError:(msg)->
    if msg.requestType==ph.TYPE_SUBSCRIBE
      @__endOfSubscribe(msg)
    else
      sd=@__getSd(msg)
      if sd
        sd.trigger(ph.RESULT_ERROR,msg.requestType,msg)
      else
        @trigger(ph.RESULT_ERROR,msg.requestType,msg)
  _onMessage:(msg)=>
    if msg.type==ph.TYPE_NEGOTIATE
      @__onMsgNego(msg)
    else if msg.type==ph.TYPE_CLOSE
      @__onClose(msg)
    else if msg.type==ph.TYPE_DOWNLOAD
      ph.log('download.msg.key:'+msg.key)
      form=ph.jQuery("<form method='POST' target='#{@_downloadFrameName}' action='#{@connectXhrUrl}/~paDownload'>" +
         "<input type='hidden' name='bid' value='#{@_getBid()}'/>" +
         "<input type='hidden' name='token' value='#{@_token}'/>" +
         "<input type='hidden' name='key' value='#{msg.key}'/>" +
         "</form>")
      ph.jQuery('body').append(form)
      form.submit()
      form.remove()
    else if msg.type==ph.TYPE_MESSAGE
      key="#{msg.qname}@#{msg.subname}"
      sd=@_subscribes[key]
      if !sd
        ph.log('before subscribe message')
        if !ph._KEEP_MSG_BEFORE_SUBSCRIBE
          return
        reciveMsgs=@_reciveMsgs[key]
        if !reciveMsgs
          reciveMsgs=@_reciveMsgs[key]=[]
        if reciveMsgs.length>=ph._KEEP_MSG_MAX
          x=reciveMsgs.shift()
          ph.log('drop msg:'+x)
        reciveMsgs.push(msg)
        return
      sd.trigger(ph.TYPE_MESSAGE,msg.message,sd)
      @trigger(sd.qname,msg.message,sd)
      @trigger(sd.subname,msg.message,sd)
      @trigger("#{sd.qname}@#{sd.subname}",msg.message,sd)
    else if msg.type==ph.TYPE_RESPONSE
      if msg.result==ph.RESULT_SUCCESS
        @__onSuccess(msg)
      else
        @__onError(msg)
#----------Connection outer api----------
  close:->
    @onLoad()
    @_openCount--
    if @_openCount==0
      @_send({type:ph.TYPE_CLOSE})
    @
  subscribe:(qname,subname,onMessage)->
    @onLoad()
    if subname && ph.jQuery.isFunction(subname)
     onMessage=subname
     subname=ph._DEFAULT_SUB_ID
    else if !subname
      subname=ph._DEFAULT_SUB_ID
    key="#{qname}@#{subname}"
    prm=@_subscribes[key]
    if prm
      if onMessage
        prm.onMessage(onMessage)
      return prm
    subscription=new Subscription(@,qname,subname)
    @_subscribes[key]=subscription
    if onMessage
      subscription.onMessage(onMessage)
    #aleady recive message check
    reciveMsgs=@_reciveMsgs[key]
    if reciveMsgs && reciveMsgs.length>0
      setTimeout(=>
        for msg in reciveMsgs
          @_onMessage(msg)
      ,0)
    subscription
  publish:(qname,msg)->
    @onLoad()
    @_send({type:ph.TYPE_PUBLISH,qname:qname,message:msg})
  qnames:(cb)->
    @onLoad()
    if cb
      @on(ph.TYPE_QNAMES,cb)
    @_send({type:ph.TYPE_QNAMES})
  deploy:(qname,className)->
    @onLoad()
    @_send({type:ph.TYPE_DEPLOY,qname:qname,paletClassName:className})
  undeploy:(qname)->
    @onLoad()
    @_send({type:ph.TYPE_UNDEPLOY,qname:qname})

