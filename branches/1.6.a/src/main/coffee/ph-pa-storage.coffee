#-------------------PagePrivateStorage-------------------
class PrivateSessionStorage extends ph.EventModule
  constructor:(url,@_auth)->
    super
    @_paPss="_paPss:#{url}:#{@_auth.loginId}:#{@_auth.appSid}"
    s=@
    ph.pa._storDecrypt(sessionStorage,@_auth,@_paPss,(decText)->
      if decText
        s.data=ph.JSON.parse(decText)
      else
        s.data={}
      )
  getItem:(key)->
    @data[key]
  setItem:(key,value)->
    oldValue=@data[key]
    @data[key]=value
    @trigger(key,{key:key,oldValue:oldValue,newValue:value})
    s=@
    @_auth.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  removeItem:(key)->
    oldValue=@data[key]
    if oldValue
      delete @data[key]
      @trigger(key,{key:key,oldValue:oldValue})
      s=@
      @_auth.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  _unload:=>
    if @encText
      sessionStorage.setItem(@_paPss,@encText)
    @trigger('save',@)
  _remove:->
    sessionStorage.removeItem(@_paPss)
    @trigger('remove',@)

#-------------------Session,Apl PrivateStorage-------------------
class PrivateLocalStorage extends ph.EventModule
  constructor:(url,@_auth)->
    super
    @_paPls="_paPls:#{url}:#{@_auth.loginId}:#{@_auth.appSid}"
    @_paPlsCtl="_paPlsCtl:#{url}:#{@_auth.loginId}:#{@_auth.appSid}"
    s=@
    ph.pa._storDecrypt(localStorage,@_auth,@_paPlsCtl,(decText)->
      if decText
        s.ctrl=ph.JSON.parse(decText)
      else
        s.ctrl={}
      )
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
