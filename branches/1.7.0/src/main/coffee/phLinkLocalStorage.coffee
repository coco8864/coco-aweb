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

