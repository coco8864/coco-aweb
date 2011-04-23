function FindHttpProxyForURL(url,host) {
  var nextHttpProxy='$proxyFinder.HttpProxy';
  if( nextHttpProxy ){
    if( isExceptDomin(host) ) return "DIRECT";
    return "PROXY " + nextHttpProxy;
  }
  return "DIRECT";
}
function FindSecureProxyForURL(url,host) {
  var nextSecureProxy='$proxyFinder.SecureProxy';
  if( nextSecureProxy ){
    if( isExceptDomin(host) ) return "DIRECT";
    return "PROXY " + nextSecureProxy;
  }
  return "DIRECT";
}
function isExceptDomin(host) {
#foreach($exceptDomiain in $proxyFinder.ExceptDomians)
 if( shExpMatch(host,'${exceptDomiain}') ){return true;}
#end
 return false;
}
function FindProxyForURL(url, host){
//  if(isPlainHostName(host)){
//    return "DIRECT";
//  }
  if (url.substring(0, 5) == "http:") {
    return FindHttpProxyForURL(url,host);
  }else if (url.substring(0, 6) == "https:") {
    return FindSecureProxyForURL(url,host);
  }
  return "DIRECT";
}
