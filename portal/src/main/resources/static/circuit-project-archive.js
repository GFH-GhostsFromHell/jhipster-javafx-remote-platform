/* Browser-only project archive: immutable circuit worksheet snapshots, NOT cloud sync.
 * Includes user inputs, computed results, code assumptions, timestamps and revisions. */
(function(root){
  'use strict';
  const KEY='ewb.circuitWorksheets.local.v1';
  const MAX_RECORDS=250;
  function validText(value,label,max=150) {
    const text=String(value==null?'':value).trim();
    if(!text||text.length>max)throw Error(label+' must be 1–'+max+' characters.');
    return text;
  }
  function read(storage) {
    let rows;
    try { rows=JSON.parse(storage.getItem(KEY)||'[]'); }
    catch(_) { throw Error('Could not read the browser project archive. Export or back it up before clearing site data.'); }
    if(!Array.isArray(rows))throw Error('Browser project archive has an unexpected format.');
    return rows.filter(r=>r&&typeof r==='object'&&r.kind==='circuitWorksheet'
      &&typeof r.id==='string'&&typeof r.project==='string'&&r.inputs&&r.results);
  }
  function build(meta,inputs,results,report,now,id){
    const project=validText(meta.project,'Project name');
    const title=validText(meta.title,'Worksheet title');
    const saved=now instanceof Date&&Number.isFinite(now.getTime())?now.toISOString():null;
    if(!saved)throw Error('A valid revision date is required.');
    if(!id||typeof id!=='string')throw Error('A unique worksheet ID is required.');
    if(!inputs||!results||results.codeComplianceVerified!==false
       ||!Number.isFinite(results.adjustedAmpacityA)
       ||typeof report!=='string'||!report.trim())
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
  function append(storage,record) {
    const rows=read(storage);
    if(rows.some(r=>r.id===record.id))throw Error('Duplicate worksheet ID; please retry.');
    const matches=rows.filter(r=>r.project===record.project&&r.title===record.title);
    const revision=Math.max(0,...matches.map(r=>Number.isInteger(r.revision)?r.revision:0))+1;
    const saved={...record,revision};
    if(rows.length>=MAX_RECORDS)
      throw Error('Local project archive has reached '+MAX_RECORDS+' records. Export a project backup before adding more revisions.');
    const all=[saved,...rows];
    // Local storage is not a durable or synchronized cloud archive.
    storage.setItem(KEY,JSON.stringify(all));
    return saved;
  }
  function forProject(storage,project) {
    return read(storage).filter(r=>r.project===project);
  }
  const API={KEY,MAX_RECORDS,read,build,append,forProject};
  root.CircuitProjectArchive=API;
  if(typeof module!=='undefined'&&module.exports)module.exports=API;
})(typeof window!=='undefined'?window:this);
