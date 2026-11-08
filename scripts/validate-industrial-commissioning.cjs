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
console.log('Industrial commissioning passed: six guarded worksheets, invalid cases, source-status safety and mobile UI syntax.');
