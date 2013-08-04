OFFLINE_KEY='PaOfflineAuth'
parentOrigin=null
userInfo=null
cdr={isIn:false,req:null}

onlineInfo=(req)->
  jQuery.ajax({
    type:'POST',
    url:'userInfo',
    dataType:'json',
    data:req,
    success:((x)->
      userInfo=x
      response({type:'refresh',result:true})),
    error:((x)->
      response({type:'refresh',result:false}))
  })

onRequest=(req)->
 if cdr.isIn
  throw "duplicate request"
 if aplAuthInfo.result==false
  throw "fail to load"
 cdr.isIn=true
 cdr.req=req
 if req.type=="encrypt"
  if userInfo.offlinePassHash
   req.encryptText=CryptoJS.AES.encrypt(req.plainText, offlinePassHash).toString()
  else
   req.encryptText=req.plainText
  response(req);
 else if req.type=="decrypt"
  if userInfo.offlinePassHash
   req.plainText=CryptoJS.AES.decrypt(req.encryptText, offlinePassHash).toString(CryptoJS.enc.Utf8)
  else
   req.plainText=req.encryptText
  response(req);
 else if req.type=="logout"
  jQuery.ajax({
        type:'GET',
        url:'ajaxLogout',
        dataType:'json',
        success:(x)->response(x),
        error:(x)->response({type:'logout',result:false})
    })
 else if req.type=="info"
  response(userInfo.authInfo)
 else if req.type=="load"
  onlineInfo(req)

onMsg=(qjev)->
 ev=qjev.originalEvent
 if !ev.data
  return
 if ev.source==parent
  req=ph.JSON.parse(ev.data)
  onRequest(req)

response=(msg)->
 cdr.isIn=false
 cdr.req=null
 _response(msg)

_response=(msg)->
 jsonMsg=ph.JSON.stringify(msg)
 if window==parent #テスト時に自分には投げない処理
  alert('response to parent:'+jsonMsg)
 else
  parent.postMessage(jsonMsg,'*')

jQuery(->
 parentOrigin=decodeURIComponent(location.search.substring('?origin='.length))
 if parentOrigin=='file://'
  parentOrigin='*'
 jQuery(window).on('message',onMsg)
 _response({type:'load',result:true})
)

