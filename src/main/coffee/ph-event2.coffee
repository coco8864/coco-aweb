#-------------------EventModule-------------------
class EventModule
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
  _callCheck:(funcName,funcThis,args...)->
    if @_stat=='@loading'
      _this=@
      @one('@load',->_this[funcName].apply(funcThis,args))
      return false
    else if @_stat=='@load'
      return true
    else
      throw 'stat error:'+@_stat


class TE extends EventModule
  constructor:->
    super
  test:(a,b,c)->
    if !@_callCheck('test',this,a,b,c)
      return
    alert(a+b+c)

window.te=new TE()

