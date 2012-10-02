## PHantom Wsq Admin
phwa={
  TAB_INDEXS : {'#statusTab' : 0, '#settingTab' : 1}
  TAB_HASH_KEY : 'userTabHash.xxx'
  STASTICS_QN : 'stastics'
}
window.onhashchange = ->
# 現在のhashからインデックスの値を取得
  hash = window.location.hash ? sessionStorage[phwa.TAB_HASH_KEY]
  sessionStorage[phwa.TAB_HASH_KEY]=hash
  index = phwa.TAB_INDEXS[hash]
  # hashが無い場合は最初のタブを選択
  index ?= 0
  if index==8 # if debug
    $('#debugLi').show()
  # 現在のインデックスでタブをセレクト
  $('#tabs').tabs('select', index)

ph.jQuery ->
  ph.debug=true # for debug
  phwa.authUser = new AuthUser()
  ##phwa.stastics = new Stastics()
  phwa.statusView = new StatusView(new Stastics())
  ph.log(phwa.statusView.el)
  phwa.authUser.bind "done",(user) ->
    ph.log(ph.JSON.stringify(user))
    $('#loginid').text(user.loginId)
  $('#tabs').tabs({
    cache:true
    ajaxOptions: {
      error: (xhr, status, index, anchor)->
        ph.log("tabs ajaxOptions error")
      success: (xhr, status, index, anchor)->
        ph.log("tabs ajaxOptions success.index:"+index +":anchor:"+anchor)
    }
    select: (event, ui)->
      window.location.hash = ui.tab.hash
  })
  window.onhashchange()

