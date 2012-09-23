class StatusView extends Backbone.View
  el:"#statusView"
  events:
    "click":"click"
  initialize:(options)->
    ph.log("StatusView inititalize")
  render:->
    ph.log("StatusView render")
  click:(e)->
    ph.log("StatusView click")
