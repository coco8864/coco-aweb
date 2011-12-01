(function(){
if(window.ph.auth){
	return;
}
window.ph.auth={
	isAuthFrameLoad:false,
	authFrameName:'_phAuthFrame',
	orderInfo:{isIn:false,req:null,cb:null},
	_order:function(req,cb){
		if(this.orderInfo.isIn){
			ph.log('duplicate order type'+ req.type);
			return false;
		}
		this.orderInfo.isIn=true;
		this.orderInfo.req=req;
		this.orderInfo.cb=cb;
		this._postAuthFrame(req);
	},
	_orderResponse:function(res){
		if(!this.orderInfo.isIn){
			ph.log('auth not request');
			return;
		}
		if(res.type!=this.orderInfo.req.type){
			ph.log('auth type error:' + res.type +':' +this.orderInfo.req.type);
			return;
		}
		if(res.result=='redirect'){
			window.location.href=res.location;
			return;
		}
		this.orderInfo.cb(res);
		this.orderInfo.isIn=false;
		this.orderInfo.cb=null;
		this.orderInfo.req=null;
	},
	/*authUrl固有のappIdを取得する、以下のパターンがある
	1)secondary既に認可されている
	2)primaryは認証済みだがsecondaryが未=>認可してappIdを作成
	3)primaryは認証未=>このメソッドは復帰せず認証画面に遷移
	*/
	getAppId:function(authUrl,cb){
		var req={type:'getAppId',authUrl:authUrl,originUrl:window.location.href};
		//type:proxy|web(ws or http)
		//isSsl:true|false
		//protocol:http:,ws:
		//authDomain:ph.xxx.com...->proxyの場合
		//authPath->wsの場合
		if(authUrl.lastIndexOf('http://',0)==0){
			req.sourceType='proxy';
			req.isSsl=false;
			req.protocol='http:';
			req.authDomain=authUrl.substr(7);
		}else if(authUrl.lastIndexOf('https://',0)==0){
			req.sourceType='proxy';
			req.isSsl=true;
			req.protocol='https:';
			req.authDomain=authUrl.substr(8);
		}else if(authUrl.lastIndexOf('ws://',0)==0){
			req.sourceType='proxy';
			req.isSsl=false;
			req.protocol='ws:';
			req.authDomain=authUrl.substr(5);
		}else if(authUrl.lastIndexOf('wss://',0)==0){
			req.sourceType='proxy';
			req.isSsl=true;
			req.protocol='wss:';
			req.authDomain=authUrl.substr(6);
		}else{
			req.sourceType='web';
			req.authPath=authUrl;
			req.isSsl=false;
			if(window.location.protocol=='https:'){
				req.isSsl=true;
			}
		}
		this._order(req,cb);
	},
	getUser:function(cb){
		var req={type:'getUser'};
		this._order(req,cb);
	},
	_onMessage:function(ev){
		ph.log("ph.auth._onMessage:"+ev.data);
		if(!ph.auth.isAuthFrameLoad){
			ph.auth.isAuthFrameLoad=true;
		}
		var origin = ev.origin;
		if(!ev.data || ph.authUrl.indexOf(origin)!=0){
			return;
  		}
		var res=ph.JSON.parse(ev.data);
		ph.auth._orderResponse(res);
	},
	_postAuthFrame:function(req){
		if(!ph.auth.isAuthFrameLoad){
			ph.log("isAuthFrameLoad=false");
			setTimeout(function(){ph.auth._postAuthFrame(req);},100);
			return;
		}
		var reqText=ph.JSON.stringify(req);
		window[this.authFrameName].postMessage(reqText,ph.authUrl);
	}
};

ph.jQuery(function(){
	if(!ph.isUseCrossDomain){
		return;
	}
	//server側authと通信するiframeを作成
	if(window.addEventListener){
		window.addEventListener('message',ph.auth._onMessage, false);
	}else if(window.attachEvent){
		window.attachEvent('onmessage',ph.auth._onMessage);
	}
	var origin=encodeURIComponent(location.protocol+'//'+location.host);
	ph.jQuery("body").append('<iframe width="0" height="0" frameborder="no" name="' + ph.auth.authFrameName + '" src="' + ph.authUrl + '/authFrame.vsp?origin='+ origin +'" ></iframe>');
});

})();

