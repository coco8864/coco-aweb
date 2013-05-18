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
    @
  getItem:(key)->
    @data[key]
  setItem:(key,value)->
    oldValue=@data[key]
    @data[key]=value
    @trigger(key,{key:key,oldValue:oldValue,newValue:value})
    s=@
    @_auth.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
    return
  removeItem:(key)->
    oldValue=@data[key]
    if !oldValue
      return
    delete @data[key]
    @trigger(key,{key:key,oldValue:oldValue})
    s=@
    @_auth.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
    return
  _unload:=>
    if @encText
      sessionStorage.setItem(@_paPss,@encText)
    @trigger('save',@)
  _remove:->
    sessionStorage.removeItem(@_paPss)
    @trigger('remove',@)

#-------------------Session,Apl PrivateStorage-------------------
class PrivateLocalStorage extends ph.EventModule
  constructor:(url,@_auth,@_bid,@_isSession)->
    super
    @_status=0 #まだデータが準備できてないの意
    if @_isSession
      uniqueName="#{url}:#{@_auth.loginId}:#{@_auth.appSid}"
    else
      uniqueName="#{url}:#{@_auth.loginId}"
    @_paPls="_paPls:#{uniqueName}"
    @_paPlsChannel="_paPlsChannel:#{uniqueName}"
    @_paPlsCtl="_paPlsCtl:#{uniqueName}"
    ph.event.on('storage',@_onStorage)

    ctlText=localStorage.getItem(@_paPlsCtl)
    if ctlText
      ctl=ph.JSON.parse(ctlText)
      ctl.connectBids.push(@_bid)
    else
      ctl={connectBids:[@_bid]}
    ctl.action='in'
    ctl.bid=@_bid
    localStorage.setItem(@_paPlsCtl,ph.JSON.stringify(ctl))
    #一番最初のpageであればdataを自力で読む、最初じゃなければイベント経由
    @_ctl=ctl
    if ctl.connectBids.length==1
      encDataText=localStorage.getItem(@_paPls)
      if !encDataText
        @data={}
        @_status=1
        return
      @_decData(encDataText)
    @
  getItem:(key)->
    @data[key]
  setItem:(key,value)->
    oldValue=@data[key]
    @data[key]=value
    updateInfo={req:'setItem',key:key,oldValue:oldValue,newValue:value}
    @_updateData(updateInfo)
    @trigger(key,updateInfo)
    return
  removeItem:(key)->
    oldValue=@data[key]
    if !oldValue
      return
    delete @data[key]
    updateInfo={req:'removeItem',key:key,oldValue:oldValue}
    @_updateData(updateInfo)
    @trigger(key,updateInfo)
    return
  _decData:(encText)->
    if !envText
      @_encDataText='{}'
      @data={}
      if @_status==0
        @trigger('dataLoad')
        @_status=1
      return
    @_encDataText=encText
    s=@
    @_auth.decrypt(@_encDataText,(decText)->
      if decText
        s.data=ph.JSON.parse(decText)
      else
        s.data={}
      if s._status==0
        s.trigger('dataLoad')
        s._status=1
      return
      )
  _unload:=>
    ph.event.off('storage',@_onStorage)
    if @_ctl.connectBids.length==1 && @_ctl.connectBids[0]==@_bid
      localStorage.removeItem(@_paPlsCtl)
      localStorage.setItem(@_paPls,@_encDataText) #同時にcloseした場合...
      return
    @_ctl.action='out'
    @_ctl.bid=@_bid
    for i in [@_ctl.connectBids.length-1..0]
      if @_ctl.connectBids[i]==@_bid
        list.splice(i,1)
    localStorage.setItem(@_paPlsCtl,ph.JSON.stringify(@_ctl))
  _updateData:(updateInfo)->
    @_encData()
    ##他に共有しているpageがなければ変更イベントは発生させない
    if @_ctl.connectBids.length==1
      return
    localStorage.setItem(@_paPlsChannel,ph.JSON.stringify(updateInfo))
    localStorage.removeItem(@_paPlsChannel)
  _encData:->
    dataText=ph.JSON.stringify(@data)
    s=@
    @_auth.encrypt(dataText,(encText)->s._encDataText=encText)
  ##scope:sessionPrivateの時セション切れの時用
  _remove:->
    localStorage.removeItem(@_paPls)
    localStorage.removeItem(@_paPlsCtl)
    @trigger('remove',@)
  _onStorage:(ev)=>
    if ev.key==@_paPls
      @_decData(ev.newValue)
    else if ev.key==@_paPlsChannel && ev.newValue
      updateInfo=ph.JSON.parse(ev.newValue)
      if updateInfo.req=='setItem'
        @data[updateInfo.key]=updateInfo.newValue
      else if updateInfo.req=='removeItem'
        delete @data[updateInfo.key]
      else
        return
      @_encData()
    else if ev.key==@_paPlsCtl && ev.newValue
      @_ctl=ph.JSON.parse(ev.newValue)
      if @_ctl.action=='connect' && @_ctl.connectBids[0]==@_bid
        localStorage.setItem(@_paPls,@_encDataText)

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
