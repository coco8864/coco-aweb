alert('start:' + location.href);
ph.wsq.open(
      'ws://127.0.0.1:1280/wsq',
      function(con){
        ph.log('++open cb++ message:'+con.message);
        ph.dump1(con);
        ph.log('--open cb--');
        con.publish('test',document.body.innerHTML);
      }
);
