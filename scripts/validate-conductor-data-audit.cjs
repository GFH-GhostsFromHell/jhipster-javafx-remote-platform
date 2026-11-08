/* Regression audit of built-in conductor sample data. Does NOT replace a
 * comparison with an authorized CSA C22.1:24 / Ontario Electrical Safety Code. */
'use strict';
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const backend=read('portal/src/main/java/com/example/remoteportal/service/ElectricalCalculatorService.java');
const desktop=read('desktop-client/src/main/java/com/example/desktop/RacewayAmpacityPane.java');
const common=read('desktop-client/src/main/java/com/example/desktop/ConductorCorrectionFactors.java');
const derating=read('desktop-client/src/main/java/com/example/desktop/AmpacityDeratingPane.java');
const ios=read('ios-client/ElectricalWorkbench/OfflinePowerCalculatorsView.swift');
const android=read('android-client/app/src/main/java/com/gfh/electricalworkbench/TransformerSizingActivity.kt');
const docs=read('docs/CEC2024_CONDUCTOR_DATA_AUDIT.md');

const portalCu=new Map([...backend.matchAll(/CU\.put\("([^"]+)",([\d.]+)d\)/g)]
  .map(([,size,amp])=>[size,Number(amp)]));
const winCu=new Map([...desktop.matchAll(/p\("([^"]+)",([\d.]+),([\d.]+),([\d.]+),([\d.]+)\)/g)]
  .map(([,size,area,a60,a75,a90])=>[size,{area:+area,a60:+a60,a75:+a75,a90:+a90}]));
if(portalCu.size<20||winCu.size<20)
 throw Error('Missing copper ampacity data or parsing rules changed: '+portalCu.size+'/'+winCu.size);
for(const [size,amp] of portalCu) {
 const w=winCu.get(size);
 if(!w||w.a75!==amp)throw Error('75°C copper worksheet mismatch for '+size+': '+amp+' / '+w?.a75);
 if(w.a60>w.a75||w.a75>w.a90)
  throw Error('Non-monotonic temperature columns for '+size);
}
for(const size of winCu.keys())
 if(!portalCu.has(size))throw Error('Windows copper size not in web dataset: '+size);

// Catch accidental divergence of the actual GUI calculation paths.
if(!derating.includes('ConductorCorrectionFactors.ambientFactor(')
   ||!derating.includes('ConductorCorrectionFactors.groupingFactor(')
   ||!desktop.includes('ConductorCorrectionFactors.ambientFactor(')
   ||!desktop.includes('ConductorCorrectionFactors.groupingFactor('))
 throw Error('Windows conductor tools do not share the audited helper.');
for(const k of ['60','75','90','50','35','40','55','60','65','70','75','80'])
 if(!common.includes(k))throw Error('Missing temperature reference value '+k);
if(!ios.includes('OfflineElectricalMath.correctedAmpacity(')
   ||!android.includes('OfflineElectricalMath.transformerCurrent('))
 throw Error('Native offline calculation paths bypass testable safe math.');
if(!backend.includes('"candidateOnly",true')
   ||!backend.includes('"codeComplianceVerified",false'))
 throw Error('Legacy starter conductor recommendations must not claim code compliance.');
if(!docs.includes('AUTHORIZED_SOURCE_NOT_AVAILABLE'))
 throw Error('Reference provenance must flag missing authorized primary table.');
console.log('Conductor data parity passed: '+portalCu.size+' copper 75°C samples match Windows; '+
 'shared factors and native formula paths are testable. Full CEC 2024/OESC reference verification remains pending.');
