if window.ph
  return

#-------------------EventModule-------------------
#オブジェクトの基本的なライフサイクル
# '@loading' まだ前処理が終了していないので使えない状態
# '@load' 使える状態
# '@unload' 使えなくなった状態
# '@load'と'@unload'の間は任意の状態を作ってもよい
class PhObject
  ###
  \#\#\#\# PhantomLinkが提供するクラスの基底クラス
  イベントの配信、オブジェクトのライフサイクルを制御する.
  アプリケーションから利用するAPIは、イベント受信メソッド登録、削除メソッドon,one,off.ライフサイクルの変化の通知を受けるonLoad,onUnload,onReady
  ###
  STAT_LOADING:'@loading'
  STAT_LOAD:'@load'
  STAT_UNLOAD:'@unload'
  STAT_READY:'@ready' # @loadingから状態が変更されたことを通知
  constructor:->
    @_ctxIdx=1
    @_ctxs={}
    @_callback ={}
    @_callbackOne ={}
    @_stat=@STAT_LOADING ##_stat: '@loading' -> '@load'-> この後自由 -> '@unload'
  one:(name, callback)->
    ###
    \#\#\#\#\# 指定したイベントの通知を受けるメソッドを登録します.イベントが通知される毎にcallbackは呼び出される.
     -   **name:** 指定するイベント名
     -   **callback:** イベントを受信するメソッド
    ###
    if !@_callbackOne[name]? then @_callbackOne[name]=[]
    @_callbackOne[name].push(callback)
    @
  on: (name, callback) ->
    ###
    \#\#\#\#\# 指定したイベントの通知を受けるメソッドを登録します.イベントを1回受信するとoffされる.
     -   **name:** 指定するイベント名
     -   **callback:** イベントを受信するメソッド
    ###
    if !@_callback[name]? then @_callback[name]=[]
    @_callback[name].push(callback)
    @
  off: (name, cb) =>
    ###
    \#\#\#\#\# 指定したイベント、受信イベントを登録解除.oneもしくはonメソッドでの登録情報が破棄されます。
     -   **name:** 指定するイベント名
     -   **callback:** イベントを受信するメソッド,省略した場合指定したイベント名の全メソッドが登録解除されます.
    ###
    list = @_callback[name]
    return @ unless list
    if !cb
      delete @_callback[name]
      return @
    n=list.length
    for i in [n-1..0]
      if list[i]==cb
        list.splice(i,1)
    @
  # このオブジェクトのstatを設定する際に呼び出す
  stat:(stat)->
    @load()
    @_stat=stat
    @trigger(stat)
    return
  # このオブジェクトが使えるようになったら呼び出す
  load:->
    if @_stat==@STAT_LOADING
      @_stat=@STAT_LOAD
      @trigger(@STAT_LOAD)
      @trigger(@STAT_READY)
    return
  # このオブジェクトが使えなくなったら呼び出す
  unload:->
    if @_stat==@STAT_LOAD||@_stat==@STAT_LOADING
      @_stat=@STAT_UNLOAD
      @trigger(@STAT_UNLOAD)
      @trigger(@STAT_READY)
    return
  _triggerOne: (name,args...) ->
    list = @_callbackOne[name]
    return @ unless list
    for callback in list
      callback.apply(@,args)
    delete @_callbackOne[name]
    return
  _trigger: (name,args...) ->
    list = @_callback[name]
    return @ unless list
    for callback in list
      callback.apply(@,args)
    @
  trigger: (args...) ->
    @_trigger.apply(@,args)
    @_triggerOne.apply(@,args)
  # 状態によってメソッドの呼び出す
  # 使えるようになったら通知されるfuncを登録
  onLoad:(func,args...)->
    if @_stat==@STAT_LOADING
      if func
        _this=@
        @one(@STAT_LOAD,->func.apply(_this,args))
      return
    else if @_stat==@STAT_LOAD
      if func
        return func.apply(@,args)
    else
      throw 'aleady unloaded'
  # 使えなくなったら通知されるfuncを登録
  onUnload:(func,args...)->
    ###
    \#\#\#\#\# 当該オブジェクトが利用できなく成った通知を受けるメソッドを登録します。
     -   **func:** イベント受信メソッド
     -   **args:** イベント受信メソッドに渡すパラメタ.
    ###
    if @_stat==@STAT_LOADING || @_stat==@STAT_LOAD
      if func
        _this=@
        @one(@STAT_UNLOAD,->func.apply(_this,args))
      return false
    else
      if func
        func.apply(@,args)
      return true
  onReady:(func,args...)->
    ###
    \#\#\#\#\# 当該オブジェクトの準備が終わった通知を受けるメソッドを登録します。
     -   **func:** イベント受信メソッド
     -   **args:** イベント受信メソッドに渡すパラメタ.
    ###
    if @_stat==@STAT_LOADING
      if func
        _this=@
        @one(@STAT_READY,->func.apply(_this,args))
      return false
    else
      if func
        func.apply(@,args)
      return true
  isUnload:->
    ### 当該オブジェクトが利用不可ならtrueを返却 ###
    @_stat==@STAT_UNLOAD
  isLoading:->
    ### 当該オブジェクトが初期化中ならtrueを返却 ###
    @_stat==@STAT_LOADING
  _pushCtx:(ctx)->
    @_ctxIdx++
    @_ctxs[@_ctxIdx]=ctx
    @_ctxIdx
  _popCtx:(ctxIdx)->
    ctx=@_ctxs[ctxIdx]
    delete @_ctxs[ctxIdx]
    ctx
#-------------------Ph-------------------
class Ph extends PhObject
  ###
  \#\#\#\# window.phに設定されるオブジェクトのクラス
  アプリケーションからは、イベント定数を直接利用する.
  ###
  STAT_INIT:'INIT'
  STAT_AUTH:'AUTH'
  STAT_IDLE:'IDLE'
  STAT_NEGOTIATION:'NEGOTIATION'
  STAT_OPEN:'OPEN',
  STAT_CONNECT:'CONNECT'
  STAT_CLOSE:'CLOSE'
  # request type
  TYPE_NEGOTIATE:'negotiate'
  TYPE_PUBLISH:'publish'
  TYPE_SUBSCRIBE:'subscribe'
  TYPE_UNSUBSCRIBE:'unsubscribe'
  TYPE_DEPLOY:'deploy'
  TYPE_UNDEPLOY:'undeploy'
  TYPE_QNAMES:'qnames'
  TYPE_CLOSE:'close'
  # response type
  TYPE_RESPONSE:'response'
  TYPE_MESSAGE:'message'
  TYPE_DOWNLOAD:'download'
  RESULT_ERROR:'error'
  RESULT_SUCCESS:'success'
  KEY_BID:'@bid'
  # strage scope
  SCOPE:{
    PAGE_PRIVATE:'pagePrivate'
    SESSION_PRIVATE:'sessionPrivate' #auth localstorage key:sessionid.key=value
    APL_PRIVATE:'aplPrivate' #apl localstorage key:loginid.key=value
    APL_LOCAL:'aplLocal' #apl localstorage key:@.key=value  (no enc)
    AUTH_PRIVATE:'authPrivate' #auth localstorage key:loginid.key=value
    AUTH_LOCAL:'authLocal' #auth localstorage key:@.key=value (no enc)
    APL_USER:'aplUser'
    APL_GLOBAL:'aplGlobal'
  }
  EVENT:{
    GET_ITEM:'@getItem'
    SET_ITEM:'@setItem'
    REMOVE_ITEM:'@removeItem'
    KEYS:'@keys'
    CHANGE_ITEM:'@changeItem'
    MESSAGE:'@message'
    QNAMES:'@qnames'
    LOGIN:'@login'
    LOGOUT:'@logout'
    SUSPEND_LOGIN:'@suspendLogin'
    CONNECTED:'@connected'
    DISCONNECT:'@disconnect'
    ENCRYPT:'@encrypt'
    DECRYPT:'@decrypt'
    ERROR:'@error'
  }
  TYPE:{
    GET_ITEM:'getItem'
    SET_ITEM:'setItem'
    REMOVE_ITEM:'removeItem'
    KEYS:'keys'
    CHANGE_ITEM:'changeItem'
    MESSAGE:'message'
  }
 # _INTERVAL:1000
  _WS_RETRY_MAX:3
  _KEEP_MSG_BEFORE_SUBSCRIBE:true
  _KEEP_MSG_MAX:64
  _DEFAULT_SUB_ID:'@'
  _DOWNLOAD_FRAME_NAME_PREFIX:'__ph_dl_'
  _XHR_FRAME_NAME_PREFIX:'__ph_xhr_' #xhrPhFrame.vspに同じ定義あり
  _XHR_FRAME_URL:'/~xhrPhFrame'

  version:'$esc.javascript(${config.getString("phantomVersion")})'
  isSsl:'$esc.javascript(${handler.isSsl()})'=='true'
  domain:'$esc.javascript(${handler.getRequestHeader().getServer()})'
  authFrameTimeout:parseInt("$esc.javascript(${config.getString('authFrameTimeout','5000')})",10)
  scriptBase:''
  scripts:['jquery-1.8.3.min.js','ph-jqnoconflict.js','ph-json2.js']
  useWebSocket:typeof window.WebSocket != 'undefined' || typeof window.MozWebSocket !='undefined' ## WebSocketを使うか否か?
  useSessionStorage:typeof window.sessionStorage != 'undefined' ## SessionStorageを使うか否か?
  useCrossDomain:typeof window.postMessage != 'undefined' ## iframeを使ったクロスドメイン通信を使うか否か?
  useHashChange:typeof window.onhashchange!='undefined'
  useBlobBuilder:false
  useAppCache:typeof window.applicationCache!='undefined'
  useBlob:typeof Uint8Array != 'undefined' && typeof ArrayBuffer != 'undefined' && typeof Blob != 'undefined'
  constructor:->
    super
  isOffline:false
  createBlob:(data)->
    if Blob
      return new Blob(data)
    else if BlobBuilder
      bb=new BlobBuilder()
      for d in data
        bb.append(d)
      return bb.getBlob()
    return null
  blobSlice:(blob,startingByte,endindByte,type)->
    if blob.webkitSlice
      return blob.webkitSlice(startingByte, endindByte,type)
    else if blob.mozSlice
      return blob.mozSlice(startingByte, endindByte,type)
    return blob.slice(startingByte, endindByte,type)
  # https://github.com/ukyo/jsziptools/blob/master/src/utils.js
  stringToArrayBuffer:(str)->
    if !ph.useBlob
      return str
    n = str.length
    idx = -1
    utf8 = []
    # http://user1.matsumoto.ne.jp/~goma/js/utf.js
    for i in [0..(n-1)]
      c = str.charCodeAt(i)
      if c <= 0x7F
        utf8[++idx] = c
      else if c <= 0x7FF
        utf8[++idx] = 0xC0 | (c >>> 6)
        utf8[++idx] = 0x80 | (c & 0x3F)
      else if(c <= 0xFFFF)
        utf8[++idx] = 0xE0 | (c >>> 12)
        utf8[++idx] = 0x80 | ((c >>> 6) & 0x3F)
        utf8[++idx] = 0x80 | (c & 0x3F)
      else
        j = 4
        while c >> (6 * j)
          ++j
        utf8[++idx] = ((0xFF00 >>> j) & 0xFF) | (c >>> (6 * --j))
        while j--
          utf8[++idx] = 0x80 | ((c >>> (6 * j)) & 0x3F)
    #  return new Uint8Array(utf8).buffer
    return new Uint8Array(utf8)
  debug:false ##debugメッセージを出力するか否か
  setDebug:(flag)->
    @debug=flag
    ## sessionStorageが使用できる場合
    if typeof sessionStorage != "undefined"
      sessionStorage['ph.debug']=flag
  showDebug:false ##debug領域を表示するか否か
  setShowDebug:(flag)->
    @showDebug=flag
    if flag
      ph.jQuery('#phDebug').show()
    else
      ph.jQuery('#phDebug').hide()
    if typeof sessionStorage != "undefined"
      sessionStorage['ph.showDebug']=flag
  clearDebug:->
    ph.jQuery('#phDebugArea').text('')
  dump:(data)->
    ph.log(ph.JSON.stringify(data))
  dump1:(data)->
    for d in data
      if typeof(d) == 'function'
        continue
      if ph.jQuery.isArray(d)
        ph.log(i+':['+d +']')
      else
        ph.log(i+':'+d)
  log:(text)->
    if ph.debug
      ph.jQuery("#phDebugArea").text( ph.jQuery("#phDebugArea").text() + "\r\n" + text)
  absolutePath:(path, base)->
    bases = base.match(/((http:\/\/)|(https:\/\/))[^/]*\//)
    if !bases
      return null
    baseroot = bases[0]
    pathes = path.split("/")
    if pathes[0].match(/(http)|(https):/)
      return path
    if pathes[0] == ""
      pathes.shift()
      return baseroot + pathes.join("/")
    while 0 < pathes.length && pathes[0] == "."
      pathes.shift()
    basetemp = base.substring(baseroot.length).match(/([^\?]*)\//)
    if !basetemp || basetemp.length < 2
      return baseroot + pathes.join("/")
    bases = basetemp[1].split("/")
    while 0 < bases.length && 0 < pathes.length && pathes[0] == ".."
      bases.pop()
      pathes.shift()
    return baseroot + bases.concat(pathes).join("/")
  scriptUrl:(script)->
    if script.match(/^http/)
      return script
    if ph.isSsl
      schme='https://'
    else
      schme='http://'
    return "#{schme}#{ph.domain}/pub/js/#{script}"
  loadAndExecuteScripts:(scriptUrls, index, callback,errorcb)->
    sc=document.createElement('script')
    sc.type='text/javascript'
    sc.onload = ->
      sc.parentNode.removeChild(sc)
      if (index+1)<=(scriptUrls.length-1)
        ph.loadAndExecuteScripts(scriptUrls,index+1,callback)
      else
        if callback
          callback()
    sc.onerror = ->
      sc.parentNode.removeChild(sc)
      if errorcb
        errorcb(sc.src)
    sc.src = ph.scriptUrl(scriptUrls[index])
    document.body.appendChild(sc)
  # ph-jqnoconflict.jsでload時にph.onPhLoadが呼び出されるようにしている
  onPhLoad:->
    if !navigator.onLine
      ph.isOffline=true
      ph.load()
      return
    phOnlineUrl=ph.scriptUrl('phOnline.js')
    ph.loadAndExecuteScripts([phOnlineUrl],0,null,->
      ph.isOffline=true
      ph.load()
    )
    ph.jQuery(window).on('message',(ev)->window.ph.trigger(ph.EVENT.MESSAGE,ev))
    ph.jQuery(window).unload((ev)->window.ph.trigger('@unload',ev))

window.ph=new Ph()

if document.readyState=='loading' || document.readyState=='interactive'
  for script in ph.scripts
    document.write('<script type="text/javascript" src="');
    url=ph.scriptUrl(script)
    document.write(url)
    document.write('" charset="utf-8"')
    document.write('></' + 'script>')
else
  ph.loadAndExecuteScripts(ph.scripts,0)

