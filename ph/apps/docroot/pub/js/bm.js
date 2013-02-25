(function(){
  var cd=ph.pa.connect('/pa');
ph.log('bm.js 1');
  cd.publish('test',document.body.innerHTML);
ph.log('bm.js 2');
  cd.close();
ph.log('bm.js 3');
})();
