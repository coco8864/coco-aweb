
ph.History=function(h){
 if(h){
  this.cur=h.cur;
  this.list=h.list;
 }else{
  this.cur=0;
  this.list=[];
 }
};

ph.History.prototype.get=function(){
 if(this.cur>0 && this.list.length>(this.cur-1)){
  return this.list[this.cur-1];
 }
 return null;
};
ph.History.prototype.put=function(obj){
 this.list[this.cur]=obj;
 this.cur++;
 this.list.splice(this.cur,this.list.length-this.cur);
};
ph.History.prototype.back=function(obj){
 if(this.cur>0){
  this.cur--;
  return this.get();
 }
 return null;
};
ph.History.prototype.prev=function(obj){
 if(this.list.length>this.cur){
  this.cur++;
  return this.get();
 }
 return null;
};

ph.Web=function(frameNo,w){
 this.frameNo=frameNo;
 this.remoteUrls={};
 //frameWin配下に存在するframe名群,key:win名+':'+url value:[frameNames]
 this.frameNames={};
 this.frameName='phFrameName'+frameNo;
 //addressBar
 this.addressBar=null;
 this.shareSpan=null;
 this.frameWin=null;
 if(w){
  this.lavel=w.lavel;
  this.home=w.home;
  this.history=new ph.History(w.history);
  if(!w.history&&w.home){
   this.history.put(new ph.Tran(w.home));
  }
  this.shareMode=w.shareMode;
  this.shareName=w.shareName;
  if(this.shareMode=='share'){
   this.subShareWeb();
  }
 }else{
  this.lavel="web"+frameNo;
  this.home="";
  this.history=new ph.History();
  this.shareMode="";
 }
};

ph.Web.prototype.onload=function(){
 this.frameWin=window[this.frameName];
 this.addressBar=ph.jQuery("#addressBar"+this.frameNo);
 this.shareSpan=ph.jQuery("#share"+this.frameNo);
 if(!this.frameWin){
  alert('fail to web load.frameNo:'+this.frameNo);
 }
}

//web参照を開始
ph.Web.prototype.subShareWeb=function(){
 var subCb={
  web:this,
  onComplete:function(msg){ph.log('shareCb onComplete:');ph.dump(msg);},
  onError:function(msg){ph.log('shareCb onError:');ph.dump(msg);},
  onMessage:function(msg){ph.log('share receive.type:'+msg.message.type);this.web.onRemoteMessage(msg.message);}
 };
 ph.queue.subscribeByName(this.shareName,ph.loginId,subCb,true,this.shareName);
};

ph.Web.prototype.postMessage=function(obj){
 var msgText=ph.JSON.stringify(obj);
 this.frameWin.postMessage(msgText, '*');
};

//実際に移動する
ph.Web.prototype._go=function(url){
 this.addressBar.val(url);
 url=ph.rewriteUrl(url);
 this.addressBar.attr("disabled","disabled");
 window.open(url,this.frameName);
};
ph.Web.prototype._submit=function(obj,isSkipConfirm){
 if(!isSkipConfirm && (obj.method=='POST'||obj.method=='post')){
  if( !confirm(obj.method +'しますか?\r\nurl:'+obj.url ) ){
   this._go(obj.url);
   return;
  }
 }
 this.addressBar.val(obj.url);
 var target=this.rewriteTarget(obj.target,obj.windowName,obj.topFrameName);
 if(obj.method!='POST'&&obj.method!='post'){
  this._go(obj.url);
  return;
 }
 var url=ph.rewriteUrl(obj.url);
 this.addressBar.attr("disabled","disabled");
 var form=ph.jQuery(
		'<form action="' + url + 
		'" target="' + target + 
		'" method="'+ obj.method+
		'"></form>');
 if(obj.params){
  for(var i=0;i<obj.params.length;i++){
   var param=obj.params[i];
   form.append('<input type="hidden" name="'+param.name +'" value="' + param.value +'"/>');
  }
 }
 ph.jQuery('body').append(form);
 form.submit();
 form.remove();
};

//積極的に遷移するメソッド、履歴に記録
ph.Web.prototype.go=function(url){
 if(!url){
  url=this.addressBar.val();
 }
 if(url){
  this.history.put(new ph.Tran(url));
  this._go(url);
 }
};

ph.Web.prototype.submit=function(obj,isSkipConfirm){
 this.history.put(obj);
 this._submit(obj,isSkipConfirm);
};

//type:back,prev,reload,stop,home
ph.Web.prototype.action=function(type){
 ph.log("web type:"+type+":list:"+this.history.list);
 var url;
 if(type=='back'){
  url=this.history.back();
 }else if(type=='prev'){
  url=this.history.prev();
 }else if(type=='reload'){
  url=this.history.get();
 }
 if(url){
  this._submit(url);
 }
 if(type=='stop'){
  if(this.addressBar.attr("disabled")){
   this.addressBar.attr("disabled","");
    window.open("stop.html",this.frameName);
  }
 }else if(type=='home'&& this.home){
  this.go(this.home);
 }
};

//遷移しつつある事を通知するメソッド
ph.Web.prototype.going=function(obj){
 var url;
 if(obj.url){
  url=obj.url;
  obj.url=ph.revRewriteUrl(url);
 }
 if(!url){
  return;
 }
 this.addressBar.val(url);
// this.history.put(url);
 this.history.put(obj);
 this.addressBar.attr("disabled","disabled");
};

//遷移した事を通知してもらうメソッド
ph.Web.prototype.gone=function(obj){
 var goingObj=this.history.get();
 if(!goingObj || goingObj.url!=obj.url){//goingが呼びだれていないのに勝手に遷移した場合
  this.history.put(obj);
  this.addressBar.val(obj.url);
 }
 this.addressBar.attr("disabled","");
};

//フレームからのイベントをすべて通知してもらうメソッド
ph.Web.prototype.onMessage=function(msgObj){
  this.frameName=msgObj.topFrameName;
  if(this.shareMode=='share'){
   this.sharePublish(msgObj);
  }
//Aタグがclick
//passwordフィールを持つformがsumit
//formがsumit
  if(msgObj.type=='click'||msgObj.type=='passwordSubmit'||msgObj.type=='submit'){
   if(msgObj.windowName==msgObj.topFrameName){
    msgObj.url=ph.revRewriteUrl(msgObj.url);
    this.going(msgObj);
   }
//passwordフィールドを持つpageがloadされた
//pageがloadされた
  }else if(msgObj.type=='passwordLoad'||msgObj.type=='load'){
   var url=ph.revRewriteUrl(msgObj.documentLocationHref);
   if(msgObj.windowName==msgObj.topFrameName){
    this.gone(new ph.Tran(url));
   }
   this.frameNames[msgObj.windowName +":" +url]=msgObj.frameNames;
  }else if(msgObj.type=='unload'){//passwordを含む画面が表示された
   var url=ph.revRewriteUrl(msgObj.documentLocationHref);
   delete this.frameNames[msgObj.windowName +":" +url];
  }
};

ph.Web.prototype.rewriteTarget=function(target,windowName,topFrameName){
 if(!target){
  if(windowName==topFrameName){
   return this.frameName;
  }
  if(!windowName){
   return this.frameName;
  }
  target=windowName;
 }
 if(target=='_top'){
  return this.frameName;
 }
 if(target=='_parent'){//maybe
  return this.frameName;
 }
 if(target==this.frameName){
  return target;
 }
 for(var i in this.frameNames){
  var names=this.frameName[i];
  for(var j=0;j<names.length;j++){
   if(target==names[j]){
    return target;
   }
  }
 }
 ph.log('not found framename.target:'+target);
 return this.frameName;
};

ph.Web.prototype.sharePublish=function(msgObj){
 if(msgObj.type=='passwordSubmit'){//psswordを含むsubmit情報はpublishしない
  return;
 }
 if(msgObj.type=='passwordLoad'||msgObj.type=='load'){
  var url=ph.revRewriteUrl(msgObj.documentLocationHref);
  //remote経由で依頼されたurlへの移動通知はpublisしない
  if( this.remoteUrls[url] ){
   delete this.remoteUrls[url];
   return;
  }
 }
 ph.queue.publish(this.shareName,msgObj,false);
};

ph.Web.prototype.remoteGo=function(url){
 var curUrl=this.history.get();
 if(curUrl && url.url!=curUrl.url){
  this.remoteUrls[curUrl.url]=true;
  this.submit(url);
 }else if(!curUrl){
  this.submit(url);
 }
};
ph.Web.prototype.onRemoteMessage=function(msgObj){
//Aタグがclick
//passwordフィールを持つformがsumit
//formがsumit
  if(msgObj.type=='click'||msgObj.type=='passwordSubmit'||msgObj.type=='submit'){
   var target=this.rewriteTarget(msgObj.target,msgObj.windowName,msgObj.topFrameName);
   msgObj.url=ph.revRewriteUrl(msgObj.url);
   if(target==this.frameName){
    this.remoteGo(msgObj);
   }else{
    window.open(msgObj.url,target);
   }
//passwordフィールドを持つpageがloadされた
//pageがloadされた
  }else if(msgObj.type=='passwordLoad'||msgObj.type=='load'){//passwordを含む画面が表示された
   if(msgObj.windowName==msgObj.topFrameName){
    var url=ph.revRewriteUrl(msgObj.documentLocationHref);
    this.remoteGo(new ph.Tran(url));
   }
  }
};

ph.Web.prototype.updateShare=function(){
 if(this.shareMode=='share'){
  this.shareSpan.text('共有中:'+this.shareName);
 }else{
  this.shareSpan.text('共有開始');
 }
}

ph.Web.prototype.share=function(){
 if(this.shareMode=='share'){
  alert('共有を終了します。')
  ph.queue.unsubscribe(this.shareName);
  this.remoteUrls={};
  this.shareMode='';
 }else{
  var shareName=prompt('共有開始\r\n共有名を入力してください','shareWeb');
  if(!shareName){
   return;//キャンセルが押されたら何もしない
  }
  this.remoteUrls={};
  this.shareName=shareName;
  this.shareMode='share';
  this.subShareWeb();
 }
 this.updateShare();
};

ph.Web.prototype.toJson=function(){
 var json=ph.JSON.stringify({lavel:this.lavel,home:this.home,history:this.history,shareName:this.shareName,shareMode:this.shareMode});
// alert(json);
 return json;
};

