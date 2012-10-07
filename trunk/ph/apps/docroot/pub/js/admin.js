var AuthUser,
  __hasProp = Object.prototype.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor; child.__super__ = parent.prototype; return child; };

AuthUser = (function(_super) {

  __extends(AuthUser, _super);

  function AuthUser() {
    AuthUser.__super__.constructor.apply(this, arguments);
  }

  AuthUser.prototype.initialize = function() {
    var _this = this;
    if (this.done) {
      this.user = null;
      this.trigger("done", this.user);
      return;
    }
    ph.auth.info(function(res) {
      if (res.result) {
        _this.user = res.user;
        _this.trigger("done", _this.user);
      } else {
        ph.log('fail to auth.info.' + res.reason);
      }
      return _this.done = true;
    });
    return this.done = false;
  };

  AuthUser.prototype.defaults = {
    user: null
  };

  return AuthUser;

})(Backbone.Model);

var phwa;

phwa = {
  TAB_INDEXS: {
    '#statusTab': 0,
    '#settingTab': 1
  },
  TAB_HASH_KEY: 'userTabHash.xxx',
  STASTICS_QN: 'stastics'
};

window.onhashchange = function() {
  var hash, index, _ref;
  hash = (_ref = window.location.hash) != null ? _ref : sessionStorage[phwa.TAB_HASH_KEY];
  sessionStorage[phwa.TAB_HASH_KEY] = hash;
  index = phwa.TAB_INDEXS[hash];
  if (index == null) index = 0;
  if (index === 8) $('#debugLi').show();
  return $('#tabs').tabs('select', index);
};

ph.jQuery(function() {
  ph.debug = true;
  phwa.authUser = new AuthUser();
  phwa.statusView = new StatusView(new Stastics());
  ph.log(phwa.statusView.el);
  phwa.authUser.bind("done", function(user) {
    ph.log(ph.JSON.stringify(user));
    return $('#loginid').text(user.loginId);
  });
  $('#tabs').tabs({
    cache: true,
    ajaxOptions: {
      error: function(xhr, status, index, anchor) {
        return ph.log("tabs ajaxOptions error");
      },
      success: function(xhr, status, index, anchor) {
        return ph.log("tabs ajaxOptions success.index:" + index(+":anchor:" + anchor));
      }
    },
    select: function(event, ui) {
      return window.location.hash = ui.tab.hash;
    }
  });
  return window.onhashchange();
});

var Stastics,
  __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; },
  __hasProp = Object.prototype.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor; child.__super__ = parent.prototype; return child; };

Stastics = (function(_super) {

  __extends(Stastics, _super);

  function Stastics() {
    this.onSubscribe = __bind(this.onSubscribe, this);
    Stastics.__super__.constructor.apply(this, arguments);
  }

  Stastics.prototype.initialize = function() {
    var _this = this;
    ph.wsq.open('/wsq', function(con) {
      _this.con = con;
      con.deploy(phwa.STASTICS_QN, 'naru.aweb.wsadm.StasticsWsqlet');
      return _this;
    }, function(con) {
      ph.log('open event:' + con.cbType + ':' + con.cause);
      return _this;
    });
    return this;
  };

  Stastics.prototype.start = function() {
    return this.con.subscribe(phwa.STASTICS_QN, this.onSubscribe);
  };

  Stastics.prototype.stop = function() {
    return this.con.unsubscribe(phwa.STASTICS_QN);
  };

  Stastics.prototype.onSubscribe = function(msg) {
    this.msg = msg;
    return this.trigger("update", msg);
  };

  Stastics.prototype.defaults = {
    con: null,
    msg: null
  };

  return Stastics;

})(Backbone.Model);

var StatusView,
  __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; },
  __hasProp = Object.prototype.hasOwnProperty,
  __extends = function(child, parent) { for (var key in parent) { if (__hasProp.call(parent, key)) child[key] = parent[key]; } function ctor() { this.constructor = child; } ctor.prototype = parent.prototype; child.prototype = new ctor; child.__super__ = parent.prototype; return child; };

StatusView = (function(_super) {

  __extends(StatusView, _super);

  function StatusView(stastics) {
    this.stastics = stastics;
    this.watch = __bind(this.watch, this);
    StatusView.__super__.constructor.apply(this, arguments);
  }

  StatusView.prototype.el = '#statusView';

  StatusView.prototype.events = {
    'click #stasticsCheck': 'watch'
  };

  StatusView.prototype.initialize = function(options) {
    this.stastics.bind('update', this.onUpdate);
    return ph.log("StatusView inititalize");
  };

  StatusView.prototype.render = function() {
    return ph.log("StatusView render");
  };

  StatusView.prototype.watch = function(e) {
    if ($('#stasticsCheck').attr("checked")) {
      this.stastics.start();
    } else {
      this.stastics.stop();
    }
    return ph.log("StatusView click");
  };

  StatusView.prototype.onUpdate = function(stcs) {
    var authSession, channelContext, channelStastics, cur, i, memory, requestContext, s, selectText, selectorStasticses, storeStack, useRate, _ref;
    $("#broadcasterTime").text("(" + stcs.counter + ")");
    memory = stcs.memory;
    useRate = (memory.max - memory.free) * 100.0 / memory.max;
    $("#broadcasterMemory").text(memory.free + "/" + memory.max + "(" + useRate.toFixed(2) + "%)");
    channelStastics = stcs.channelStastics;
    $("#broadcasterIo").text(channelStastics.asyncAcceptCount + ":" + channelStastics.onAcceptedCount + ":" + channelStastics.onConnectedCount + ":" + channelStastics.asyncReadCount + "(" + channelStastics.onReadLength + "):" + channelStastics.asyncWriteCount + "(" + channelStastics.asyncWriteLength + ")");
    selectorStasticses = stcs.selectorStasticses;
    selectText = "";
    for (i = 0, _ref = selectorStasticses.length - 1; 0 <= _ref ? i <= _ref : i >= _ref; 0 <= _ref ? i++ : i--) {
      s = selectorStasticses[i];
      selectText += s.loopCount + "(" + s.selectCount + "):";
    }
    $("#broadcasterSelector").text(selectText);
    storeStack = stcs.storeStack;
    $("#broadcasterStore").text(storeStack.join(":"));
    channelContext = stcs.channelContext;
    cur = channelContext.total - channelContext.poolBack - channelContext.gc;
    $("#broadcasterChannel").text(cur + "(" + channelContext.total + "-" + channelContext.instance + ")");
    requestContext = stcs.requestContext;
    cur = requestContext.total - requestContext.poolBack - requestContext.gc;
    $("#broadcasterRequest").text(cur + "(" + requestContext.total + "-" + requestContext.instance + ")");
    authSession = stcs.authSession;
    cur = authSession.total - authSession.poolBack - authSession.gc;
    return $("#broadcasterSession").text(cur + "(" + authSession.total + "-" + authSession.instance + ")");
  };

  return StatusView;

})(Backbone.View);

