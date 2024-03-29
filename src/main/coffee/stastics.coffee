# 認証情報を保持する、ログイン時に取得し、SessionStrageに保持
class Stastics extends Backbone.Model
  initialize: ->
    ph.wsq.open('/wsq'
      ,(con)=>
        @con=con
        con.deploy(phwa.STASTICS_QN,'naru.aweb.wsadm.StasticsWsqlet')
        ph.log('wsq open:'+con.cbType+':'+con.causeType)
        @
      ,(con)=>
        ph.log('wsq event:'+con.cbType+':'+con.causeType)
        @
      )
    @
  start:->
   @con.subscribe(phwa.STASTICS_QN,@onSubscribe)
  stop:->
   @con.unsubscribe(phwa.STASTICS_QN)
  onSubscribe:(msg)=>
   @msg=msg
   @trigger("update",msg)
  defaults: {
    con: null
    msg: null
  }
