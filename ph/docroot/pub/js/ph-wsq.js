if(typeof ph == "undefined"){
    ph={};
}
(function () {
  if(ph.wsq){
    return;
  }
  var wsq={
    XHR_INTERVAL:1000,
    _connections:{},
    /* 接続毎に作られるオブジェクト */
    _Connection:function(url,appId,cb){
      this._url=url;
      this._appId=appId;
      this._cb=cb;
      this._ssKey='wsq.' + url + '@' +appId;
      var subscribesString=sessionStorage[this._ssKey];
      if(subscribesString){
        this.subscribes=ph.JSON.parse(subscribesString);
//TODO function restore
      }else{
        this._subscribes={};
        sessionStorage[this._ssKey]='{}';
      }
    },
    open:function(url,cb){
      if(this._connections[url]){
        cb({type:'open',message:'aleady opened'});
        return;
      }
      ph.auth.auth(url,false,function(res){
        if(!res.result){
          cb({type:'open',message:'fail to auth'});
          return;
        }
        ph.log('1:appId:'+res.appId);
        var wsq=new ph.wsq._Connection(url,res.appId,cb);
        ph.wsq._connections[url]=wsq;
        cb({type:'open',wsq:wsq});
      });
    },

	/*内部インタフェース */
    //timerハンドラ
    _onTimer:function(){
      if(ph.isUseWebSocket){
        if(ph.wsq.ws==null){
          ph.wsq._openWebSocket();
        }
      }else{
        if(!ph.wsq.isXhrCall){
          ph.wsq._sendXhr();
        }
        if(ph.queue.isAuth){
          setTimeout(ph.wsq._onTimer,ph.wsq.interval);
        }
      }
    },
  }/* end of wsq */

  wsq._Connection.prototype.close=function(){};
  wsq._Connection.prototype.subscribe=function(qname,onMessageCb,subscribeId){};
  wsq._Connection.prototype.unsubscribe=function(qname,subscribeId){};
  wsq._Connection.prototype.publish=function(qname,obj,subscribeId){};
  wsq._Connection.prototype.getQnames=function(qname){};
  ph.wsq=wsq;
;})();
