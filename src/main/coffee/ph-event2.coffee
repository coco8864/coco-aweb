#-------------------EventModule-------------------
class EventModule2
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
  checkCall:(func,args...)->
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
      throw 'stat error:'+@_stat
  isUnload:->
    @_stat=='@unload'

window.ph.EventModule2=EventModule2
