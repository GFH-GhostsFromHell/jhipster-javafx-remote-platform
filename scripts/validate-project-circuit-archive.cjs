'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const A=require('../portal/src/main/resources/static/circuit-project-archive.js');
const {circuitReview}=require('../portal/src/main/resources/static/circuit-review-math.js');
const data={
 loadA:40,designMultiplier:1.25,voltageV:600,phases:3,
 oneWayLengthM:40,circularMils:66360,kOhmCircularMilPerFoot:12.9,
 terminalColumnAmpacityA:85,terminalC:75,insulationC:90,
 ambientFactor:1,groupingFactor:1,selectedOcpdA:50,maxVoltageDropPercent:3,
 conductorDescription:'4 AWG Cu — sample only',tableReference:'Table 2'
};
const result=circuitReview(data);
let state=new Map();
const storage={
 getItem:k=>state.get(k)??null,
 setItem:(k,v)=>{state.set(k,v);}
};
const today=new Date('2026-11-08T14:30:00Z');
function save(project,title,id,notes='Checked in field'){
 const record=A.build({project,title,notes,referenceTables:'Table 2, 5A, 5C'},
  data,result,'Worked circuit arithmetic; NOT CODE VERIFIED',today,id);
 return A.append(storage,record);
}
const r1=save('Mine Expansion','Feeder A','id-1');
assert.equal(r1.revision,1);
assert.equal(r1.saved,'2026-11-08T14:30:00.000Z');
assert.equal(r1.kind,'circuitWorksheet');
assert.equal(r1.codeEdition,'CEC_2024');
assert.equal(r1.results.codeComplianceVerified,false);
assert.equal(r1.assumptions.terminationMarkingVerified,false);
assert.equal(r1.assumptions.conductorTableValuesVerified,false);
assert.deepEqual(r1.inputs,data);
assert.equal(r1.assumptions.notes,'Checked in field');
assert.ok(r1.result.includes('NOT CODE VERIFIED'));
const r2=save('Mine Expansion','Feeder A','id-2');
const r3=save('Mine Expansion','Feeder B','id-3');
const r4=save('Other Project','Feeder A','id-4');
assert.equal(r2.revision,2);
assert.equal(r3.revision,1);
assert.equal(r4.revision,1);
assert.equal(A.forProject(storage,'Mine Expansion').length,3);
assert.equal(A.read(storage).length,4);
assert.equal(A.read(storage).find(r=>r.id==='id-1').revision,1);
assert.equal(A.read(storage).find(r=>r.id==='id-2').revision,2);
assert.throws(()=>save('Mine Expansion','Feeder A','id-1'),/Duplicate/);
assert.throws(()=>save('','Feeder A','id-5'),/Project name/);
assert.throws(()=>save('Mine Expansion','','id-6'),/Worksheet title/);
assert.throws(()=>A.build({project:'x',title:'y'},data,{...result,codeComplianceVerified:true},
 'report',today,'bad'),/valid circuit calculation/);
assert.throws(()=>A.build({project:'x',title:'y'},data,result,'',today,'bad'),/valid circuit calculation/);
const broken={getItem:()=>'{bad JSON',setItem:()=>{}};
assert.throws(()=>A.read(broken),/Could not read/);
const fakeProjectName='Project-'+'x'.repeat(160);
assert.throws(()=>save(fakeProjectName,'Feeder A','badname'),/Project name/);
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const html=read('portal/src/main/resources/static/circuit-sizing.html');
for(const key of ['projectName','worksheetTitle','worksheetNotes','savedWorksheet','saveWorksheet','loadWorksheet','exportProject']){
 assert.ok(html.includes('id="'+key+'"'),'Web project UI missing '+key);
}
assert.ok(html.includes('CircuitProjectArchive.append(localStorage,rec)'));
assert.ok(html.includes("el('calculate').click()"),'Web save must recalculate current inputs');
assert.ok(read('android-client/app/src/main/java/com/gfh/electricalworkbench/CircuitSizingActivity.kt')
 .includes('CircuitWorksheetStore.save('));
assert.ok(read('ios-client/ElectricalWorkbench/UnifiedCircuitSizingView.swift')
 .includes('CircuitProjectStore.save('));
assert.ok(read('desktop-client/src/main/java/com/example/desktop/CircuitReviewPane.java')
 .includes('store.append("designReports",item)'));
assert.ok(read('ios-client/ElectricalWorkbench/AccountRecordSync.swift')
 .includes('"troubleshootingCases","benderProfiles","designReports"'));
console.log('Project worksheet archive passed: versioned snapshots, 4-platform wiring, invalid saves and local project export hooks.');
