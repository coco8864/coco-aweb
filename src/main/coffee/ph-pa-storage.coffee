#-------------------PagePrivateStorage-------------------
class PagePrivateStorage extends ph.EventModule
  constructor:(@ssKey)->
    super
    str=sessionStorage.getItem(@ssKey) ? '{}'
    @data=ph.JSON.parse(str)
  getItem:(key)->
    @data[key]
  setItem:(key,value)->
    oldValue=@data[key]
    @data[key]=value
    @trigger(key,{oldValue:oldValue,value:value})
  removeItem:(key)->
    oldValue=@data[key]
    if oldValue
      delete @data[key]
      @trigger(key,{oldValue:oldValue,value:null})
  _unload:->
    str=ph.JSON.stringify(@data)
    sessionStorage.setItem(@ssKey,str)
    @trigger('save',@)
  _remove:->
    sessionStorage.removeItem(@ssKey)
    @trigger('remove',@)

#-------------------Session,Apl PrivateStorage-------------------
class PrivateStorage extends ph.EventModule
  constructor:(@lsKey)->
    super
    str=localStorage.getItem(@lsKey) ? '{}'
    @data=ph.JSON.parse(str)
  getItem:(key)->
    @data[key]
  setItem:(key,value)->
    oldValue=@data[key]
    @data[key]=value
    str=ph.JSON.stringify(@data)
    localStorage.setItem(@ssKey,str)
    @trigger('save',@)
    @trigger(key,{oldValue:oldValue,value:value})
  removeItem:(key)->
    oldValue=@data[key]
    if oldValue
      delete @data[key]
      str=ph.JSON.stringify(@data)
      localStorage.setItem(@ssKey,str)
      @trigger('save',@)
      @trigger(key,{oldValue:oldValue,value:null})
  _unload:->
  _remove:->
    localStorage.removeItem(@lsKey)
    @trigger('remove',@)

#-------------------AplLocaleStorage-------------------
class AplLocalStorage extends ph.EventModule
  constructor:(@lsKey)->
    super
  getItem:(key)->
    realKey="#{@lsKey}:#{key}"
    localStorage.getItem[realKey]
  setItem:(key,value)->
    realKey="#{@lsKey}:#{key}"
    oldValue=localStorage.getItem[realKey]
    localStorage.setItem(realKey,value)
    @trigger('save',@)
    @trigger(key,{oldValue:oldValue,value:value})
  removeItem:(key)->
    realKey="#{@lsKey}:#{key}"
    oldValue=localStorage.getItem[realKey]
    if oldValue
      localStorage.removeItem(realKey)
      @trigger('save',@)
      @trigger(key,{oldValue:oldValue,value:null})
  _unload:->
  _remove:->
    @trigger('remove',@)
#TODO all item
