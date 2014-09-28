#set($spdySession=${handler.getSpdySession()})
#if($spdySession)
#set($spdyInfo=$esc.javascript($spdySession.spdyInfo()))
#else
#set($spdyInfo="")
#end
ph["authUrl"]="$esc.javascript(${config.authUrl})";
ph["adminUrl"]="$esc.javascript(${config.adminUrl})";
ph["publicWebUrl"]="$esc.javascript(${config.publicWebUrl})";
ph["websocketSpec"]="$esc.javascript(${config.getString("websocketSpecs")})";
ph["isSpdyAvailable"]=${config.getSpsyConfig().spdyAvailable};
ph["spdyProtocols"]="$esc.javascript(${config.getSpsyConfig().spdyProtocols})";
ph["spdyInfo"]="$spdyInfo";
ph["cid"]=${handler.channelId};
ph["authFrameTimeout"]=$esc.javascript(${config.getString('authFrameTimeout','5000')});
ph["webSocketMessageLimit"]=${config.getInt('webSocketMessageLimit',2048000)};

if(ph.useWebSocket && !ph.websocketSpec)
 ph.useWebSocket=false;
ph.isOffline=false;
ph.load();

