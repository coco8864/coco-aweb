#-------------------EventModule-------------------
class EventModule
  constructor:->
    @_callback ={}
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
      if list[i]==callback
        list.splice(i,1)
    @
  trigger: (name,args...) ->
    list = @_callback[name]
    return @ unless list
    for callback in list
      callback.apply(@,args)
    @
  checkState:->
    if @deferred.state()!='pending'
      throw 'state error:'+@deferred.state()

window.ph.EventModule=EventModule
window.ph.event=new ph.EventModule();
window.ph.messageCb=(ev)->window.ph.event.trigger('message',ev)
window.ph.unloadCb=(ev)->window.ph.event.trigger('unload',ev)
window.ph.strageCb=(ev)->window.ph.event.trigger('strage',ev)

if window.addEventListener
  window.addEventListener('message',window.ph.messageCb, false)
else if window.attachEvent
  window.attachEvent('onmessage',window.ph.messageCb)
ph.jQuery(window).unload(window.ph.unloadCb)
ph.jQuery(window).bind("storage",window.ph.strageCb)

