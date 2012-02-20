(function(){
if(window.ph.auth){
  return;
}
window.ph.auth={
  _authFrameName:'_phAuthFrame',
  _urlPtn:/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/,
  _authCheckReq:null,
  _authCb:null,
  _userPath:'/user',
  _checkAuthPath:'/checkSession?',
  _checkAplQuery:'?PH_AUTH=check',
  _userCb:function(resObj){
    var res=resObj;
    if(typeof resObj.result==="undefined"){
      res={result:true,user:resObj};
    }
    var authCb=ph.auth._authCb;
    ph.auth._authCb=null;
    authCb(res);
  },
  _user:function(authUrl){
    var url=authUrl+this._userPath;
    this._reqestUrl(url,this._userCb);
  },
  _authCheckCb:function(res){
    var authCb=ph.auth._authCb;
    ph.auth._authCb=null;
    if('redirect'==res.result){
      //primaryがない場合は、redirectして認証する
      window.location.href=res.location;
      return;
    }
    authCb(res);
  },
  _authCheck:function(authUrl){
    var url=authUrl+this._checkAuthPath+ph.jQuery.param(this._authCheckReq);
    this._reqestUrl(url,this._authCheckCb);
  },
  _aplCheckCb:function(res){
    if(res.result || 'url error'===res.reason){
      var authCb=ph.auth._authCb;
      ph.auth._authCb=null;
      authCb(res);
      return;
    }
    /* authUrlにsessionを問い合わせ */
    ph.auth._authCheck(res.authUrl);
  },
  /*authUrl固有のappIdを取得する、以下のパターンがある
  1)secondary既に認可されている
  2)primaryは認証済みだがsecondaryが未=>認可してappIdを作成
  3)primaryは認証未=>このメソッドは復帰せず認証画面に遷移
  */
  getAppId:function(path,cb){
    var url=window.location.protocol+"//"+window.location.host+path;
    this.auth(url,cb,false);
  },
  auth:function(aplUrl,cb,isProxy){
    if(this._authCb){
      cb({result:false,reason:'duplicate call error'});
      return;
    }
    aplUrl.match(this._urlPtn);
    var protocol=RegExp.$1;
    var authDomain=RegExp.$2;
    var authPath=RegExp.$3;
    var req={type:'getAppId',aplUrl:aplUrl,originUrl:window.location.href};
    if(isProxy){
      req.sourceType='proxy';
    }
    req.protocol=protocol;
    req.authDomain=authDomain;
    req.authPath=authPath;
    if(protocol==null || protocol==''){
      req.protocol=window.location.protocol;
      req.authDomain=window.location.hostname;
    }
    this._authCheckReq=req;
    this._authCb=cb;
    var checkAplUrl;
    if(req.protocol==='ws:'){
      checkAplUrl='http://'+req.authDomain+req.authPath;
    }else if(req.protocol==='wss:'){
      checkAplUrl='https://'+req.authDomain+req.authPath;
    }else{
      checkAplUrl=aplUrl;
    }
    /* aplUrlにsessionを問い合わせ */
    this._reqestUrl(checkAplUrl+this._checkAplQuery,this._aplCheckCb);
  },
  user:function(authUrl,cb){
    if(this._authCb){
      cb({result:false,reason:'duplicate call error'});
      return;
    }
    if(!authUrl){//指定がなければ自分をダウンロードしたauthUrl
      authUrl=ph.authUrl;
    }
    this._authCb=cb;
    ph.auth._user(authUrl);
  },
  _reqestCb:null,
  _url:null,
  _onMessage:function(ev){
//  ph.dump1(ev);
    var url=ph.auth._url;
    if(url.lastIndexOf(ev.origin, 0)!=0){
      return;
    }
    var reqestCb=ph.auth._reqestCb;
    ph.auth._url=null;
    ph.auth._reqestCb=null;
    var res=ph.JSON.parse(ev.data);
    reqestCb(res);
  },
  _reqestUrl:function(url,cb){
    var origin=encodeURIComponent(location.protocol+'//'+location.host);
    this._url=url;
    this._reqestCb=cb;
    window[this._authFrameName].location.href=url;
  },
  _frameLoad:function(){
    var reqestCb=ph.auth._reqestCb;
    if(reqestCb!=null){//frameにloadされたがmessageが来ない
      ph.auth._reqestCb=null;
      reqestCb({result:false,reason:'url error'});
    }
  }
};

ph.jQuery(function(){
//if(!ph.isUseCrossDomain){
//  return;
//}
//server側authと通信するiframeを作成
  if(window.addEventListener){
    window.addEventListener('message',ph.auth._onMessage, false);
  }else if(window.attachEvent){
    window.attachEvent('onmessage',ph.auth._onMessage);
  }
  ph.jQuery("body").append('<iframe width="0" height="0" frameborder="no" name="' + ph.auth._authFrameName + '" onload=\'setTimeout(ph.auth._frameLoad,2000);\'></iframe>');
});

})();

