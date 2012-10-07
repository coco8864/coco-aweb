class StatusView extends Backbone.View
  constructor:(@stastics)->
    super
  el:'#statusView'
  events:
    'click #stasticsCheck':'watch'
  initialize:(options)->
    @stastics.bind('update',@onUpdate)
    ph.log("StatusView inititalize")
  render:->
    ph.log("StatusView render")
  watch:(e)=>
    if($('#stasticsCheck').attr("checked"))
      @stastics.start()
    else
      @stastics.stop()
    ph.log("StatusView click")
  onUpdate:(stcs)->
    $("#broadcasterTime").text("("+stcs.counter+")")
    memory=stcs.memory
    useRate=(memory.max-memory.free)*100.0/memory.max
    $("#broadcasterMemory").text(memory.free+"/"+memory.max+"("+useRate.toFixed(2)+"%)")
    channelStastics=stcs.channelStastics
    $("#broadcasterIo").text(channelStastics.asyncAcceptCount+":" +
				channelStastics.onAcceptedCount+":" +
				channelStastics.onConnectedCount+":" +
				channelStastics.asyncReadCount+"(" +
				channelStastics.onReadLength+"):" +
				channelStastics.asyncWriteCount+"(" +
				channelStastics.asyncWriteLength+")" )
    selectorStasticses=stcs.selectorStasticses
    selectText=""
    for i in [0..selectorStasticses.length-1]
        s=selectorStasticses[i];
        selectText+=s.loopCount+"("+s.selectCount+"):"
    $("#broadcasterSelector").text(selectText)
    storeStack=stcs.storeStack
    $("#broadcasterStore").text(storeStack.join(":"))
    channelContext=stcs.channelContext
    cur=(channelContext.total-channelContext.poolBack-channelContext.gc)
    $("#broadcasterChannel").text(cur+"("+channelContext.total+"-"+channelContext.instance+")")
    requestContext=stcs.requestContext
    cur=(requestContext.total-requestContext.poolBack-requestContext.gc)
    $("#broadcasterRequest").text(cur+"("+requestContext.total+"-"+requestContext.instance+")")
    authSession=stcs.authSession
    cur=(authSession.total-authSession.poolBack-authSession.gc)
    $("#broadcasterSession").text(cur+"("+authSession.total+"-"+authSession.instance+")")

