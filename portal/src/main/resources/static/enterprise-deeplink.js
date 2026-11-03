(function(){
  function openEnterpriseFromHash(){
    if(location.hash!=='#enterprise')return;
    let attempts=0;
    const open=()=>{
      const button=document.getElementById('enterpriseNav');
      if(button){button.click();return;}
      if(attempts++<30)setTimeout(open,100);
    };
    open();
  }
  if(document.readyState==='complete')openEnterpriseFromHash();
  else window.addEventListener('load',openEnterpriseFromHash);
  window.addEventListener('hashchange',openEnterpriseFromHash);
})();
