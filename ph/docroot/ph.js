##offline cacheに入る
(function(){
if(window.ph){
 return;
}
window.ph={
 version:'$esc.javascript(${config.getString("phantomVersion")})',
 isSsl:$esc.javascript(${handler.isSsl()}),
 domain:'$esc.javascript(${handler.getLocalIp()}):$esc.javascript(${handler.getLocalPort()})',
 hostHeader:'$esc.javascript(${handler.getRequestHeader().getServer()})',
 authUrl:'$esc.javascript(${config.authUrl})',
 adminUrl:'$esc.javascript(${config.adminUrl})',
 publicWebUrl:'$esc.javascript(${config.publicWebUrl})',
 scriptBase:'',
 scripts:['jquery-1.8.3.min.js','ph-jqnoconflict.js','ph-json2.js','ph-event.js','ph-auth.js','ph-pa.js'],
 useWebSocket:false,## WebSocketを使うか否か?
 useSessionStorage:false,## SessionStorageを使うか否か?
 useCrossDomain:false,## iframeを使ったクロスドメイン通信を使うか否か?
 useHashChange:false,
 useBlobBuilder:false,
 useAppCache:false,
 isOffline:false,
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
 blobSlice:function(blob,startingByte,endindByte,type){
  if(blob.webkitSlice) {
   return blob.webkitSlice(startingByte, endindByte,type);
  }else if (blob.mozSlice) {
   return blob.mozSlice(startingByte, endindByte,type);
  }
  return blob.slice(startingByte, endindByte,type);
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
 }
};//end of window.ph

ph.useSessionStorage=true;
if(typeof sessionStorage == "undefined"){
 ph.useSessionStorage=false;
}
if(ph.useSessionStorage){
 ph.debug=(sessionStorage['ph.debug']==='true');
 ph.showDebug=(sessionStorage['ph.showDebug']==='true');
}

ph.useCrossDomain=true;
if(typeof window.postMessage == 'undefined'){
 ph.useCrossDomain=false;
}

ph.useWebSocket=true;
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

ph.useAppCache=true;
if(typeof window.applicationCache==='undefined'){
 ph.useAppCache=false;
}

if(ph.isSsl){
 ph.scriptBase='https://';
}else{
 ph.scriptBase='http://';
}

ph.scriptBase+=ph.hostHeader +'/pub/js/';
for(var i=0;i<ph.scripts.length;i++){
 var script=ph.scripts[i];
 document.write('<script type="text/javascript" src="');
 if(!script.match(/^http/)){
  document.write(ph.scriptBase);
 }
 document.write(script + '" charset="utf-8"');
 document.write('></' + 'script>');
}

ph.onLoad=function(){
 ph.load=ph.jQuery.Deferred();
 if(navigator.onLine===false){
   ph.load.reject();
   ph.isOffline=true;
   return;
 }
 ph.jQuery.ajax({
  type: 'GET',
  url: '/ph.json',
  dataType:'json',
  success: function(json){
   for(key in json){
    ph[key]=json[key]
   }
   if(ph.useWebSocket && !ph.websocketSpec){
    ph.useWebSocket=false;
   }
   ph.load.resolve();
   ph.isOffline=false;
  },
  error: function(xhr){ph.load.reject();ph.isOffline=true,alert('error');}
 })
}
##if (window.addEventListener){## W3C standard
##  window.addEventListener('load', phLoad, false); // NB **not** 'onload'
##}else if (window.attachEvent){## Microsoft
##  window.attachEvent('onload', phLoad);
##}

})();

