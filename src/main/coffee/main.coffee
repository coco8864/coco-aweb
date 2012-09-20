tabIndexs = {'#statusTab' : 0, '#settingTab' : 1}
userTabHashKey='userTabHash.xxx'
window.onhashchange = ->
# ���݂�hash����C���f�b�N�X�̒l���擾
  hash = window.location.hash ? sessionStorage[userTabHashKey]
  sessionStorage[userTabHashKey]=hash
  index = tabIndexs[hash]
  # hash�������ꍇ�͍ŏ��̃^�u��I��
  index ?= 0
  if index==8 # if debug
    $('#debugLi').show()
  # ���݂̃C���f�b�N�X�Ń^�u���Z���N�g
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

