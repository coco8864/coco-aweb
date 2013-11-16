#strage scope
#  SCOPE_PAGE_PRIVATE:'pagePrivate' ...そのページだけ、reloadを挟んで情報を維持するため
#  SCOPE_SESSION_PRIVATE:'sessionPrivate'...開いている同一セションのwindow間で情報を共有
#  SCOPE_APL_PRIVATE:'aplPrivate'...当該apl当該userの情報を保持
#  SCOPE_APL_LOCAL:'aplLocal'...当該aplの情報を保持
#  SCOPE_APL:'apl'
#  SCOPE_QNAME:'qname'
#  SCOPE_SUBNAME:'subname'
#  SCOPE_USER:'user'
#-------------------PagePrivateStorage-------------------
class PrivateSessionStorage extends PhObject
 ###
 \#\#\#\# SrorageAPIのメインクラス
 Link.storageメソッドの復帰値として取得.PAGE_PRIVATE scopeの復帰オブジェクト.
 APIは、その他のscopeのstorageと同じ.getItemを含めてイベントもしくはcallback経由の非同期APIとなっている.

 \#\#\#\# イベント
 *  **ph.EVENT.GET_ITEM:** getItemメソッドの復帰情報を通知
 *   通知={key:'key1',value:'valu1'},ユーザ指定ctx
 *  **ph.EVENT.KEYS:** keysメソッドの復帰情報を通知
 *   通知={keys:['key1','key2'...]},ユーザ指定ctx
 *  **ph.EVENT.CHANGE_ITEM:** 他pageでの値変更を通知
 *   通知={key:'key1',value:'valu1',newValue:'valu1',oldValue:'value2'}
 *  **ph.STAT_UNLOAD:** このオブジェクトの終了を通知
 ###
 constructor:(@link)->
  ###利用しない.Link.storageから呼び出される。###
  super
  @link.on(ph.EVENT.LOGIN,@_init)
 _init:=>
  if !@link.isAuth
    return
  aplInfo=@link.aplInfo
  @_linkPss="&#{@link.keyUrl}:#{aplInfo.loginId}:#{aplInfo.appSid}"
  ##不要なsessionStorageの刈り取り
  sameLoginIdKey="&#{@link.keyUrl}:#{aplInfo.loginId}"
  i=sessionStorage.length
  while (i-=1) >=0
   key=sessionStorage.key(i)
   if key.lastIndexOf(sameLoginIdKey,0)==0 && key!=@_linkPss
    sessionStorage.removeItem(key)
  #_storDecrypt:(storage,key,cb)->
  @encText=sessionStorage.getItem(@_linkPss)
  if @encText
   s=@
   @link.decrypt(@encText,(decText)->
    if decText
     s.data=ph.JSON.parse(decText)
    else
     s.data={}
    s.load()
    s.onUnload(s._onUnload)
   )
  else
   @data={}
   @load()
   @onUnload(@_onUnload)
  #ph.one('@unload',@_onUnload) #pageがunloadされるときsessionStorageに残す
 getItem:(key,ctx)->
  ###
  \#\#\#\#\# 当該storageの指定したkeyに対するvalueを取得します.
   -   **key:** 指定するkey
   -   **ctx:** メソッドの場合は、このメソッドにvalueを通知します.
       それ以外の場合は、当該storageオブジェクトのph.EVENT.GET_ITEMイベントで通知します。
  ###
  s=@
  @onLoad(->s._getItem(key,ctx))
 _getItem:(key,ctx)->
  if typeof(ctx)=='function'
   ctx(@data[key])
  else
   data={key:key,scope:ph.SCOPE.PAGE_PRIVATE,value:@data[key]}
   @trigger(key,data,ctx)
   @trigger(ph.EVENT.GET_ITEM,data,ctx)
  @data[key]
 keys:(ctx)->
  ###
  \#\#\#\#\# 当該storageのkey一覧を取得します.
   -   **ctx:** メソッドの場合は、このメソッドにkey一覧を通知します.
       それ以外の場合は、当該storageオブジェクトのph.EVENT.KEYSイベントで通知します。
  ###
  s=@
  @onLoad(->s._keys(ctx))
 _keys:(ctx)->
  if typeof(Object.keys)=='function'
   keys=Object.keys(@data)
  else
   keys=[]
   for key,value of @data
    keys.push(key)
  if typeof(ctx)=='function'
   ctx(keys)
  else
   data={scope:ph.SCOPE.PAGE_PRIVATE,keys:keys}
   @trigger(ph.EVENT.KYES,data,ctx)
  keys
 setItem:(key,value)->
  ###
  \#\#\#\#\# 当該storageの指定したkeyに指定したvalueを設定します.
   -   **key:** 指定するkey
   -   **value:** 設定するvalue
  ###
  s=@
  @onLoad(->s._setItem(key,value))
 _setItem:(key,value)->
  oldValue=@data[key]
  @data[key]=value
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 removeItem:(key)->
  ###
  \#\#\#\#\# 当該storageの指定したkey項目を削除します.
   -   **key:** 指定するkey
  ###
  s=@
  @onLoad(->s._removeItem(key))
 _removeItem:(key)->
  oldValue=@data[key]
  if !oldValue
   return
  delete @data[key]
  s=@
  @link.encrypt(ph.JSON.stringify(@data),(text)->s.encText=text)
  return
 _onUnload:=>
  # ph.off('unload',@_unload)
  if @encText
   sessionStorage.setItem(@_linkPss,@encText)
  @trigger('@save',@)
