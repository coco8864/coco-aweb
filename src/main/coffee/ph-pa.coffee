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
        url='http' + url.substring(2)
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
      if event.source==tmpCd._xhrFrame[0].contentWindow
          cd=tmpCd
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
    msgs=@_sendMsgs
    @_sendMsgs=[]
    if @isWs
#WebSocketの場合は、1メッセージづつ送る
      for msg in msgs
        env=new Envelope()
        env.pack(msg,(protocolData)=>
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
  _send:(msg)->
    if msg.type==ph.pa.TYPE_NEGOTIATE
      @_sendMsgs.unshift(msg)
    else
      @_sendMsgs.push(msg)
    @_flushMsg()
  _onWsOpen:=>
    ph.log('Pa _onWsOpen')
    this._onOpen()
  _onWsClose:=>
    ph.log('Pa _onWsClose')
  __parseBlob:(blob)->
    fr=new FileReader()
    mode=1
    fr.onload=(e)=>
      switch mode
        when 1 #read header length
          headerLenView=new DataView(e.target.result)
          headerLength=headerLenView.getUint32(0,false)
          ph.log('headerLength:'+headerLength)
          headerBlob=ph.blobSlice(blob,offset,offset+headerLength)
          offset+=headerLength
          mode=2
          fr.readAsText(headerBlob)
        when 2 #read header
          ph.log('header:'+e.target.result)
          header=ph.JSON.parse(e.target.result)
          meta=header.meta
          env=new Envelope()
          for date in meta.dates
            env.dates.push(date)
          for blobMeta in meta.blobs
            size=blobMeta.size
            blob=ph.blobSlice(blob,offset,offset+size)
            offset+=size
            blob.type=blobMeta.type
            if blobMeta.name
              blob.name=blobMeta.name
            if blobMeta.lastModifiedDate
              blob.lastModifiedDate=blobMeta.lastModifiedDate
            env.blobs.push(blob)
          obj=env.unpack(header)
          @_onMessage(obj)
    offset=4
    headerLengthBlob=ph.blobSlice(blob,0,offset)
    fr.readAsArrayBuffer(headerLengthBlob)
  _onWsMessage:(msg)=>
    if typeof msg.data=='string'
      ph.log('Pa _onWsMessage text:'+msg.data)
      obj=ph.JSON.parse(msg.data)
      @_onMessage(obj)
    else if msg.data instanceof Blob
      ph.log('Pa _onWsMessage blob:'+msg.data)
      obj=@__parseBlob(msg.data)
    else
      ph.log('_onWsMessage type error')
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
#    this.stat=ph.pa.STAT_LOADING
  _onXhrOpen:(res)->
    ph.log('Pa _onXhrOpened')
    if res.load
      @_onOpen()
    else
      ph.log('_onXhrOpened error.'+ph.JSON.stringify(res))
  _onXhrMessage:(obj)->
    ph.log('Pa _onXhrMessage')
    @_onMessage(obj)
  _onOpen:->
    @stat=ph.pa.STAT_CONNECT
    @_send({type:ph.pa.TYPE_NEGOTIATE,bid:ph.pa._getBid(@_appId)})
  __onMsgNego:(msg)->
    if msg.bid!=ph.pa._getBid(@_appId)
      ph.pa._setBid(@_appId,msg.bid)
      @_send({type:ph.pa.TYPE_NEGOTIATE,bid:msg.bid})
  _onMessage:(msg)->
    key="#{msg.qname}@#{msg.subname}"
    sd=@_subscribes[key]
    if msg.type==ph.pa.TYPE_NEGOTIATE
      @__onMsgNego(msg)
    else if msg.type==ph.pa.TYPE_MESSAGE
      if sd
        sd.promise.trigger('message',msg.message)
    else if msg.type==ph.pa.TYPE_RESPONSE && msg.result==ph.pa.RESULT_OK
      if sd && msg.requestType==ph.pa.TYPE_SUBSCRIBE && @_subscribes[key]
        sd.deferred.resolve(msg,@_subscribes[key].promise)
        @_subscribes[key]=null
    else sd && if msg.type==ph.pa.TYPE_RESPONSE && msg.result==ph.pa.RESULT_ERROR
      if msg.requestType==ph.pa.TYPE_SUBSCRIBE && @_subscribes[key]
        sd.deferred.resolve(msg,@_subscribes[key].promise)
        @_subscribes[key]=null
#  _onMessageText:(textMsg)->
#  _onMessageBlob:(blobMsg)->
#  _callback:()->
#  _onTimer:->
#  _getOnlySubId:(qname)->
  close:->
  subscribe:(qname,subname,onSubscribe)->
    if !subname
      subname='@'
    key=qname + '@' +subname
    if @_subscribes[key]
      return @_subscribes[key].promise
    dfd=ph.jQuery.Deferred()
    prm=dfd.promise(new SD(this,qname,subname))
    @_subscribes[key]={deferred:dfd,promise:prm}
    if onSubscribe
      prm.on('message',onSubscribe)
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
  unsubscribe:->
    @_cd._send({type:'unsubscribe',qname:qname,subname:subname})
  publish:(msg)->
    @_cd._send({type:'publish',qname:@qname,subname:@subname,message:msg})

#Envelope
class Envelope
  BLOB_VALUE_NAME_PREFIX:'_paBlobValue'
  DATE_VALUE_NAME_PREFIX:'_paDateValue'
  mainObj:null
  constructor:->
    @blobs=[]
    @blobMetas=[]
    @dates=[]
    @blobDfd=null
    @asyncBlobCount=0
  meta:->
    {dates:@dates,blobs:@blobMetas}
  serialize:(obj)->
    if ph.jQuery.isArray(obj)
      result=[]
      size=obj.length
      for i in [0..size-1]
        result[i]=@serialize(obj[i])
      return result
    else if ph.useBlob && obj instanceof Uint8Array
      idx=@blobs.length
      key = @BLOB_VALUE_NAME_PREFIX+idx
      @blobs[idx]=obj
      size=obj.length*obj.BYTES_PER_ELEMENT
      @blobMetas[idx]={size:size,jsType:'ArrayBufferView'}
      return key
    else if ph.useBlob && obj instanceof ArrayBuffer
      idx=@blobs.length
      key = @BLOB_VALUE_NAME_PREFIX+idx
      @blobs[idx]=new Uint8Array(obj)
      @blobMetas[idx]={size:obj.byteLength,jsType:'ArrayBuffer'}
      return key
    else if ph.useBlob && obj instanceof Blob
      idx=@blobs.length
      key = @BLOB_VALUE_NAME_PREFIX+idx
#      fileReader=new FileReader()
#      @asyncBlobCount++
#      fileReader.onload=(e)=>
#        @blobs[idx]=e.target.result
#        @asyncBlobCount--
#        if @asyncBlobCount==0 && @blobDfd
#          @blobDfd.resolve()
#      fileReader.readAsArrayBuffer(obj)
      @blobs[idx]=obj
      meta={size:obj.size,type:obj.type,jsType:'Blob'}
      if obj.name
        meta.name=obj.name
      if obj.lastModifiedDate
        meta.lastModifiedDate=obj.lastModifiedDate.getTime()
      @blobMetas[idx]=meta
      return key
    else if obj instanceof Date
      idx=@dates.length
      key = @DATE_VALUE_NAME_PREFIX+idx
      @dates[idx]=obj.getTime()
      return key
    else if ph.jQuery.isPlainObject(obj)
      result={}
      for key,value of obj
        result[key]=@serialize(obj[key])
      return result
    else
      return obj
  deserialize:(obj)->
    if ph.jQuery.isArray(obj)
      result=[]
      size=obj.length
      for i in [0..(size-1)]
        result[i]=@deserialize(obj[i])
      return result
    else if ph.jQuery.isPlainObject(obj)
      result={}
      for key,value of obj
        result[key]=@deserialize(obj[key])
      return result
    else if typeof obj =='string'
      if obj.lastIndexOf(@BLOB_VALUE_NAME_PREFIX,0)==0
        idx=parseInt(obj.substring(@BLOB_VALUE_NAME_PREFIX.length),10)
        return @blobs[idx]
      else if obj.lastIndexOf(@DATE_VALUE_NAME_PREFIX,0)==0
        idx=parseInt(obj.substring(@DATE_VALUE_NAME_PREFIX.length),10)
        return new Date(@dates[idx])
    obj
  #bin protocol data読み込み完了時
  onDoneBinPtc:(onPacked)=>
    headerText=ph.JSON.stringify(@mainObj)
    headerTextBuf=ph.stringToArrayBuffer(headerText)
    #bb=ph.createBlobBuilder()
    headerLenBuf=new ArrayBuffer(4)
    #header長 bigEndianにして代入
    headerLenArray=new DataView(headerLenBuf)
    wkLen=headerTextBuf.byteLength
    headerLenArray.setUint32(0,wkLen,false)
    #for i in[0..3]
    #  headerLenArray[3-i]=wkLen&0xff##headerTextサイズ
    #  wkLen>>=8
    blobData=[]
#    blobData.push(headerLenArray)
    blobData.push(new Uint8Array(headerLenBuf))
    blobData.push(headerTextBuf)
    for blob in @blobs
      blobData.push(blob)
    onPacked(ph.createBlob(blobData))
  pack:(obj,onPacked)->
    @mainObj=@serialize(obj)
    @mainObj.meta=@meta()
    if !onPacked
      return @mainObj
    if @blobs.length==0
      onPacked(ph.JSON.stringify(@mainObj))
    else if @asyncBlobCount==0
      @onDoneBinPtc(onPacked)
    else
      @blobDfd=ph.jQuery.Deferred()
      @blobDfd.done(=>@onDoneBinPtc(onPacked))
  unpack:(obj)->
    @deserialize(obj)

