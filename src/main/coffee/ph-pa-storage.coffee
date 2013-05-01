#-------------------Storage-------------------
class Storage extends EventModule
  constructor:(@ssKey)->
    super
    str=sessionStorage.getItem(@ssKey) ? '{"bid":0}'
    if !str
      str='{"bid":0}'
    @data=ph.JSON.parse(str)
  getItem:(key)->
    @data[key]
  setItem:(key,value)->
    oldValue=@data[key]
    @data[key]=value
    @trigger(key,{oldValue:oldValue,value:value})
  removeItem:(key)->
    delete @data[key]
  _unload:->
    str=ph.JSON.stringify(@data)
    sessionStorage.setItem(@ssKey,str)
  _remove:->
    sessionStorage.removeItem(@ssKey)

