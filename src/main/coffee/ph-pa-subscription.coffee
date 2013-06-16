#-------------------Subscription-------------------
class Subscription extends ph.EventModule
  constructor:->
    super
    @_ready=ph.jQuery.Deferred()
  _init:(@_con,@deferred,@qname,@subname)->
    @_ready.resolve()
    @_con._send({type:ph.pa.TYPE_SUBSCRIBE,qname:@qname,subname:@subname})
  unsubscribe:->
    sub=@
    @_ready.done(->sub._unsubscribe())
  _unsubscribe:->
    @checkState()
    @_con._send({type:ph.pa.TYPE_UNSUBSCRIBE,qname:@qname,subname:@subname})
  publish:(msg)->
    sub=@
    @_ready.done(->sub._publish(msg))
  _publish:(msg)->
    @checkState()
    @_con._send({type:ph.pa.TYPE_PUBLISH,qname:@qname,subname:@subname,message:msg})
  publishForm:(formId)->
    sub=@
    @_ready.done(->sub._publishForm(formId))
  _publishForm:(formId)->
    @checkState()
    form=ph.jQuery('#'+formId)
    if form.length==0 || form[0].tagName!='FORM'
      throw 'not form tag id'
    form.attr("method","POST")
    form.attr("enctype","multipart/form-data")
    form.attr("action","#{@_con.httpUrl}/~paUpload")
    form.attr("target","#{@_con._downloadFrameName}")
    bidInput=ph.jQuery("<input type='hidden' name='bid' value='#{@_con._getBid()}'/>")
    tokenInput=ph.jQuery("<input type='hidden' name='token' value='#{@_con._token}'/>")
    qnameInput=ph.jQuery("<input type='hidden' name='qname' value='#{@qname}'/>")
    subnameInput=ph.jQuery("<input type='hidden' name='subname' value='#{@subname}'/>")
    form.append(bidInput)
    form.append(tokenInput)
    form.append(qnameInput)
    form.append(subnameInput)
    form.submit()
    form[0].reset()
    bidInput.remove()
    tokenInput.remove()
    qnameInput.remove()
    subnameInput.remove()
  onMessage:(cb)->
    sub=@
    @_ready.done(->sub._onMessage(cb))
  _onMessage:(cb)->
    @on(ph.pa.TYPE_MESSAGE,cb)
