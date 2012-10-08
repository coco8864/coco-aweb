class EventModule
  on: (name, callback) ->
    @_callback ={} unless @_callback?
    @_callback[name] = [] unless @__callback[name]?
    @_callback[name].push(callback)
    @
  trigger: (name) ->
    list = @_callback[name]
    return @ unless list
    for callback in list
      callback.apply(@)
    @

class ph.Wsq extens EventModule
  _onmessages:{} #qname:subid:cb
  constructor:->(@url)
    @url=url;
    @stat=this.STAT_INIT #init auth idle open connect close
    @qnameList=[]
    @cbType=null
    @message=''
    @causeType='' # 'open','subscribe','unsubscribe','publish','qnames','deploy','close','server','beforeSubscribe' */
    @isFin=false # 最終呼び出しか否か */
    @qname=''
    @subId=''
    @appId=null # not authrize yet
    @errCount=0 # 連続してerrorになった数 */
    @poolMsgs=[]
    @isCloseRequest=false # close要求を受け付けた */
    @openCount=0
    @isOpenCallback=false
    @subscribes={}
    @unSubscribeMsgs=[] # serverからmessageが送信されたがsubscribeがされていなかった時にmsgを貯めるところ*/
    @xhrFrameName=null # xhr使用時frame名,URLを子frmeに伝える */
    @xhrFrame=null; #xhr使用時frame
    if(url.lastIndexOf('ws://',0)===0||url.lastIndexOf('wss://',0)===0)
      if(!ph.useWebSocket)
      # webSocketが使えなくてurlがws://だったらhttp://に変更
        url='http' + url.substring(2);
    else if(url.lastIndexOf('http://',0)==0||url.lastIndexOf('https://',0)==0)
    else
      if(ph.useWebSocket)
        if(ph.isSsl)
          scm='wss://'
        else
          scm='ws://'
      else
        if(ph.isSsl)
          scm='https://'
        else
          scm='http://'
      url=scm+ph.domain+url
    con=this._connections[url];
    if(con){
      if(con._isOpenCallback){
        con._openCount++;
        con._callback(this.CB_INFO,'open','aleady opened.'+con._openCount);
      }else{
        con._callback(this.CB_ERROR,'open','aleady openning.');
      }
      return;
    }


    @
  open:->
    @stat=@STAT_AUTH # auth
    ph.auth.auth(@url,(auth)=>
      if(!auth.result)
        @stat=@STAT_INIT
        @_callback(ph.wsq.CB_ERROR,'open','fail to auth',true)
        return
      ph.log('1:appId:'+auth.appId)
      @stat=this.STAT_IDLE # idle
      @_openCount++
      @_appId=auth.appId
      @_onTimer()
    @
  close:(con)->
    @
  subscribe:(con)->
    @
  unsubscribe:()->
    @
  publish:(con)->
    @
  qnames:->
    @
  deploy:->
    @
  undeploy:->
    @
  _onTimer:->
    if(@stat==@STAT_CONNECT)
      if(!@_isOpenCallback)
        @_isOpenCallback=true;
        @_callback(ph.wsq.CB_INFO,'open','opened',false);
        # 最初にconnectする際には、subscribe中のpeerをサーバに通知する
        peers=ph.wsq._loadFromSS(this._appId);
        for(var key in peers){
          this._send(peers[key]);
      var msgs=this._collectMsgs();
      if(msgs)
        this._send(msgs);
    else if(@stat==@STAT_IDLE)
      if(@_isCloseRequest)
        @stat=@STAT_CLOSE
        @_callback(ph.wsq.CB_INFO,'close','closed',true);
      else if(@_errCount>=@RETRY_COUNT){
        @_callback(ph.wsq.CB_ERROR,'open','connect retry over error',true)
        @stat=@STAT_CLOSE
      else
        if(@isWs)
          @_openWebSocket();
        else
          @_openXhr();


