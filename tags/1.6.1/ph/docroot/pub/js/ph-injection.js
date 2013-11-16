ph.log('ph.jQuery after:'+window.name);
ph.debug=true;
ph.isInPortal=false;

var w=window;
while(true){
  try{
    var ret=w.name.match(/phFrameName(\d*)/);
    if(ret!=null){
     ph.isInPortal=true;
     ph.portalFrameNo=RegExp.$1;
     ph.topFrame=w;
     break;
    }
    ph.log('parent name:'+w.name);
    if(w==w.parent){
     break;
    }
    w=w.parent;
  }catch(e){
     ph.dump(e);
     break;
  }
}

//-----injection main start-----
if(ph.isInPortal){

ph.rewriteUrl=function(url){
 if( url.match(/^http:\/\//) && !url.match(/^http:\/\/inj\./)){
  return 'http://inj.' +url.substring(7);
 }else if( url.match(/^https:\/\//) && !url.match(/^https:\/\/inj\./)){
  return 'https://inj.' +url.substring(8);
 }
 return url;
};
ph.isRewriteUrl=function(url){
//alert(url);
 if( !url.match(/^http:\/\//)&&!url.match(/^https:\/\//) ){
  return true;//相対pathであるためrewirte済み
 }
 if( url.match(/^http:\/\/inj\./)||url.match(/^https:\/\/inj\./) ){
  return true;
 }
 return false;
};
ph.rewriteTarget=function(target){
 if(!target){
  return '_self';
 }
 if(target=='_top'){
  return ph.topFrame.name;
 }
 if(target=='_parent'){
  if(ph.topFrame.name==window.name){
   return ph.topFrame.name;
  }
 }
 return target;
};
ph.rewriteFrameSrc=function(tag){
 var frameNames=[];
 var frames=ph.jQuery(tag);
 for(var i=0;i<frames.length;i++){
  frameNames.push(frames[i].name);
  if(ph.isRewriteUrl(frames[i].src)){
   continue;
  }
  frames[i].src=ph.rewriteUrl(frames[i].src);
//	ph.postMessage({
//		type:tag+'RewriteSrc',
//		src:frames[i].src,
//		name:frames[i].name
//	});
 }
 return frameNames;
};

ph.onMessage=function(event){
//// alert(evnet.data);
 ph.log('onMessage:'+event.data);
 if(!event.origin || event.origin.indexOf(ph.domain)<0){
  return true;
 }
 if(!event.data){
  return true;
 }
//// alert(event.data);
 var msgObj=ph.JSON.parse(event.data);
 if(msgObj.type=="executeSubmit"){//commission auth execute
//   alert(msgObj.action);
   var postMsg=new ph.Tran(msgObj.url,msgObj.target,'passwordSubmit',msgObj.method,msgObj.params,msgObj.enctype);
   msgObj.target=ph.rewriteTarget(msgObj.target);
   msgObj.url=ph.rewriteUrl(msgObj.url);
   var form=ph.jQuery(
			'<form action="' + msgObj.url + 
			'" target="' + msgObj.target + 
			'" method="'+ msgObj.method+
			'"></form>');
   for(var i=0;i<msgObj.params.length;i++){
    var param=msgObj.params[i];
    var hidden=ph.jQuery('input[type="hidden"][name="' + param.name + '"]');
    if(hidden.length!=0){
     param.value=ph.jQuery(hidden[0]).attr('value');
    }
    form.append('<input type="hidden" name="'+param.name +'" value="' + param.value +'"/>');
   }
   ph.jQuery('body').append(form);
   form.submit();
   form.remove();
   ph.postMessage(postMsg);
   return;
 }else if (msgObj.type=="executeClick"){//commission auth execute
   var clickMsg={
    type:'click',
    href:msgObj.href,
    target:msgObj.target
   };
   msgObj.target=ph.rewriteTarget(msgObj.target);
   msgObj.href=ph.rewriteUrl(msgObj.href);
   window.open(msgObj.href,msgObj.target);
   ph.postMessage(clickMsg);
   return;
 }
};
ph.postMessage=function(obj){
 obj.portalFrameNo=ph.portalFrameNo;
 obj.topFrameName=ph.topFrame.name;
 obj.windowName=window.name;
 obj.title=document.title;
 obj.documentLocationHref=document.location.href;
 var msg=ph.JSON.stringify(obj);
 if(window!=ph.realTop){
   ph.log('postMessage:'+msg);
   ph.realTop.postMessage(msg, '*');
 }else{
   ph.log('top.not postMessage:'+msg);
 }
};

ph.isIncludePassowrd=function(form){
//.find('input[type="password"]');が効かない場合がある?
 if(!form.elements){
  return false;
 }
 for(var i=0;i<form.elements.length;i++){
  var elem=form.elements[i];
  if(/input/i.test(elem.nodeName) && /password/i.test(elem.type)){
   return true;
  }
 }
 return false;
};
ph.rewriteTag=function(){
  var atags=ph.jQuery('a');
  atags.bind("click", function(){
		if(!this.href){
			return true;
		}
		if(this.href.match(/^javascript:/)){
			return true;
		}
//		ph.postMessage({
//			type:'click',
//			target:this.target,
//			href:this.href
//		});
		ph.postMessage(new ph.Tran(this.href,this.target,'click'));
        this.target=ph.rewriteTarget(this.target);
		this.href=ph.rewriteUrl(this.href);
		return true;
	});
  var forms=ph.jQuery('form');
  forms.bind("submit", function(event){
		var thisform=ph.jQuery(this);
        var type='submit';
		var paramArray=thisform.serializeArray();
        var s;
        if(event.originalEvent.explicitOriginalTarget){
         s=event.originalEvent.explicitOriginalTarget;
        }else{
         s=document.activeElement;
        }
        if(s){
         var submitBtn=ph.jQuery(s);//押下されたsubmitボタンのkey/valueを追加
         if(submitBtn.attr('type')=='submit'){
          var name=submitBtn.attr('name');
          var value=submitBtn.attr('value');
          var param={name:name,value:value};
          paramArray.push(param);
         }
        }
		if(ph.isIncludePassowrd(this)){
//FF IEは押下されたsubmitボタンが特定できるが、crome,safariではできない,TODO
         type='passwordSubmit';
        }
		ph.postMessage(new ph.Tran(this.action,this.target,type,this.method,paramArray,this.enctype));
        this.target=ph.rewriteTarget(this.target);
		this.action=ph.rewriteUrl(this.action);
		return true;
  });
};

//top and parent rewrite
ph.orgFrameName=window.name;
ph.realTop=window.top;
ph.realParent=window.parent;
window.eval("var top=ph.topFrame;");
//document.writeをフックする場合
//window.eval("ph.documentWrite=document.write;");
//window.eval("document.write=function(x){alert(x.replace('_top','_self'));ph.documentWrite(x.replace('_top','_self'));};");
if(window==ph.topFrame){
 window.eval("var parent=ph.topFrame;");
}
ph.jQuery(function(){
/*
if(false){
  R=0; x1=.1; y1=.05; x2=.25; y2=.24; x3=1.6; y3=.24; x4=300; y4=200; x5=300; y5=200; DI=document.images; DIL=DI.length; 
	ph.A=function(){
		for(i=0; i<DIL; i++){
			DIS=DI[ i ].style; DIS.position='absolute'; DIS.left=Math.sin(R*x1+i*x2+x3)*x4+x5; DIS.top=Math.cos(R*y1+i*y2+y3)*y4+y5
		}R++
	};setInterval('ph.A();',100); void(0);
}
*/
  var start=new Date().getTime();
  ph.log('window.name:' + window.name);
  ph.log('document.location:' + document.location);
//  ph.jQuery(window).bind('message',ph.onMessage);
  if (window.addEventListener){  
   window.addEventListener('message', ph.onMessage, false);  
  } else if (window.attachEvent){  
   window.attachEvent('onmessage', ph.onMessage);
  }
  ph.jQuery(window).unload(function(){
   window.name=ph.orgFrameName;
   ph.postMessage({type:'unload'});
  });
  ph.rewriteTag();
//  setTimeout(ph.rewriteTag,0);
  var frameNames=ph.rewriteFrameSrc('frame');
  var iframeNames=ph.rewriteFrameSrc('iframe');
  frameNames=frameNames.concat(iframeNames);
  var type='load';
  var passwords=ph.jQuery('input[type="password"]');
  if(passwords.length>0){
   type='passwordLoad'
  }
  var end=new Date().getTime();
  ph.postMessage({
			type:type,
            frameNames:frameNames,
            rewirteTime:(end-start)
  });
  ph.log("rewrite ok."+(end-start));
 });

}//end of if isInPortal
//-----injection main end-----

