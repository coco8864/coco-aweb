## PHantom Wsq Admin
phwa={
  TAB_INDEXS : {'#statusTab' : 0, '#settingTab' : 1, '#debugTab' : 2}
  TAB_HASH_KEY : 'userTabHash.xxx'
  STASTICS_QN : 'stastics'
}
window.onhashchange = ->
# 現在のhashからインデックスの値を取得
  hash = window.location.hash ? sessionStorage[phwa.TAB_HASH_KEY]
  sessionStorage[phwa.TAB_HASH_KEY]=hash
  index = phwa.TAB_INDEXS[hash]
  # hashが無い場合は最初のタブを選択
  index ?= 0
  if index==8 # if debug
    $('#debugLi').show()
  # 現在のインデックスでタブをセレクト
  $('#tabs').tabs('select', index)

ph.jQuery ->
  ph.debug=true # for debug
  phwa.authUser = new AuthUser()
  ##phwa.stastics = new Stastics()
  phwa.statusView = new StatusView(new Stastics())
  ph.log(phwa.statusView.el)
  phwa.authUser.bind "done",(user) ->
    ph.log(ph.JSON.stringify(user))
    $('#loginid').text(user.loginId)
  $('#tabs').tabs({
    cache:true
    ajaxOptions: {
      error: (xhr, status, index, anchor)->
        ph.log("tabs ajaxOptions error")
      success: (xhr, status, index, anchor)->
        ph.log("tabs ajaxOptions success.index:"+index +":anchor:"+anchor)
    }
    select: (event, ui)->
      window.location.hash = ui.tab.hash
  })
  window.onhashchange()
  ws=new ph.XhrCon('https://127.0.0.1:1280/wsq')
  ws.on("frameLoad",->ph.log("frameLoad"))
  ws.on("open",->
    ph.log('open')
    ws.send('{"type":"deploy","qname":"stastics","className":"naru.aweb.wadm.StasticsWsqlet"}');
  )
  ws.on("close",->ph.log('close'))
  ws.on("messageText",(msg)->
    ph.log("messageText:#{msg}")
    ws.close()
  )
###
  ws=new ph.WsCon('wss://127.0.0.1:1280/wsq')
  ws.on("open",->
    ph.log('open')
    ws.send('{"type":"deploy","qname":"stastics","className":"naru.aweb.wadm.StasticsWsqlet"}');
  )
  ws.on("close",->ph.log('close'))
  ws.on("messageText",(msg)->
    ph.log("messageText:#{msg}")
    ws.close()
  )
###


ph.wsq2={
  _INTERVAL:1000
  xhrCons:{}
  _onXhrMessage:(event)=>
    for url,xhrCon of ph.wsq2.xhrCons
      if event.source!=xhrCon._xhrFrame[0].contentWindow
        continue
      xhrCon._onXhrMessage(event)
      break
    return
  _onTimer:->
    for url,xhrCon of ph.wsq2.xhrCons
      xhrCon._onTimer()
    setTimeout(ph.wsq2._onTimer,ph.wsq2._INTERVAL)
}

if window.addEventListener
  window.addEventListener('message',ph.wsq2._onXhrMessage, false)
else if window.attachEvent
  window.attachEvent('onmessage',ph.wsq2._onXhrMessage)
setTimeout(ph.wsq2._onTimer,ph.wsq2._INTERVAL)

class ph.EventModule
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

class ph.WsCon extends ph.EventModule
  constructor:(@url)->
    @_ws=new WebSocket(this.url)
    @_ws.onopen=@_onOpen
    @_ws.onmessage=@_onMessage
    @_ws.onclose=@_onClose
    @_ws.onerror=@_onError
    this
  close:=>
    @_ws.close()
    this
  send:(text)=>
    @_ws.send(text)
    this
  _onOpen:=>
    @trigger('open');
  _onClose:=>
    @trigger('close');
  _onError:=>
    @trigger('error');
  _onMessage:(msg)=>
    if(typeof msg.data=='string')
      @trigger('messageText',msg.data)
    else if(msg.data instanceof Blob)
      @trigger('messageBlob',msg.data)

class ph.XhrCon extends ph.EventModule
  _XHR_FRAME_NAME_PREFIX='__wsq_'
  _XHR_FRAME_URL='/xhrFrame.vsp'
  constructor:(url)->
    ph.log('Queue Xhr start')
    @url=url
    @_xhrFrameName=_XHR_FRAME_NAME_PREFIX+url
    @_xhrFrame=ph.jQuery(
      """
      <iframe width='0' height='0' frameborder='no' 
      name='#{@_xhrFrameName}' src='#{url}#{_XHR_FRAME_URL}'></iframe>
      """
    )
    url=@url;
    @_xhrFrame.bind("load",@_xhrLoad)
    @_xhrFrame.bind("unload",@_xhrUnload)
    ph.jQuery("body").append(@_xhrFrame);
    ph.wsq2.xhrCons[url]=@
    ph.log('constructor')
    this
  close:=>
    if @_xhrFrame
      @_xhrFrame.remove()
      @_xhrFrame=null
      @_onClose()
    this
  send:(text)=>
    @_xhrFrame[0].contentWindow.postMessage(text,"*")
    this
  _onOpen:=>
    @trigger('open')
  _onClose:=>
    @trigger('close')
  _onError:=>
    @trigger('error')
  _onMessage:(msg)=>
    @trigger('messageText',msg.data)
  _onXhrMessage:(event)=>
    msg=event.data
    if '{"load":true}'==msg
      @_onOpen()
    else if '{"load":false}'==msg
      @close();
      ph.log("_onXhrMessage:#{msg}")
    else
      @_onMessage(event)
  _xhrLoad:=>
    ph.log('_xhrLoad')
  _xhrUnload:=>
    ph.log('_xhrUnload')
    @_onClose()
  _onTimer:->
