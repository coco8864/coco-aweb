#-------------------Connection-------------------
class Connection extends ph.Deferred
  constructor:(@pa) ->
    super
    @_subscribes={}
    @_openCount=0
    @stat=ph.pa.STAT_INIT
    @_sendMsgs=[]
    @_reciveMsgs=[] #未subcriveで配信できなったmessage todo:溜まりすぎ
    @isWs=(ph.useWebSocket && connectWsUrl!=null)
    @stat=ph.pa.STAT_AUTH
    @ppStorage=new PrivateSessionStorage(@connectXhrUrl,@_auth)
    @trigger(ph.pa.RESULT_SUCCESS,'auth',@)#success to auth
    @trigger('auth',@)#success to auth
    if @_storageScope
      @_initStorage=@storage(@_storageScope)
##unload時にsessionStrageに保存
    @on('unload',->
        @ppStorage?._unload()
        @spStorage?._unload()
        @apStorage?._unload()
        @alStorage?._unload()
      )
    if @ppStorage.status!='load'
      @ppStorage.on('dataLoad',@_loadStorage)
    else
      @_loadStorage()
  _loadStorage:=>
    if @isWs
      @_openWebSocket()
    else
      @_openXhr()
    @_downloadFrameName=ph.pa._DOWNLOAD_FRAME_NAME_PREFIX + @connectXhrUrl
    @_downloadFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      @_downloadFrameName + 
      '"></iframe>')
#    con=@
#    @_xhrFrame.load(->con._onXhrLoad())
    ph.jQuery('body').append(@_downloadFrame)
  _flushMsg:->
    if @stat!=ph.pa.STAT_CONNECT
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
          if ph.useBlob && protocolData instanceof Blob && protocolData.size>=ph.pa._SEND_DATA_MAX
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
    if msg.type==ph.pa.TYPE_NEGOTIATE
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
    if @stat==ph.pa.STAT_OPEN
# 接続直後に切れるのはwebsocketが使えないと考える
      @isWs=false
      @_openXhr()
      return
    else if @stat!=ph.pa.STAT_CONNECT
#      if @stat==ph.pa.STAT_OPEN
      @trigger('unload')
      @unload()
#      @deferred.resolve('out session')
      @stat=ph.pa.INIT
      return
    @stat=ph.pa.STAT_IDLE
    @_openWebSocket()
    return
  _onWsMessage:(msg)=>
    ph.log('Pa _onWsMessage:'+msg.data)
    envelope=new Envelope()
    envelope.unpack(msg.data,@_onMessage)
  _openWebSocket:=>
    ph.log('Pa WebSocket start')
    @stat=ph.pa.STAT_OPEN
    ws=new WebSocket(@connectWsUrl)
    ws.onopen=@_onWsOpen
    ws.onmessage=@_onWsMessage
    ws.onclose=@_onWsClose
    ws.onerror=@_onWsClose
    ws._connection=@
    @_ws=ws
  _openXhr:->
    ph.log('Pa _openXhr')
    if @_xhrFrameName
      return
    @stat=ph.pa.STAT_OPEN
    @_xhrFrameName=ph.pa._XHR_FRAME_NAME_PREFIX + @connectXhrUrl
    @_xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      @_xhrFrameName + 
      '" src="' + 
      @connectXhrUrl + ph.pa._XHR_FRAME_URL +
      '"></iframe>')
    con=@
    @_xhrFrame.load(->con._onXhrLoad())
    ph.jQuery('body').append(@_xhrFrame)
  _onXhrLoad:=>
    ph.log('Pa _onXhrLoad')
#    @stat=ph.pa.STAT_LOADING
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
    @ppStorage.getItem('bid') ? 0
  _setBid:(bid)->
    if bid
      @ppStorage.setItem('bid',bid)
    else
      @ppStorage._remove()
      @ppStorage=null
  _onOpen:->
    @stat=ph.pa.STAT_NEGOTIATION
    @_sendNego({type:ph.pa.TYPE_NEGOTIATE,bid:@_getBid(),token:@_token,needRes:true})
  __onMsgNego:(msg)->
    @stat=ph.pa.STAT_CONNECT
    if msg.bid!=@_getBid()
      @_setBid(msg.bid)
      @_send({type:ph.pa.TYPE_NEGOTIATE,bid:msg.bid,token:@_token,needRes:false})
    if @_initStorage && @_initStorage.status!='load'
      c=@
      @_initStorage.on('dataLoad',->c.trigger('connected',c))
    else
      @trigger('connected',@) #success to connect
    @load()
    return
  __onClose:(msg)->
    if @isUnload()
      ph.log('aleady closed')
      return
    @trigger('unload')
    @unload()
#    @deferred.resolve(msg,@)
    if @isWs
      @stat=ph.pa.STAT_CLOSE
      @_ws.close(1000)
    else
      @stat=ph.pa.STAT_INIT
      @_xhrFrame.remove()
    @_setBid(null)
    delete ph.pa._connections[@connectXhrUrl]
#----------for response event----------
  __getSd:(msg)->
    key="#{msg.qname}@#{msg.subname}"
    return @_subscribes[key]
  __endOfSubscribe:(msg)->
    key="#{msg.qname}@#{msg.subname}"
    sd=@_subscribes[key]
    if !sd
      return false
#    sd.deferred.resolve(msg,sd.promise)
    sd.trigger('done',msg,sd)
    delete @_subscribes[key]
    true
  __onSuccess:(msg)->
    if msg.requestType==ph.pa.TYPE_SUBSCRIBE
      @__endOfSubscribe(msg)
    else if msg.requestType==ph.pa.TYPE_CLOSE
      @__onClose(msg)
    else if msg.requestType==ph.pa.TYPE_QNAMES
      @trigger(ph.pa.TYPE_QNAMES,msg.message)
    else
      sd=@__getSd(msg)
      if sd
        sd.trigger(ph.pa.RESULT_SUCCESS,msg.requestType,msg)
      else
        @trigger(ph.pa.RESULT_SUCCESS,msg.requestType,msg)
  __onError:(msg)->
    if msg.requestType==ph.pa.TYPE_SUBSCRIBE
      @__endOfSubscribe(msg)
    else
      sd=@__getSd(msg)
      if sd
        sd.trigger(ph.pa.RESULT_ERROR,msg.requestType,msg)
      else
        @trigger(ph.pa.RESULT_ERROR,msg.requestType,msg)
  _onMessage:(msg)=>
    if msg.type==ph.pa.TYPE_NEGOTIATE
      @__onMsgNego(msg)
    else if msg.type==ph.pa.TYPE_CLOSE
      @__onClose(msg)
    else if msg.type==ph.pa.TYPE_DOWNLOAD
      ph.log('download.msg.key:'+msg.key)
      form=ph.jQuery("<form method='POST' target='#{@_downloadFrameName}' action='#{@connectXhrUrl}/~paDownload'>" +
         "<input type='hidden' name='bid' value='#{@_getBid()}'/>" +
         "<input type='hidden' name='token' value='#{@_token}'/>" +
         "<input type='hidden' name='key' value='#{msg.key}'/>" +
         "</form>")
#      frame=ph.jQuery('<iframe width="0" height="0" frameborder="no"' +
#        ' src="' + 
#        @downloadUrl + '?bid=' + @_getBid() + '&token=' + @_token + '&key=' + msg.key +
#        '"></iframe>')
#      frame.load(->alert('load'))
      ph.jQuery('body').append(form)
      form.submit()
      form.remove()
    else if msg.type==ph.pa.TYPE_MESSAGE
      key="#{msg.qname}@#{msg.subname}"
      sd=@_subscribes[key]
      if !sd
        ph.log('before subscribe message')
        if !ph.pa._KEEP_MSG_BEFORE_SUBSCRIBE
          return
        reciveMsgs=@_reciveMsgs[key]
        if !reciveMsgs
          reciveMsgs=@_reciveMsgs[key]=[]
        if reciveMsgs.length>=ph.pa._KEEP_MSG_MAX
          x=reciveMsgs.shift()
          ph.log('drop msg:'+x)
        reciveMsgs.push(msg)
        return
#      promise=sd.promise
      sd.trigger(ph.pa.TYPE_MESSAGE,msg.message,sd)
      @trigger(sd.qname,msg.message,sd)
      @trigger(sd.subname,msg.message,sd)
      @trigger("#{sd.qname}@#{sd.subname}",msg.message,sd)
    else if msg.type==ph.pa.TYPE_RESPONSE
      if msg.result==ph.pa.RESULT_SUCCESS
        @__onSuccess(msg)
      else
        @__onError(msg)
#----------Connection outer api----------
  close:->
    @onLoad()
    @_openCount--
    if @_openCount==0
      @_send({type:ph.pa.TYPE_CLOSE})
    @
  subscribe:(qname,subname,onMessage)->
    @onLoad()
    if subname && ph.jQuery.isFunction(subname)
     onMessage=subname
     subname=ph.pa._DEFAULT_SUB_ID
    else if !subname
      subname=ph.pa._DEFAULT_SUB_ID
    key="#{qname}@#{subname}"
    prm=@_subscribes[key]
    if prm
      if onMessage
        prm.onMessage(onMessage)
      return prm
    subscription=new Subscription(@,qname,subname)
#    prm=subscription.promise
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
    @_send({type:ph.pa.TYPE_PUBLISH,qname:qname,message:msg})
  qnames:(cb)->
    @onLoad()
    if cb
      @on(ph.pa.TYPE_QNAMES,cb)
    @_send({type:ph.pa.TYPE_QNAMES})
  deploy:(qname,className)->
    @onLoad()
    @_send({type:ph.pa.TYPE_DEPLOY,qname:qname,paletClassName:className})
  undeploy:(qname)->
    @onLoad()
    @_send({type:ph.pa.TYPE_UNDEPLOY,qname:qname})
  storage:(scope)->
    if !scope || scope==ph.pa.SCOPE_PAGE_PRIVATE
      return @ppStorage
    else if scope==ph.pa.SCOPE_SESSION_PRIVATE
      if !@spStorage
        @spStorage=new PrivateLocalStorage(@connectXhrUrl,@_auth,@_getBid(),scope)
      return @spStorage
    else if scope==ph.pa.SCOPE_APL_PRIVATE
      if !@apStorage
        @apStorage=new PrivateLocalStorage(@connectXhrUrl,@_auth,@_getBid(),scope)
      return @apStorage
    else if scope==ph.pa.SCOPE_APL_LOCAL
      if !@alStorage
        @alStorage=new PrivateLocalStorage(@connectXhrUrl,@_auth,@_getBid(),scope)
      return @alStorage

