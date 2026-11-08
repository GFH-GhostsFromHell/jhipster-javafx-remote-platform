'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {MODES,calculate}=require('../portal/src/main/resources/static/industrial-commissioning-math.js');
const near=(a,b,tol=1e-4)=>assert.ok(Number.isFinite(a)&&Math.abs(a-b)<tol,
 'Expected '+b+', got '+a);
assert.equal(Object.keys(MODES).length,6);
const ct=calculate('ct',{primaryRatedA:400,secondaryRatedA:5,actualPrimaryA:200});
near(ct.ratio,80);near(ct.secondaryCurrentA,2.5);
near(ct.reconstructedPrimaryA,200);
const vt=calculate('vt',{primaryRatedV:4160,secondaryRatedV:120,actualPrimaryV:4000});
near(vt.secondaryVoltageV,115.384615);
const power=calculate('power',{lineVoltageV:600,lineCurrentA:100,powerFactor:.85});
near(power.apparentPowerKva,103.923048);
near(power.realPowerKw,88.33459);
const pickup=calculate('pickup',{primaryRatedA:400,secondaryRatedA:5,primaryPickupA:240});
near(pickup.secondaryPickupA,3);
assert.ok(pickup.note.includes('Not an approved relay setting'));
const fault=calculate('fault',{transformerKva:75,secondaryVoltageV:600,impedancePercent:5});
near(fault.transformerFullLoadCurrentA,72.16878365);
near(fault.idealInfiniteSourceFaultA,1443.375673);
assert.ok(fault.note.includes('NOT available fault current'));
const analog=calculate('analog',{engineeringMin:0,engineeringMax:1800,engineeringValue:1200});
near(analog.analogMilliamp,14.6666666);
near(analog.spanPercent,66.666667);
for(const result of [ct,vt,power,pickup,fault,analog]){
 assert.equal(result.codeComplianceVerified,false);
 assert.equal(result.referenceProfileId,'OESC_ON_2024');
 assert.equal(result.numericDatasetVerificationStatus,'AUTHORIZED_TABLE_VERIFICATION_REQUIRED');
}
const invalid=[
 ['ct',{primaryRatedA:0,secondaryRatedA:5,actualPrimaryA:200}],
 ['ct',{primaryRatedA:400,secondaryRatedA:5,actualPrimaryA:NaN}],
 ['vt',{primaryRatedV:4160,secondaryRatedV:0,actualPrimaryV:4000}],
 ['power',{lineVoltageV:600,lineCurrentA:100,powerFactor:1.2}],
 ['power',{lineVoltageV:600,lineCurrentA:-1,powerFactor:.8}],
 ['pickup',{primaryRatedA:400,secondaryRatedA:5,primaryPickupA:Infinity}],
 ['fault',{transformerKva:75,secondaryVoltageV:600,impedancePercent:0}],
 ['fault',{transformerKva:75,secondaryVoltageV:600,impedancePercent:100}],
 ['analog',{engineeringMin:0,engineeringMax:0,engineeringValue:0}],
 ['analog',{engineeringMin:0,engineeringMax:1800,engineeringValue:1801}],
 ['analog',{engineeringMin:0,engineeringMax:1800,engineeringValue:''}],
 ['unknown',{}]
];
invalid.forEach(([mode,values])=>assert.throws(()=>calculate(mode,values),'Must reject '+mode));
const file=fs.readFileSync(path.join(__dirname,'../portal/src/main/resources/static/industrial-commissioning.html'),'utf8');
for(const field of ['industrial-commissioning-math.js','id="calculate"','id="reset"','id="fields"','NOT CODE VERIFIED']){
 assert.ok(file.includes(field),'Industrial UI missing '+field);
}
const script=file.match(/<script>([\s\S]*?)<\/script>/);
assert.ok(script);
new (require('node:vm').Script)(script[1],{filename:'industrial-ui.js'});
// Native implementations are exercised independently by Android Gradle,
 // Swift self-tests and Windows Maven/JUnit workflows.
const read=relative=>fs.readFileSync(path.join(__dirname,'..',relative),'utf8');
for(const [platform,source,screen] of [
 ['Android','android-client/app/src/main/java/com/gfh/electricalworkbench/IndustrialFieldMath.kt',
  'android-client/app/src/main/java/com/gfh/electricalworkbench/IndustrialFieldActivity.kt'],
 ['iOS','ios-client/ElectricalWorkbench/IndustrialFieldMath.swift',
  'ios-client/ElectricalWorkbench/IndustrialFieldView.swift'],
 ['Windows','desktop-client/src/main/java/com/example/desktop/IndustrialFieldMath.java',
  'desktop-client/src/main/java/com/example/desktop/IndustrialFieldPane.java']
]){
 const src=read(source),ui=read(screen);
 for(const mode of ['ct','vt','power','pickup','fault','analog']){
   assert.ok(src.includes(mode),platform+' missing '+mode);
 }
 assert.ok(src.includes('codeComplianceVerified'),platform+' missing false compliance flag');
 assert.ok(ui.includes('NOT CODE VERIFIED'),platform+' missing Code warning');
}
assert.ok(read('android-client/app/src/main/AndroidManifest.xml').includes('IndustrialFieldActivity'));
assert.ok(read('android-client/app/src/main/java/com/gfh/electricalworkbench/MobileWorkbenchActivity.kt').includes('IndustrialFieldActivity::class.java'));
assert.ok(read('ios-client/ElectricalWorkbench/WorkbenchTools.swift').includes('IndustrialFieldView()'));
assert.ok(read('desktop-client/src/main/java/com/example/desktop/NativeCalculatorPane.java').includes('new IndustrialFieldPane()'));
const circuitWeb=read('portal/src/main/resources/static/circuit-sizing.html');
const circuitAndroid=read('android-client/app/src/main/java/com/gfh/electricalworkbench/CircuitSizingActivity.kt');
const circuitIOS=read('ios-client/ElectricalWorkbench/UnifiedCircuitSizingView.swift');
const circuitWindows=read('desktop-client/src/main/java/com/example/desktop/CircuitReviewPane.java');
for(const [platform,ui]of [
 ['web',circuitWeb],['Android',circuitAndroid],['iOS',circuitIOS],['Windows',circuitWindows]
]){
 for(const nominal of ['120','208','347','600'])
  assert.ok(ui.includes(nominal),platform+' missing '+nominal+' V fast preset');
}
const cec=JSON.parse(read('docs/CEC2024_TABLE_VERIFICATION_REGISTER.json'));
assert.equal(cec.referenceProfileId,'OESC_ON_2024');
assert.equal(cec.numericDatasetReleaseStatus,'AUTHORIZED_SOURCE_NOT_AVAILABLE');
assert.equal(cec.reviewRows.length,8);
assert.ok(cec.reviewRows.every(x=>x.status==='PENDING_AUTHORIZED_COPY' &&
  x.acceptedNumericRowCount===0 && x.approvedForDistribution===false));
console.log('Industrial commissioning passed: six guarded worksheets, invalid cases, four-platform UI wiring and provisional CEC audit gates.');
