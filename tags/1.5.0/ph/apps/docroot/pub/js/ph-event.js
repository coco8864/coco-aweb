if(typeof ph == "undefined"){
    ph={};
}
(function () {
	if(ph.event){
		return;
	}
	var event={
		EVENTID_PREFIX:'subId.',
		EVENTID_REGXP:/^subId\.(.*)/,
		interval:10000,
		publicEvents:[],
		publishMsgs:[],
		events:{},
		ws:null,
		url:'http://',
		isUseWebSocket:false,
		isXhrCall:false,
		/*外部インタフェース */
		regEvent:function(evname,cbs){this.events[evname]=cbs},
		nameSubscribe:function(evname,from){
				//subscrive中のeventはlocalStorageに覚えておく
				this._regEventId(subId,evname);
				this._send({type:'subscribe',name:evname,from:from});
			},
		subscribe:function(subId,evname){
				//subscrive中のeventはlocalStorageに覚えておく
				this._regEventId(subId,evname);
				this._send({type:'subscribe',id:subId});
			},
		publish:function(subId,message){
				var msg={type:'publish',id:subId,message:message};
				if(this._send(msg)==false){
					this.publishMsgs.push(msg);
				}
			},
		listSubscribe:function(){
				var result=[];
				var n=localStorage.length;
				for(var i=0;i<n;i++){
					var key=localStorage.key(i);
					var token=this.EVENTID_REGXP.exec(key);
					if(token==null){
						continue;
					}
					var evname=localStorage[token[1]];
					result.push({id:token[1],name:evname});
				}
				return result;
			},
		listPublicEvent:function(){return this.publicEvents},
		init:function(events){
				if(typeof WebSocket == "undefined"){
					event.isUseWebSocket=false;
					this.url="http://" + location.host + "/admin/event";
				}else{
					this.isUseWebSocket=true;
					this.url="ws://" + location.host + "/admin/event";
				}
				for(var i=0;i<events.length;i++){
					var ev=events[i];
					this.regEvent(ev.name,ev);
				}
			},
		start:function(){//起動時に一回呼ばれる,start前にregEventする事
				this.onTimer();
			},
		onTimer:function(){
				if(this.isUseWebSocket){
					if(this.ws==null){
						this._openWebSocket();
					}
				}else{
					if(!this.isXhrCall){
						this._sendXhr();
					}
					setTimeout('ph.event.onTimer',this.interval);
				}
			},
		_regEventId:function(subId,evname){
				localStorage[this.EVENTID_PREFIX+subId]=evname;
			},
		_unregEventId:function(subId){
				localStorage.removeItem(this.EVENTID_PREFIX+subId);
			},
		_openWebSocket:function(){
				this.ws=new WebSocket(this.url);
				this.ws.onopen=this.onOpen;
				this.ws.onmessage=this.onMessage;
				this.ws.onclose=this.onClose;
				this.ws.onerror=this.onError;
			},
		_collectMsgs:function(){
				//溜まっているpublishMsgs
				var result=this.publishMsgs;
				this.publishMsg=[];
				//subscribe中のevent
				var subs=this.listSubscribe();
				for(var i=0;i<subs.length;i++){
					result.push({type:'subscribe',id:subs[i].id});
				}
				return result;
			},
		_sendXhr:function(){
				var datas=this._collectMsgs();
				if(datas.length==0){
					return;
				}
				var sendText=ph.JSON.stringify([datas]);
				jQuery.ajax({
					type: 'POST',
					url: this.url,
					contentType : 'application/json',
					processData: false,
					data: sendText,
					success: function(res){
						},
					error: function(xhr, st, errorTh){
						}
				});
				this.isXhrCall=true;
			},
		_send:function(message){
		//可能か判断してデータを即座に送信、できなかった場合はfalseで復帰
				if(!this.isUseWebSocket){
					return false;
				}
				if(this.ws==null||this.ws.readyState!=1){
					return false;
				}
				//open状態なら
				var sendText=ph.JSON.stringify([message]);
				this.ws.send(sendText);
				return true
			},
		/* websocketのcallbackハンドラ */
		onOpen:function(){
naru.log('onOpen');
				var datas=ph.event._collectMsgs();
				if(datas.length==0){
					return;
				}
				var sendText=ph.JSON.stringify([datas]);
				ph.event.ws.send(sendText);
			},
		onMessage:function(datas){
naru.log('onMessage');
				for(var i=0;i<datas.length;i++){
					this._onMessage(datas[i]);
				}
			},
		_onMessage:function(data){
				if(data.type=='subscribeList'){
					this.publicEvents=data.result;
					return;
				}
//以降必ず、typeは、publish,brawserにsubscribeしてくる事はない
//				if(data.type!=('publish')){
//					return;//error
//				}
				var ev=ph.event.events[data.id];
				if(!ev){
					return;//error
				}
				if(data.action=='complete'){
					ev.onComplete(data.message);
					this._unregEventId(data.id);
				}else if(data.action=='error'){
					ev.onError(data.message);
					this._unregEventId(data.id);
				}else if(data.action=='message'){
					ev.onMessage(data.message);
				}
			},
		onClose:function(){
naru.log('onClose');
ph.event.ws=null;setTimeout('ph.event.onTimer',this.interval);},
		onError:function(){ph.event.ws=null;setTimeout('ph.event.onTimer',this.interval);},
		/* xhrのcallbackハンドラ */
		onCallback:function(datas){
				ph.event.isXhrCall=false;
				ph.event.onMessage(datas);
			},
		dummy:null
	}
	ph.event=event;

;})();
