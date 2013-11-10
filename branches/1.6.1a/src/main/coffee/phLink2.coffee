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
     link.trigger(ph.EVENT.ERROR,{type:'link',keyUrl:link.keyUrl,cause:link.cause,detail:'loadFrame'},link)
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
    # link.trigger('ppStorage',link)
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
    # link.trigger('ppStorage',link)
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
  #ie9で、画面切り替え中によばれるとオブジェクトが存在しない場合がある
  try
   if !@_frame || ev.source!=@_frame[0].contentWindow
    return
  catch error
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
    link.trigger(ph.EVENT.ERROR,{type:'loadAplFrame',keyUrl:@keyUrl,cause:@cause,detail:res},@)
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
    @trigger(ph.EVENT.SUSPEND_LOGIN,@)
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
    @isAuth=true
    @trigger(ph.EVENT.LOGIN,@)
    @_connect()
   else
    @cause=res.reason
    @trigger(ph.EVENT.ERROR,{type:'onlineAuth',keyUrl:@keyUrl,cause:@cause,detail:res},@)
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
    @trigger(ph.EVENT.ERROR,{type:'offlineAuth',keyUrl:@keyUrl,cause:@cause,detail:res},@)
    @unload()
  else if res.type=='logout'
   @_frame[0].src='about:blank'
   @_frame.remove()
   @unload()
  else if res.type=='offlineLogout'
   if res.result==true
    @trigger(ph.EVENT.SUSPEND_LOGIN,link)
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
 storage:(scope,storName)->
  ###
  \#\#\#\#\# storage APIの開始.各scopeのstoregeオブジェクトを取得します.offline/onlineの認証の種別によらず利用可能
   -   **scope:** 取得するstorageのscope
   -   **storName:** APL_USER,APL_GLOBALの場合、対象となるstorage name
   -   **復帰値:** Storageオブジェクト(PrivateSessionStorage or PhLocalStorage or PhServerStorageだがAPIは同一)

    scopeの種類
    -   **PAGE_PRIVATE:** そのpageのreloadや画面遷移を挟んで情報を維持できるstorage,別タブの同一画面とは別storage.browser,暗号化あり
    -   **SESSION_PRIVATE:** onlineログイン時に利用できる.aplを跨いで同一logon sessionで利用できるstorage.browser,暗号化あり
    -   **APL_PRIVATE:** logonしたaplicationの同一Userで利用できるstorage.browser,暗号化あり
    -   **APL_LOCAL:** aplicationにlogonしたUser全員で共有するstorage.browser,暗号化なし
    -   **AUTH_PRIVATE:** aplicationによらず、logonした同一Userで利用できるstorage.browser,暗号化あり
    -   **AUTH_LOCAL:** aplicationによらず、User全員で共有するstorage.browser,暗号化なし
    -   **APL_USER:** logonしたaplicationの同一Userで利用できるstorage.server
    -   **APL_GLOBAL:** aplicationにlogonしたUser全員で共有するstorage.server

   **例** SESSION_PRIVATE scopeのstorageを取得する
    >     var storage=link.storage(ph.SCOPE.SESSION_PRIVATE)

  ###
  if scope==ph.SCOPE.PAGE_PRIVATE || !scope
   return @ppStorage
  else if scope==ph.SCOPE.APL_USER || scope==ph.SCOPE.APL_GLOBAL
   storKey=scope+storName
  else
   storKey=scope
  storage=@storages[storKey]
  if storage
    return storage
  if scope==ph.SCOPE.APL_USER || scope==ph.SCOPE.APL_GLOBAL
    storage=new PhServerStorage(@,scope,storName)
  else
    storage=new PhLocalStorage(@,scope)
  @storages[storKey]=storage
  return storage
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
 link.onUnload(->delete ph._links[keyUrl])
 return link
