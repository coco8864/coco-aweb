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
    STAT_OPEN:'OPEN',
    STAT_CONNECT:'CONNECT',
    STAT_CLOSE:'CLOSE',
    CB_INFO:'INFO',
    CB_ERROR:'ERROR',
    CB_MESSAGE:'MESSAGE',
    DEFAULT_SUB_ID:'DEFAULT_SUB_ID',
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
      this.cbType=null;
      this.message='';
      this.cause='';/* 'open','subscribe','unsubscribe','publish','qnames','close','server' */
      this.isFin=false;/* 最終呼び出しか否か */
      this.qname='';
      this.subId='';

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
      //webSocketが使えなくてurlがws://だったらhttp://に変更
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
        con._callback(ph.wsq.CB_ERROR,'open','fail to auth',true);
        return;
      }
      ph.log('1:appId:'+auth.appId);
      con.stat=this.STAT_IDLE;/* idle */
      con._opencount++;
      con._appId=auth.appId;
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
    }else{
      //postMessage();
    }
  };
  /* WebSocket */
  wsq._Connection.prototype._onWsOpen=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_CONNECT;/* connect */
    var jsonText=con._collectMsgsText();
    if(jsonText){
      this._send(jsonText);
    }
  };
  wsq._Connection.prototype._onWsClose=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount++;
  };
  wsq._Connection.prototype._onWsError=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount++;
  };
  wsq._Connection.prototype._onWsMessage=function(msg){
    var con=this._connection;
    con._errCount=0;
    con._onMessageText(msg.data);
  };
  wsq._Connection.prototype._openWebSocket=function(){
    ph.log('Queue WebSocket start');
    this.stat=ph.wsq.STAT_OPEN;/* wsOpen */

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
  wsq._Connection.prototype._onMessage=function(msg){
    if(msg.type==ph.wsq.CB_MESSAGE){
      var callbacks=this._subscribes[msg.qname];
      if(!callbacks){//serverからsubscribeした覚えがないqnameにmessageが来た
        this._callback(ph.wsq.CB_ERROR,'server',msg.message,false,msg.qname,msg.subId);
        return;
      }
      var cb=callbacks[msg.subId]
      if(!cb){//serverからsubscribeした覚えがないsubIdにmessageが来た
        this._callback(ph.wsq.CB_ERROR,'server',msg.message,false,msg.qname,msg.subId);
        return;
      }
      cb(msg.message,this);
      return;
    }else if(msg.type==ph.wsq.CB_ERROR && msg.cause=='subscribe'){//subscribe失敗
      var callbacks=this._subscribes[msg.qname];
      if(callbacks){
        delete callbacks[msg.subId];
      }
    }else if(msg.type==ph.wsq.CB_INFO && msg.cause=='unsubscribe'){//正常にunsubscribe
      var callbacks=this._subscribes[msg.qname];
      if(callbacks){
        delete callbacks[msg.subId];
      }
    }else if(msg.type==ph.wsq.CB_INFO && msg.cause=='qnames'){//正常にqnames
      this.qnames=msg.message;
    }
    this._callback(msg.type,msg.cause,msg.message,false,msg.qname,msg.subId);
  }
  wsq._Connection.prototype._onMessageText=function(messageText){
    var message=ph.JSON.parse(messageText);
    if(!ph.jQuery.isArray(message)){
      this._onMessage(message);
      return;
    }
    for(var i in message){
      this._onMessage(message[i]);
    }
  };
  wsq._Connection.prototype._callback=function(cbType,cause,message,isFin,qname,subId){
    this.cbType=cbType;
    this.cause=cause;
    this.message=message;
    this.qname=qname;
    this.subId=subId;
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
    if(this.stat==ph.wsq.STAT_CONNECT){
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
  wsq._Connection.prototype._getOnlySubId=function(qname){
    var callbacks=this._subscribes[qname];
    if(!callbacks){
      return null;
    }
    var count=0;
    var subId;
    for(subId in callbacks){
      count++;
    }
    if(count!=1){
      return null;
    }
    return subId;
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
  wsq._Connection.prototype.subscribe=function(qname,onMessageCb,subId){
    if(this.stat!=ph.wsq.STAT_CONNECT){
      this._callback(ph.wsq.CB_ERROR,'subscribe','stat error',false,qname,subId);
      return undefined;
    }
    if(!subId){
      subId=ph.wsq.DEFAULT_SUB_ID;
    }
    var callbacks=this._subscribes[qname];
    if(!callbacks){
      callbacks={};
      this._subscribes[qname]=callbacks;
    }
    callbacks[subId]=onMessageCb;
    var sendData={type:'subscribe',qname:qname,subId:subId};
    this._send(sendData);
    return subId;
  };
  wsq._Connection.prototype.unsubscribe=function(qname,subId){
    var callbacks=this._subscribes[qname];
    if(!callbacks){
      this._callback(ph.wsq.CB_ERROR,'unsubscribe','unkown qname',false,qname,subId);
      return;
    }
    if(!subId){
      subId=this._getOnlySubId(qname);
      if(!subId){
        this._callback(ph.wsq.CB_ERROR,'unsubscribe','unkown qname',false,qname,subId);
      }
    }
    if(!callbacks[subId]){
      this._callback(ph.wsq.CB_ERROR,'unsubscribe','unkown subId',false,qname,subId);
      return;
    }
    var sendData={type:'unsubscribe',qname:qname,subId:subId};
    this._send(sendData);
  };
  wsq._Connection.prototype.publish=function(qname,message,subId){
    if(this.stat!=ph.wsq.STAT_CONNECT){
      this._callback(ph.wsq.CB_ERROR,'publish','stat error',false,qname,subId);
      return;
    }
    var sendData={type:'publish',qname:qname,subId:subId,message:message};
    if(subId===null){//明示的にsubIdにnullを指定,唯一のsubId指定とみなす
      subId=this._getOnlySubId(qname);
      if(!subId){
        this._callback(ph.wsq.CB_ERROR,'publish','unkown subId',false,qname,subId);
      }
      sendData={type:'publish',qname:qname,subId:subId,message:message};
    }else if(!subId){//省略した場合
      sendData={type:'publish',qname:qname,message:message};
    }
    this._send(sendData);
  };
  wsq._Connection.prototype.getQnames=function(){
    if(this.stat!=ph.wsq.STAT_CONNECT){
      this._callback(ph.wsq.CB_ERROR,'getQnames','stat error',false,null,null);
      return;
    }
    var sendData={type:'qnames'};
    this._send(sendData);
  };
  wsq._Connection.prototype._send=function(obj){
    var text=ph.JSON.stringify(obj);
    this._ws.send(text);
  };
  ph.wsq=wsq;
  setTimeout(ph.wsq._onTimer,ph.wsq.INTERVAL);
;})();
