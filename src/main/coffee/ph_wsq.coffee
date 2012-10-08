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
    @isFin=false # �ŏI�Ăяo�����ۂ� */
    @qname=''
    @subId=''
    @appId=null # not authrize yet
    @errCount=0 # �A������error�ɂȂ����� */
    @poolMsgs=[]
    @isCloseRequest=false # close�v�����󂯕t���� */
    @openCount=0
    @isOpenCallback=false
    @subscribes={}
    @unSubscribeMsgs=[] # server����message�����M���ꂽ��subscribe������Ă��Ȃ���������msg�𒙂߂�Ƃ���*/
    @xhrFrameName=null # xhr�g�p��frame��,URL���qfrme�ɓ`���� */
    @xhrFrame=null; #xhr�g�p��frame
    if(url.lastIndexOf('ws://',0)===0||url.lastIndexOf('wss://',0)===0)
      if(!ph.useWebSocket)
      # webSocket���g���Ȃ���url��ws://��������http://�ɕύX
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
        # �ŏ���connect����ۂɂ́Asubscribe����peer���T�[�o�ɒʒm����
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


