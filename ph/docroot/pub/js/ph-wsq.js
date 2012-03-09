if(typeof ph == "undefined"){
  ph={};
}
(function () {
  if(ph.wsq){
    return;
  }
  var wsq={
    STAT_INIT:'INIT',
    STAT_AUTH:'AUTH',
    STAT_IDLE:'IDLE',
    STAT_OPEN:'OPEN',
    STAT_CONNECT:'CONNECT',
    STAT_CLOSE:'CLOSE',
    CB_INFO:'INFO',
    CB_ERROR:'ERROR',
    CB_MESSAGE:'MESSAGE',
    _INTERVAL:1000,
    _DEFAULT_SUB_ID:'defaultSubId',
    _XHR_FRAME_NAME_PREFIX:'__wsq_',
    _XHR_FRAME_URL:'/xhrFrame.html',
    _RETRY_COUNT:3,
    _connections:{},
    /* 接続毎に作られるオブジェクト */
    _Connection:function(url,cb){
      /* callback通知情報 */
      this.url=url;
      if(url.lastIndexOf('ws',0)==0 ){
        this.isWs=true;/* WebSocket */
      }else{
        this.isWs=false;/* Xhr */
      }
      this.stat=this.STAT_INIT;/* init auth idle open connect close */
      this.qnames=[];

      /* callback通知情報 */
      this.cbType=null;
      this.message='';
      this.cause='';/* 'open','subscribe','unsubscribe','publish','qnames','deploy','close','server' */
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
      this._xhrFrameName=null;/*xhr使用時frame名,URLを子frmeに伝える */
      this._xhrFrame=null;/*xhr使用時frame*/
    },
    /* */
    open:function(url,cb){/* isSsl,hostPort,cb */
      //webSocketが使えなくてurlがws://だったらhttp://に変更
      var con=this._connections[url];
      if(con){
        if(con._isOpenCallback){
          con._openCount++;
          con._callback(this.CB_INFO,'open','aleady opened.'+con._openCount);
        }else{
          con._callback(this.CB_ERROR,'open','aleady openning.');
        }
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
    //auth
    _authCb:function(isOpen,con,auth){
      if(!auth.result){
        con.stat=0;
        con._callback(ph.wsq.CB_ERROR,'open','fail to auth',true);
        return;
      }
      ph.log('1:appId:'+auth.appId);
      con.stat=this.STAT_IDLE;/* idle */
      con._openCount++;
      con._appId=auth.appId;
    },
    //timerハンドラ
    _onTimer:function(){
      var cons=ph.wsq._connections;
      for(var i in cons){
        cons[i]._onTimer();
      }
      setTimeout(ph.wsq._onTimer,ph.wsq._INTERVAL);
    },
    //xhr関連
    _frameNameToConnection:function(frameName){
      if( frameName.lastIndexOf(ph.wsq._XHR_FRAME_NAME_PREFIX,0)!=0 ){
        return null;
      }
      var url=frameName.substring(ph.wsq._XHR_FRAME_NAME_PREFIX.length);
      return this._connections[url];
    },
    _xhrLoad:function(frameName){//xhr会話用のframeがloadされたら呼び出される
      var con=this._frameNameToConnection(frameName);
      if(!con){
        //異常ありえない
        return;
      }
      con._onXhrLoad();
    },
    _xhrOnMessage:function(event){
//TODO originチェック
      var frameName=event.source.name;
      if( !frameName || frameName.lastIndexOf(ph.wsq._XHR_FRAME_NAME_PREFIX, 0) != 0 ){
        return;
      }
      var con=ph.wsq._frameNameToConnection(frameName);
      if(!con){
        //他のイベント
        return;
      }
      if(!con._isOpenCallback){
        con._onXhrOpen();
      }else{
        con._onXhrMessage(event);
      }
    }
  }/* end of wsq */
  /* Connection method */
  wsq._Connection.prototype._send=function(msg){
    if(this.stat==ph.wsq.STAT_IDLE){
      this._poolMsgs.push(msg);
      return;
    }else if(this.stat!=ph.wsq.STAT_CONNECT){
      //error
      return;
    }
    var jsonText=ph.JSON.stringify(msg);
    if(this.isWs){
      this._ws.send(jsonText);
    }else{
      window.frames[this._xhrFrameName].postMessage(jsonText,"*");//TODO origin
    }
  };
  /* WebSocket */
  wsq._Connection.prototype._onWsOpen=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_CONNECT;/* connect */
  };
  wsq._Connection.prototype._onWsClose=function(){
    var con=this._connection;
    con._ws=null;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount++;
  };
  wsq._Connection.prototype._onWsMessage=function(msg){
    var con=this._connection;
    con._errCount=0;
    if(typeof msg.data==='string'){
      con._onMessageText(msg.data);
    }
  };
  wsq._Connection.prototype._openWebSocket=function(){
    ph.log('Queue WebSocket start');
    this.stat=ph.wsq.STAT_OPEN;/* wsOpen */
    var ws=new WebSocket(this.url);
    ws.onopen=this._onWsOpen;
    ws.onmessage=this._onWsMessage;
    ws.onclose=this._onWsClose;
    ws.onerror=this._onWsClose;
    ws._connection=this;
    this._ws=ws;
  };
  /* Xhr */
  wsq._Connection.prototype._onXhrOpen=function(){//frameからの初期化messageが来たら呼び出される
    this.stat=ph.wsq.STAT_CONNECT;/* connect */
  };
  wsq._Connection.prototype._onXhrLoad=function(){//frameのonloadから呼び出される。openが呼び出されていない場合error
    if(this.stat==ph.wsq.STAT_CONNECT){
      return;//正常
    }
    //異常
    this.stat=ph.wsq.STAT_CLOSE;
  };
  wsq._Connection.prototype._onXhrMessage=function(event){
    this._onMessageText(event.data);
  };
  wsq._Connection.prototype._openXhr=function(){
    ph.log('Queue Xhr start');
    this.stat=ph.wsq.STAT_OPEN;/* wsOpen */
    this._xhrFrameName=ph.wsq._XHR_FRAME_NAME_PREFIX + this.url;
    this._xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
//    this.xhrFrame=ph.jQuery('<iframe name="' +
      this._xhrFrameName + 
      '" onload=\'ph.wsq._xhrLoad(this.name);\' src="' + 
      this.url + ph.wsq._XHR_FRAME_URL +
      '"></iframe>');
    ph.jQuery("body").append(this._xhrFrame);
  };
  /*message 処理*/
  wsq._Connection.prototype._onMessage=function(msg){
    var isFin=false;
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
      cb({type:'text',data:msg.message},this);
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
    }else if(msg.type==ph.wsq.CB_INFO && msg.cause=='getQnames'){//正常にgetQnames
      this.qnames=msg.message;
    }else if(msg.type==ph.wsq.CB_INFO && msg.cause=='close'){//正常にclose
      if(this.isWs){
        this._ws.close();
        this._ws=null;
      }else{
        this._xhrFrame.remove();
        this._xhrFrame=null;
      }
      isFin=true;
    }
    this._callback(msg.type,msg.cause,msg.message,isFin,msg.qname,msg.subId);
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
  wsq._Connection.prototype._collectMsgs=function(){
    if(this._poolMsgs.length==0){
      return null;
    }
    var msgs=this._poolMsgs;
    this._poolMsgs=[];
    return msgs;
  };
  wsq._Connection.prototype._onTimer=function(){
    if(this.stat==ph.wsq.STAT_CONNECT){
      if(!this._isOpenCallback){
        this._isOpenCallback=true;
        this._callback(ph.wsq.CB_INFO,'open','opened',false);
      }
      var msgs=this._collectMsgs();
      if(msgs){
        this._send(msgs);
      }
    }else if(this.stat==ph.wsq.STAT_IDLE){
      if(this._isCloseRequest){
        this.stat=ph.wsq.STAT_CLOSE;
        this._callback(ph.wsq.CB_INFO,'close','closed',true);
      }else if(this._errCount>=ph.wsq._RETRY_COUNT){
        this._callback(ph.wsq.CB_ERROR,'open','connect retry over error',true);
        this.stat=ph.wsq.STAT_CLOSE;
      }else{
        if(this.isWs){
          this._openWebSocket();
        }else{
          this._openXhr();
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
    this._send({type:'close'});
  };
  wsq._Connection.prototype.subscribe=function(qname,onMessageCb,subId){
    if(this.stat!=ph.wsq.STAT_CONNECT){
      this._callback(ph.wsq.CB_ERROR,'subscribe','stat error',false,qname,subId);
      return undefined;
    }
    if(!subId){
      subId=ph.wsq._DEFAULT_SUB_ID;
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
    var sendData;
    if(subId===null){//明示的にsubIdにnullを指定,唯一のsubId指定とみなす
      subId=this._getOnlySubId(qname);
      if(!subId){
        this._callback(ph.wsq.CB_ERROR,'publish','unkown subId',false,qname,subId);
        return;
      }
      sendData={type:'publish',qname:qname,subId:subId,message:message};
    }else if(!subId){//省略した場合
      sendData={type:'publish',qname:qname,message:message};
    }else{
      sendData={type:'publish',qname:qname,subId:subId,message:message};
    }
    this._send(sendData);
  };
  wsq._Connection.prototype.publishBinary=function(qname,message,subId){
    if(!this._ws){
        this._callback(ph.wsq.CB_ERROR,'publishBinary','unsuppoert publish need WebSocket',false,qname,subId);
        return;
    }
    var sendData;
    if(subId===null){//明示的にsubIdにnullを指定,唯一のsubId指定とみなす
      subId=this._getOnlySubId(qname);
      if(!subId){
        this._callback(ph.wsq.CB_ERROR,'publish','unkown subId',false,qname,subId);
        return;
      }
      sendData={type:'publish',qname:qname,subId:subId,message:message.meta};
    }else if(!subId){//省略した場合
      sendData={type:'publish',qname:qname,message:message.meta};
    }else{
      sendData={type:'publish',qname:qname,subId:subId,message:message.meta};
    }
    var metaText=ph.JSON.stringify(sendData);
    var blob = jz.zip.compress([
      {name: 'meta', str: "dummy dummy"+metaText}
    ]);
    var meta=jz.utils.stringToArrayBuffer(metaText);
    var frame=new ArrayBuffer(8+meta.byteLength+blob.size);
    var header=new Uint32Array(frame,0,8);
    /* ヘッダ(8) 全体長、meta長 */
    header[0]=8+meta.byteLength+blob.size;//全体長
    header[1]=meta.byteLength;//metaサイズ
    /* meta情報 */
    var frameMeta=new Uint8Array(frame,8,meta.byteLength);
    frameMeta.set(new Uint8Array(meta));
    var fr=new FileReader();
    fr._ws=this._ws;
    fr.onload=function(e){
      /* 圧縮データ */
      var frameZipBuf=new Uint8Array(frame,8+meta.byteLength,blob.size);
      frameZipBuf.set(new Uint8Array(e.target.result));
      this._ws.send(frame);
    };
    fr.readAsArrayBuffer(blob);
  };
  wsq._Connection.prototype.getQnames=function(){
    if(this.stat!=ph.wsq.STAT_CONNECT){
      this._callback(ph.wsq.CB_ERROR,'getQnames','stat error',false,null,null);
      return;
    }
    var sendData={type:'getQnames'};
    this._send(sendData);
  };
  wsq._Connection.prototype.deploy=function(qname,className){
    if(this.stat!=ph.wsq.STAT_CONNECT){
      this._callback(ph.wsq.CB_ERROR,'deploy','stat error',false,null,null);
      return;
    }
    var sendData={type:'deploy',qname:qname,className:className};
    this._send(sendData);
  };
  ph.wsq=wsq;
  //xhr通信用のイベント登録
  if(window.addEventListener){
    window.addEventListener('message',ph.wsq._xhrOnMessage, false);
  }else if(window.attachEvent){
    window.attachEvent('onmessage',ph.wsq._xhrOnMessage);
  }
  setTimeout(ph.wsq._onTimer,ph.wsq._INTERVAL);
;})();
