//phantom proxy用javascript集
(function(){

//mapping関連メソッド
var mapping={
//表示上必要な属性一覧
 mapKeys:[
  'id','enabled','notes',
  'realHostName','sourceType','secureType','sourceHost','sourcePort','sourcePath',
  'destinationType','destinationHost','destinationPort','destinationPath',
  'option.logType','option.replay','option.replayDocroot'/*,'option.velocityPage','option.velocityExtention'*/],
//idをキーとしたObjectで管理,mapKeysに依存せず全てのkeyを格納
 userMapping:null,
 adminMapping:null,
 _updateMapping:function(targetMapping,mappings){
  for(var key in mappings){
   if(key==mappings[key].id){
    var map=targetMapping[key]=mappings[key];
    for(var i=0;i<this.mapKeys.length;i++){
     if(!map[this.mapKeys[i]]){
      map[this.mapKeys[i]]='';
     }
    }
   }else{
    delete targetMapping[key];
   }
  }
 },
 _setValue:function(mappings,m,value){
  var mapping=mappings[m[1]];
  if(!mapping){
    mapping=new Object();
    mappings[m[1]]=mapping;
  }
  mapping[m[2]]=value;
 },
 _valuePattern:/mapping\.(\d*)\.(.*)/,
 _parse:function(setting){
  var mappings=[];
  for(var key in setting){
    var m=key.match(this._valuePattern);
    if(m){
      this._setValue(mappings,m,setting[key]);
      continue;
    }
  }
  return mappings;
 },
 get:function(cb){
  ph.setting.get(['mapping'],['mapping'],function(resObj){
   ph.mapping.userMapping={};//getの場合初期化
   ph.mapping._updateMapping(ph.mapping.userMapping,ph.mapping._parse(resObj.user));
   ph.mapping.adminMapping={};//getの場合初期化
   ph.mapping._updateMapping(ph.mapping.adminMapping,ph.mapping._parse(resObj.admin));
   cb(resObj);
   });
 },
 _update:function(id,map,condition,cb){
  var reqObj={};
  for(var key in map){
   reqObj['mapping.'+id+'.'+key]=map[key];
  }
//  var mapKeys=ph.mapping.mapKeys;
//  for(var i=0;i<mapKeys.length;i++){
//   reqObj['mapping.'+id+'.'+mapKeys[i]]=map[mapKeys[i]];
//  }
  ph.setting.set(condition,reqObj,function(resObj){
    ph.mapping._updateMapping(ph.mapping.userMapping,ph.mapping._parse(resObj.update));
    if(cb){
     cb(resObj);
    }
   });
 },
 update:function(id,map,cb){
  map.id=id;//idを追加
  ph.mapping._update(id,map,{},cb);
 },
 create:function(map,cb){
  if(!this.userMapping){
   ph.mapping.get(
    function(resObj){
     ph.mapping.create(map,cb);
    });
   return;
  }
  var id=1;//mappingのidは、1から始まる
  for(;;id++){
    if(!ph.mapping.userMapping[id]){
//   alert(id + ":" + ph.mapping.userMapping[id]);
     break;
    }
  }
  map.id=id;//idを追加
  var idKey='mapping.'+id + '.id.'+id;
  var condition={};
  condition[idKey]='';
  ph.mapping._update(id,map,condition,cb);
 },
 remove:function(id,cb){
  var map={};
  var mapKeys=ph.mapping.mapKeys;
  for(var i=0;i<mapKeys.length;i++){
    map[mapKeys[i]]='';
  }
  ph.mapping._update(id,map,{},cb);
 },
 dummy:null
};

//setting関連メソッド
var setting={
 get:function(adminKeys,userKeys,cb){
  var req={
   admin:adminKeys,
   user:userKeys
  };
  ph.ajaxlet.call({
   resource:'setting',action:'get',
   isSync:true,
   reqObj:req,
   cb:function(res){cb(res)}
  });
 },
 set:function(condition,update,cb){
  var req={
   condition:condition,
   update:update
  };
  ph.ajaxlet.call({
   resource:'setting',action:'set',
   isSync:true,
   reqObj:req,
   cb:function(res){cb(res)}
  });
 }
};

//store関連メソッド
var store={
 getUrl:function(storeId,length,offset,headers,cb){
  var req={
   responseStore:storeId,
   responseContentLength:length,
   responseOffset:offset,
   responseHeaders:headers
  };
  ph.ajaxlet.call({
   phResource:'store',phAction:'download',
   phIsSync:false,
   phReqObj:req,
   cb:function(res){},
   synccb:function(res){cb('/ph/ajaxlet?ajaxletId='+res.reqid);}
  });
 },
 getData:function(storeId,length,offset,cb){
  ph.store.getUrl(storeId,length,offset,{contentType:'text/plain'},function(url){ph.getReq(url,cb);});
 }
};

//メインオブジェクト
ph.addAttr(
{
//thizをthisとしてcallbackを呼び出すユーティリティ
 _execCb:function(thiz,name,args,defcb){
  var f=thiz[name];
  if(!f){f=defcb;}
  if(f){
   f.apply(thiz,args);
  }
 },
//単純呼び出し関数,cbのパラメタはString
 getReq:function(url,cb){
  jQuery.ajax({
   type: 'GET',
   url: url,
   success: function(res){
    cb(res);
   },
   error: function(xhr, textStatus, errorThrown){throw new Error(
    'fail to request.' + textStatus + ':' + xhr.status + ':' + xhr.responseText);
   }
  });
 },
//コマンド呼び出し
 command:function(command,param,cb){
  var req={
   command:command,
   param:param
  };
  ph.ajaxlet.call({
   resource:'command',action:null,
   isSync:true,
   reqObj:req,
   cb:cb
  });
 },
//typeシリアライズ関連
 typedefs:{},
 createType:function(name,protoObj,conv,clazz){
  ph.typedefs[name]={};
  ph.typedefs[name].protoObj=protoObj;
  if(clazz){
   ph.typedefs[name].Class=clazz;
  }else{
   ph.typedefs[name].Class=function(){};
  }
  ph.typedefs[name].Class.prototype['phTypeName']=name;
  if(protoObj){
   for(var attr=0 in protoObj){
    ph.typedefs[name].Class.prototype[attr]=protoObj[attr];
   }
  }
  if(!conv){
   conv=new ph.Converter(name);
  }
  ph.typedefs[name].conv=conv;
 },
 newObj:function(name){
  if(ph.typedefs[name]){
   return new ph.typedefs[name].Class;
  }
  return null;
 },
 _getConverter:function(obj){
  if(!obj){
   return null;
  }
  var name=obj.phTypeName;
  if(!name){
   return null;
  }
  if(!ph.typedefs[name]){
   return null;
  }
  return ph.typedefs[name].conv;
 },
 _replacer:function(key,value){
  var conv=ph._getConverter(this[key]);
  if(conv){
   value={};
   value[conv.name]=conv.serialize(this[key]);
  }
  return value;
 },
 _reviver:function(key,value){
  var name,i=0;
  for(name in value){
   if(i>0){//not only one attribute
    return value;
   }
   i++;
  }
  if(!ph.typedefs[name]||!ph.typedefs[name].conv){
    return value;
  }
  var conv=ph.typedefs[name].conv;
  return conv.deserialize(value[name]);
 },
 toJsonString:function(obj){
  var text=ph.JSON.stringify(obj,this._replacer);
  return text;
 },
 toObj:function(jsonString){
  var obj;
  try{
   obj=ph.JSON.parse(jsonString,this._reviver);
  }catch(e){
   throw new Error(e.toString() + ':ph.JSON.parse:'+jsonString); 
  }
  return obj;
 }
}
);//end of ph.addAttr()

//ajaxlet関連メソッド
var ajaxlet={
//単レスポンスのcallbackを実行するメソッド,thizは、callback関数を含むObject
 _seqResesCb:function(seqReses,userObj,thiz){
  var isLast=false;
  var seq=1;
  if(thiz.phResSeq){
   seq=thiz.phResSeq;
  }
  while(true){
   var seqRes=seqReses[seq];
   if(!seqRes){
    break;
   }
   if(seqRes.phIsOk){
    ph._execCb(userObj,"cb",[seqRes.phResObj,seqRes.phIsLast,seq]);
    if(seqRes.phIsLast){
     isLast=true;
    }
   }else{
    ph._execCb(userObj,"errorcb",[seqRes.phReason,seqRes],ph.ajaxlet._defErrCb);
    isLast=true;
   }
   seq++;
  }
  if(thiz.phResSeq){
   thiz.phResSeq=seq;
  }
  return isLast;
 },
 ajaxlets:{},
 _defErrCb:function(reason,detail){
  throw new Error(reason+":"+ph.toJsonString(detail)+":"+ph.toJsonString(this));
 },
 pop:function(x/*ajaxletIds,interval,cb,errorcb*/){
  ajaxletIds=[];
  if(x.ajaxletId){
   for(var id in x.ajaxletId){
    if(ph.ajaxlet.ajaxlets[id]){
     ajaxletIds.push(id);
    }
   }
  }else{
   for(var id in ph.ajaxlet.ajaxlets){
    ajaxletIds.push(id);
   }
  }
  var interval=1000;
  if(x.interval){
   interval=x.interval;
  }
  if(ajaxletIds.length==0){
   x.cb(interval);//pop対象がない場合
   return;
  }
  this._ajaxlet({
   resource:'ajaxlet',
   action:'pop',
   isSync:true,
   ajaxletId:ajaxletIds,
   interval:interval,
   cb: function(res,interval){
    for(var ajaxletId in res){
     var seqReses=res[ajaxletId];
     var ai=ph.ajaxlet.ajaxlets[ajaxletId];
     if(ai.cb(seqReses)){
       delete ph.ajaxlet.ajaxlets[ajaxletId];
     }
    }
    x.cb(interval);
   },
   errorcb: function(){ph._execCb(x,"errorcb",arguments,ph.ajaxlet._defErrCb);}
  });
 },
 push:function(x/*ajaxletId,reqObj,cb,errorcb*/){
  var ai=ph.ajaxlet.ajaxlets[x.ajaxletId];
  if(!ai){
   if(x.errorcb){
    x.errorcb("ajaxletId error."+x.ajaxletId);
   }else{
    ph._defErrCb("ajaxletId error."+x.ajaxletId);
   }
   return;
  }
  ai.phReqSeq++;
  this._ajaxlet({
   resource:'ajaxlet',
   action:'push',
   isSync:false,
   ajaxletId:x.ajaxletId,
   seq:ai.phReqSeq,
   reqObj:x.reqObj,
   interval:x.interval,
   errorcb: function(){ph._execCb(x,"errorcb",arguments,ph.ajaxlet._defErrCb);},
   synccb: function(ajaxletId){ph._execCb(x,"cb",arguments);}
  });
 },
 cancel:function(x/*ajaxletIds,cb,errorcb*/){
  ajaxletIds=[];
  if(ajaxletId){
   for(var id in ajaxletId){
    if(ph.ajaxlet.ajaxlets[id]){
     ajaxletIds.push(id);
     delete ph.ajaxlet.ajaxlets[id];
    }
   }
  }else{
   for(var id in ph.ajaxlet.ajaxlets){
    ajaxletIds.push(id);
    delete ph.ajaxlet.ajaxlets[id];
   }
  }
  this._ajaxlet({
   resource:'ajaxlet',
   action:'cancel',
   isSync:true,
   ajaxletId:x.ajaxletId,
   cb: function(){ph._execCb(x,"cb",arguments);},
   errorcb: function(){ph._execCb(x,"errorcb",arguments,ph.ajaxlet._defErrCb);}
  });
 },
 call:function(x/*resouce,action,isSync,reqObj,cb,errorcb,synccb*/){
  this._ajaxlet({
   resource:x.resource,
   action:x.action,
   isSync:x.isSync,
   reqObj:x.reqObj,
   cb: function(res){//単レスポンスを受け取る
    return ph.ajaxlet._seqResesCb(res,x,this);
   },
   errorcb:function(){ph._execCb(x,"errorcb",arguments,ph.ajaxlet._defErrCb);},
   synccb:function(){ph._execCb(x,"synccb",arguments);}
  });
 },
 _ajaxlet:function(x/*resouce,action,ajaxletId,isSync,reqObj,interval,cb,errorcb,synccb*/){
//送信Objectを構成
  var r={};
  r.phResource=x.resource;
  r.phAction=x.action;
  if(x.ajaxletId){
   r.phAjaxletId=x.ajaxletId;
  }
  if(x.seq){
   r.phSeq=x.seq;
  }
  if(x.isSync || x.isSync==false){
   r.phIsSync=x.isSync;
  }
  if(x.reqObj){
   r.phReqObj=x.reqObj;
  }
  if(x.interval){
   r.phInterval=x.interval;
  }
  jQuery.ajax({
   type: 'POST',
   url: '/ph/ajaxlet',
   contentType : 'application/json',
   processData: false,
   data: ph.toJsonString(r),
   success: function(res){
    var resObj=ph.toObj(res);
    if(resObj.phIsOk){
     if(x.isSync){
      x.cb(resObj.phResObj,resObj.phInterval);
     }else{
      if(x.resource!='ajaxlet'){
       x.reqObj=null;//リクエストデータ覚えておく必要なし
       x.phResSeq=1;//レスポンスシーケンス番号
       x.phReqSeq=0;//リクエストシーケンス番号
       ph.ajaxlet.ajaxlets[resObj.phAjaxletId]=x;
      }
      x.synccb(resObj.phAjaxletId);
     }
    }else{
     x.errorcb(resObj.phReason,resObj);
    }
   },
   error: function(xhr, st, errorTh){x.errorcb("jQuery.ajax() error.",xhr);}
  });
 },
 watcher:function(){
  this.pop({cb:function(interval){
   if(interval){
    this.timer=setTimeout("ph.ajaxlet.watcher();",interval);
   }else{
    this.timer=setTimeout("ph.ajaxlet.watcher();",1000);
   }
  }});
 }
};//end of ajaxlet

var user={
 map:{},
 linkAjaxletId:null,
 insert:function(user){
//  alert('linkAjaxletId:'+this.linkAjaxletId);
  ph.ajaxlet.push({
   ajaxletId:this.linkAjaxletId,
   reqObj:{insert:user}
  });
 },
 update:function(user){
  ph.ajaxlet.push({
   ajaxletId:this.linkAjaxletId,
   reqObj:{update:user}
  });
 },
 remove:function(user){
  ph.ajaxlet.push({
   ajaxletId:this.linkAjaxletId,
   reqObj:{remove:user}
  });
 },
 link:function(cb){
  ph.ajaxlet.call({
  resource:'hibernate',action:'link',
  isSync:false,
  reqObj:{
   watch:{typeName:'$User$',maxId:-1},
   select:{maxResults:100,firstResult:0,query:'from  User'}
   },
  cb:function(res,isLast){
    var list=res.select;
//alert('list'+list);
    if(list){
     for(var i=0;i<list.length;i++){
      var u=list[i];
      ph.user.map[u.id]=u;
     }
    }
    var ins=res.insert;
    if(ins){
     ph.user.map[ins.id]=ins;
    }
    var upd=res.update;
    if(upd){
     ph.user.map[upd.id]=upd;
    }
    var rmv=res.remove;
    if(rmv){
     delete ph.user.map[rmv.id];
    }
    cb();
   },
  synccb:function(aid){ph.user.linkAjaxletId=aid;}
  });
 }
};//end of user

ph.addAttr('ajaxlet',ajaxlet);
ph.addAttr('store',store);
ph.addAttr('setting',setting);
ph.addAttr('mapping',mapping);
ph.addAttr('user',user);

//Converterのデフォルト実装
ph.Converter=function(name){this.name=name;};
ph.Converter.prototype.serialize=function(obj){
 var sirializeObj={};
 for(var i in obj){
  if(i=='phTypeName'){
   continue;
  }
  sirializeObj[i]=obj[i];
 }
 return sirializeObj;
};
ph.Converter.prototype.deserialize=function(obj){
 var typedObj=ph.newObj(this.name);
 for(var i in typedObj){
  if(i=='phTypeName'){
   continue;
  }
  typedObj[i]=obj[i];
 }
 return typedObj;
};
;})();
//alert(ph.JSON);

