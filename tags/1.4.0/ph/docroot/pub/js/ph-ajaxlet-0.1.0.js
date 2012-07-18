(function(){

var ajaxlet={
//非同期呼び出しメソッド
 noticeIds:{},
 _queueCb:function(resObj,regCb,noticeCx){
  var noticeId;
  if(resObj.phIsSuccess){
   noticeId=resObj.phNoticeId;
   this.noticeIds[noticeId]=noticeCx;
  }
  if(regCb){
   regCb(noticeId,resObj);
  }
 },
 _noticeCb:function(noticeId,resObj){
  var cbInfo=this.noticeIds[noticeId];
  cbInfo.cb(resObj,cbInfo.cx);
  if( !cbInfo.isPermanent ){
   delete this.noticeIds[noticeId];
  }
 },
 _pollCb:function(resObj,pollCb){
  if(resObj.phIsSuccess){
   for(var noticeId in resObj.phNoticeIds){
    this._noticeCb(noticeId,resObj.phNoticeIds[noticeId]);
   }
  }
  if(pollCb){
   pollCb(resObj);
  }
 },
 _cancelCb:function(resObj,cancelCb){
  if(resObj.phIsSuccess){
   for(var noticeId in resObj.phNoticeIds){
    if(resObj.phNoticeIds[noticeId]){
     delete this.noticeIds[noticeId];
    }
   }
  }
  if(cancelCb){
   cancelCb(resObj);
  }
 },
 queue:function(resouce,action,reqType,reqObj,cb,regCb,cx){
  ph.requestAjaxlet(resouce,action,reqType,reqObj,function(resObj){ph.ajaxlet._queueCb(resObj,regCb,{cb:cb,cx:cx,isPermanent:isPermanent});});
 },
 _pollOrCancel:function(argnoticeId,argCb,action,cb){
  var reqObj={};
  if( typeof(argnoticeId)=='function' ){
   argCb=argnoticeId;
   argnoticeId=null;
  }
  reqObj.phNoticeIds=[];
  if(argnoticeId){
   if(!ph.ajaxlet.noticeIds[argnoticeId]){
    if(argCb){
     argCb({phIsSuccess:false,phReason:'not in queue.'+argnoticeId});
    }
    return;
   }
   reqObj.phNoticeIds.push(argnoticeId);
  }else{
   if(ph.ajaxlet.noticeIds.length==0){
    if(argCb){
     argCb({phIsSuccess:false,phReason:'not in queue.'});
    }
    return;
   }
   for(var id in ph.ajaxlet.noticeIds){
    reqObj.phNoticeIds.push(id);
   }
  }
  ph.requestAjaxlet('ajaxlet',action,'sync',reqObj,function(resObj){cb(resObj,argCb);});
 },
 poll:function(noticeId,pollCb){
  this._pollOrCancel(noticeId,pollCb,'poll',this._pollCb);
 },
 cancel:function(noticeId,cancelCb){
  this._pollOrCancel(noticeId,cancelCb,'cancel',this._cancelCb);
 },
 _typeCb:function(resObj,cb){
  if(resObj.phIsSuccess){
   for(var name in resObj.phResult){
    var attrs=resObj.result[name].attrs;
    ph.type.createType(name,attrs);
   }
  }
  if(cb){
   cb(resObj);
  }
 },
 type:function(typeCb){
  ph.requestAjaxlet('ajaxlet','type','sync',{},function(resObj){ph.ajaxlet._typeCb(resObj,typeCb);});
 },
 wtcher:function(){
  this.pool(function(resObj){
   if(resObj.phInterval){
    this.timer=setTimeout(ph.ajaxlet.wtcher,resObj.phInterval);
   }else{
    this.timer=setTimeout(ph.ajaxlet.wtcher,1000);
   }
  });
 }
};
ph.ajaxlet=ajaxlet;

;})();
