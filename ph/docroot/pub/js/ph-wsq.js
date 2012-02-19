if(typeof ph == "undefined"){
    ph={};
}
(function () {
  if(ph.wsq){
    return;
  }
  var wsq={
    INTERVAL:1000,
    STAT_INIT:0,
    STAT_AUTH:1,
    STAT_IDLE:2,
    STAT_WS_OPEN:3,
    STAT_WS_CONNECT:4,
    STAT_XHR_SEND:5,
    STAT_CLOSE:9,
    CB_INIT:0,
    CB_INFO:1,
    CB_ERROR:2,
//  isStartTimer:false,
    _connections:{},
    /* 接続毎に作られるオブジェクト */
    _Connection:function(url,cb){
      /* callback通知情報 */
      this.url=url;
      this.isWs=true;/* WebSocket or Xhr */
      this.stat=this.STAT_INIT;/* 0:init 1:auth 2:idle 3:wsOpen 4:wsConnect 13:xhrSend */
      this.qnames=[];

      /* callback通知情報 */
      this.cbType=this.CB_INIT;/* INIT|INFO|ERROR */
      this.cause='';/* 'open','subscribe','unsubscribe','publish','qnames','close','server' */
      this.isFin=false;/* 最終呼び出しか否か */
      this.qname='';
      this.subscribeId=-1;
      this.message='';

      /* 内部制御情報 */
      this._cb=cb;
      this._appId=null;//not authrize yet
      this._errCount=0;/* 連続してerrorになった数 */
      this._poolMsgs=[];
      this._isCloseRequest;/* close要求を受け付けた */
    },
    /* */
    open:function(url,cb){/* isSsl,hostPort,cb */
      if(this._connections[url]){
        cb({type:'open',message:'aleady opened'});
        return;
      }
      var con=new ph.wsq._Connection(url,cb);
      ph.wsq._connections[url]=con;
      con.stat=this.STAT_AUTH;/* auth */
      ph.auth.auth(url,false,function(auth){
        ph.wsq._authCb(true,con,auth);
      });
    },
    /* inner interface */
    _authCb:function(isOpen,con,auth){
      if(!auth.result){
        con.stat=0;
        con._callback(ph.wsp.CB_ERROR,'open','fail to auth',true);
        return;
      }
      ph.log('1:appId:'+auth.appId);
      con.stat=this.STAT_IDLE;/* idle */
      con._setup(auth.appId);
      this._onTimer();
    },
    //timerハンドラ
    _onTimer:function(){
      var cons=ph.wsq._connections;
      for(var i in cons){
        cons[i]._onTimer();
      }
      if(cons.length>=1){
        setTimeout(ph.wsq._onTimer,ph.wsq.INTERVAL);
      }
    }
  }/* end of wsq */
  /* Connection method */
  /* WebSocket */
  wsq._Connection.prototype._onOpen=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_WS_CONNECT;/* connect */
    var sendText=con._collectMsgsText();
    if(sendText){
      this.send(sendText);
    }
  };
  wsq._Connection.prototype._onClose=function(){
    var con=this.this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
//    this._errCount++;
  };
  wsq._Connection.prototype._onError=function(){
    var con=this.this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount++;
  };
  wsq._Connection.prototype._onMessage=function(){
    var con=this.this._connection;
    con._errCount=0;
    //TODO callback
  };
  wsq._Connection.prototype._openWebSocket=function(){
    ph.log('Queue WebSocket start');
    this.stat=ph.wsq.STAT_WS_OPEN;/* wsOpen */
    var ws=new WebSocket(this.url);
    ws.onopen=this._onOpen;
    ws.onmessage=this._onMessage;
    ws.onclose=this._onClose;
    ws.onerror=this._onError;
    ws._connection=this;
    this._ws=ws;
  };
  /* Xhr */
  wsq._Connection.prototype._onXhrError=function(){
    var con=this.this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount++;
  };
  wsq._Connection.prototype._onXhrMessage=function(msgs){
    var con=this.this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount=0;
    //TODO callback
  };
  wsq._Connection.prototype._sendXhr=function(){
    var sendText=this._collectMsgsText();
    /* sendTextはnullの可能性あり */
    /*TODO _connectionの設定 */
    this.stat=ph.wsq.STAT_XHR_SEND;/* xhrSend */
    ph.jQuery.ajax({
      type: 'POST',
      url: this.url,
      contentType : 'application/json',
      processData: false,
      data:sendText,
      success:this._onXhrMessage,
      error:this._onXhrError,
      _connection:this
    });
//ph.log('_sendXhr.sendText:'+sendText);
  };

  /* */
  wsq._Connection.prototype._setup=function(authId){
    this._appId=authId;//authrize
    this._ssKey='ph.wsq.' + this.url + '@' +this._appId;
    this._restoreSs();
    this._callback({type:'open',con:this});/* 以降はtimerからcallbackする */
  };
  wsq._Connection.prototype._restoreSs=function(){
    var subscribesString=sessionStorage[this._ssKey];
    if(subscribesString){
      this._subscribes=ph.JSON.parse(subscribesString);
//TODO function restore
    }else{
      _subscribes={};
      sessionStorage[this._ssKey]='{}';
    }
  };
  wsq._Connection.prototype._storeSs=function(){
//TODO function store
    var subscribesString=ph.JSON.stringify(this._subscribes);
    sessionStorage[this._ssKey]=subscribesString;
  };
  wsq._Connection.prototype._callback=function(cbType,cause,message,isFin,qname,subscribeId){
    this.cbType=cbType;
    this.cause=cause;
    this.message=message;
    this.qname=qname;
    this.subscribeId=subscribeId;
    if(isFin){
      this.isFin=true;
      delete ph.wsq._connections[this.url];
    }else{
      this.isFin=false;
    }
    this._cb(this);
  };
  wsq._Connection.prototype._collectMsgsText=function(){
    if(this._poolMsgs.length==0){
      return null;
    }
    var msgs=this._poolMsgs;
    this._poolMsgs=[];
    return ph.JSON.stringify(msgs);
  };
  wsq._Connection.prototype._onTimer=function(){
    if(this.stat!=ph.wsq.STAT_IDLE){
      return;
    }
    if(this._isCloseRequest){
      this.stat=ph.wsq.STAT_CLOSE;
      this._callback({isFin:true});
    }else if(this._errCount>=3){
      this._callback({isFin:true,message:'connect retry over error'});
    }else{
      if(this.isWs){
        this._openWebSocket();
      }else{
        this._sendXhr();
      }
    }
  };
  /* outer function */
  wsq._Connection.prototype.close=function(){};
  wsq._Connection.prototype.subscribe=function(qname,onMessageCb,subscribeId){};
  wsq._Connection.prototype.unsubscribe=function(qname,subscribeId){};
  wsq._Connection.prototype.publish=function(qname,obj,subscribeId){};
  wsq._Connection.prototype.getQnames=function(qname){};
  ph.wsq=wsq;
;})();
