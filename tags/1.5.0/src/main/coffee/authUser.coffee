# �F�؏���ێ�����A���O�C�����Ɏ擾���ASessionStrage�ɕێ�
class AuthUser extends Backbone.Model
  initialize: ->
    if @done
      @user=null #TODO get from SessionStrage
      @trigger("done",@user)
      return
    ph.auth.info (res)=>
      if res.result
        @user=res.user
        @trigger("done",@user)
        #TODO put to SessionStrage
      else
        ph.log('fail to auth.info.'+res.reason)
      @done=true
    @done=false
  defaults:
    user: null
