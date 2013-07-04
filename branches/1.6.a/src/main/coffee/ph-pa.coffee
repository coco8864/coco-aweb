if window.ph.pa
  return
window.ph.pa={
  STAT_INIT:'INIT'
  STAT_AUTH:'AUTH'
  STAT_IDLE:'IDLE'
  STAT_NEGOTIATION:'NEGOTIATION'
  STAT_OPEN:'OPEN',
  STAT_LOADING:'LOADING'
  STAT_CONNECT:'CONNECT'
  STAT_CLOSE:'CLOSE'
  CB_INFO:'INFO'
  CB_ERROR:'ERROR'
  CB_MESSAGE:'MESSAGE'
#request type
  TYPE_NEGOTIATE:'negotiate'
  TYPE_PUBLISH:'publish'
  TYPE_SUBSCRIBE:'subscribe'
  TYPE_UNSUBSCRIBE:'unsubscribe'
  TYPE_DEPLOY:'deploy'
  TYPE_UNDEPLOY:'undeploy'
  TYPE_QNAMES:'qnames'
  TYPE_CLOSE:'close'
#response type
  TYPE_RESPONSE:'response'
  TYPE_MESSAGE:'message'
  TYPE_DOWNLOAD:'download'
  RESULT_ERROR:'error'
  RESULT_SUCCESS:'success'
#strage scope
  SCOPE_PAGE_PRIVATE:'pagePrivate'
  SCOPE_SESSION_PRIVATE:'sessionPrivate'
  SCOPE_APL_PRIVATE:'aplPrivate'
  SCOPE_APL_LOCAL:'aplLocal'
  SCOPE_APL:'apl'
  SCOPE_QNAME:'qname'
  SCOPE_SUBNAME:'subname'
  SCOPE_USER:'user'

#  _INTERVAL:1000
  _SEND_DATA_MAX:(1024*1024*2)
  _WS_RETRY_MAX:3
  _KEEP_MSG_BEFORE_SUBSCRIBE:true
  _KEEP_MSG_MAX:64
  _DEFAULT_SUB_ID:'@'
  _DOWNLOAD_FRAME_NAME_PREFIX:'__pa_dl_'
  _XHR_FRAME_NAME_PREFIX:'__pa_xhr_' #xhrPaFrame.vsp�ɓ�����`����
  _XHR_FRAME_URL:'/~xhrPaFrame'
  _connections:{} #key:url value:{deferred:dfd,promise:prm}
# url����ȉ��̎��𔻒f����B
# http�Ŏn�܂�ꍇ�A�K��xhr�ŒʐM����
# ws�Ŏn�܂�ꍇ�Aws�ŒʐM�A���s�����xhr�ŒʐM����
# ���҂łȂ��ꍇ�Aph.js��download����domain�Ɣ��f�A��́Aws���w�肳�ꂽ�ꍇ�Ɠ���
# ##
# connectWsUrl:�K�����݂��āA�Ǘ���̃L�[�Ƃ��Ă����p����
# connectXhrUrl
  connect:(url,conCb,initCon)->
    state=ph.load.state()
    if state=='pending'
      dfd=ph.jQuery.Deferred()
      prm=dfd.promise(new Connection())
      ph.load.always(->ph.pa.connect(url,conCb,{deferred:dfd,promise:prm}))
      return prm
    connectWsUrl=connectXhrUrl=null
    if url.lastIndexOf('ws://',0)==0||url.lastIndexOf('wss://',0)==0
      connectXhrUrl='http' + url.substring(2)
      connectWsUrl=url
    else if url.lastIndexOf('http://',0)==0||url.lastIndexOf('https://',0)==0
      connectXhrUrl=url
    else
      if ph.isSsl
        connectXhrUrl='https://' + ph.domain + url
        connectWsUrl='wss://' + ph.domain + url
      else
        connectXhrUrl='http://' + ph.domain + url
        connectWsUrl='ws://' + ph.domain + url
    ph.log('url:' + url+':connectXhrUrl:'+connectXhrUrl)
    if @_connections[connectXhrUrl]
      prm=@_connections[connectXhrUrl].promise
    else
      if initCon
        dfd=initCon.deferred
        prm=initCon.promise
      else
        dfd=ph.jQuery.Deferred()
        prm=dfd.promise(new Connection())
      prm._init(connectXhrUrl,connectWsUrl,dfd)
      @_connections[url]={deferred:dfd,promise:prm}
    prm._openCount++
    if conCb
      prm.on('connected',conCb)
    prm
  _onUnload:->
    ph.log('onUnload')
    for url,con of ph.pa._connections
      pms=con.promise
      pms.trigger('unload')
  _onStorage:(event)->
    ph.log('onStorage.key:'+event.key)
    for url,con of ph.pa._connections
      pms=con.promise
      pms.trigger('storage',event)
  _xhrOnMessage:(event)->
    for url,con of ph.pa._connections
      c=con.promise
      if !c._xhrFrame
        continue
      if event.source==c._xhrFrame[0].contentWindow
          cd=c
          break
    if !cd
      #���̃C�x���g
      return
    ress=ph.JSON.parse(event.data)
    if !ph.jQuery.isArray(ress)
      if ress.load
        cd._onXhrOpen(ress)
      return
    for res in ress
      cd._onXhrMessage(res)
    return
#  _onTimer:->
  _storDecrypt:(storage,auth,key,cb)->
    encText=storage.getItem(key)
    if encText
      auth.decrypt(encText,(decText)->cb(decText))
    else
      cb(null)
  _encryptStor:(storage,auth,key,value,cb)->
    auth.encrypt(value,(encText)->
      strage.setItem(key,encText)
      if cb
        cb(encText)
      )
}
#xhr�ʐM�p�̃C�x���g�o�^
ph.jQuery(->
  ph.event.on('message',ph.pa._xhrOnMessage)
  ph.event.on('unload',ph.pa._onUnload)
  ph.event.on('storage',ph.pa._onStorage)
)
#setTimeout(ph.pa._onTimer,ph.pa._INTERVAL)
