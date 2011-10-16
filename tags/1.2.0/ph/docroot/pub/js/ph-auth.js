//認証されている事が前提（認証を実施するapiはない)
//認証されていない場合は、errorルート
//frame間通信が使える場合は、postMessageで使えない場合はjsonpで処理
//logoff,frame内で処理、postMessageで使えない場合はframe名に設定
(function(){
if(window.ph.auth){
	return;
}
window.ph.auth={
	isAuthFrameLoad:false,
	authFrameName:'authFrame',
	_getUserCb:null,
	getUser:function(cb){
		this._userCb=cb;
		if(ph.isUseCrossDomain){
			ph.auth._postMessage({type:'getUser'});
		}else{
			var userCb=function(data){
				ph.auth._userCb(data);
				ph.auth._userCb=null;
			}
			ph.jQuery.post(ph.authUrl+"/ajaxUser",{},userCb,"jsonp");
		}
	},
	_authCb:null,
	_authPath:null,
	setAuth:function(path,cb){
		var authIdcb=function(data){
			var msg=ph.JSON.parse(data);
			if(msg.result){
				ph.log("setAuth direct true:"+path);
				cb(true,msg.appId);
				return;
			}
			ph.auth._authCb=cb;
			ph.auth._authPath=path;
			ph.auth._getPathOnceId(msg.authId);
		}
		ph.jQuery.post(path+"/ajaxAuthId",{},authIdcb,"html").error(function() {ph.log('setAuth error');cb(false);});
	},
	_getPathOnceId:function(authId){
		ph.log("_getPathOnceId:"+authId);
		if(ph.isUseCrossDomain){
			ph.auth._postMessage({type:'getPathOnceId',authId:authId});
		}else{
			var pathOnceIdcb=function(data){
				ph.auth._setAuth(data);
//				var res={type:'getPathOnceId',result:true,data:data};
//				ph.auth._onMessage(res);
			}
			ph.jQuery.post(ph.authUrl+"/ajaxPathOnceId",{authId:authId},pathOnceIdcb,"jsonp");
		}
	},
	_setAuth:function(pathOnceId){
		ph.log("_setAuth:"+pathOnceId);
		var setAuthCb=function(data){
			var msg=ph.JSON.parse(data);
			ph.auth._authCb(msg.result,msg.appId);
			ph.log("_setAuth:"+msg.result+":"+ph.auth._authPath);
			ph.auth._authPath=null;
			ph.auth._authCb=null;
		}
		ph.jQuery.post(ph.auth._authPath+"/ajaxSetAuth",{pathOnceId:pathOnceId},setAuthCb,"html");
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
		if(res.type=='getUser'){
			ph.auth._userCb(res.data);
			ph.auth._userCb=null;
		}else if(res.type=='getPathOnceId'){
			ph.auth._setAuth(res.data);
		}
	},
	_postMessage:function(msg){
		if(!ph.auth.isAuthFrameLoad){
			ph.log("isAuthFrameLoad=false");
			setTimeout(function(){ph.auth._postMessage(msg);},100);
			return;
		}
		var jsonMsg=ph.JSON.stringify(msg);
		window[this.authFrameName].postMessage(jsonMsg,ph.authUrl);
	}
};

ph.jQuery(function(){
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

