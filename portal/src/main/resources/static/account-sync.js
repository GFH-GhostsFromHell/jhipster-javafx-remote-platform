/* Electrical Workbench account sync for browser-based field tools.
 * Local-first. Never overwrites a concurrent change without retaining a conflict backup.
 * Authenticated via the portal's same-origin secure session cookie.
 */
(function(){
'use strict';
const PREFIX='ewb.sync.v1.';
const busy=new Set(), pending=new Set();
const messageHandlers=[];
function notify(message){messageHandlers.forEach(fn=>{try{fn(message)}catch(_){}})}
function uuid(){return 'w'+(globalThis.crypto?.randomUUID?.()||Date.now().toString(36)+Math.random().toString(36).slice(2)).replace(/[^A-Za-z0-9_.:-]/g,'')}
function parse(key){try{const v=JSON.parse(localStorage.getItem(key)||'[]');return Array.isArray(v)?v:[]}catch(_){return []}}
function hash(value){return JSON.stringify(value)}
function bucketKey(bucket){return PREFIX+bucket}
async function json(url,options){
 const res=await fetch(url,{credentials:'same-origin',cache:'no-store',...options});
 if(!res.ok)throw Error(res.status===401?'Sign in to sync':res.status===403?'Account has no sync access':'Sync HTTP '+res.status);
 return res.json();
}
function backup(bucket,rows){try{
 const key='ewb.sync.conflicts.'+bucket;
 const prior=JSON.parse(localStorage.getItem(key)||'[]');
 const combined=(Array.isArray(prior)?prior:[]).concat(rows).slice(-100);
 localStorage.setItem(key,JSON.stringify(combined))
}catch(_){}}
async function syncArray(bucket,key,onApplied){
 if(busy.has(bucket)){pending.add(bucket);return}
 busy.add(bucket);
 try{
  const original=parse(key);
  const previous=(()=>{try{return JSON.parse(localStorage.getItem(bucketKey(bucket))||'{}')}catch(_){return {}}})();
  const local=original.map(row=>{
    if(!row||typeof row!=='object')return null;
    const copy={...row};
    if(!copy._syncId)copy._syncId=copy.id ? String(copy.id).replace(/[^A-Za-z0-9_.:-]/g,'_') :uuid();
    return copy;
  }).filter(Boolean);
  const duplicates=new Set();
  for(const row of local){if(duplicates.has(row._syncId))row._syncId=uuid();duplicates.add(row._syncId)}
  const remote=await json('/api/sync/'+encodeURIComponent(bucket));
  const remoteMap=new Map(remote.map(r=>[r.id,r]));
  const localMap=new Map(local.map(row=>[row._syncId,row]));
  const push=[];
  for(const row of local){
    const id=row._syncId,base=previous[id],server=remoteMap.get(id),changed=!base||hash(row)!==base.data;
    if(!server){push.push({id,revision:0,data:row,deleted:false});continue}
    if(changed&&base&&server.revision!==base.revision){
      backup(bucket,[{id,local:row,server,reason:'concurrent-edit',at:new Date().toISOString()}]);continue
    }
    if(changed){push.push({id,revision:server.revision,data:row,deleted:false})}
  }
  // Explicit user deletion is identified relative to last successful mirror.
  for(const [id,base] of Object.entries(previous)){
    if(localMap.has(id)||base.deleted)continue;
    const server=remoteMap.get(id);
    if(server&&!server.deleted){
      if(server.revision===base.revision)push.push({id,revision:server.revision,data:server.data,deleted:true});
      else backup(bucket,[{id,server,reason:'deleted-while-remote-edited',at:new Date().toISOString()}])
    }
  }
  let result={accepted:[],conflicts:[]};
  if(push.length){
    for(let start=0;start<push.length;start+=200){
      const response=await json('/api/sync/'+encodeURIComponent(bucket)+'/batch',{
        method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({records:push.slice(start,start+200)})
      });
      result.accepted.push(...response.accepted);
      result.conflicts.push(...response.conflicts);
    }
  }
  if(result.conflicts.length)backup(bucket,result.conflicts.map(x=>({id:x.id,server:x,local:localMap.get(x.id),reason:'revision-conflict',at:new Date().toISOString()})));
  const fresh=await json('/api/sync/'+encodeURIComponent(bucket));
  const finalMap=new Map();
  const changedIds=new Set(push.map(x=>x.id));
  for(const row of local){
    const base=previous[row._syncId],server=fresh.find(x=>x.id===row._syncId);
    if(!server){finalMap.set(row._syncId,row);continue}
    if(!server.deleted&&(!base||changedIds.has(row._syncId)||!base.data||hash(row)===base.data)){
      finalMap.set(row._syncId,server.data||row)
    }else if(!server.deleted){
      // Keep local unsynchronized edits visible; they are in conflict backup too.
      finalMap.set(row._syncId,row)
    }
  }
  for(const record of fresh){if(record.deleted)continue;if(!finalMap.has(record.id))finalMap.set(record.id,record.data)}
  const next=Array.from(finalMap.values());
  const mirror={};for(const record of fresh)mirror[record.id]={revision:record.revision,deleted:record.deleted,data:hash(record.data)};
  localStorage.setItem(bucketKey(bucket),JSON.stringify(mirror));
  const changed=hash(next)!==hash(original);
  if(changed){localStorage.setItem(key,JSON.stringify(next));if(onApplied)onApplied(next)}
  const note=result.conflicts.length?'Synced with '+result.conflicts.length+' conflict(s); local backups retained.':'Synced '+fresh.filter(x=>!x.deleted).length+' records.';
  notify(note);
 }catch(err){notify('Offline changes kept on this device. '+(err.message||'Sync failed'))}
 finally{busy.delete(bucket);if(pending.has(bucket)){pending.delete(bucket);setTimeout(()=>syncArray(bucket,key,onApplied),1500)}}
}
const jobs=[];
function register(bucket,key,onApplied){
 if(jobs.some(x=>x.bucket===bucket&&x.key===key))return;
 const job={bucket,key,onApplied};jobs.push(job);syncArray(bucket,key,onApplied);
}
function run(){for(const j of jobs)syncArray(j.bucket,j.key,j.onApplied)}
globalThis.EWAccountSync={register,run,onStatus:fn=>messageHandlers.push(fn),conflictKey:bucket=>'ewb.sync.conflicts.'+bucket};
window.addEventListener('online',run);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)run()});
setInterval(run,60000);
})();