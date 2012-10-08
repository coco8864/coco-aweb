class ph.Ws extens EventModule
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

class ph.WsXhr extens EventModule
  constructor:->(@url)
    ph.log('Queue Xhr start');
    @_xhrFrameName=ph.wsq._XHR_FRAME_NAME_PREFIX + @url;
    @_xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      @_xhrFrameName + 
      '" src="' + 
      @url + ph.wsq._XHR_FRAME_URL +
      '"></iframe>');
    url=@url;
    this._xhrFrame.load(->
      ph.wsq._xhrLoad(url)
    )
    ph.jQuery("body").append(this._xhrFrame);
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


