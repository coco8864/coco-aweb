if window.ph
 return

#-------------------EventModule-------------------
class Deferred
  constructor:->
    @_callback ={}
    @_callbackOne ={}
    @_stat='@loading' ##_stat: '@loading' -> '@load' -> '@unload'
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
  load:->
    if @_stat=='@loading'
      @_stat='@load'
    @trigger('@load')
    return
  unload:->
    if @_stat=='@load'
      @_stat='@unload'
    @trigger('@unload')
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
# @loading+funcが指定された場合は、load時にfuncを実行
  onLoad:(func,args...)->
    if @_stat=='@loading'
      if func
        _this=@
        @one('@load',->func.apply(_this,args))
      return false
    else if @_stat=='@load'
      if func
        func.apply(@,args)
      return true
    else
      throw 'aleady unloaded'
  onUnload:(func,args...)->
    if @_stat=='@loading' || @_stat=='@load'
      if func
        _this=@
        @one('@unload',->func.apply(_this,args))
      return false
    else
      if func
        func.apply(@,args)
      return true
  isUnload:->
    @_stat=='@unload'

#-------------------Ph-------------------
class Ph extends Deferred
 version:'$esc.javascript(${config.getString("phantomVersion")})'
 isSsl:'$esc.javascript(${handler.isSsl()})'=='true'
 domain:'$esc.javascript(${handler.getRequestHeader().getServer()})'
 scriptBase:''
 scripts:['jquery-1.8.3.min.js','ph-jqnoconflict.js','ph-json2.js','ph-auth.js','ph-pa.js']
 useWebSocket:typeof window.WebSocket != 'undefined' || typeof window.MozWebSocket !='undefined' ## WebSocketを使うか否か?
 useSessionStorage:typeof window.sessionStorage != 'undefined' ## SessionStorageを使うか否か?
 useCrossDomain:typeof window.postMessage != 'undefined' ## iframeを使ったクロスドメイン通信を使うか否か?
 useHashChange:typeof window.onhashchange!='undefined'
 useBlobBuilder:false
 useAppCache:typeof window.applicationCache!='undefined'
 useBlob:typeof Uint8Array != 'undefined' && typeof ArrayBuffer != 'undefined' && typeof Blob != 'undefined'
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
#ph-jqnoconflict.jsでload時にph.onPhLoadが呼び出されるようにしている
 onPhLoad:->
  if !navigator.onLine
    ph.unload()
    ph.isOffline=true
    return
  ph.jQuery.ajax({
   type: 'GET',
   url: '/ph.json',
   dataType:'json',
   success: (json)->
    for key,value of json
     ph[key]=value
    if ph.useWebSocket && !ph.websocketSpec
     ph.useWebSocket=false
    ph.load()
    ph.isOffline=false
   error: (xhr)->
    ph.unload()
    ph.isOffline=true
  })
  ph.jQuery(window).on('message',(ev)->window.ph.trigger('message',ev))
  ph.jQuery(window).on('storage',(ev)->window.ph.trigger('storage',ev))
  ph.jQuery(window).unload((ev)->window.ph.trigger('unload',ev))

window.ph=new Ph()
window.ph.Deferred=Deferred

for script in ph.scripts
 document.write('<script type="text/javascript" src="');
 if !script.match(/^http/)
  document.write('/pub/js/')
 document.write(script + '" charset="utf-8"')
 document.write('></' + 'script>')

