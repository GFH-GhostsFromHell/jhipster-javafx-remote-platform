/* Explicit, account-scoped local worksheet drafts. No background upload or code calculation changes. */
(function(){
 'use strict';
 var root=document.querySelector('main.main, main#main, main');
 if(!root)return;
 var username=null, storeKey=null;
 function active(){
  return document.querySelector('.module.active, .section.active')||root;
 }
 function title(){
  var panel=active(),h=panel.querySelector('h1,h2');
  return (h?h.textContent:'Worksheet').trim().slice(0,100);
 }
 function section(){return active().id||'default';}
 function records(){
  if(!storeKey)return[];
  try{var a=JSON.parse(localStorage.getItem(storeKey)||'[]');return Array.isArray(a)?a.filter(function(x){return x&&typeof x==='object'&&x.page===location.pathname;}):[];}catch(_){return[];}
 }
 function allRecords(){if(!storeKey)return[];try{var a=JSON.parse(localStorage.getItem(storeKey)||'[]');return Array.isArray(a)?a:[];}catch(_){return[];}}
 function put(items){try{localStorage.setItem(storeKey,JSON.stringify(items.slice(0,24)));return true;}catch(_){return false;}}
 var box=document.createElement('section');box.id='ewbWorksheetAssist';
 box.innerHTML='<div class="ewb-links"><a href="/start.html">← Find another tool</a> · <a href="/portal.html">Dashboard</a></div><h2>Worksheet workflow</h2><div class="ewb-steps"><span><b>1</b> Enter values</span><span><b>2</b> Calculate</span><span><b>3</b> Check result</span><span><b>4</b> Save / export</span></div>'+
 '<p class="ewb-note">Confirm the CEC/OESC rules, table values and equipment markings before using a result in the field.</p>'+
 '<div class="ewb-controls"><label for="ewbDraftName">Worksheet name <input id="ewbDraftName" maxlength="100" placeholder="Example: Motor feeder 600 V"></label>'+
 '<button id="ewbSave" type="button">Save input draft</button>'+
 '<label for="ewbSaved">Saved drafts <select id="ewbSaved"><option value="">Select a saved draft</option></select></label>'+
 '<button id="ewbRestore" type="button">Restore inputs</button>'+
 '<button id="ewbExport" type="button">Export selected JSON</button>'+
 '<button id="ewbCopy" type="button">Copy visible result</button>'+
 '<button id="ewbDelete" type="button">Delete selected draft</button></div>'+
 '<p id="ewbStatus" class="ewb-note" role="status" aria-live="polite">Checking your account…</p>'+
 '<p class="ewb-note"><strong>Device-only:</strong> drafts saved here are browser-local and are not cloud-synced. Open <a href="/account-records.html">Saved Work</a> for existing cloud records. You must rerun a restored worksheet before relying on its result.</p>';
 var style=document.createElement('style');
 style.textContent='#ewbWorksheetAssist{margin:0 0 17px;padding:14px;border-radius:13px;border:1px solid #36617d;background:#0c253b;color:#f1f8ff;font:14px/1.5 Inter,Segoe UI,system-ui,sans-serif;max-width:100%;overflow-wrap:anywhere}'+
 '#ewbWorksheetAssist h2{font-size:19px!important;color:#fff;margin:0 0 10px}#ewbWorksheetAssist .ewb-steps{display:flex;flex-wrap:wrap;gap:8px}'+
 '#ewbWorksheetAssist .ewb-steps span{background:#153b55;border:1px solid #326985;padding:7px 9px;border-radius:9px;font-weight:700}'+
 '#ewbWorksheetAssist .ewb-steps b{background:#77e8ff;color:#083447;border-radius:99px;padding:2px 7px}'+
 '#ewbWorksheetAssist .ewb-note{color:#d0e4f1;font-size:12px;margin:9px 0 4px}#ewbWorksheetAssist a{color:#81e5ff}'+
 '#ewbWorksheetAssist .ewb-controls{display:flex;flex-wrap:wrap;gap:8px;align-items:end;margin-top:10px}'+
 '#ewbWorksheetAssist label{font-size:12px;color:#e0f0fa;font-weight:750;display:flex;flex-direction:column;gap:4px;flex:1 1 180px;min-width:0}'+
 '#ewbWorksheetAssist input,#ewbWorksheetAssist select{background:#fff;color:#102338;border:1px solid #9dbaca;border-radius:8px;padding:9px;font:inherit;min-width:0;max-width:100%;min-height:44px}'+
 '#ewbWorksheetAssist button{color:#fff;background:#087c98;border:1px solid #2e9fb9;padding:9px 12px;border-radius:8px;font-weight:750;min-height:44px;cursor:pointer;font:inherit}'+
 '#ewbWorksheetAssist button:disabled{opacity:.5;cursor:not-allowed}#ewbWorksheetAssist button:focus-visible,#ewbWorksheetAssist input:focus-visible,#ewbWorksheetAssist select:focus-visible{outline:3px solid #67e8f9;outline-offset:2px}'+
 '@media(max-width:650px){#ewbWorksheetAssist .ewb-controls>button{flex:1 1 135px}#ewbWorksheetAssist .ewb-steps span{flex:1 1 130px}}';
 document.head.append(style);
 root.prepend(box);
 var save=document.getElementById('ewbSave'),pick=document.getElementById('ewbSaved'),restore=document.getElementById('ewbRestore'),exp=document.getElementById('ewbExport'),del=document.getElementById('ewbDelete');
 function status(msg){document.getElementById('ewbStatus').textContent=msg;}
 function fields(){
  var values={};
  active().querySelectorAll('input,select,textarea').forEach(function(input){
   if(!input.id||input.disabled||/password|file|hidden|email/i.test(input.type)||/password|token|secret|email|address/i.test(input.id))return;
   if(Object.keys(values).length>=150)return;
   var value=input.type==='checkbox'||input.type==='radio'?input.checked:input.value;
   if(typeof value==='string'&&value.length>1200)return;
   values[input.id]={type:input.type,value:value};
  });
  return values;
 }
 function result(){
  var candidates=Array.from(active().querySelectorAll('pre.result,pre.out,.out,[id^="o_"]'));
  var text=candidates.map(function(n){return n.textContent.trim();}).filter(function(v){return v.length>8&&!/^Enter the question facts|^Calculate to see/.test(v);}).join('\n\n');
  return text.slice(0,25000);
 }
 function update(){
  var list=records().filter(function(x){return x.section===section();});
  pick.replaceChildren(new Option('Select saved worksheet',''));
  list.forEach(function(item){pick.add(new Option(item.name+' · '+new Date(item.savedAt).toLocaleDateString(),item.id));});
  var activeRecord=list.some(function(x){return x.id===pick.value;});
  restore.disabled=exp.disabled=del.disabled=!activeRecord;
  save.disabled=!storeKey;
 }
 function selected(){return records().find(function(x){return x.id===pick.value;});}
 save.addEventListener('click',function(){
  if(!storeKey){status('Sign in before saving a local draft.');return;}
  var map=fields(),count=Object.keys(map).length;
  if(!count){status('No input fields in the selected worksheet. Open a calculator first.');return;}
  var item={id:'draft-'+Date.now()+'-'+Math.floor(Math.random()*100000),page:location.pathname,section:section(),name:(document.getElementById('ewbDraftName').value.trim()||title()).slice(0,100),savedAt:new Date().toISOString(),inputs:map,visibleResult:result()};
  var ok=put([item].concat(allRecords()));if(!ok){status('Could not save locally. Check device storage settings.');return;}
  update();pick.value=item.id;restore.disabled=exp.disabled=del.disabled=false;
  status('Saved '+count+' input values to this device. Confirm the calculation again when reopening.');
 });
 pick.addEventListener('change',function(){var has=!!selected();restore.disabled=exp.disabled=del.disabled=!has;});
 restore.addEventListener('click',function(){
  var item=selected();if(!item){status('Select a saved worksheet first.');return;}
  var changed=0;
  Object.entries(item.inputs||{}).forEach(function(pair){
   var input=document.getElementById(pair[0]),data=pair[1];
   if(!input||!active().contains(input)||!data)return;
   if(input.type==='checkbox'||input.type==='radio')input.checked=!!data.value;
   else input.value=String(data.value);
   input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));changed++;
  });
  status('Restored '+changed+' fields. Press Calculate again; saved results may be stale.');
 });
 exp.addEventListener('click',function(){
  var item=selected();if(!item){status('Select a saved worksheet to export.');return;}
  var data=JSON.stringify({format:'ElectricalWorkBench.LocalWorksheet.v1',record:item,warning:'Verify all field values, code tables and equipment ratings.'},null,2);
  var blob=new Blob([data],{type:'application/json'}),url=URL.createObjectURL(blob),a=document.createElement('a');
  a.href=url;a.download='electrical-workbench-worksheet.json';document.body.append(a);a.click();a.remove();setTimeout(function(){URL.revokeObjectURL(url);},1000);status('Exported JSON worksheet.');
 });
 document.getElementById('ewbCopy').addEventListener('click',function(){
  var text=result();if(!text){status('No visible result yet. Run Calculate first.');return;}
  if(!navigator.clipboard||!navigator.clipboard.writeText){status('Copy is unavailable in this browser. Select the result text manually.');return;}
  navigator.clipboard.writeText(text).then(function(){status('Copied the visible result.');},function(){status('Copy was blocked by the browser. Select the result text manually.');});
 });
 del.addEventListener('click',function(){
  var item=selected();if(!item)return;
  if(!confirm('Delete the selected local worksheet draft?'))return;
  if(put(allRecords().filter(function(x){return x.id!==item.id;}))){update();status('Local worksheet deleted.');}
 });
 document.addEventListener('click',function(e){if(e.target&&e.target.closest&&e.target.closest('nav,.tabs,[data-tabset]'))setTimeout(update,0);},true);
 window.EWBWorksheetActions=Object.freeze({
  saveCurrent:function(){
   if(!storeKey){status('Sign in before saving a local draft.');return false;}
   save.click();
   return !!selected();
  }
 });
 function setAccount(profile){
  username=profile.username;storeKey='ewb.worksheetDrafts.v1.'+encodeURIComponent(username);
  status('Ready to save input drafts for '+username+' on this device.');
  update();
 }
 [save,restore,exp,del].forEach(function(b){b.disabled=true;});
 fetch('/api/profile',{credentials:'same-origin'}).then(function(r){if(!r.ok)throw Error('Not signed in');return r.json();}).then(setAccount).catch(function(){status('Sign in to save or restore local drafts. Calculations still work without this panel.');});
})();
