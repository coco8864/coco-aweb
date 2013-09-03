// expand next pac transefer FindProxyForURL->NextFindProxyForURL
$!{proxyFinder.nextPac}

function _FindHttpProxyForURL(url,host) {
 if(_domainCheck(url,host)){
  var port=_plainProxyPort(host);
  if( port ){
   return "PROXY ${selfHost}:"+port;
  }
 }
 #if(${proxyFinder.nextPac})
 return ${proxyFinder.NextFindProxyForUrlFuncName}(url,host);
 #else
 var nextHttpProxy='$!{proxyFinder.HttpProxy}';
 if( nextHttpProxy ){
  if( _isExceptDomin(host) ){return "DIRECT";}
  return "PROXY " + nextHttpProxy;
 }
 return "DIRECT";
 #end
}

function _FindSecureProxyForURL(url,host) {
 if(_domainCheck(url,host)){
  var port=_secureProxyPort(host);
  if( port ){
   return "PROXY ${selfHost}:"+port;
  }
 }
 #if(${proxyFinder.nextPac})
 return ${proxyFinder.NextFindProxyForUrlFuncName}(url,host);
 #else
 var nextSecureProxy='$!{proxyFinder.SecureProxy}';
 if( nextSecureProxy ){
  if( _isExceptDomin(host) ){return "DIRECT";}
  return "PROXY " + nextSecureProxy;
 }
 return "DIRECT";
 #end
}

function _isExceptDomin(host) {
 if( host==='${selfHost}' ){return true;}
#foreach($exceptDomiain in $proxyFinder.ExceptDomians)
 if( shExpMatch(host,'${exceptDomiain}') ){return true;}
#end
 return false;
}
function FindProxyForURL(url, host){
 if (url.substring(0, 5) === "http:") {
  return _FindHttpProxyForURL(url,host);
 }else if (url.substring(0, 6) === "https:") {
  return _FindSecureProxyForURL(url,host);
 }else if (url.substring(0, 3) === "ws:") {
  return _FindSecureProxyForURL(url,host);
 }else if (url.substring(0, 4) === "wss:") {
  return _FindSecureProxyForURL(url,host);
 }
 #if(${proxyFinder.nextPac})
 return ${proxyFinder.NextFindProxyForUrlFuncName}(url,host);
 #else
 return "DIRECT";
 #end
}

## exclude—DæA‚ ‚ê‚Îinclude‚Í–³Ž‹‚³‚ê‚é
function _domainCheck(url,host){
 ##Ž©•ª‚Íproxy‚µ‚È‚¢
 if( host==='${selfHost}' ){return false;}
 #if(${excludeDomains.size()}>=0)
  #foreach($excludeDomiain in $excludeDomains)
   if( shExpMatch(host,'${excludeDomiain}') ){return false;}
  #end
   ## exclude‚ª‚ ‚ê‚ÎA’Ê‰ß‚µ‚½‚à‚Ì‚Í‚·‚×‚Ä‘ÎÛ
   return true;
 #elseif(${includeDomains.size()}>=0)
  #foreach($includeDomiain in $includeDomains)
   if( shExpMatch(host,'${includeDomiain}') ){return true;}
  #end
   return false;
 #else
   return true;
 #end
}

function _plainProxyPort(host){
#foreach($entry in $plainDomains.entrySet())
 if( shExpMatch(host,'${entry.key}') ){return '${entry.value}';}
#end
 return null;
}

function _secureProxyPort(host){
#foreach($entry in $secureDomains.entrySet())
 if( shExpMatch(host,'${entry.key}') ){return '${entry.value}';}
#end
 return null;
}
