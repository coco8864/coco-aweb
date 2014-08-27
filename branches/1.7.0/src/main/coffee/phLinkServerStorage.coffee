#-------------------PageLocalStorage-------------------
class PhServerStorage extends PhObject
  ###
  ###
  constructor:(@link,@scope,@storName)->
    super
    @sub=@link.subscribe('@Storage',scope+@storName)
    stor=@
    @sub.onMsg((msg)->stor._onMsg(msg))
    @sub.on(ph.EVENT.ERROR,@_onTerm)
    @link.onUnload(@_onTerm)
    if @link.isLoading()
      s=@
      link.onLoad(->s.load())
    else
      @load()
  getItem:(key,ctx)->
    s=@
    @onLoad(->s._getItem(key,ctx))
  _getItem:(key,ctx)->
    ctxIdx=@_pushCtx(ctx)
    @sub.publish({type:ph.TYPE.GET_ITEM,scope:@scope,storName:@storName,key:key,ctxIdx:ctxIdx})
    return
  setItem:(key,value)->
    s=@
    @onLoad(->s._setItem(key,value))
  _setItem:(key,value)->
    @sub.publish({type:ph.TYPE.SET_ITEM,scope:@scope,storName:@storName,key:key,value:value})
    return
  removeItem:(key)->
    s=@
    @onLoad(->s._removeItem(key))
  _removeItem:(key)->
    @sub.publish({type:ph.TYPE.REMOVE_ITEM,scope:@scope,storName:@storName,key:key})
    return
  keys:(ctx)->
    s=@
    @onLoad(->s._keys(ctx))
  _keys:(ctx)->
    ctxIdx=@_pushCtx(ctx)
    @sub.publish({type:ph.TYPE.KEYS,scope:@scope,storName:@storName,ctxIdx:ctxIdx})
    return
  _onTerm:(data)->
    @unload()
  _onMsg:(data)->
    ctx=@_popCtx(data.ctxIdx)
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
      data.newValue=data.value
      @trigger(ph.EVENT.CHANGE_ITEM,data)
      @trigger(data.key,data)

