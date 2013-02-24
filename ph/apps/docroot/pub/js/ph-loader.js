(function(){
var app='$!esc.javascript(${parameter.getParameter("ph")})'!=='ph';## from phantom proxy system
var bm='$!esc.javascript(${parameter.getParameter("bm")})';## from bookmarklet
var include='$!esc.javascript(${parameter.getParameter("include")})'.split(',');
if(window.ph){
 if(bm){
##bookmarkletから呼ばれた場合、必ずscriptを呼び出す
  ph.loadScript(include);
 }
 return;
}
## spdyInfoの取得
#set( $spdySession=${handler.getSpdySession()} )
#if( ${spdySession} )
#set( $spdyInfo="'${spdySession.spdyInfo()}'" )
#else
#set( $spdyInfo=false )
#end
##
window.ph={
 version:'$esc.javascript(${config.getString("phantomVersion")})',
 isSsl:$esc.javascript(${handler.isSsl()}),
 domain:'$esc.javascript(${handler.getLocalIp()}):$esc.javascript(${handler.getLocalPort()})',
 hostHeader:'$esc.javascript(${handler.getRequestHeader().getServer()})',
 authUrl:'$esc.javascript(${config.authUrl})',
 adminUrl:'$esc.javascript(${config.adminUrl})',
 publicWebUrl:'$esc.javascript(${config.publicWebUrl})',
 spdyInfo:${spdyInfo},
 scriptBase:'',
 scripts:['jquery-1.9.1.min.js','ph-jqnoconflict.js','ph-json2.js'],
 appScripts:['ph-auth.js'],
 useWebSocket:false,## WebSocketを使うか否か?
 useSessionStorage:false,## SessionStorageを使うか否か?
 useCrossDomain:false,## iframeを使ったクロスドメイン通信を使うか否か?
 useHashChange:false,
 useBlobBuilder:false,
 createBlob:function(data){
  if(Blob){
   return new Blob(data);
  }else if(BlobBuilder){
   var bb=new BlobBuilder();
   for(var i=0;i<data.length;i++){
     bb.append(data[i]);
   }
   return bb.getBlob();
  }
  return null;
 },
 blobSlice:function(blob,startingByte,endindByte){
  if(blob.webkitSlice) {
   return blob.webkitSlice(startingByte, endindByte);
  }else if (blob.mozSlice) {
   return blob.mozSlice(startingByte, endindByte);
  }
  return blob.slice(startingByte, endindByte);
 },
 //https://github.com/ukyo/jsziptools/blob/master/src/utils.js
 stringToArrayBuffer:function(str){
  if(!ph.useBlob){
    return str;
  }
  var n = str.length,
  idx = -1,
  utf8 = [],
  i, j, c;
  //http://user1.matsumoto.ne.jp/~goma/js/utf.js
  for(i = 0; i < n; ++i){
   c = str.charCodeAt(i);
   if(c <= 0x7F){
    utf8[++idx] = c;
   } else if(c <= 0x7FF){
    utf8[++idx] = 0xC0 | (c >>> 6);
    utf8[++idx] = 0x80 | (c & 0x3F);
   } else if(c <= 0xFFFF){
    utf8[++idx] = 0xE0 | (c >>> 12);
    utf8[++idx] = 0x80 | ((c >>> 6) & 0x3F);
    utf8[++idx] = 0x80 | (c & 0x3F);
   } else {
    j = 4;
    while(c >> (6 * j)) ++j;
    utf8[++idx] = ((0xFF00 >>> j) & 0xFF) | (c >>> (6 * --j));
    while(j--)
     utf8[++idx] = 0x80 | ((c >>> (6 * j)) & 0x3F);
   }
  }
//  return new Uint8Array(utf8).buffer;
  return new Uint8Array(utf8);
 },
 debug:false,##debugメッセージを出力するか否か
 setDebug:function(flag){
  this.debug=flag;
##sessionStorageが使用できる場合
  if(typeof sessionStorage != "undefined"){
    sessionStorage['ph.debug']=flag;
  }
 },
 showDebug:false,##debug領域を表示するか否か
 setShowDebug:function(flag){
  this.showDebug=flag;
  if(flag){
   ph.jQuery('#phDebug').show();
  }else{
   ph.jQuery('#phDebug').hide();
  }
  if(typeof sessionStorage != "undefined"){
   sessionStorage['ph.showDebug']=flag;
  }
 },
 clearDebug:function(){
  ph.jQuery('#phDebugArea').text('');
 },
 dump:function(data){ph.log(ph.JSON.stringify(data))},
 dump1:function(data){
  for(var i in data){
   if(typeof(data[i]) == 'function'){
     continue;
   }
   if(ph.jQuery.isArray(data[i])){
     ph.log(i+':['+data[i] +']');
   }else{
     ph.log(i+':'+data[i]);
   }
  }
 },
 log:function(text){
  if(ph.debug){
   ph.jQuery("#phDebugArea").text( ph.jQuery("#phDebugArea").text() + "\r\n" + text);
  }
 },
 absolutePath:function(path, base){
  var bases = base.match(/((http:\/\/)|(https:\/\/))[^/]*\//);
  if(!bases){
   return null;
  }
  var baseroot = bases[0];
  var pathes = path.split("/");
  if (pathes[0].match(/(http)|(https):/)){
   return path;
  }
  if (pathes[0] == ""){
   pathes.shift();
   return baseroot + pathes.join("/");
  }
  while (0 < pathes.length && pathes[0] == "."){
   pathes.shift();
  }
  basetemp = base.substring(baseroot.length).match(/([^\?]*)\//);
  if (!basetemp || basetemp.length < 2){
   return baseroot + pathes.join("/");
  }
  var bases = basetemp[1].split("/");
  while (0 < bases.length && 0 < pathes.length && pathes[0] == ".."){
   bases.pop();
   pathes.shift();
  }
  return baseroot + bases.concat(pathes).join("/");
 },
 onload:function(){
##  alert('ph-loader onload');
 },
 loadScript:function(includeScript){
  var jsl=new ph.JSLoader(/*{finish:ph.onload}*/);
  for(var i=0;i<includeScript.length;i++){
   var script=includeScript[i];
   if(!script.match(/^http/)){
     jsl.next(ph.scriptBase+script);
   }else{
     jsl.next(script);
   }
  }
  jsl.start();
 }
};//end of window.ph

ph.JSLoader = function(options){
  var options  = options || {};
  this.pointer = 0;
  this.finish  = options.finish || function(){};
  this.append  = options.append || null;
  this.queue = {
    length : 0,
    list : [],
    push : function(arg){
      this.list.push(arg);
      this.length++;
    }
  };
  return this;
};
ph.JSLoader.prototype = {
  next : function(){
    var self = this;
    var loader = new ph.JSLoader({
      append : self.append,
      finish : function(){
        self._next();
      }
    });
    var args = [];
    for(var i=0, l=arguments.length; i<l; i++){
      loader.assign(arguments[i]);
    }
    self.assign(function(){
      loader.run();
    });
    return this;
  },

  _next : function(){
    var func = this.queue.list[this.pointer++];
    if(func){
      func();
    }
  },

  assign : function(arg){
    var self = this;
    switch(typeof arg){
      case 'string' :
      this.queue.push(function(){self.load(arg,{
        append : self.append,
        onload : function(){
          self.report();
        }});
      });
      break;
      case 'function' :
      this.queue.push(function(){arg();self.report();});
      break;
    }
  },

  report : function(){
    this.queue.length--;
    if(this.queue.length == 0){
      this.finish();
    }
  },

  start : function(){
    this._next();
  },

  run : function(){
    for( var i=0,l=this.queue.length; i<l; i++){
      this.queue.list[i]();
    }
  },

  load : function(src, options){
    var options   = options || {};
    var element   = options.append || document.body || document.documentElement;
    var script  = document.createElement('script');
    script.src  = src;
    script.type   = options.type   || 'text/javascript';
    script.onload = options.onload || function(){};
    if(options.charset)
      script.charset = options.charset;
    if( document.all ){
      script.onreadystatechange = function(){
        switch(script.readyState){
          case 'complete':
          case 'loaded' :
          script.onload();
          break;
        }
      };
    }
    element.appendChild(script);
  }
};

ph.useSessionStorage=$esc.javascript(${config.getBoolean("useSessionStorage",true)});
if(typeof sessionStorage == "undefined"){
 ph.useSessionStorage=false;
}
if(ph.useSessionStorage){
 ph.debug=(sessionStorage['ph.debug']==='true');
 ph.showDebug=(sessionStorage['ph.showDebug']==='true');
}

ph.useCrossDomain=$esc.javascript(${config.getBoolean("useCrossDomain",true)});
if(typeof window.postMessage == 'undefined'){
 ph.useCrossDomain=false;
}

var spec='$esc.javascript(${config.getString("websocketSpecs")})';
if(spec){
 ph.useWebSocket=true;
}else{
 ph.useWebSocket=false;
}
if(typeof WebSocket === 'undefined'){
 if(typeof MozWebSocket ==='undefined'){
  ph.useWebSocket=false;
 }else{
  window.WebSocket=MozWebSocket;
 }
}

ph.useBlob=false;
if(typeof Uint8Array !== 'undefined' && typeof ArrayBuffer !== 'undefined' && typeof Blob !== 'undefined'){
 ph.useBlob=true;
}

ph.useHashChange=true;
if(typeof window.onhashchange==='undefined'){
 ph.useHashChange=false;
}

ph.useBlobBuilder=true;
if(typeof window.BlobBuilder==='undefined'){
 window.BlobBuilder = window.MozBlobBuilder || window.WebKitBlobBuilder;
 if(typeof window.BlobBuilder==='undefined'&&typeof window.Blob==='undefined'){
  ph.useBlobBuilder=false;
 }
}


##画面遷移を表現するオブジェクトtrunsmission
ph.Tran=function(url,target,type,method,params,enctype){
 this.url=url;
 if(target){
  this.target=target;
 }
 if(type){
  this.type=type;
 }
 if(method){
  this.method=method;
 }
 if(params){
  this.params=params;
 }
 if(enctype){
  this.enctype=enctype;
 }
};
ph.Tran.prototype.target="";
ph.Tran.prototype.type="click";
ph.Tran.prototype.method="GET";
ph.Tran.prototype.toJson=function(){
 ph.JSON.stringify(this);
};

if(ph.isSsl){
 ph.scriptBase='https://';
}else{
 ph.scriptBase='http://';
}

if(app){
 for(var i=0;i<ph.appScripts.length;i++){
   ph.scripts.push(ph.appScripts[i]);
 }
}
for(var i=0;i<include.length;i++){
 if(include[i]==''){
  continue;
 }
 ph.scripts.push(include[i]);
}

ph.scriptBase+=ph.hostHeader +'/pub/js/';
if(bm){
  ph.loadScript(ph.scripts);
}else{
##  //alert(scriptBase);
  for(var i=0;i<ph.scripts.length;i++){
   var script=ph.scripts[i];
   document.write('<script type="text/javascript" src="');
   if(!script.match(/^http/)){
    document.write(ph.scriptBase);
   }
   document.write(script + '" charset="utf-8"');
   document.write('></' + 'script>');
  }
}
##document.write('<script type="text/javascript">');
##document.write('ph.jQuery(ph.onload);');IE8でエラーになる、ph.jQueryが見つからない
##document.write('</' + 'script>');
})();

