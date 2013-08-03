
authFrame=null
workFrame=null
cdr={isIn:false,req:null}

ONLINE_CHECK_AUTH_URL='?__PH_AUTH__=__XHR_CHECK__'

onlineCheckAuthInfo=->
 jQuery.ajax({
  type:'POST',
  url:ONLINE_CHECK_AUTH_URL+'&xhr=true',
##    dataType:'json',
  success:(x)->alert(x),
  error:(x)->alert(x)
 })

onMsg=(qjev)->
 ev=qjev.originalEvent
 if !ev.data
  return
 if ev.source==parent
  req=ph.JSON.parse(ev.data)
  onRequest(req)
 else if ev.source==authFrame[0].contentWindow
  res=ph.JSON.parse(ev.data)
  onAuthResponse(res)
 else if ev.source==workFrame[0].contentWindow
  res=ph.JSON.parse(ev.data)
  onWorkResponse(res)

onAuthResponse=(req)->

onWorkResponse=(req)->

onRequest=(req)->
 if cdr.isIn
  throw "duplicate request"##parentからresponseを待たずに続けてrequestされた（異常)
 if req.type=='onlineAuth'
  alert('onlineAuth')
 if req.type=='offlineAuth'
  alert('offlineAuth')
 if req.type=='encrypt'
  alert('encrypt')
 if req.type=='decrypt'
  alert('decrypt')
 if req.type=='logout'
  alert('logout')

response=(msg)->
 cdr.isIn=false
 cdr.req=null
 _response(msg)

_response=(msg)->
 jsonMsg=ph.JSON.stringify(msg)
 parent.postMessage(jsonMsg,'*')

jQuery(->
 jQuery(window).on('message',onMsg)
 authFrame=jQuery(
      "<iframe " +
      "style='frameborder:no;background-color:#CFCFCF;overflow:auto;height:0px;width:0px;position:absolute;top:0%;left:0%;margin-top:-100px;margin-left:-150px;' " +
      "name='aplOfflineAuth#{@location.href}' >"+
      "</iframe>")
 workFrame=jQuery(
      "<iframe width='0' height='0' frameborder='no' "+
      "name='aplOfflineWork#{@location.href}' >"+
      "</iframe>")
 jQuery("body").append(workFrame)
 jQuery("body").append(authFrame)
 onlineCheckAuthInfo()
)
