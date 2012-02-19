if(typeof ph == "undefined"){
    ph={};
}
(function () {
  if(ph.wsq){
    return;
  }
  var wsq={
    INTERVAL:1000,
    STAT_INIT:'INIT',
    STAT_AUTH:'AUTH',
    STAT_IDLE:'IDLE',
    STAT_WS_OPEN:'WS_OPEN',
    STAT_WS_CONNECT:'WS_CONNECT',
    STAT_XHR_SEND:'XHR_SEND',
    STAT_CLOSE:'CLOSE',
    CB_INFO:'INFO',
    CB_ERROR:'ERROR',
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
      this.message='';
      this.cbType=null;
      this.cause='';/* 'open','subscribe','unsubscribe','publish','qnames','close','server' */
      this.isFin=false;/* 最終呼び出しか否か */
      this.qname='';
      this.subscribeId=-1;

      /* 内部制御情報 */
      this._cb=cb;
      this._appId=null;//not authrize yet
      this._errCount=0;/* 連続してerrorになった数 */
      this._poolMsgs=[];
      this._isCloseRequest=false;/* close要求を受け付けた */
      this._openCount=0;
      this._isOpenCallback=false;
      this._subscribes={};
    },
    /* */
    open:function(url,cb){/* isSsl,hostPort,cb */
      var con=this._connections[url];
      if(con){
        if(con._isOpenCallback){
          con._openCount++;
          con.cbType=this.CB_INFO;
          con.message='aleady opened.'+con._openCount;
        }else{
          con.cbType=this.CB_ERROR;
          con.message='aleady openning.';
        }
        con.cause='open';
        cb(con);
        return;
      }
      var con=new ph.wsq._Connection(url,cb);
      ph.wsq._connections[url]=con;
      con.stat=this.STAT_AUTH;/* auth */
      ph.auth.auth(url,function(auth){
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
      con._opencount++;
      con._setup(auth.appId);
    },
    //timerハンドラ
    _onTimer:function(){
      var cons=ph.wsq._connections;
      for(var i in cons){
        cons[i]._onTimer();
      }
      setTimeout(ph.wsq._onTimer,ph.wsq.INTERVAL);
    }
  }/* end of wsq */
  /* Connection method */
  wsq._Connection.prototype._send=function(jsonText){
    if(this._isWs){
      this._ws.send(jsonText);
    }
  };
  /* WebSocket */
  wsq._Connection.prototype._onWsOpen=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_WS_CONNECT;/* connect */
    var jsonText=con._collectMsgsText();
    if(jsonText){
      this.send(jsonText);
    }
  };
  wsq._Connection.prototype._onWsClose=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
//    this._errCount++;
  };
  wsq._Connection.prototype._onWsError=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount++;
  };
  wsq._Connection.prototype._onWsMessage=function(msg){
    var con=this._connection;
    con._errCount=0;
    //TODO callback
  };
  wsq._Connection.prototype._openWebSocket=function(){
    ph.log('Queue WebSocket start');
    this.stat=ph.wsq.STAT_WS_OPEN;/* wsOpen */
    var ws=new WebSocket(this.url);
    ws.onopen=this._onWsOpen;
    ws.onmessage=this._onWsMessage;
    ws.onclose=this._onWsClose;
    ws.onerror=this._onWsError;
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
//  this._callback(ph.wsq.CB_INFO,'opened',null,false,null,null);/* 以降はtimerからcallbackする */
  };
  wsq._Connection.prototype._restoreSs=function(){
    var subscribesString=sessionStorage[this._ssKey];
    if(subscribesString){
      this._subscribes=ph.JSON.parse(subscribesString);
//TODO function restore
    }else{
      this._subscribes={};
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
    if(this.stat==ph.wsq.STAT_WS_CONNECT){
      if(!this._isOpenCallback){
        this._isOpenCallback=true;
        this._callback(ph.wsq.CB_INFO,'open','opened',false);
      }
    }else if(this.stat==ph.wsq.STAT_IDLE){
      if(this._isCloseRequest){
        this.stat=ph.wsq.STAT_CLOSE;
        this._callback(ph.wsq.CB_INFO,'close','closed',true);
      }else if(this._errCount>=3){
        this._callback(ph.wsq.CB_ERROR,'open','connect retry over error',true);
        this.stat=ph.wsq.STAT_CLOSE;
      }else{
        if(this.isWs){
          this._openWebSocket();
        }else{
          this._sendXhr();
        }
      }
    }
  };
  /* outer function */
  wsq._Connection.prototype.close=function(){
    this._openCount--;
    if(this._openCount>0){
      return;
    }
    this._isCloseRequest=true;
    if(this._ws){
      this._ws.close();
    }
  };
  wsq._Connection.prototype.subscribe=function(qname,onMessageCb,subscribeId){
    if(this.stat!=ph.wsq.STAT_WS_CONNECT){
      this._callback(ph.wsp.CB_ERROR,'subscribe','stat error',false,qname,subscribeId);
      return;
    }
    var callbacks=this._subscribes[qname];
    if(!callbacks){
      callbacks={};
      this._subscribes[qname]=callbacks;
    }
    callbacks[subscribeId]=onMessageCb;
    var sendData={type:'subscribe',qname:qname,subscribeId:subscribeId};
    this._send(sendData);
  };
  wsq._Connection.prototype.unsubscribe=function(qname,subscribeId){
    var callbacks=this._subscribes[qname];
    if(!callbacks){
      this._callback(ph.wsp.CB_ERROR,'unsubscribe','unkown qname',false,qname,subscribeId);
      return;
    }
    if(subscribeId){
      if(!callbacks[subscribeId]){
        this._callback(ph.wsp.CB_ERROR,'unsubscribe','unkown subscribeId',false,qname,subscribeId);
        return;
      }else{
        delete callbacks[subscribeId];
      }
    }else{
      delete this._subscribes[qname];
    }
    var sendData={type:'unsubscribe',qname:qname,subscribeId:subscribeId};
    this._send(sendData);
  };
  wsq._Connection.prototype.publish=function(qname,message,subscribeId){
    if(this.stat!=ph.wsq.STAT_WS_CONNECT){
      this._callback(ph.wsp.CB_ERROR,'publish','stat error',false,qname,subscribeId);
      return;
    }
    var sendData={type:'publish',qname:qname,subscribeId:subscribeId,message:message};
    this._send(sendData);
  };
  wsq._Connection.prototype.getQnames=function(qname){};
  wsq._Connection.prototype._send=function(obj){
    var text=ph.JSON.stringify(obj);
    this._ws.send(text);
  };
  ph.wsq=wsq;
  setTimeout(ph.wsq._onTimer,ph.wsq.INTERVAL);
;})();
