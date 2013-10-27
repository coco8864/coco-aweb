#-------------------Subscription-------------------
class Subscription extends PhObject
  ###
  \#\#\#\# pubsubAPIのメインクラス
  Link.subscribeメソッドの復帰値として取得.このオブジェクトを通じてサーバからのデータを送受信する.
  送受信するデータには、JSONでは表現できないDateオブジェクトやBlobオブジェクトを含める事ができる.

  \#\#\#\# イベント
  *  **ph.EVENT.MESSAGE:** サーバからのmessage受信を通知
  *  **ph.STAT_UNLOAD:** このオブジェクトの終了を通知,onUnloadと同義
  ###
  constructor:(@_con,@qname,@subname)->
    ###利用しない.Link.subscribeから呼び出される。###
    super
    @_con.onLoad(@_connectionOnLoad)
    @
  _connectionOnLoad:=>
    @_con._send({type:ph.TYPE_SUBSCRIBE,qname:@qname,subname:@subname})
    @load()
    return
  unsubscribe:->
    ### subscribe終了 ###
    @onLoad(@_unsubscribe)
  _unsubscribe:=>
    @_con._send({type:ph.TYPE_UNSUBSCRIBE,qname:@qname,subname:@subname})
  publish:(msg)->
    ### 指定したmsgオブジェクトをサーバに送信 ###
    @onLoad(@_publish,msg)
  _publish:(msg)=>
    @_con._send({type:ph.TYPE_PUBLISH,qname:@qname,subname:@subname,message:msg})
  publishForm:(formId)->
    ###
    \#\#\#\#\# formに含まれるデータを、指定したqname,dubnameにsubscribeします.
    指定したformデータをサーバに送信,Blobをサポートしていないブラウザからでもファイルデータが送信できる.
     -   **formId:** publishするformタグのid属性
    ###
    @onLoad(@_publishForm,formId)
  _publishForm:(formId)->
    form=ph.jQuery('#'+formId)
    if form.length==0 || form[0].tagName!='FORM'
      throw 'not form tag id'
    form.attr("method","POST")
    form.attr("enctype","multipart/form-data")
    form.attr("action","#{@_con.connectXhrUrl}/~upload")
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
  onMsg:(cb)->
    ### message通知メソッドを登録.sub.on(ph.EVENT.MESSAGE,cb)と同義. ###
    @on(ph.EVENT.MESSAGE,cb)
