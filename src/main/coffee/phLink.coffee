class Link extends PhObject
 ###
 \#\#\#\# PhntomLinkアプリの入口となるクラス
 ph.linkメソッドの復帰値として取得.
 このオブジェクトから、storageAPI(storage),pubsubAPI(subscribe)を開始する。

 \#\#\#\# 属性
 *  **isAuth:** 認証の有無
 *  **isOffline:** offline状態か否か
 *  **isConnect:** サーバとの接続の有無

 \#\#\#\# イベント
 *  **ph.EVENT.LOGIN:** 認証完了を通知
 *  **ph.EVENT.QNAMES:** qnameメソッドでcallbackを指定しなかった場合APIの完了を通知
  *   通知={qnames:['qname1','qname2'...]},ユーザ指定ctx
 *  **ph.EVENT.ENCRYPT:** encryptメソッドでcallbackを指定しなかった場合APIの完了を通知
  *   通知={encryptText:'暗号化文字列',plainText:'指定した平文']},ユーザ指定ctx
 *  **ph.EVENT.DECRYPT:** decryptメソッドでcallbackを指定しなかった場合APIの完了を通知
  *   通知={encryptText:'指定した暗号化文字列',plainText:'平文']},ユーザ指定ctx
 *  **ph.STAT_UNLOAD:** このオブジェクトの終了を通知,onUnloadメソッドと同義
 *  **ph.STAT_READY:** このオブジェクトの準備が完了した事を通知,認証完了or認証失敗,onReadyメソッドと同義
 ###
 constructor:(@param)->
  ###利用しない.ph.linkから呼び出される。###
  super
  @keyUrl=@param.keyUrl
  @ctxs={}
  @ctxIdx=1
  @storages={}
  @ppStorage=new PrivateSessionStorage(@)
  link=@
  if @param.useConnection
   @connection=new Connection(@)
   @connection.onLoad(->
     link.load(link)
     link.trigger('linked',link)
     return
    )
   @connection.onUnload(->
     if link.ppStorage
      link.ppStorage.unload()
      link.ppStorage=null
     return
    )
  ph.on(ph.EVENT.MESSAGE,@_onMessage)
  link=@
  ph.onLoad(->
   link._frame=ph.jQuery(
     "<iframe " +
     "style='frameborder:no;background-color:#CFCFCF;overflow:auto;height:0px;width:0px;position:absolute;top:0%;left:0%;' " +
     "name='AplFrame#{link.keyUrl}' " +
     "src='#{link.keyUrl}/~ph.vsp'>" + 
     "</iframe>")
   ph.jQuery("body").append(link._frame)
   # link._frame.on('load',->ph.log('apl frame onload'))
   link._frameTimerId=setTimeout((->
     link._frameTimerId=null
     link._frame.remove()
     link.isAuth=false
     link.cause='apl frame load timeout'
     link.trigger('failToAuth',link)
     link.unload(link)
     return)
    ,ph.authFrameTimeout*2
    )
   )
  ph.on(@STAT_UNLOAD,->link.unlink())
  @
 _connect:->
  # alert('connect start2')
  # @ppStorage=new PrivateSessionStorage(@)
  link=@
  # @ppStorage.onUnload(->link._logout())
  if @param.useConnection==false
   @isConnect=false
   @ppStorage.onLoad(->
    link.trigger('ppStorage',link)
    link.load()
   )
   return
  @isConnect=true
  isWs=@param.useWs
  if !ph.useWebSocket
   isWs=false
  con=@connection
  @ppStorage.onLoad(->
    con.init(isWs)
    link.trigger('ppStorage',link)
    return
   )
  return
 logout:->
  ###online認証している場合ログオフします###
  @_requestToAplFrame({type:'logout'})
 _requestToAplFrame:(msg,ctx)->
  if ctx
   @ctxs[@ctxIdx]=ctx
   msg.ctxIdx=@ctxIdx
   @ctxIdx++
  jsonMsg=ph.JSON.stringify(msg)
  @_frame[0].contentWindow.postMessage(jsonMsg,'*')
 _onMessage:(qjev)=>
  ev=qjev.originalEvent
  if !@_frame || ev.source!=@_frame[0].contentWindow
   return
  res=ph.JSON.parse(ev.data)
  if res.type=='encrypt'
   if res.ctxIdx
    ctx=@ctxs[res.ctxIdx]
    delete @ctxs[res.ctxIdx]
    if typeof(ctx)=='function'
     ctx(res.encryptText)
    else
     @trigger(@EVENT.ENCRYPT,res)
   return
  else if res.type=='decrypt'
   if res.ctxIdx
    ctx=@ctxs[res.ctxIdx]
    delete @ctxs[res.ctxIdx]
    if typeof(ctx)=='function'
     ctx(res.plainText)
    else
     @trigger(@EVENT.DECRYPT,res)
   return
  else if res.type=='showFrame'
   @_frame.css({'height':"#{res.height}px",'width':'500px','top':'100px','left':'50%','margin-top':'0px','margin-left':'-250px'})
   # @_frame.focus()
   return
  if res.type=='hideFrame'
   @_frame.css({'height':'0px','width':'0px','top':'0%','left':'0%'})
   return
  if res.type=='loadAplFrame'
   clearTimeout(@_frameTimerId)
   ph.log('loadAplFrame')
   @_frameTimerId=null
   if !res.result
    @cause='fail to onlineAuth'
    @trigger('failToAuth',@)
    @unload()
   # else if res.aplInfo.loginId #TODO:認証済みなら,最適化
   #  @aplInfo=res.aplInfo
   #  @authInfo=res.authInfo
   #  @trigger('onlineAuth',@)
   #  @trigger('auth',@)
   #  @_connect()
   else if res.aplInfo.isOffline==true && @param.useOffline==false
    # offlineなのにofflineは絶対使うな指定
    @isOffline=true
    @trigger('suspendAuth',@)
   else if @param.useOffline==true || res.aplInfo.isOffline==true
    # 必ずofflineを使う指定、もしくは実際にoffline
    @isOffline=true
    @_requestToAplFrame({type:'offlineAuth',aplUrl:@keyUrl})
   else
    # online
    @isOffline=false
    # isWsは、wsでチェックするかhttpでチェックするかだが、mappingに両者登録しないとlinkアプリは正しく動かない
    # あまり意味はない
    isWs=!(@param.useWs==false) && ph.useWebSocket
    @_requestToAplFrame({type:'onlineAuth',isWs:isWs,originUrl:location.href})
  if res.type=='onlineAuth'
   if res.result=='redirectForAuthorizer'
     location.href=res.location
     return
   else if res.result==true
    # jQuery UIでdialogをopenするとaplFrameがreloadされる。http://bugs.jqueryui.com/ticket/9166
    # その延長でonlineAuthが再度通知される動作に対応
    if @aplInfo
     return
    @isOffline=false
    @aplInfo=res.aplInfo
    @authInfo=res.authInfo
    @trigger('onlineAuth',@)
    @isAuth=true
    @trigger(ph.EVENT.LOGIN,@)
    @_connect()
   else
    @cause=res.reason
    @trigger('failToAuth',@)
    @isAuth=false
    @unload()
  else if res.type=='offlineAuth'
   if res.result==true
    @isOffline=true
    @isAuth=true
    @authInfo=res.authInfo
    @aplInfo={loginId:@authInfo.user.loginId,appSid:'offline'}
    # @ppStorage=new PrivateSessionStorage(@)
    # @trigger('offlineAuth',link)
    @trigger(ph.EVENT.LOGIN,@)
    @load()
   else
    @isAuth=false
    @cause='fail to offlineAuth'
    @trigger('failToAuth',@)
    @unload()
  else if res.type=='logout'
   @_frame[0].src='about:blank'
   @_frame.remove()
   @unload()
  else if res.type=='offlineLogout'
   if res.result==true
    @trigger('suspendAuth',link)
    @unload()
  else if res.type==ph.TYPE.GET_ITEM || res.type==ph.TYPE.CHANGE_ITEM || res.type==ph.TYPE.KEYS
   storage=@storages[res.scope]
   if storage
    storage._storageTrigger(res)
  return
 offlineAuth:()->
  ###利用しない.offline認証します.###
  @_requestToAplFrame({type:'offlineAuth',aplUrl:@keyUrl})
 offlineLogout:()->
  ###利用しない.offline認証している場合logoutします.###
  @_requestToAplFrame({type:'offlineLogout'})
 userProfile:()->
  ###ユーザプロファイル画面を表示します.###
  @_requestToAplFrame({type:'userProfile'})
 subscribe:(qname,subname,cb)->
  ###
  \#\#\#\#\# pubsub APIの開始.online認証しているのを前提に、指定したqname,subnameにsubscribeします。
   -   **qname:** subscribeするqname
   -   **subname:** subscribeするsubname
   -   **cb:** messageの通知メソッド.省略可 subscription.onMsg(cb)と同義
   -   **復帰値:** Subscriptionオブジェクト

   **例** qname:'qname1',subname:'subname1'にsubscribe,msgをfuncに受け取る
    >     var sub=link.subscribe('qname1','subname1',func);
    >//   sub.onMsg(func);

  ###
  @connection.subscribe(qname,subname,cb)
 publish:(qname,msg)->
  ###
  \#\#\#\#\# online認証しているのを前提に、指定したqnameにpublishします.
   -   **qname:** publishするqname
   -   **meg:** 通知するオブジェクト,エントリにDate,Blobを含める事ができます.
  ###
  @connection.publish(qname,msg)
 qnames:(ctx)->
  ###
  \#\#\#\#\# 接続先に登録されているqname一覧を取得します.
   -   **ctx:** メソッドの場合は、このメソッドにqname一覧を通知します.
       それ以外の場合は、当該Linkオブジェクトのph.EVENT.QNAMESイベントで通知します。
  ###
  @connection.qnames(ctx)
 deploy:(qname,className)->
  ### 未サポート,指定したqnameにapplicationを配備します(adminのみ) ###
  @connection.deploy(qname,className)
 undeploy:(qname)->
  ### 未サポート,指定したqnameのapplicationを配備解除します(adminのみ) ###
  @connection.undeploy(qname)
 storage:(scope)->
  ###
  \#\#\#\#\# storage APIの開始.各scopeのstoregeオブジェクトを取得します.offline/onlineの認証の種別によらず利用可能
   -   **scope:** 取得するstorageのscope
   -   **復帰値:** Storageオブジェクト(PrivateSessionStorage or PhLocalStorageだがAPIは同一)

    scopeの種類
    -   **PAGE_PRIVATE:** そのpageのreloadや画面遷移を挟んで情報を維持できるstorage,別タブの同一画面とは別storage.暗号化あり
    -   **SESSION_PRIVATE:** onlineログイン時に利用できる.aplを跨いで同一logon sessionで利用できるstorage.暗号化あり
    -   **APL_PRIVATE:** logonしたaplicationの同一Userで利用できるstorage.暗号化あり
    -   **APL_LOCAL:** aplicationにlogonしたUser全員で共有するstorage.暗号化なし
    -   **AUTH_PRIVATE:** aplicationによらず、logonした同一Userで利用できるstorage.暗号化あり
    -   **AUTH_LOCAL:** aplicationによらず、User全員で共有するstorage.暗号化なし

   **例** SESSION_PRIVATE scopeのstorageを取得する
    >     var storage=link.storage(ph.SCOPE.SESSION_PRIVATE)

  ###
  if scope==ph.SCOPE.PAGE_PRIVATE || !scope
   return @ppStorage
  stor=@storages[scope]
  if !stor
    stor=new PhLocalStorage(@,scope)
    @storages[scope]=stor
  return stor
 encrypt:(plainText,ctx)->
  ###
  \#\#\#\#\# 文字列をlogonしているUserのofflineパスワードで暗号化します.
   -   **plainText:** 平文文字列
   -   **ctx:** メソッドの場合は、このメソッドに暗号化文字列を通知します.
       それ以外の場合は、当該Linkオブジェクトのph.EVENT.ENCRYPTイベントで通知します。
  ###
  @_requestToAplFrame({type:'encrypt',plainText:plainText},ctx)
 decrypt:(encryptText,ctx)->
  ###
  \#\#\#\#\# 暗号化文字列をlogonしているUserのofflineパスワードで復号化します.復号化に失敗した場合は、nullを通知情報とします。
   -   **encryptText:** 暗号化文字列
   -   **ctx:** メソッドの場合は、このメソッドに平文文字列を通知します.
       それ以外の場合は、当該Linkオブジェクトのph.EVENT.DECRYPTイベントで通知します。
  ###
  @_requestToAplFrame({type:'decrypt',encryptText:encryptText},ctx)
 unlink:->
  ### PhantomLink APLの終了 ###
  if @connection
   @connection.unload()
  if @ppStorage
   @ppStorage.unload()
  for scope, storage of @storages
   storage.unload()
  @_frame.remove()
  @unload()

URL_PTN=/^(?:([^:\/]+:))?(?:\/\/([^\/]*))?(.*)/
ph._links={}

ph.link=(aplUrl,useOffline,useConnection,useWs)->
 ###
 \#\#\#\# PhantomLink APIの開始
 認証,サーバとの接続を行う.
 online認証する場合はあらかじめそのブラウザで認証済みにする必要がある。
 サーバが動作しているにもかか関わらず認証済みでない場合は、
 一旦認証した後、このAPIを呼び出した画面に戻ってくる

 -   **aplUrl:** 'http','ws'から始まるURLを指定。'/'から開始した場合は、ph.jsの配信サイトが基準
 -   **userOffline:** true offline認証,false online認証,指定なし online認証できない場合offline認証
 -   **useConnection:** 非公開 online認証時のサーバ接続の有無
 -   **useWs:** 非公開 接続時websocketを使うか否か
 -   **復帰値:** Linkオブジェクト

 **例** online認証できない場合は、offline認証する
  >     ph.link('https://host:port/apl')

 **例** ph.jsを取得したサイトの/aplに必ずoffline認証する
  >     ph.link('/apl',true)
 ###
 if !aplUrl
  pos=location.href.lastIndexOf("/")
  aplUrl=location.href.substring(0,pos)
  useOffline=false
  useConnection=false
  useWs=false
 if typeof aplUrl=='Object'
  param=aplUrl
  if !param.aplUrl
    throw 'aplUrl is null'
  aplUrl=param.aplUrl
 else
  if useConnection!=false
   useConnection=true
  if useWs!=false
   useWs=true
 param={aplUrl:aplUrl,useOffline:useOffline,useConnection:useConnection,useWs:useWs}
 aplUrl.match(URL_PTN)
 protocol=RegExp.$1
 aplDomain=RegExp.$2
 aplPath=RegExp.$3
 if protocol=='ws:'
  keyUrl="http://#{aplDomain}#{aplPath}"
 else if protocol=='wss:'
  keyUrl="https://#{aplDomain}#{aplPath}"
 else if protocol==null || protocol==''
  if ph.isSsl
   keyUrl="https://#{ph.domain}#{aplPath}"
  else
   keyUrl="http://#{ph.domain}#{aplPath}"
 else #http or https
  keyUrl=aplUrl
 link=ph._links[keyUrl]
 if link
  return link
 param.keyUrl=keyUrl
 link=new Link(param)
 ph._links[keyUrl]=link
 link.onUnload(->
   link.trigger('unlinked',link)
   delete ph._links[keyUrl]
  )
 return link

#strage scope
#  SCOPE_PAGE_PRIVATE:'pagePrivate' ...そのページだけ、reloadを挟んで情報を維持するため
#  SCOPE_SESSION_PRIVATE:'sessionPrivate'...開いている同一セションのwindow間で情報を共有
#  SCOPE_APL_PRIVATE:'aplPrivate'...当該apl当該userの情報を保持
#  SCOPE_APL_LOCAL:'aplLocal'...当該aplの情報を保持
#  SCOPE_APL:'apl'
#  SCOPE_QNAME:'qname'
#  SCOPE_SUBNAME:'subname'
#  SCOPE_USER:'user'
#-------------------PagePrivateStorage-------------------
class PrivateSessionStorage extends PhObject
 ###
 \#\#\#\# SrorageAPIのメインクラス
 Link.storageメソッドの復帰値として取得.PAGE_PRIVATE scopeの復帰オブジェクト.
 APIは、その他のscopeのstorageと同じ.getItemを含めてイベントもしくはcallback経由の非同期APIとなっている.

 \#\#\#\# イベント
 *  **ph.EVENT.GET_ITEM:** getItemメソッドの復帰情報を通知
  *   通知={key:'key1',value:'valu1'},ユーザ指定ctx
 *  **ph.EVENT.KEYS:** keysメソッドの復帰情報を通知
  *   通知={keys:['key1','key2'...]},ユーザ指定ctx
 *  **ph.EVENT.CHANGE_ITEM:** 他pageでの値変更を通知
  *   通知={key:'key1',value:'valu1',newValue:'valu1',oldValue:'value2'}
 *  **ph.STAT_UNLOAD:** このオブジェクトの終了を通知
 ###
 constructor:(@link)->
  ###利用しない.Link.storageから呼び出される。###
  super
  @link.on(ph.EVENT.LOGIN,@_init)
 _init:=>
  if !@link.isAuth
    return
  aplInfo=@link.aplInfo
  @_linkPss="&#{@link.keyUrl}:#{aplInfo.loginId}:#{aplInfo.appSid}"
  ##不要なsessionStorageの刈り取り
  sameLoginIdKey="&#{@link.keyUrl}:#{aplInfo.loginId}"
  i=sessionStorage.length
  while (i-=1) >=0
   key=sessionStorage.key(i)
   if key.lastIndexOf(sameLoginIdKey,0)==0 && key!=@_linkPss
    sessionStorage.removeItem(key)
  #_storDecrypt:(storage,key,cb)->
  @encText=sessionStorage.getItem(@_linkPss)
  if @encText
   s=@
   @link.decrypt(@encText,(decText)->
    if decText
     s.data=ph.JSON.parse(decText)
    else
     s.data={}
    s.load()
    s.onUnload(s._onUnload)
   )
  else
   @data={}
   @load()
   @onUnload(@_onUnload)
  #ph.one('@unload',@_onUnload) #pageがunloadされるときsessionStorageに残す
 getItem:(key,ctx)->
  ###
  \#\#\#\#\# 当該storageの指定したkeyに対するvalueを取得します.
   -   **key:** 指定するkey
   -   **ctx:** メソッドの場合は、このメソッドにvalueを通知します.
       それ以外の場合は、当該storageオブジェクトのph.EVENT.GET_ITEMイベントで通知します。
  ###
  s=@
  @onLoad(->s._getItem(key,ctx))
 _getItem:(key,ctx)->
  if typeof(ctx)=='function'
   ctx(@data[key])
  else
   data={key:key,scope:ph.SCOPE.PAGE_PRIVATE,value:@data[key]}
   @trigger(key,data,ctx)
   @trigger(ph.EVENT.GET_ITEM,data,ctx)
  @data[key]
 keys:(ctx)->
  ###
  \#\#\#\#\# 当該storageのkey一覧を取得します.
   -   **ctx:** メソッドの場合は、このメソッドにkey一覧を通知します.
       それ以外の場合は、当該storageオブジェクトのph.EVENT.KEYSイベントで通知します。
  ###
  s=@
  @onLoad(->s._keys(ctx))
 _keys:(ctx)->
  if typeof(Object.keys)=='function'
   keys=Object.keys(@data)
  else
   keys=[]
   for key,value of @data
    keys.push(key)
  if typeof(ctx)=='function'
   ctx(keys)
  else
   data={scope:ph.SCOPE.PAGE_PRIVATE,keys:keys}
   @trigger(ph.EVENT.KYES,data,ctx)
  keys
 setItem:(key,value)->
  ###
  \#\#\#\#\# 当該storageの指定したkeyに指定したvalueを設定します.
   -   **key:** 指定するkey
   -   **value:** 設定するvalue
  ###
  s=@
  @onLoad(->s._setItem(key,value))
 _setItem:(key,value)->
  oldValue=@data[key]
  @data[key]=value
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 removeItem:(key)->
  ###
  \#\#\#\#\# 当該storageの指定したkey項目を削除します.
   -   **key:** 指定するkey
  ###
  s=@
  @onLoad(->s._removeItem(key))
 _removeItem:(key)->
  oldValue=@data[key]
  if !oldValue
   return
  delete @data[key]
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 _onUnload:=>
  # ph.off('unload',@_unload)
  if @encText
   sessionStorage.setItem(@_linkPss,@encText)
  @trigger('@save',@)

#-------------------PageLocalStorage-------------------
class PhLocalStorage extends PhObject
  ###
  \#\#\#\# SrorageAPIのメインクラス
  Link.storageメソッドの復帰値として取得.PAGE_PRIVATE以外のscopeの復帰オブジェクト.
  API,イベントは、PrivateSessionStorageと同一。PrivateSessionStorage参照
  ###
  constructor:(@link,@scope)->
    super
    @ctxs={}
    @ctxIdx=0
    if @link.isLoading()
      s=@
      link.onLoad(->s.load())
    else
      @load()
  getItem:(key,ctx)->
    s=@
    @onLoad(->s._getItem(key,ctx))
  _getItem:(key,ctx)->
    @ctxs[@ctxIdx]=ctx
    @link._requestToAplFrame({type:ph.TYPE.GET_ITEM,scope:@scope,key:key,ctxIdx:@ctxIdx,via:0})
    @ctxIdx++;
    return
  setItem:(key,value)->
    s=@
    @onLoad(->s._setItem(key,value))
  _setItem:(key,value)->
    @link._requestToAplFrame({type:ph.TYPE.SET_ITEM,scope:@scope,key:key,value:value,via:0})
    return
  removeItem:(key)->
    s=@
    @onLoad(->s._removeItem(key))
  _removeItem:(key)->
    @link._requestToAplFrame({type:ph.TYPE.REMOVE_ITEM,scope:@scope,key:key,via:0})
    return
  keys:(ctx)->
    s=@
    @onLoad(->s._keys(ctx))
  _keys:(ctx)->
    @ctxs[@ctxIdx]=ctx
    @link._requestToAplFrame({type:ph.TYPE.KEYS,scope:@scope,ctxIdx:@ctxIdx,via:0})
    @ctxIdx++;
    return
  _storageTrigger:(data)->
    ctx=@ctxs[data.ctxIdx]
    delete @ctxs[data.ctxIdx]
    if data.type==ph.TYPE.GET_ITEM
      if typeof(ctx)=='function'
        ctx(data.value)
        return
      @trigger(ph.EVENT.GET_ITEM,data,ctx)
      @trigger(data.key,data,ctx)
    else if data.type==ph.TYPE.KEYS
      if typeof(ctx)=='function'
        ctx(data.keys)
        return
      @trigger(ph.EVENT.KEYS,data,ctx)
    else if data.type==ph.TYPE.CHANGE_ITEM
      data.value=data.newValue
      @trigger(ph.EVENT.CHANGE_ITEM,data)
      @trigger(data.key,data)

