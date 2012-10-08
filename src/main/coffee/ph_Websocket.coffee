class ph.WebSocket extens EventModule
  _ws:null
  constructor:->(@url)
    @_ws=new WebSocket(this.url)
    @_ws.onopen=@_onOpen
    @_ws.onmessage=@_onMessage
    @_ws.onclose=@_onClose
    @_ws.onerror=@_onError
    @
  close:->
    @_ws.close()
    @
  send:(msg)->
    @_ws.send(msg)
  _onOpen:->
    trigger('open');
    @
  _onClose:->
    trigger('close');
    @
  _onError:->
    trigger('error');
    @
  _onMessage:->(msg)->
    if(typeof msg.data==='string')
      trigger('messageText',msg.data)
    else if(msg.data instanceof Blob)
      trigger('messageBlob',msg.data)
    @

class ph.XhrWebSocket extens EventModule
  _ws:null
  constructor:->(@url)
    @_ws=new WebSocket(this.url)
    @_ws.onopen=@_onOpen
    @_ws.onmessage=@_onMessage
    @_ws.onclose=@_onClose
    @_ws.onerror=@_onError
    @
  close:->
    @_ws.close()
    @
  send:(msg)->
    @_ws.send(msg)
  _onOpen:->
    trigger('open');
    @
  _onClose:->
    trigger('close');
    @
  _onError:->
    trigger('error');
    @
  _onMessage:->(msg)->
    trigger('messageText',msg.data)
    @


