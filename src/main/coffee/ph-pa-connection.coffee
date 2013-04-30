#-------------------Connection Deferred-------------------
class CD extends EventModule
  _BROWSERID_PREFIX:'bid.'
  constructor: (@url,@httpUrl,@deferred) ->
    super
    @_subscribes={}
    @isWs=(url.lastIndexOf('ws',0)==0)
    @_openCount=0
    @_errorCount=0
    @stat=ph.pa.STAT_AUTH
    @_sendMsgs=[]
    @_reciveMsgs=[] #未subcriveで配信できなったmessage todo:溜まりすぎ
    con=@
    ph.auth.auth(url,con._doneAuth)
  _doneAuth:(auth)=>
    if !auth.result
      ph.pa._connections[@url]=null
      @trigger(ph.pa.RESULT_ERROR,'auth',@)#fail to auth
      return
    @_loginId=auth.loginId
    @_appSid=auth.appSid
    @SsKey='_paSs:'+@_loginId+':'+@url+':'+@_appSid
    str=sessionStorage.getItem(@SsKey) ? '{"bid":0}'
    @paSsObj=ph.JSON.parse(str)
    @_token=auth.token
#    @stat=ph.pa.STAT_IDLE
    if @isWs
      @_openWebSocket()
    else
      @_openXhr()
    @_downloadFrameName=ph.pa._DOWNLOAD_FRAME_NAME_PREFIX + @url
    @_downloadFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      @_downloadFrameName + 
      '"></iframe>')
#    con=@
#    @_xhrFrame.load(->con._onXhrLoad())
    ph.jQuery('body').append(@_downloadFrame)
    @trigger(ph.pa.RESULT_SUCCESS,'auth',@)#success to auth
    @trigger('auth',@)#success to auth
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
    @__onClose(event.reason)
    if @stat!=ph.pa.STAT_CONNECT
      @stat=ph.pa.INIT
      return
    @_errorCount++
    @stat=ph.pa.STAT_IDLE
    if @_errorCount>=ph.pa._WS_RETRY_MAX
      ph.log('too many error')
    else
      @_openWebSocket()
  _onWsMessage:(msg)=>
    ph.log('Pa _onWsMessage:'+msg.data)
    envelope=new Envelope()
    envelope.unpack(msg.data,@_onMessage)
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
    @_xhrFrameName=ph.pa._XHR_FRAME_NAME_PREFIX + @url
    @_xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      @_xhrFrameName + 
      '" src="' + 
      @url + ph.pa._XHR_FRAME_URL +
      '"></iframe>')
    con=@
    @_xhrFrame.load(->con._onXhrLoad())
    ph.jQuery('body').append(@_xhrFrame)
  _onXhrLoad:=>
    ph.log('Pa _onXhrLoad')
#    @stat=ph.pa.STAT_LOADING
  _onXhrOpen:(res)->
    ph.log('Pa _onXhrOpened')
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
    @paSsObj.bid
  _setBid:(bid)->
    if bid
      @paSsObj.bid=bid
##todo unload時にsaveする
      str=ph.JSON.stringify(@paSsObj)
      sessionStorage.setItem(@SsKey,str)
    else
      sessionStorage.removeItem(@SsKey)
  _onOpen:->
    @stat=ph.pa.STAT_NEGOTIATION
    @_sendNego({type:ph.pa.TYPE_NEGOTIATE,bid:@_getBid(),token:@_token,needRes:true})
  __onMsgNego:(msg)->
    @stat=ph.pa.STAT_CONNECT
    if msg.bid!=@_getBid()
      @_setBid(msg.bid)
      @_send({type:ph.pa.TYPE_NEGOTIATE,bid:msg.bid,token:@_token,needRes:false})
    @offlinePassHash=msg.offlinePassHash
  __onClose:(msg)->
    if @deferred.state()!='pending'
      ph.log('aleady closed')
      return
    @deferred.resolve(msg,@)
    if @isWs
      @stat=ph.pa.STAT_CLOSE
      @_ws.close(1000)
    else
      @stat=ph.pa.STAT_INIT
      @_xhrFrame.remove()
    @_setBid(null)
    delete ph.pa._connections[@url]
#----------for response event----------
  __getSd:(msg)->
    key="#{msg.qname}@#{msg.subname}"
    return @_subscribes[key]
  __endOfSubscribe:(msg)->
    key="#{msg.qname}@#{msg.subname}"
    sd=@_subscribes[key]
    if !sd
      return false
    sd.deferred.resolve(msg,sd.promise)
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
        sd.promise.trigger(ph.pa.RESULT_SUCCESS,msg.requestType,msg)
      else
        @trigger(ph.pa.RESULT_SUCCESS,msg.requestType,msg)
  __onError:(msg)->
    if msg.requestType==ph.pa.TYPE_SUBSCRIBE
      @__endOfSubscribe(msg)
    else
      sd=@__getSd(msg)
      if sd
        sd.promise.trigger(ph.pa.RESULT_ERROR,msg.requestType,msg)
      else
        @trigger(ph.pa.RESULT_ERROR,msg.requestType,msg)
  _onMessage:(msg)=>
    if msg.type==ph.pa.TYPE_NEGOTIATE
      @__onMsgNego(msg)
    else if msg.type==ph.pa.TYPE_CLOSE
      @__onClose(msg)
    else if msg.type==ph.pa.TYPE_DOWNLOAD
      ph.log('download.msg.key:'+msg.key)
      form=ph.jQuery("<form method='POST' target='#{@_downloadFrameName}' action='#{@httpUrl}/!paDownload'>" +
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
      promise=sd.promise
      sd.promise.trigger(ph.pa.TYPE_MESSAGE,msg.message,promise)
      @trigger(promise.qname,msg.message,promise)
      @trigger(promise.subname,msg.message,promise)
      @trigger("#{promise.qname}@#{promise.subname}",msg.message,promise)
    else if msg.type==ph.pa.TYPE_RESPONSE
      if msg.result==ph.pa.RESULT_SUCCESS
        @__onSuccess(msg)
      else
        @__onError(msg)
#----------CD outer api----------
  close:->
    @checkState()
    @_openCount--
    if @_openCount==0
      @_send({type:ph.pa.TYPE_CLOSE})
    @
  subscribe:(qname,subname,onMessage)->
    @checkState()
    if subname && ph.jQuery.isFunction(subname)
     onMessage=subname
     subname=ph.pa._DEFAULT_SUB_ID
    else if !subname
      subname=ph.pa._DEFAULT_SUB_ID
    key="#{qname}@#{subname}"
    if @_subscribes[key]
      return @_subscribes[key].promise
    dfd=ph.jQuery.Deferred()
    prm=dfd.promise(new SD(@,dfd,qname,subname))
    @_subscribes[key]={deferred:dfd,promise:prm}
    if onMessage
      prm.onMessage(onMessage)
    #aleady recive message check
    reciveMsgs=@_reciveMsgs[key]
    if reciveMsgs && reciveMsgs.length>0
      setTimeout(=>
        for msg in reciveMsgs
          @_onMessage(msg)
      ,0)
    prm
  publish:(qname,msg)->
    @checkState()
    @_send({type:ph.pa.TYPE_PUBLISH,qname:qname,message:msg})
  qnames:(cb)->
    @checkState()
    if cb
      @on(ph.pa.TYPE_QNAMES,cb)
    @_send({type:ph.pa.TYPE_QNAMES})
  deploy:(qname,className)->
    @checkState()
    @_send({type:ph.pa.TYPE_DEPLOY,qname:qname,paletClassName:className})
  undeploy:(qname)->
    @checkState()
    @_send({type:ph.pa.TYPE_UNDEPLOY,qname:qname})
