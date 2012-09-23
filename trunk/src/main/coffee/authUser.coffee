# 認証情報を保持する、ログイン時に取得し、SessionStrageに保持
class AuthUser extends Backbone.Model
  initialize: ->
    if @done==true
      @user=null #TODO get from SessionStrage
      @trigger("done",@user)
      return
    ph.auth.info null,(res)=>
      if res.result==true
        @user=res.user
        @trigger("done",@user)
        #TODO put to SessionStrage
      else
        alert('fail to auth.info.'+res.reason)
      @done=true
    @done=false
  defaults: {
    user: null
  }

  