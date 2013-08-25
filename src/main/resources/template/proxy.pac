// expand next pac transefer FindProxyForURL->NextFindProxyForURL
$!{proxyFinder.nextPac}

function FindHttpProxyForURL(url,host) {
  if( isHttpPhantomDomain(host) ) return "PROXY ${localHost}";
  #if(${proxyFinder.nextPac})
  return ${proxyFinder.NextFindProxyForUrlFuncName}(url,host);
  #else
  var nextHttpProxy='$!{proxyFinder.HttpProxy}';
  if( nextHttpProxy ){
    if( isExceptDomin(host) ) return "DIRECT";
    return "PROXY " + nextHttpProxy;
  }
  return "DIRECT";
  #end
}
function FindSecureProxyForURL(url,host) {
  if( isSecurePhantomDomain(host) ) return "PROXY ${localHost}";
  #if(${proxyFinder.nextPac})
  return ${proxyFinder.NextFindProxyForUrlFuncName}(url,host);
  #else
  var nextSecureProxy='$!{proxyFinder.SecureProxy}';
  if( nextSecureProxy ){
    if( isExceptDomin(host) ) return "DIRECT";
    return "PROXY " + nextSecureProxy;
  }
  return "DIRECT";
  #end
}
//phantom not support ... but need research ftp proxy 
function FindFtpProxyForURL(url,host) {
  #if(${proxyFinder.nextPac})
  return NextFindProxyForURL(url,host);
  #else
  return "DIRECT";
  #end
}
//phantom not support ... need research socks proxy
function FindSocksProxyForURL(url,host) {
  #if(${proxyFinder.nextPac})
  return NextFindProxyForURL(url,host);
  #else
  return "DIRECT";
  #end
}
function isSecurePhantomDomain(host) {
 if( host=='localhost' ){return false;}
 if( shExpMatch(host,'127.*') ){return false;}
 if( shExpMatch(host,'192.*') ){return false;}
 if( host=='${localServer}' ){return false;}
#foreach($phantom in $proxyFinder.SecurePhantomDomians)
 if( shExpMatch(host,'${phantom}') ){return true;}
#end
 return false;
}
function isHttpPhantomDomain(host) {
 if( host=='localhost' ){return false;}
 if( shExpMatch(host,'127.*') ){return false;}
 if( shExpMatch(host,'192.*') ){return false;}
 if( host=='${localServer}' ){return false;}
#foreach($phantom in $proxyFinder.HttpPhantomDomians)
 if( shExpMatch(host,'${phantom}') ){return true;}
#end
 return false;
}
function isExceptDomin(host) {
 if( host=='${localServer}' ){return true;}
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
  }else if (url.substring(0, 3) == "ws:") {
    return FindSecureProxyForURL(url,host);
  }else if (url.substring(0, 4) == "wss:") {
    return FindSecureProxyForURL(url,host);
  }
  #if(${proxyFinder.nextPac})
  return NextFindProxyForURL(url,host);
  #else
  return "DIRECT";
  #end
}
