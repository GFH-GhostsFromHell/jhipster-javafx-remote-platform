/* Account-scoped circuit worksheet archive, with explicit offline-only legacy
 * storage. Signed-in browser sync uses designReports; do not delete unrelated
 * items in that shared collection. Imports are additive and never overwrite IDs.
 */
(function(root){
'use strict';
const KEY='ewb.circuitWorksheets.local.v1';
const MAX_RECORDS=250;
const ACCOUNT_PREFIX='ewb.circuitWorksheets.account.v1.';
function keyForAccount(user){
  const name=String(user||'').trim();
  if(!name||name.length>255)throw Error('A verified account is required.');
  return ACCOUNT_PREFIX+encodeURIComponent(name);
}
function validText(value,label,max=150){
  const t=String(value??'').trim();
  if(!t||t.length>max)throw Error(label+' must be 1–'+max+' characters.');
  return t;
}
function allRows(storage,key=KEY){
  let items;
  try{items=JSON.parse(storage.getItem(key)||'[]')}
  catch(_){throw Error('The project archive is unreadable. Back up the raw browser data; no records were overwritten.')}
  if(!Array.isArray(items))throw Error('Project archive has an unexpected format; no changes saved.');
  return items;
}
function isWorksheet(r){
  return r&&typeof r==='object'&&r.kind==='circuitWorksheet'&&
    typeof r.id==='string'&&typeof r.project==='string'&&r.inputs&&r.results;
}
function read(storage,key=KEY){return allRows(storage,key).filter(isWorksheet)}
function build(meta,inputs,results,report,now,id){
  const project=validText(meta.project,'Project name');
  const title=validText(meta.title,'Worksheet title');
  const saved=now instanceof Date&&Number.isFinite(now.getTime())?now.toISOString():null;
  if(!saved)throw Error('A valid revision date is required.');
  if(!/^[A-Za-z0-9_.:-]{1,128}$/.test(String(id||'')))
    throw Error('A valid unique worksheet ID is required.');
  if(!inputs||!results||results.codeComplianceVerified!==false
    ||!Number.isFinite(results.adjustedAmpacityA)||
    typeof report!=='string'||!report.trim())
    throw Error('Run a valid circuit calculation before saving.');
  return {
    _syncId:id,id,kind:'circuitWorksheet',schemaVersion:1,
    project,title,revision:0,saved,codeEdition:'CEC_2024',
    jurisdiction:'Ontario (verify effective OESC amendments)',
    referenceTables:meta.referenceTables||'CEC Table 1–4; 5A; 5C (user-supplied)',
    assumptions:{
      conductorTableValuesVerified:false,terminationMarkingVerified:false,
      codeComplianceVerified:false,
      voltageDropMethod:'resistive K-factor estimate, not full AC impedance',
      specialCircuitRulesReviewed:false,
      notes:String(meta.notes||'').slice(0,2000)
    },
    inputs:{...inputs},results:{...results},result:report
  };
}
function append(storage,record,key=KEY){
  const all=allRows(storage,key);
  if(!isWorksheet(record)||record.results.codeComplianceVerified!==false)
    throw Error('Invalid or unverified worksheet snapshot.');
  if(all.some(r=>r&&(r._syncId===record.id||r.id===record.id)))
    throw Error('Duplicate worksheet ID; please retry.');
  const matches=all.filter(r=>isWorksheet(r)&&r.project===record.project&&r.title===record.title);
  const revision=Math.max(0,...matches.map(r=>Number.isInteger(r.revision)?r.revision:0))+1;
  if(all.length>=MAX_RECORDS)
    throw Error('Project archive has reached '+MAX_RECORDS+' records. Export a backup before adding more.');
  const saved={...record,revision};
  // Keep all existing designReports, including records from desktop or mobile.
  storage.setItem(key,JSON.stringify([saved,...all]));
  return saved;
}
function forProject(storage,project,key=KEY){
  return read(storage,key).filter(r=>r.project===project);
}
function validateImported(row){
  if(!isWorksheet(row)||row.schemaVersion!==1||
      typeof row._syncId!=='string'||row.id!==row._syncId||
      !/^[A-Za-z0-9_.:-]{1,128}$/.test(row.id)||
      !row.project.trim()||row.project.length>150||
      typeof row.title!=='string'||!row.title.trim()||row.title.length>150||
      !Number.isInteger(row.revision)||row.revision<1||
      typeof row.saved!=='string'||!Number.isFinite(Date.parse(row.saved))||
      typeof row.result!=='string'||!row.result.trim()||
      !row.assumptions||row.assumptions.codeComplianceVerified!==false||
      row.results.codeComplianceVerified!==false||
      !Number.isFinite(row.results.adjustedAmpacityA))
    throw Error('Invalid worksheet backup. No records were imported.');
  return row;
}
function importProject(storage,payload,key=KEY){
  if(!payload||payload.format!=='ElectricalWorkbenchCircuitProject'||
      payload.schemaVersion!==1||!Array.isArray(payload.worksheets)||
      payload.worksheets.length<1||payload.worksheets.length>MAX_RECORDS)
    throw Error('Unsupported project backup. No records were imported.');
  const incoming=payload.worksheets.map(validateImported);
  const all=allRows(storage,key);
  const ids=new Set(all.filter(Boolean).map(r=>r._syncId||r.id));
  const seen=new Set();
  let added=0,skipped=0;
  const merged=all.slice();
  for(const row of incoming){
    if(seen.has(row.id))throw Error('Duplicate IDs inside backup. No records were imported.');
    seen.add(row.id);
    if(ids.has(row.id)){
      const prior=all.find(r=>r&&(r._syncId===row.id||r.id===row.id));
      // A different record with the same immutable ID is a conflict: never
      // overwrite either record, and do not silently skip changed history.
      if(JSON.stringify(prior)!==JSON.stringify(row))
        throw Error('Worksheet ID conflict. Export both archives and reconcile before importing; nothing changed.');
      skipped++;continue;
    }
    merged.push(row);added++;
  }
  if(merged.length>MAX_RECORDS)
    throw Error('Project archive capacity exceeded. Export and separate the projects; no changes made.');
  if(added)storage.setItem(key,JSON.stringify(merged));
  return {added,skipped,total:merged.length};
}
const API={KEY,MAX_RECORDS,keyForAccount,read,build,append,forProject,importProject};
root.CircuitProjectArchive=API;
if(typeof module!=='undefined'&&module.exports)module.exports=API;
})(typeof window!=='undefined'?window:this);
