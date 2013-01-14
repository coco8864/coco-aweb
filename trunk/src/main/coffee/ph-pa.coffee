window.ph.pa={
  _connections:{} #key:url value:ConnectionDfd
  connect:(url)->
    if this._connections[url]
      return this._connections[url]
    condef = ph.jQuery.defferd()
    dfd=condef.promess(new ConnectionDfd())
    this._connections[url]=dfd
    dfd
}

class ConnectionDfd
  _subscribes:{} #key:qname@subname value:SubscribeDfd
  _send:(msg)->
  _onWsOpen:->
  _onWsClose:->
  _onWsMessage:(msg)->
  _openWebSocket:->
    ph.log('Pa WebSocket start')
    this.stat=ph.pa.STAT_OPEN
    ws=new WebSocket(this.url)
    ws.onopen=this._onWsOpen
    ws.onmessage=this._onWsMessage
    ws.onclose=this._onWsClose
    ws.onerror=this._onWsClose
    ws._connection=this
    this._ws=ws
  _onXhrOpen:->
    ph.log('Queue Xhr start')
    this._xhrFrameName=ph.pa._XHR_FRAME_NAME_PREFIX + this.url
    this._xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
      this._xhrFrameName + 
      '" src="' + 
      this.url + ph.wsq._XHR_FRAME_URL +
      '"></iframe>')
    url=this.url
    con=this;
    this._xhrFrame.load(->con._xhrLoad())
    ph.jQuery("body").append(this._xhrFrame)
  _onXhrLoad:->
  _onXhrMessage:(data)->
  _openXhr:->
    ph.log('Pa Xhr start')
  _onMessage:(msg)->
  _onMessageText:(textMsg)->
  _onMessageBlob:(blobMsg)->
  _callback:()->
  _collectMsgs:->
  _onTimer:->
  _getOnlySubId:(qname)->
  close:->
  subscribe:(qname,subname)->
    if !subname
      subname='@'
    key=qname + '@' +subname
    if @_subscribes[key]
      return @_subscribes[key]
    subdef = ph.jQuery.defferd()
    dfd=subdef.promess(new SubscribeDfd())
    @_subscribes[key]=dfd
    dfd
  publish:(msg)->
  qnames:->

class SubscribeDfd
  unsubscribe:->
  publish:(msg)->

