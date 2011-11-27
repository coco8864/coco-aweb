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
	orderResponse:function(res){
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
	getAppId:function(authUrl,cb){
		if(authUrl.lastIndexOf('/',0)==0){
			authUrl=window.location.protocol + "//" + window.location.host + authUrl;
		}
		var req={type:'getAppId',authUrl:authUrl,originUrl:window.location.href};
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
		ph.auth.orderResponse(res);
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

