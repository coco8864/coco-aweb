(function(){
if(window.ph.auth){
  return;
}
window.ph.auth={
  _authFrameName:'__phAuthFrame',
  _urlPtn:/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/,
  /* 呼び出しをqueueして順次処理する仕組み */
  _callQueue:[],
  _cb:null,
  _callback:function(res){
    var cb=this._cb;
    this._cb=null;
    cb(res);
    this._call();
  },
  _callRequest:function(url,urlCb,cb){
    var req={url:url,urlCb:urlCb,cb:cb};
    this._callQueue.push(req);
    this._call();
  },
  _call:function(){
    if(this._cb!=null||this._callQueue.length==0){
      return;
    }
    var req=this._callQueue.pop();
    this._cb=req.cb;
    this._reqestUrl(req.url,req.urlCb);
  },
  _infoPath:'/info',
  _checkAplQuery:'?__PH_AUTH__=__CD_CHECK__',
  _checkWsAplQuery:'?__PH_AUTH__=__CD_WS_CHECK__',
  _authCheckCb:function(res){
    if(res.result=='redirect'){
      /* authUrlにsessionを問い合わせ */
      ph.auth._authCheck(res.location);
      return;
    }else if(res.result=='redirectForAuthorizer'){
//      alert(ph.JSON.stringify(res));
      window.location.href=res.location;
      return;
    }
    ph.auth._callback(res);
  },
  _authCheck:function(authUrl){
    this._reqestUrl(authUrl,this._authCheckCb);
  },
  _aplCheckCb:function(res){
    if(res.result=='redirect'){
      /* authUrlにsessionを問い合わせ */
      ph.auth._authCheck(res.location+'&originUrl='+window.location.href);
      return;
    }
    ph.auth._callback(res);
  },
  /*authUrl固有のappIdを取得する、以下のパターンがある
  1)secondary既に認可されている
  2)primaryは認証済みだがsecondaryが未=>認可してappIdを作成
  3)primaryは認証未=>このメソッドは復帰せず認証画面に遷移
  */
  auth:function(aplUrl,cb){
//    if(this._cb){
//      cb({result:false,reason:'duplicate call error'});
//      return;
//    }
    aplUrl.match(this._urlPtn);
    var protocol=RegExp.$1;
    var authDomain=RegExp.$2;
    var authPath=RegExp.$3;
    var checkAplUrl;
    if(protocol==='ws:'){
      checkAplUrl='http://'+authDomain+authPath+this._checkWsAplQuery;
    }else if(protocol==='wss:'){
      checkAplUrl='https://'+authDomain+authPath+this._checkWsAplQuery;
    }else if(protocol==null || protocol==''){
      checkAplUrl=window.location.protocol+'//'+window.location.host+authPath+this._checkWsAplQuery;
    }else{//http or https
      checkAplUrl=aplUrl+this._checkAplQuery;
    }
    /* aplUrlにsessionを問い合わせ */
//    this._cb=cb;
//    this._reqestUrl(checkAplUrl,this._aplCheckCb);
    this._callRequest(checkAplUrl,this._aplCheckCb,cb);
  },
  _infoCb:function(res){
    ph.auth._callback(res);
  },
  info:function(authUrl,cb){
    if(!authUrl){//指定がなければ自分をダウンロードしたauthUrl
      authUrl=ph.authUrl;
    }
    var url=authUrl+this._infoPath;
    this._callRequest(url,this._infoCb,cb);
  },
  _reqestCb:null,
  _url:null,
  _onMessage:function(ev){
ph.log("_onMessage:"+ev.origin);
//  ph.dump1(ev);
    var url=ph.auth._url;
    if(url.lastIndexOf(ev.origin, 0)!=0){
      return;
    }
    if(ev.source!==window[ph.auth._authFrameName]){
      return;
    }
    var reqestCb=ph.auth._reqestCb;
    ph.auth._url=null;
    ph.auth._reqestCb=null;
    var res=ph.JSON.parse(ev.data);
    reqestCb(res);
  },
  _reqestUrl:function(url,cb){
ph.log("_reqestUrl:"+url);
    var origin=encodeURIComponent(location.protocol+'//'+location.host);
    this._url=url;
    this._reqestCb=cb;
    window[this._authFrameName].location.href=url;
  },
  _frameLoad:function(){
    var reqestCb=ph.auth._reqestCb;
    if(reqestCb!=null){//frameにloadされたがmessageが来ない
ph.log("_frameLoad timeout:"+this._url);
      ph.auth._reqestCb=null;
      reqestCb({result:false,reason:'url error'});
    }
  }
};

ph.jQuery(function(){
//server側authと通信するiframeを作成
  if(window.addEventListener){
    window.addEventListener('message',ph.auth._onMessage, false);
  }else if(window.attachEvent){
    window.attachEvent('onmessage',ph.auth._onMessage);
  }
  ph.jQuery("body").append('<iframe width="0" height="0" frameborder="no" name="' + ph.auth._authFrameName + '" onload=\'setTimeout(ph.auth._frameLoad,2000);\'></iframe>');
});

})();

