(function(){
ph.log('bm.js 1');
  var cd=ph.pa.connect('/admin',function(){
//  cd.publish('test',document.body.innerHTML);
ph.log('bm.js 2');
  var st=cd.storage(ph.pa.SCOPE_SESSION_PRIVATE);
  setTimeout(function(){st.setItem('test1',document.body.innerHTML);
//  cd.storage(ph.pa.SCOPE_APL_PRIVATE).setItem('test2',document.body.innerHTML);
//  cd.storage(ph.pa.SCOPE_APL_LOCAL).setItem('test3',document.body.innerHTML);
ph.log('bm.js 3');
  cd.close();
ph.log('bm.js 4');
    },1000);
  });
})();
