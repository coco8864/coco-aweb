if window.ph
  return

#-------------------EventModule-------------------
#オブジェクトの基本的なライフサイクル
# '@loading' まだ前処理が終了していないので使えない状態
# '@load' 使える状態
# '@unload' 使えなくなった状態
# '@load'と'@unload'の間は任意の状態を作ってもよい
class PhObject
  STAT_LOADING:'@loading'
  STAT_LOAD:'@load'
  STAT_UNLOAD:'@unload'
  STAT_NOT_LOADING:'@notLoading'
  constructor:->
    @_callback ={}
    @_callbackOne ={}
    @_stat=@STAT_LOADING ##_stat: '@loading' -> '@load'-> この後自由 -> '@unload'
  one:(name, callback)->
    if !@_callbackOne[name]? then @_callbackOne[name]=[]
    @_callbackOne[name].push(callback)
    @
  on: (name, callback) ->
    if !@_callback[name]? then @_callback[name]=[]
    @_callback[name].push(callback)
    @
  off: (name, cb) =>
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
      @trigger(@STAT_NOT_LOADING)
    return
  # このオブジェクトが使えなくなったら呼び出す
  unload:->
    if @_stat==@STAT_LOAD||@_stat==@STAT_LOADING
      @_stat=@STAT_UNLOAD
      @trigger(@STAT_UNLOAD)
      @trigger(@STAT_NOT_LOADING)
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
      return false
    else if @_stat==@STAT_LOAD
      if func
        func.apply(@,args)
      return true
    else
      throw 'aleady unloaded'
  # 使えなくなったら通知されるfuncを登録
  onUnload:(func,args...)->
    if @_stat==@STAT_LOADING || @_stat==@STAT_LOAD
      if func
        _this=@
        @one(@STAT_UNLOAD,->func.apply(_this,args))
      return false
    else
      if func
        func.apply(@,args)
      return true
  # 使えるか使えないかはっきりしたら通知されるfuncを登録
  onNotLoading:(func,args...)->
    if @_stat==@STAT_LOADING
      if func
        _this=@
        @one(@STAT_NOT_LOADING,->func.apply(_this,args))
      return false
    else
      if func
        func.apply(@,args)
      return true
  isUnload:->
    @_stat==@STAT_UNLOAD
  isLoading:->
    @_stat==@STAT_LOADING

#-------------------Ph-------------------
class Ph extends PhObject
  STAT_INIT:'INIT'
  STAT_AUTH:'AUTH'
  STAT_IDLE:'IDLE'
  STAT_NEGOTIATION:'NEGOTIATION'
  STAT_OPEN:'OPEN',
  STAT_CONNECT:'CONNECT'
  STAT_CLOSE:'CLOSE'
  CB_INFO:'INFO'
  CB_ERROR:'ERROR'
  CB_MESSAGE:'MESSAGE'
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
  # strage scope
  SCOPE_PAGE_PRIVATE:'pagePrivate'
  SCOPE_SESSION_PRIVATE:'sessionPrivate' #auth localstorage key:sessionid.key=value
  SCOPE_APL_PRIVATE:'aplPrivate' #apl localstorage key:loginid.key=value
  SCOPE_APL_LOCAL:'aplLocal' #apl localstorage key:@.key=value  (no enc)
  SCOPE_AUTH_PRIVATE:'authPrivate' #auth localstorage key:loginid.key=value
  SCOPE_AUTH_LOCAL:'authLocal' #auth localstorage key:@.key=value (no enc)
  SCOPE_APL:'apl'
  SCOPE_QNAME:'qname'
  SCOPE_SUBNAME:'subname'
  SCOPE_USER:'user'
 # _INTERVAL:1000
  _SEND_DATA_MAX:(1024*1024*2)
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
    ph.jQuery(window).on('message',(ev)->window.ph.trigger('@message',ev))
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

