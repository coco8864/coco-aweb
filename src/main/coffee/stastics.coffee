# �F�؏���ێ�����A���O�C�����Ɏ擾���ASessionStrage�ɕێ�
class Stastics extends Backbone.Model
  initialize: ->
    ph.wsq.open('/wsq'
      ,(con)=>
        @con=con
        con.deploy('stastics','naru.aweb.wsadm.StasticsWsqlet')
      ,(con)=>
        ph.log('open event:'+con.cbType+':'+con.cause)
    )
  start:->
   @con.subscribe('stastics',@onSubscribe)
  stop:->
   @con.unsubscribe('stastics')
  onSubscribe:(msg)=>
   @msg=msg
   @trigger("update",msg)
  defaults: {
    con: null
    msg: null
  }
