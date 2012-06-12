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
    _APPID_PREFIX:'appid.',
    _isBlobMessage:true,/* Blobメッセージを使うか否か */
    _isGz:false,/* Blobメッセージにgzipを使うか否か */
    _loadFromSS:function(appId){
      var peersText=sessionStorage[this._APPID_PREFIX+appId];
      if(!peersText){
        return {};
      }
      return ph.JSON.parse(peersText);
    },
    _saveToSS:function(appId,peers){
      var peersText=ph.JSON.stringify(peers);
      sessionStorage[this._APPID_PREFIX+appId]=peersText;
    },
    _addToSS:function(appId,msg){
      var peers=this._loadFromSS(appId);
      peers[msg.qname+'@'+msg.subId]=msg;
      this._saveToSS(appId,peers);
    },
    _removeFromSS:function(appId,msg){
      var peers=this._loadFromSS(appId);
      delete peers[msg.qname+'@'+msg.subId];
      this._saveToSS(appId,peers);
    },
    _createBlobBuilder:function(){
      var BlobBuilder = window.MozBlobBuilder || window.WebKitBlobBuilder;
      if(!BlobBuilder){
        return null;
      }
      return new BlobBuilder();
    },
    _blobSlice:function(blob,startingByte,endindByte){
      if (blob.webkitSlice) {
        return blob.webkitSlice(startingByte, endindByte);
      } else if (blob.mozSlice) {
        return blob.mozSlice(startingByte, endindByte);
      }
      return blob.slice(startingByte, endindByte);
    },
    //https://github.com/ukyo/jsziptools/blob/master/src/utils.js
    _stringToArrayBuffer:function(str){
      var n = str.length,
      idx = -1,
      utf8 = [],
      i, j, c;
      //http://user1.matsumoto.ne.jp/~goma/js/utf.js
      for(i = 0; i < n; ++i){
        c = str.charCodeAt(i);
        if(c <= 0x7F){
          utf8[++idx] = c;
        } else if(c <= 0x7FF){
          utf8[++idx] = 0xC0 | (c >>> 6);
          utf8[++idx] = 0x80 | (c & 0x3F);
        } else if(c <= 0xFFFF){
          utf8[++idx] = 0xE0 | (c >>> 12);
          utf8[++idx] = 0x80 | ((c >>> 6) & 0x3F);
          utf8[++idx] = 0x80 | (c & 0x3F);
        } else {
          j = 4;
          while(c >> (6 * j)) ++j;
          utf8[++idx] = ((0xFF00 >>> j) & 0xFF) | (c >>> (6 * --j));
          while(j--)
            utf8[++idx] = 0x80 | ((c >>> (6 * j)) & 0x3F);
        }
      }
      return new Uint8Array(utf8).buffer;
    },
    _connections:{},
    /* 接続毎に作られるオブジェクト */
    _Connection:function(url,cb){
      /* callback通知情報 */
      if(url.lastIndexOf('ws',0)==0 ){
        this.isWs=true;/* WebSocket */
      }else{
        this.isWs=false;/* Xhr */
      }
      this.url=url;
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
      this._isAllowBlob=this.isWs;//TODO check File Api too BlobBuilder...
      this._cb=cb;
      this._appId=null;//not authrize yet
      this._errCount=0;/* 連続してerrorになった数 */
      this._poolMsgs=[];
      this._isCloseRequest=false;/* close要求を受け付けた */
      this._openCount=0;
      this._isOpenCallback=false;
      this._subscribes={};
      this._unSubscribeMsgs=[];/*serverからmessageが送信されたがsubscribeがされていなかった時にmsgを貯めるところ*/
      this._xhrFrameName=null;/*xhr使用時frame名,URLを子frmeに伝える */
      this._xhrFrame=null;/*xhr使用時frame*/
    },
    /* */
    open:function(url,cb){/* isSsl,hostPort,cb */
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
      if(url.lastIndexOf('ws',0)==0 ){
        if(!ph.isUseWebSocket){
          //webSocketが使えなくてurlがws://だったらhttp://に変更
          url="http" + url.substring(2);
        }
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
      con._onTimer();
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
    _xhrLoad:function(url){//xhr会話用のframeがloadされたら呼び出される
//      alert('ph-wsq _xhrLoad:' + url);
      var con=this._connections[url];
      if(!con){
        //異常ありえない
        return;
      }
      con._onXhrLoad();
    },
    _xhrOnMessage:function(event){
//TODO originチェック
      var con;
      for(var url in ph.wsq._connections){
        var tmp=ph.wsq._connections[url];
        if(!tmp._xhrFrame){
          continue;
        }
        if(event.source===tmp._xhrFrame[0].contentWindow){
//          alert("!!!!hit!!!!:"+url);
          con=tmp;
          break;
        }
      }
//      var frameName=event.source.name;
//      if( !frameName || frameName.lastIndexOf(ph.wsq._XHR_FRAME_NAME_PREFIX, 0) != 0 ){
//        return;
//      }
//      var allData=event.data;
//      var urlPosition=allData.indexOf('@');
//      if(urlPosition<0){
//        return;
//      }
//      var url=allData.substring(0,urlPosition);
//      var con=ph.wsq._connections[url];
      if(!con){
        //他のイベント
        return;
      }
      var data=event.data;
      if(!con._isOpenCallback){
        con._onXhrOpen();
      }else{
        con._onXhrMessage(data);
      }
    },
    _buildBlobs:function(header,message,data,con){
      if(!ph.jQuery.isArray(data)){
        data=[data];
      }
      var blobHeader={};
      blobHeader.message=message;
      blobHeader.metas=[];
      blobHeader.count=0;
      blobHeader.totalLength=0;
      var results=[];//ArrayBufferの列に変換する
      var readCount=0;
      for(var i in data){
        var meta={};
        var d=data[i].data;
        blobHeader.metas[i]=meta;
        if(d instanceof ArrayBuffer){
          blobHeader.count++;
          meta.jsType='ArrayBuffer';
          meta.size=d.byteLength;
          results[i]=b;
          blobHeader.totalLength+=meta.size;
          continue;
        }else if(typeof d==='string'){
          blobHeader.count++;
          meta.jsType='string';
          results[i]=ph.wsq._stringToArrayBuffer(d);
          meta.size=results[i].byteLength;
          blobHeader.totalLength+=meta.size;
          continue;
        }else if(!(d instanceof Blob)){
          blobHeader.count++;
          meta.jsType='object';
          var dText=ph.JSON.stringify(d);
          results[i]=ph.wsq._stringToArrayBuffer(dText);
          meta.size=results[i].byteLength;
          blobHeader.totalLength+=meta.size;
          continue;
        }
        meta.mimeType=d.type;
        meta.jsType='Blob';
        meta.name=d.name;
        meta.size=d.size;
        blobHeader.totalLength+=meta.size;
        var fr=new FileReader();
        fr._i=i;
        fr.onload=function(e){
          results[this._i]=e.target.result;
          blobHeader.count++;
          if(blobHeader.count===data.length){//複数のblobを全て読みきったら
            ph.wsq._sendBlobMessage(header,blobHeader,results,con);
          }
        }
        fr.readAsArrayBuffer(d);
      }
      if(blobHeader.count===data.length){//Blobは無かった
        ph.wsq._sendBlobMessage(header,blobHeader,results,con);
      }
    },
    _sendBlobMessage:function(header,blobHeader,data,con){
      var blobHeaderText=ph.JSON.stringify(blobHeader);
      var blobHeaderBuf=ph.wsq._stringToArrayBuffer(blobHeaderText);
      header.dataType='blobMessage';
      header.blobHeaderLength=blobHeaderBuf.byteLength;

      var payloadGzBuf=null;
      var payloadBuf=new ArrayBuffer(blobHeader.totalLength);
      var payloadBufA=new Uint8Array(payloadBuf);
      var pos=0;
      for(var i in data){
        var len=data[i].byteLength;
        payloadBufA.set(new Uint8Array(data[i]),pos,len);
        pos+=len;
      }
      if(ph.wsq._isGz){
        blobHeader.isGz=true;
        payloadBuf=jz.gz.compress(payloadBuf);
      }else{
        blobHeader.isGz=false;
      }
//      var BlobBuilder = window.MozBlobBuilder || window.WebKitBlobBuilder;
      var bb=ph.wsq._createBlobBuilder();
      var headerLenBuf=new ArrayBuffer(4);
      var headerText=ph.JSON.stringify(header);
      var headerBuf=ph.wsq._stringToArrayBuffer(headerText);
      /* header長 bigEndianにして代入 */
      var headerLenArray=new Uint8Array(headerLenBuf);
      var wkLen=headerBuf.byteLength;
      for(var i=0;i<4;i++){
        headerLenArray[3-i]=wkLen&0xff//metaサイズ
        wkLen>>=8;
      }
      bb.append(headerLenBuf);
      bb.append(headerBuf);
      bb.append(blobHeaderBuf);
      bb.append(payloadBuf);
      con._ws.send(bb.getBlob());
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
      this._xhrFrame[0].contentWindow.postMessage(jsonText,"*");//TODO origin
    }
  };
  /* WebSocket */
  wsq._Connection.prototype._onWsOpen=function(){
    var con=this._connection;
    con.stat=ph.wsq.STAT_CONNECT;/* connect */
    con._onTimer();
  };
  wsq._Connection.prototype._onWsClose=function(){
    var con=this._connection;
    con._ws=null;
    con.stat=ph.wsq.STAT_IDLE;/* idle */
    con._errCount++;
  };
  wsq._Connection.prototype._onWsMessage=function(msg){
    ph.log('Queue Message type:' + typeof msg.data);
    var con=this._connection;
    con._errCount=0;
    if(typeof msg.data==='string'){
      con._onMessageText(msg.data);
    }else if(msg.data instanceof Blob){
      con._onMessageBlob(msg.data);
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
    this._onTimer();
  };
  wsq._Connection.prototype._onXhrLoad=function(){//frameのonloadから呼び出される。openが呼び出されていない場合error
//alert('ph-wsq stat:' +this.stat);
    if(this.stat===ph.wsq.STAT_IDEL){
      this.stat=ph.wsq.STAT_OPEN;
      return;//正常
    }else if(this.stat===ph.wsq.STAT_CONNECT){
      return;//正常
    }
    //異常
    this.stat=ph.wsq.STAT_CLOSE;
  };
  wsq._Connection.prototype._onXhrMessage=function(data){
    this._onMessageText(data);
  };
  wsq._Connection.prototype._openXhr=function(){
    ph.log('Queue Xhr start');
//  this.stat=ph.wsq.STAT_OPEN;/* wsOpen */
    this._xhrFrameName=ph.wsq._XHR_FRAME_NAME_PREFIX + this.url;
    this._xhrFrame=ph.jQuery('<iframe width="0" height="0" frameborder="no" name="' +
//    this.xhrFrame=ph.jQuery('<iframe name="' +
      this._xhrFrameName + 
//    '" onload=\'ph.wsq._xhrLoad(this.url);\' src="' + 
    '" src="' + 
      this.url + ph.wsq._XHR_FRAME_URL +
      '"></iframe>');
    var url=this.url;
    this._xhrFrame.load(function(x){ph.wsq._xhrLoad(url);});
    ph.jQuery("body").append(this._xhrFrame);
  };
  /*message 処理*/
  wsq._Connection.prototype._onMessage=function(msg){
    var isFin=false;
    if(msg.type==ph.wsq.CB_MESSAGE){
      var callbacks=this._subscribes[msg.qname];
      if(!callbacks){//serverからsubscribeした覚えがないqnameにmessageが来た
//まだsubscribeされていない可能性あり、msgをqueueする
        this._unSubscribeMsgs.push(msg);
//      this._callback(ph.wsq.CB_ERROR,'server',msg.message,false,msg.qname,msg.subId);
        return;
      }
      var cb=callbacks[msg.subId]
      if(!cb){//serverからsubscribeした覚えがないsubIdにmessageが来た
//まだsubscribeされていない可能性あり、msgをqueueする
        this._unSubscribeMsgs.push(msg);
//        this._callback(ph.wsq.CB_ERROR,'server',msg.message,false,msg.qname,msg.subId);
        return;
      }
//      cb({type:'text',data:msg.message},this);
      cb(msg.message,this);
      return;
    }else if(msg.type==ph.wsq.CB_ERROR && msg.cause=='subscribe'){//subscribe失敗
      ph.wsq._removeFromSS(this._appId,{type:'subscribe',qname:msg.qname,subId:msg.subId,isAllowBlob:this._isAllowBlob});
      if(!this.isWs){//xhrの場合frameに覚えているsubscribe情報をクリア
        this._send({type:'xhrUnsubscribe',qname:msg.qname,subId:msg.subId});
      }
      var callbacks=this._subscribes[msg.qname];
      if(callbacks){
        delete callbacks[msg.subId];
      }
    }else if(msg.type==ph.wsq.CB_INFO && msg.cause=='subscribe'){//subscribe成功
      ph.wsq._addToSS(this._appId,{type:'subscribe',qname:msg.qname,subId:msg.subId,isAllowBlob:this._isAllowBlob});
    }else if(msg.type==ph.wsq.CB_INFO && msg.cause=='unsubscribe'){//正常にunsubscribe
      ph.wsq._removeFromSS(this._appId,{type:'subscribe',qname:msg.qname,subId:msg.subId,isAllowBlob:this._isAllowBlob});
      if(!this.isWs){//xhrの場合frameに覚えているsubscribe情報をクリア
        this._send({type:'xhrUnsubscribe',qname:msg.qname,subId:msg.subId});
      }
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
  wsq._Connection.prototype._onMessageBlob=function(blob){
    var con=this;
    var header;
    var offset=0;
    var mode=1;
    var fr=new FileReader();
    fr.onload=function(e){
      switch(mode){
        case 1:
          var u8array=new Uint8Array(e.target.result);
          var headerLength=0;
          for(var i=0;i<4;i++){
            headerLength<<=8;
            headerLength+=u8array[i];
          }
          ph.log('headerLength:'+headerLength);
          var headerBlob=wsq._blobSlice(blob,offset,offset+headerLength);
          offset+=headerLength;
          mode=2;
          fr.readAsText(headerBlob);
        break;
        case 2:
          ph.log('header:'+e.target.result);
          header=ph.JSON.parse(e.target.result);
          var blobHeaderBlob=wsq._blobSlice(blob,offset,offset+header.blobHeaderLength);
          offset+=header.blobHeaderLength;
          mode=3;
          fr.readAsText(blobHeaderBlob);
        break;
        case 3:
          ph.log('blobHeader:'+e.target.result);
          var blobHeader=ph.JSON.parse(e.target.result);
          var metas=blobHeader.metas;
          var message=blobHeader.message;
          message.blobData=[];
          for(var i=0;i<metas.length;i++){
            var meta=metas[i];
            meta.data=wsq._blobSlice(blob,offset,offset+meta.size,meta.mimeType);
            meta.jsType='Blob';
            message.blobData[i]=meta;
            offset+=meta.size;
          }
          header.message=message;
          con._onMessage(header);
        break;
      }
    }
    offset=4;
    var headerLengthBlob=wsq._blobSlice(blob,0,offset);
    fr.readAsArrayBuffer(headerLengthBlob);
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
        //最初にconnectする際には、subscribe中のpeerをサーバに通知する
        var peers=ph.wsq._loadFromSS(this._appId);
        for(var key in peers){
          this._send(peers[key]);
        }
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
    var peers=ph.wsq._loadFromSS(this._appId);
    for(var key in peers){
      var peer=peers[key];
      this.unsubscribe(peer.qname,peer.subId);
    }
    if(this.isWs){
      if(this._ws){
        this._ws.close();
      }
      this._ws=null;
    }else{
      if(this._xhrFrame){
        this._xhrFrame.remove();
      }
      this._xhrFrame=null;
    }
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
    var sendData={type:'subscribe',qname:qname,subId:subId,isAllowBlob:this._isAllowBlob};
    ph.wsq._addToSS(this._appId,sendData);
    this._send(sendData);
    /* 既に到着しているmessageがあるかも知れない */
    if(this._unSubscribeMsgs.length!=0){
      var msgs=this._unSubscribeMsgs;
      this._unSubscribeMsgs=[];
      for(var i=0;i<msgs.length;i++){
        this._onMessage(msgs[i]);
      }
    }
    return subId;
  };
  wsq._Connection.prototype.unsubscribe=function(qname,subId){
    var callbacks=this._subscribes[qname];
    if(!callbacks){
//    this._callback(ph.wsq.CB_ERROR,'unsubscribe','unkown qname',false,qname,subId);
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
  //binaryの場合のmessage形式,message.blobData:[]の有無
  wsq._Connection.prototype.publish=function(qname,message,subId){
    var header;
    if(subId===null){//明示的にsubIdにnullを指定,唯一のsubId指定とみなす
      subId=this._getOnlySubId(qname);
      if(!subId){
        this._callback(ph.wsq.CB_ERROR,'publish','unkown subId',false,qname,subId);
        return;
      }
      header={type:'publish',qname:qname,subId:subId};
    }else if(!subId){//省略した場合
      header={type:'publish',qname:qname};
    }else{
      header={type:'publish',qname:qname,subId:subId};
    }
    if(!message.blobData){//TODO check
      header.message=message;
      this._send(header);
    }else{
      if(!this._isAllowBlob){
        this._callback(ph.wsq.CB_ERROR,'publish binary','unsuppoert publish need WebSocket',false,qname,subId);
        return;
      }
      var blobData=message.blobData;
      delete message.blobData;
      ph.wsq._buildBlobs(header,message,blobData,this);
    }
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
