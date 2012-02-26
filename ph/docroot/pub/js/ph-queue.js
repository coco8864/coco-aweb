if(typeof ph == "undefined"){
    ph={};
}
(function () {
	if(ph.queue){
		return;
	}
	var queue={
		CHANNEL_ID_PREFIX:'chId.',
		CHANNEL_ID_REGXP:/^chId\.(.*)/,
		interval:1000,
		publicQueue:[],
		publishMsgs:[],
		idCbs:{},//idとcallbackの組
		nameIds:{},//nameとidの組,subscribedが通知されるまでは、idにcbが入る
		ws:null,
		url:null,
		isAuth:false,
		appId:null,
		isXhrCall:false,
		isWebSocketOpen:false,
		_errorCb:null,
		_path:null,
		/*外部インタフェース */
		subscribeById:function(chId,cb){
				//subscrive中のqueueはSessionStorageに覚えておく
				this._setCb(chId,cb,null,true);
				this._send({type:'subscribe',action:'byId',id:chId});
			},
		subscribeByName:function(qname,user,cb,isNew,qcomment){
				var chId=this.nameIds[qname];
				if(chId){
					if(!typeof chId=='string'){//在ったとしても、subscribed未
						//待てばsubscribeに成功する
						this.nameIds[qname]=cb;
						return;
					}
					this._setCb(chId,cb,qname,false);//cbだけ置き換える
					return;
				}
				this.nameIds[qname]=cb;
				var msg={type:'subscribe',action:'byName',name:qname,user:user,isNew:isNew,comment:qcomment};
				if( this._send(msg)==false){
					this.publishMsgs.push(msg);
				}
			},
		unsubscribe:function(name){
				var chId=this.nameIds[name];
				if(!chId){
					ph.log('unsubscribe aleady unsubscribed.name:'+name);
					return;
				}
				delete this.nameIds[name];
				this._removeCb(chId);
				var msg={type:'subscribe',action:'unsubscribe',id:chId,name:name};
				if( this._send(msg)==false){
					this.publishMsgs.push(msg);
				}
			},
		publish:function(name,message,echoback){
				ph.log('publish message:'+message);
				var chId=this.nameIds[name];
				if(!chId){//nameIdテーブルにあるか？
					ph.log('publish not subscribed.name:'+name);
					return;
				}
				if(!typeof chId=='string'){//在ったとしても、subscribed未ではないか？
					ph.log('not recive subscribed.name:'+name);
					return;
				}
				var msg={type:'publish',id:chId,message:message,echoback:echoback};
				if(this._send(msg)==false){
					this.publishMsgs.push(msg);
				}
			},
		changeUser:function(name,newUser){
				var chId=this.nameIds[name];
				if(!chId){//nameIdテーブルにあるか？
					ph.log('changeUser not subscribed.name:'+name);
					return;
				}
				if(!typeof chId=='string'){//在ったとしても、subscribed未ではないか？
					ph.log('not recive subscribed.name:'+name);
					return;
				}
				var msg={type:'subscribe',action:'changeUser',id:chId,user:newUser};
				if(this._send(msg)==false){
					this.publishMsgs.push(msg);
				}
			},
		listUsers:function(name){
				var chId=this.nameIds[name];
				if(!chId){//nameIdテーブルにあるか？
					ph.log('listUsers not subscribed.name:'+name);
					return;
				}
				if(!typeof chId=='string'){//在ったとしても、subscribed未ではないか？
					ph.log('not recive subscribed.name:'+name);
					return;
				}
				var msg={type:'subscribe',action:'listUsers',id:chId};
				if(this._send(msg)==false){
					this.publishMsgs.push(msg);
				}
			},
		listName:function(cb){//subscribeName可能なqueueのリストを要求
				this._send({type:'list',action:'name'});
			},
		listUser:function(name,cb){//subscribeしているqueueに参加している人をリストを要求
				var chId=this.nameIds[name];
				if(!chId){//nameIdテーブルにあるか？
					ph.log('publish not subscribed.name:'+name);
					return;
				}
				if(!typeof chId=='string'){//在ったとしても、subscribed未ではないか？
					ph.log('not recive subscribed.name:'+name);
					return;
				}
				this._send({type:'list',action:'user',id:chId});
			},
		checkChId:function(chId){//有効なchIdか否かを判定
				if(this.idCbs[chId]){return true;}
				return false;
			},
		getAppId:function(){//queueのappId,sessionStrageに保存しても安全なセションID
				return this.appId;
			},
		init:function(){
			},
		start:function(path,errorCb){//起動時に一回呼ぶ,queueを受け付けるパスを指定,例'/queue'
//ph.log("queue start:"+path);
				if(!errorCb){
					errorCb=function(){ph.log('queue.start error.path:'+path);};
				}
				if(ph.isUseWebSocket){
					if(window.location.protocol=='https:'){
						this.url="wss://";
					}else{
						this.url="ws://";
					}
				}else{
					this.url=window.location.protocol + "//";
				}
				this.url+=window.location.host + path;
//				ph.auth.getAppId(path,function(res/*isAuth,appId*/){
				ph.auth.auth(path,function(res/*isAuth,appId*/){
//alert("queue auth:"+isAuth);
					if(res.result){
						ph.log('1:appId:'+res.appId);
						ph.queue.isAuth=true;
						ph.queue.appId=res.appId;
						ph.queue.onTimer();
						ph.queue._path=path;
						ph.queue._errorCb=errorCb;
						if(ph.isUseSessionStorage){
							ph.queue._restoreFromSessionStorage();
						}
					}else{
						errorCb();
					}
				});
			},
		end:function(path,errorCb){
				ph.queue._errorCb=null;
				ph.queue.isAuth=false;
				ph.queue.appId=null;
				if(ph.isUseWebSocket){
					if(ph.queue.ws!=null){
						ph.queue.ws.close();
						ph.queue.ws=null;
					}
				}
			},
		//timerハンドラ
		onTimer:function(){
				if(ph.isUseWebSocket){
					if(ph.queue.ws==null){
						ph.queue._openWebSocket();
					}
				}else{
					if(!ph.queue.isXhrCall){
						ph.queue._sendXhr();
					}
					if(ph.queue.isAuth){
						setTimeout(ph.queue.onTimer,ph.queue.interval);
					}
				}
			},
		_getCb:function(chId){
				return ph.queue.idCbs[chId];
			},
		_setCb:function(chId,cb,queueName,isPermanent){
				ph.queue.idCbs[chId]=cb;
				if(!isPermanent){
					return;
				}
				var cbStr='{onComplete:'+cb.onComplete+',onMessage:'+cb.onMessage+',onError:'+cb.onError+',appId:"'+ph.queue.appId +'"';
				if(queueName){
					cbStr+=',_queueName:"'+queueName +'"';
					cb._queueName=queueName;
				}
				cbStr+='}';
				this._regChId(chId,cbStr);
			},
		_removeCb:function(chId){
				var cb=ph.queue.idCbs[chId];
				if(cb&&cb._queueName){
					delete ph.queue.nameIds[cb._queueName];
				}
				ph.queue._unregChId(chId);
				delete ph.queue.idCbs[chId];
			},
		_restoreFromSessionStorage:function(){
				//sessionStrorageからcbを復活
				var n=sessionStorage.length;
				for(var i=0;i<n;i++){
					var key=sessionStorage.key(i);
					var token=this.CHANNEL_ID_REGXP.exec(key);
					if(token==null){
						continue;
					}
					var cbString=sessionStorage[key];
					var chId=token[1];
					eval('var cb='+cbString+';');
					if(!cb.appId || cb.appId!=ph.queue.appId){
						ph.log('skip chId:'+chId +':appId:'+ph.queue.appId);
						this._removeCb(chId);
						continue;//異なるセションのcallbackは復活させない
					}
					ph.log('load chId:'+chId +':appId:'+ph.queue.appId);
					if(cb._queueName){
						var oldChId=ph.queue.nameIds[cb._queueName];
						if(oldChId){
							this._removeCb(oldChId);
						}
						ph.queue.nameIds[cb._queueName]=chId;
					}
					ph.queue.idCbs[chId]=cb;
				}
			},
		_regChId:function(chId,cbqtext){
				if(ph.isUseSessionStorage){
					ph.log('out chid:'+chId);
					ph.log('out:'+cbqtext);
					sessionStorage[this.CHANNEL_ID_PREFIX+chId]=cbqtext;
				}
			},
		_unregChId:function(chId){
				if(ph.isUseSessionStorage){
					sessionStorage.removeItem(this.CHANNEL_ID_PREFIX+chId);
				}
			},
		_openWebSocket:function(){
				ph.log('Queue WebSocket start');
				ph.queue.isWebSocketOpen=false;
				this.ws=new WebSocket(this.url);
				this.ws.onopen=this.onOpen;
				this.ws.onmessage=this.onMessage;
				this.ws.onclose=this.onClose;
				this.ws.onerror=this.onError;
			},
		_collectMsgs:function(){
				//溜まっているpublishMsgs
				var result=this.publishMsgs;
				this.publishMsgs=[];
				//subscribe中のqueue
				for(var id in ph.queue.idCbs){
					result.push({type:'subscribe',action:'byId',id:id});
				}
				return result;
			},
		_sendXhr:function(){
				var datas=this._collectMsgs();
				if(datas.length==0){
					return;
				}
//				ph.log('Queue ajax request');
				var sendText=ph.JSON.stringify(datas);
//alert(sendText);
				ph.jQuery.ajax({
					type: 'POST',
					url: this.url,
					contentType : 'application/json',
					processData: false,
					data: sendText,
					success: this.onCallback,
					error: function(){
						ph.auth.getAppId(ph.queue._path,function(res/*isAuth,appId*/){
							if(res.result){
								ph.log('2:appId'+res.appId);
								ph.queue.isAuth=true;
								ph.queue.appId=res.appId;
							}else{
								ph.queue.isAuth=false;
								ph.queue.appId=null;
								if(ph.queue._errorCb){
									ph.queue._errorCb();
									ph.queue._errorCb=null;
								}
							}
						});
					}
				});
//				ph.log('_sendXhr.sendText:'+sendText);
				this.isXhrCall=true;
			},
		_send:function(message){
		//可能か判断してデータを即座に送信、できなかった場合はfalseで復帰
				if(!ph.isUseWebSocket){
					return false;
				}
				if(this.ws==null||this.ws.readyState!=1){
					return false;
				}
				//open状態なら
				var sendText=ph.JSON.stringify(message);
				this.ws.send(sendText);
				ph.log('Queue WebSocket send.');
				return true
			},
		/* websocketのcallbackハンドラ */
		onOpen:function(){
				ph.log('Queue WebSocket open.');
				ph.queue.isWebSocketOpen=true;
				var datas=ph.queue._collectMsgs();
				if(datas.length==0){
					return;
				}
				var sendText=ph.JSON.stringify(datas);
				ph.queue.ws.send(sendText);
				ph.log('Queue WebSocket send.');
			},
		onMessage:function(msg){
//				ph.log('Queue WebSocket message.');
				var datas=ph.JSON.parse(msg.data);
				ph.queue._onMessageObjs(datas);
			},
		_onMessageObjs:function(datas){
				if(datas instanceof Array){
					for(var i=0;i<datas.length;i++){
						ph.queue._onMessageObjs(datas[i]);
					}
				}else{
					ph.queue._onMessage(datas);
				}
			},
		_onMessage:function(data){
				if(data.type=='subscribed' && data.action=='success'){
					ph.log('subscribed success.name:'+data.name);
					var cb=ph.queue.nameIds[data.name];
					if(!cb){
						ph.log('subscribed error not subscribe.name:'+data.name);
						return;
					}
					var oldChId=ph.queue.nameIds[data.name];
					if(oldChId){
						ph.queue._removeCb(oldChId);
					}
					ph.queue.nameIds[data.name]=data.id;
					ph.queue._setCb(data.id,cb,data.name,false);
					return;
				}
				if(data.type=='subscribed' && data.action=='error'){
					ph.log('subscribed error.name:'+data.name);
					var cb=ph.queue.nameIds[data.name];
					if(!cb){
						ph.log('subscribed error not subscribe.name:'+data.name);
						return;
					}
					cb.onError(data);
					delete ph.queue.nameIds[data.name];
					return;
				}
				if(data.type=='list'){
//					this.publicQueue=data.result;
					return;
				}
				if(data.action=='logout'){
					if(ph.queue._errorCb){
							ph.queue._errorCb(data);
							ph.queue._errorCb=null;
					}
					return;
				}
//以降必ず、typeは、publish,brawserにsubscribeしてくる事はない
//				if(data.type!=('publish')){
//					return;//error
//				}
				var cb=ph.queue.idCbs[data.id];
				if(!cb){
					return;//error
				}
				if(data.action=='complete'){
					if(cb.onComplete){
						cb.onComplete(data);
					}
					ph.queue._removeCb(data.id);
				}else if(data.action=='error'){
					if(cb.onError){
						cb.onError(data);
					}
					ph.queue._removeCb(data.id);
				}else if(data.action=='message'){
					if(cb.onMessage){
						cb.onMessage(data);
					}
				}else if(data.action=='listUsers'){
					if(cb.onMessage){
						cb.onMessage(data);
					}
				}
			},
		onClose:function(){//websocketが途切れた場合認証を確認する
				ph.log('Queue WebSocket close.');
				ph.queue.ws=null;
				//一度もopenできずにcloseされた場合は、WebSocketが使えないと考える
				if(!ph.queue.isWebSocketOpen){
					ph.isUseWebSocket=false;
					ph.queue.url=window.location.protocol + "//" + window.location.host + ph.queue._path;
					ph.log("change to Polling:"+ ph.queue.url);
				}
				ph.auth.getAppId(ph.queue._path,function(res/*isAuth,appId*/){
					if(res.result){
						ph.log('3:appId'+res.appId);
						ph.queue.isAuth=true;
						ph.queue.appId=res.appId;
						setTimeout(ph.queue.onTimer,ph.queue.interval);
					}else{
						ph.queue.isAuth=false;
						ph.queue.appId=null;
						if(ph.queue._errorCb){
							ph.queue._errorCb();
							ph.queue._errorCb=null;
						}
					}
				});
			},
		onError:function(){
				ph.log('Queue WebSocket error.');
				ph.queue.ws=null;
				setTimeout(ph.queue.onTimer,ph.queue.interval);
		},
		/* xhrのcallbackハンドラ */
		onCallback:function(res){
//				ph.log('Queue ajax response.');
				ph.queue.isXhrCall=false;
				if(!res){
					return;
				}
				var datas=ph.JSON.parse(res);
				ph.queue._onMessageObjs(datas);
			},
		dummy:null
	}
	ph.queue=queue;

;})();
