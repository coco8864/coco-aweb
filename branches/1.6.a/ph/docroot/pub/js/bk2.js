alert('start:' + location.href);
setTimeout(
  function(){
    ph.wsq.open(
      'ws://127.0.0.1:1280/wsq',
      function(con){
        con.publish('test',document.body.innerHTML);
//        con.publish('test','testtest');
	alert('end:' + location.href);
//        con.close();
      }
    );
  },100);
