/* Cross-platform circuit project import and account sync simulation:
 * no actual account credentials or network access required. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const Archive=require('../portal/src/main/resources/static/circuit-project-archive.js');
const {circuitReview}=require('../portal/src/main/resources/static/circuit-review-math.js');
function store(){
 const data=new Map();
 return {
  getItem:k=>data.get(k)??null,
  setItem:(k,v)=>data.set(k,String(v)),
  data
 };
}
const input={
 loadA:40,designMultiplier:1.25,voltageV:600,phases:3,oneWayLengthM:40,
 circularMils:66360,kOhmCircularMilPerFoot:12.9,
 terminalColumnAmpacityA:85,terminalC:75,insulationC:90,
 ambientFactor:1,groupingFactor:1,selectedOcpdA:50,maxVoltageDropPercent:3,
 conductorDescription:'Copper sample',tableReference:'Table 2'
};
const results=circuitReview(input);
const r=Archive.build({project:'Mine',title:'Feeder',notes:'Verify terminals'},
 input,results,'WORKED; NOT CODE VERIFIED',new Date('2026-11-08T15:00:00Z'),'web-1');
r.revision=1;
const a=store();
const alice=Archive.keyForAccount('alice');
const bob=Archive.keyForAccount('bob');
assert.notEqual(alice,bob);
Archive.append(a,{...r,id:'local-1',_syncId:'local-1'},Archive.KEY);
Archive.append(a,{...r,id:'alice-1',_syncId:'alice-1'},alice);
assert.equal(Archive.read(a,Archive.KEY).length,1);
assert.equal(Archive.read(a,alice).length,1);
assert.equal(Archive.read(a,bob).length,0,'accounts never automatically share local records');
// Windows LocalWorkspaceStore supplies _syncId but does not require an id field.
const windows={...r,_syncId:'windows-1'};
delete windows.id;
const foreign={
  _syncId:'other-1',kind:'transformerDesign',title:'A transformer report',
  data:{createdOn:'2026-11-08'}
};
const clientA=store(),clientB=store();
clientA.setItem(alice,JSON.stringify([windows,foreign]));
assert.equal(Archive.read(clientA,alice).length,1);
assert.equal(Archive.read(clientA,alice)[0].id,'windows-1');
const web=Archive.append(clientA,{...r,id:'browser-1',_syncId:'browser-1'},alice);
assert.equal(web.revision,2);
const raw=JSON.parse(clientA.getItem(alice));
assert.equal(raw.filter(x=>x.kind==='transformerDesign').length,1,
 'Saving circuit must never erase unrelated design report');
assert.equal(raw.length,3);
const backup={
 format:'ElectricalWorkbenchCircuitProject',schemaVersion:1,
 project:'Mine',exported:'2026-11-08T16:00:00Z',worksheets:Archive.forProject(clientA,'Mine',alice)
};
const first=Archive.importProject(clientB,backup,alice);
assert.equal(first.added,2);
assert.equal(Archive.read(clientB,alice).length,2);
const second=Archive.importProject(clientB,backup,alice);
assert.equal(second.added,0);
assert.equal(second.skipped,2);
assert.equal(Archive.read(clientB,alice).length,2);
const corrupt=JSON.parse(JSON.stringify(backup));
corrupt.worksheets[0].result='tampered';
assert.throws(()=>Archive.importProject(clientB,corrupt,alice),/conflict/);
assert.equal(Archive.read(clientB,alice).length,2,'tampered import cannot overwrite records');
// If any row in an import is invalid, no earlier rows may be partially imported.
const invalid=JSON.parse(JSON.stringify(backup));
invalid.worksheets.push({...r,id:'added-but-not-saved',_syncId:'added-but-not-saved',revision:-1});
assert.throws(()=>Archive.importProject(clientB,invalid,bob),/Invalid worksheet/);
assert.equal(Archive.read(clientB,bob).length,0);
assert.throws(()=>Archive.importProject(clientB,{format:'wrong',worksheets:[]},alice),/Unsupported/);
const corruptedStore=store();corruptedStore.setItem(alice,'{bad');
assert.throws(()=>Archive.append(corruptedStore,r,alice),/unreadable/);
assert.equal(corruptedStore.getItem(alice),'{bad','corrupt original is not overwritten');
// Simulated account API: native-origin Windows row -> browser B -> web save -> browser A.
// Account-sync.js uses the same authenticated designReports bucket as all native clients.
const syncSource=fs.readFileSync(path.join(root,'portal/src/main/resources/static/account-sync.js'),'utf8');
const server=new Map();let nextId=0;
function mockFetch(url,opt={}){
 const p=String(url).split('/');assert.equal(p[3],'designReports');
 if(opt.method==='POST'){
  const accepted=[],conflicts=[];
  for(const x of JSON.parse(opt.body).records){
   const old=server.get(x.id);
   if(old&&old.revision!==x.revision){conflicts.push(old);continue}
   const rec={id:x.id,revision:(old?.revision||0)+1,data:x.data,
     deleted:x.deleted,updatedAt:'2026-11-08T15:00:00Z'};
   server.set(x.id,rec);accepted.push(rec);
  }
  return Promise.resolve({ok:true,json:async()=>({accepted,conflicts})});
 }
 return Promise.resolve({ok:true,json:async()=>[...server.values()]});
}
function browser(initial){
 const data=store();data.setItem(alice,JSON.stringify(initial));
 const context={
  fetch:mockFetch,console,window:{addEventListener(){}},
  document:{addEventListener(){},hidden:false},localStorage:data,
  crypto:{randomUUID:()=> 'ui-'+(++nextId)},
  setTimeout,clearTimeout,setInterval(){},JSON,Math,Promise,Date
 };
 context.globalThis=context;
 vm.runInNewContext(syncSource,context,{filename:'account-sync.js'});
 context.EWAccountSync.register('designReports',alice,()=>{});
 return {data,run:()=>context.EWAccountSync.run()};
}
const pause=()=>new Promise(resolve=>setTimeout(resolve,120));
(async()=>{
 const native=browser([windows,foreign]);
 await pause();
 assert.equal(server.size,2,'Windows record should sync to account');
 const webClient=browser([]);
 await pause();
 assert.equal(Archive.read(webClient.data,alice).length,1,
   'browser pulls Windows saved worksheet with only _syncId');
 assert.equal(JSON.parse(webClient.data.getItem(alice)).length,2,
   'browser must retain unrelated design report types');
 Archive.append(webClient.data,{...r,id:'web-new',_syncId:'web-new'},alice);
 webClient.run();await pause();
 native.run();await pause();
 assert.equal(Archive.read(native.data,alice).length,2);
 assert.equal(Archive.read(webClient.data,alice).length,2);
 assert.equal(server.size,3);
 const html=fs.readFileSync(path.join(root,'portal/src/main/resources/static/circuit-sizing.html'),'utf8');
 assert.ok(html.includes("EWAccountSync.register('designReports',archiveKey"));
 assert.ok(html.includes("fetch('/api/profile'"));
 assert.ok(html.includes("CircuitProjectArchive.importProject(localStorage,payload,archiveKey)"));
 assert.ok(html.includes("CircuitProjectArchive.append(localStorage,rec,archiveKey)"));
 const scripts=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
 assert.ok(scripts.length>=1);
 scripts.forEach(m=>new vm.Script(m[1],{filename:'circuit-sizing inline.js'}));
 const ios=fs.readFileSync(path.join(root,'ios-client/ElectricalWorkbench/AccountRecordSync.swift'),'utf8');
 const android=fs.readFileSync(path.join(root,'android-client/app/src/main/java/com/gfh/electricalworkbench/MobileAccountSync.kt'),'utf8');
 const desktop=fs.readFileSync(path.join(root,'desktop-client/src/main/java/com/example/desktop/DesktopAccountSync.java'),'utf8');
 assert.ok(ios.includes('"designReports"')&&android.includes('"designReports"')&&desktop.includes('bucket'));
 console.log('Circuit sync/restore PASS: account separation, Windows+browser round trip, foreign report preservation, immutable JSON backups, safe conflicts.');
})().catch(e=>{console.error(e);process.exit(1)});
