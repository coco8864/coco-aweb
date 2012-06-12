//alert('start:' + location.href);
setTimeout(
  function(){
    ph.wsq.open(
      'ws://127.0.0.1:1280/wsq',
      function(con){
          if(con.cbType!=='INFO'||con.message!=='opened'){
            return;
          }
//        var res=ph.JSON.stringify(con);
//        alert(res);
        con.publish('test',document.body.innerHTML);
//alert(document.body.innerHTML);
//        con.publish('test',document.title);
//	alert('end:' + location.href);
        con.close();
      }
    );
  },100);
