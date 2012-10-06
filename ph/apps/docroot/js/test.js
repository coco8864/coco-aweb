(function() {
  var template;

  ph.jQuery(function() {
    return $('#tabs').tabs({
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
  });

  template = _.template("aa<%=aaa%>aa");

  ph.log(template({
    aaa: '1111'
  }));

}).call(this);
