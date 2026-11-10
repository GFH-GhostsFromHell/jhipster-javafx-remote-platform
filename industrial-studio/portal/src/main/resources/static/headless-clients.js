// Headless edge collectors: separate per-ID configuration. No cloud-to-PLC command API.
(()=>{
'use strict';
const $=id=>document.getElementById(id);
const api=(p,m='GET',body)=>window.studioAdminApi(p,m,body);
let agents=[],current=null,draft=null,dirty=false,timer=null;
const note=(message,bad=false)=>{$('edgeStatus').textContent=message;$('edgeStatus').style.color=bad?'#ffbabb':'#b6f0d5'};
const opt=(id,name)=>{const x=document.createElement('option');x.value=id;x.textContent=name;return x};
const lab=(name,field)=>{const l=document.createElement('label');l.append(document.createTextNode(name),field);return l};
function reset(){
 if(timer)clearInterval(timer);timer=null;agents=[];current=null;draft=null;dirty=false;
 $('edgeSelect').replaceChildren();$('edgeEditor').classList.add('hidden');
 $('edgeSecretPanel').classList.add('hidden');$('edgeSecret').value='';
 $('edgeStatus').textContent='Sign in to manage headless collectors.';
}
function select(id){
 if(dirty&&!window.confirm('Discard unsaved collector edits?'))return;
 current=agents.find(x=>x.id===id)||null;draft=current?structuredClone(current.configuration):null;
 dirty=false;$('edgeEditor').classList.toggle('hidden',!draft);
 if(!draft)return;
 $('edgeName').value=draft.name;$('edgeMode').value=draft.sourceMode;
 $('edgeEnabled').checked=draft.enabled;$('edgeScript').value=draft.script;
 $('edgeRevision').textContent='Revision '+current.revision;
 tags();telemetry();
}
function renderList(){
 const id=current?.id||'';
 $('edgeSelect').replaceChildren(opt('','Choose a collector'));
 for(const a of agents)$('edgeSelect').append(opt(a.id,a.configuration.name+' ('+a.id+')'));
 const next=agents.find(x=>x.id===id)||agents[0];
 $('edgeSelect').value=next?.id||'';
 if(!dirty)select(next?.id);
 else if(next){current={...current,lastSeen:next.lastSeen,recentlySeen:next.recentlySeen,
    lastSamples:next.lastSamples,proposedWrites:next.proposedWrites};telemetry();}
}
function textField(obj,key){const field=document.createElement('input');field.value=obj[key]||'';
 field.oninput=()=>{obj[key]=field.value;dirty=true};return field;}
function tags(){
 const host=$('edgeTags');host.replaceChildren();
 for(const [i,t] of draft.tags.entries()){
   const row=document.createElement('div');row.className='widgetEdit';
   for(const [name,key] of [['Tag ID','id'],['Label','label'],['OPC UA NodeId','nodeId'],['Unit','unit']]){
      row.append(lab(name,textField(t,key)));
   }
   const remove=document.createElement('button');remove.type='button';remove.textContent='Remove';
   remove.onclick=()=>{draft.tags.splice(i,1);dirty=true;tags()};
   row.append(remove);host.append(row);
 }
}
function telemetry(){
 const out=$('edgeTelemetry');out.replaceChildren();
 if(!current)return;
 const h=document.createElement('p');
 h.textContent=current.recentlySeen?'Collector connected recently · last sample '+current.lastSeen:
   'Collector offline or not reporting · last sample '+(current.lastSeen||'never');
 out.append(h);
 for(const sample of current.lastSamples||[]){
   const p=document.createElement('p');p.textContent=sample.id+': '+sample.value+' · '+sample.quality;out.append(p);
 }
 for(const proposal of current.proposedWrites||[]){
   const p=document.createElement('p');
   p.textContent='Dry-run '+proposal.outputTag+' = '+proposal.value+' (not sent to PLC)';
   out.append(p);
 }
}
async function reload(){
 try{
   agents=await api('/api/studio/headless-agents');
   renderList();note(agents.length+' registered collectors · all PLC writes disabled');
 }catch(e){note('Cannot load collectors: '+e.message,true)}
}
function showSecret(value){
 $('edgeSecretPanel').classList.remove('hidden');$('edgeSecret').value=value;
 $('edgeSecret').focus();$('edgeSecret').select();
 note('Save the one-time collector secret in the approved edge machine. It cannot be retrieved again.');
}
async function create(){
 const id=$('edgeNewId').value.trim(),name=$('edgeNewName').value.trim();
 if(!/^[A-Za-z][A-Za-z0-9_-]{1,47}$/.test(id)||!name){
   note('Provide a collector name and an ID of 2–48 letters/numbers.',true);return;
 }
 try{
   const global=await api('/api/studio/config');
   const inputs=global.configuration.tags.slice(0,10).map(t=>({id:t.id,label:t.label,nodeId:t.nodeId,unit:t.unit}));
   if(!inputs.length)throw Error('Create at least one tag in PLC tag mappings first');
   const script='# Proposed writes are simulations, not real PLC commands.\n'+
     'SIM_WRITE desiredValue = CLAMP('+inputs[0].id+' * 1 + 0, 0, 100)';
   const result=await api('/api/studio/headless-agents','POST',{
     id,configuration:{name,sourceMode:'SIMULATION',enabled:true,tags:inputs,script}
   });
   agents.push(result.agent);dirty=false;current=result.agent;renderList();showSecret(result.oneTimeToken);
   $('edgeNewId').value='';$('edgeNewName').value='';
 }catch(e){note('Cannot create collector: '+e.message,true)}
}
function definition(){
 if(!draft)throw Error('Choose a collector');
 const result=structuredClone(draft);
 result.name=$('edgeName').value.trim();result.sourceMode=$('edgeMode').value;
 result.enabled=$('edgeEnabled').checked;result.script=$('edgeScript').value;
 if(!result.name||!result.tags.length)throw Error('A name and at least one input tag are required');
 return result;
}
async function save(){
 try{
   if(!current)return;
   const result=await api('/api/studio/headless-agents/'+encodeURIComponent(current.id),'PUT',
     {expectedRevision:current.revision,configuration:definition()});
   agents=agents.map(x=>x.id===result.id?result:x);current=result;dirty=false;
   renderList();note('Collector revision '+result.revision+' saved; edge PC will pull it automatically.');
 }catch(e){note('Save failed: '+e.message,true)}
}
async function rotate(){
 if(!current||!window.confirm('Rotating the secret will disconnect this collector until the new secret is installed. Continue?'))return;
 try{
   const result=await api('/api/studio/headless-agents/'+encodeURIComponent(current.id)+'/rotate-token','POST');
   agents=agents.map(x=>x.id===result.agent.id?result.agent:x);current=result.agent;
   showSecret(result.oneTimeToken);
 }catch(e){note('Token rotation failed: '+e.message,true)}
}
function sampleFields(){
 const host=$('edgePreviewInputs');host.replaceChildren();
 if(!draft)return;
 for(const tag of draft.tags){
   const input=document.createElement('input');
   input.id='edgeTest_'+tag.id;input.type='number';input.step='any';input.value='50';
   host.append(lab(tag.label+' · '+tag.id,input));
 }
}
async function testRules(){
 if(!current)return;
 if(dirty){note('Save the collector configuration before testing its rules.',true);return}
 try{
   const values={};
   for(const tag of draft.tags){
      const v=$('edgeTest_'+tag.id);
      if(!v||!v.value.trim()||!Number.isFinite(Number(v.value)))throw Error('Invalid sample for '+tag.id);
      values[tag.id]=Number(v.value);
   }
   const r=await api('/api/studio/headless-agents/'+encodeURIComponent(current.id)+'/preview','POST',values);
   const host=$('edgeDryRun');host.replaceChildren();
   const h=document.createElement('strong');h.textContent='DRY RUN · 0 actual PLC writes';
   host.append(h);
   for(const x of r.proposedWrites){
      const p=document.createElement('p');
      p.textContent=x.outputTag+' = '+x.value+' · '+x.mode;host.append(p);
   }
   note('Cloud rule preview finished: '+r.proposedWrites.length+' simulated write proposals.');
 }catch(e){note('Preview failed: '+e.message,true)}
}
$('edgeCreate').onclick=create;$('edgeSave').onclick=save;$('edgeRotate').onclick=rotate;
$('edgeSelect').onchange=()=>select($('edgeSelect').value);
$('edgeName').oninput=()=>{if(draft){draft.name=$('edgeName').value;dirty=true}};
$('edgeMode').onchange=()=>{if(draft){draft.sourceMode=$('edgeMode').value;dirty=true}};
$('edgeEnabled').onchange=()=>{if(draft){draft.enabled=$('edgeEnabled').checked;dirty=true}};
$('edgeScript').oninput=()=>{if(draft){draft.script=$('edgeScript').value;dirty=true}};
$('edgeAddTag').onclick=()=>{
 if(!draft)return;
 if(draft.tags.length>=50){note('Maximum 50 input tags',true);return}
 draft.tags.push({id:'input'+(draft.tags.length+1),label:'New input',
  nodeId:'ns=2;s=Channel1.Device1.NewInput',unit:''});
 dirty=true;tags();
};
$('edgeTestPrepare').onclick=sampleFields;
$('edgeTestRun').onclick=testRules;
$('edgeRefresh').onclick=reload;
$('edgeCopySecret').onclick=async()=>{
 try{await navigator.clipboard.writeText($('edgeSecret').value);note('One-time token copied.');}
 catch(e){$('edgeSecret').select();note('Use Ctrl+C to copy the selected secret.',true)}
};
window.addEventListener('studio:connected',()=>{reset();reload();timer=setInterval(reload,10000)});
window.addEventListener('studio:disconnected',reset);
})();
