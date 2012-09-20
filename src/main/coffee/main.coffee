tabIndexs = {'#statusTab' : 0, '#settingTab' : 1}
userTabHashKey='userTabHash.xxx'
window.onhashchange = ->
# 現在のhashからインデックスの値を取得
  hash = window.location.hash ? sessionStorage[userTabHashKey]
  sessionStorage[userTabHashKey]=hash
  index = tabIndexs[hash]
  # hashが無い場合は最初のタブを選択
  index ?= 0
  if index==8 # if debug
    $('#debugLi').show()
  # 現在のインデックスでタブをセレクト
  $('#tabs').tabs('select', index)

ph.jQuery ->
  auth = new Auth({})
  auth.bind "done",(user) ->
    alert(ph.JSON.stringify(user))
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

