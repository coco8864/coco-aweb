ph["authUrl"]="$esc.javascript(${config.authUrl})";
ph["adminUrl"]="$esc.javascript(${config.adminUrl})";
ph["publicWebUrl"]="$esc.javascript(${config.publicWebUrl})";
ph["websocketSpec"]="$esc.javascript(${config.getString("websocketSpecs")})";
ph["isSpdyAvailable"]=${config.getSpsyConfig().spdyAvailable};
ph["spdyProtocols"]="$esc.javascript(${config.getSpsyConfig().spdyProtocols})";
ph["spdyInfo"]="$esc.javascript(${handler.getSpdySession().spdyInfo()})";
ph["cid"]=${handler.channelId};
ph["authFrameTimeout"]=$esc.javascript(${config.getString('authFrameTimeout','5000')});
ph["webSocketMessageLimit"]=$esc.javascript(${config.getString("webSocketMessageLimit", '(1024*1024*2)')});

if(ph.useWebSocket && !ph.websocketSpec)
 ph.useWebSocket=false;
ph.isOffline=false;
ph.load();

