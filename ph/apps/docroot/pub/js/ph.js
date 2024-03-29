(function(){
if(window.ph){
 return;
}
window.ph={
##このjavascriptの取得経路を記録,proxy or web...
 version:'',
 isProxy:null,
 isSsl:null,
 domain:'',
 hostHeader:'',
 authUrl:'',
 adminUrl:'',
 publicWebUrl:'',
 scripts:['jquery-1.5.1.min.js','ph-jqnoconflict.js','ph-json2.js','ph-auth2.js'],
 isUseWebSocket:false,//WebSocketを使うか否か?
 isUseSessionStorage:false,//SessionStorageを使うか否か?
 isUseCrossDomain:false,//iframeを使ったクロスドメイン通信を使うか否か?
 debug:false,
 setDebug:function(flag){
  this.debug=flag;
##sessionStorageが使用できる場合
  if(typeof sessionStorage != "undefined"){
    sessionStorage['ph.debug']=this.debug;
  }
 },
 dump:function(data){ph.log(ph.JSON.stringify(data))},
 dump1:function(data){
  for(var i in data){
   ph.log(i+':'+data[i]);
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
//  alert('ph-loader onload');
 }
};//end of window.ph

//ph.isUseSessionStorage=$esc.javascript(${config.getBoolean("isUseSessionStorage",true)});
//if(typeof sessionStorage == "undefined"){
// ph.isUseSessionStorage=false;
//}

//if(ph.isUseSessionStorage){
// if(sessionStorage['ph.debug']=='true'){
//	 ph.setDebug(true);
// }else{
//	 ph.setDebug(false);
// }
//}

//var spec='$esc.javascript(${config.getString("websocketSpecs")})';
//if(spec){
// ph.isUseWebSocket=true;
//}else{
// ph.isUseWebSocket=false;
//}
if(typeof WebSocket == 'undefined'){
 if(typeof MozWebSocket =='undefined'){
  ph.isUseWebSocket=false;
 }else{
  window.WebSocket=MozWebSocket;
 }
}

//ph.isUseCrossDomain=$esc.javascript(${config.getBoolean("isUseCrossDomain",true)});
//if(typeof window.postMessage == 'undefined'){
// ph.isUseCrossDomain=false;
//}
//var include='$!esc.javascript(${parameter.getParameter("include")})'.split(',');
//for(var i=0;i<include.length;i++){
// if(include[i]==''){
//  continue;
// }
// ph.scripts.push(include[i]);
}

var scriptBase;
if(ph.isSsl){
 scriptBase='https://';
}else{
 scriptBase='http://';
}
scriptBase+=ph.hostHeader +'/pub/js/';
//alert(scriptBase);
for(var i=0;i<ph.scripts.length;i++){
 var script=ph.scripts[i];
 document.write('<script type="text/javascript" src="');
 if(!script.match(/^http/)){
  document.write(scriptBase);
 }
 document.write(script + '" charset="utf-8"');
 document.write('></' + 'script>');
}
//document.write('<script type="text/javascript">');
//document.write('ph.jQuery(ph.onload);');IE8でエラーになる、ph.jQueryが見つからない
//document.write('</' + 'script>');
})();

