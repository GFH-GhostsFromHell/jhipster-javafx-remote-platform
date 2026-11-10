/* Cross-platform acceptance: independent worked examples for the web math.
 * Android runs the same examples using CircuitReviewMathTest in Gradle. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const {circuitReview}=require('../portal/src/main/resources/static/circuit-review-math.js');
const approx=(actual,expected,tol=1e-3)=>{
 assert.ok(Number.isFinite(actual)&&Math.abs(actual-expected)<=tol,
   'Expected '+expected+', got '+actual);
};
const v={
 loadA:40,designMultiplier:1.25,voltageV:600,phases:3,
 oneWayLengthM:40,circularMils:66360,kOhmCircularMilPerFoot:12.9,
 terminalColumnAmpacityA:85,terminalC:75,insulationC:90,
 ambientFactor:1,groupingFactor:1,selectedOcpdA:50,
 maxVoltageDropPercent:3
};
const r=circuitReview(v);
approx(r.designCurrentA,50);
approx(r.adjustedAmpacityA,85);
approx(r.voltageDropV,1.76746,1e-4);
approx(r.voltageDropPercent,.294576,1e-4);
approx(r.minimumCircularMilsForDrop,6516.02666,.03);
approx(r.maximumOneWayLengthM,407.3648,.03);
assert.equal(r.ampacityArithmeticFits,true);
assert.equal(r.voltageDropArithmeticFits,true);
assert.equal(r.protectionScreenFits,true);
assert.equal(r.codeComplianceVerified,false);
const s=circuitReview({...v,phases:1});
approx(s.voltageDropV,2.04096,.02);
const hot=circuitReview({...v,terminalC:60,terminalColumnAmpacityA:55,
 ambientFactor:.58,groupingFactor:.8});
approx(hot.adjustedAmpacityA,25.52,1e-9);
assert.equal(hot.ampacityArithmeticFits,false);
assert.equal(hot.protectionScreenFits,false);
assert.equal(circuitReview({...v,circularMils:4000}).voltageDropArithmeticFits,false);
assert.equal(circuitReview({...v,selectedOcpdA:100}).protectionScreenFits,false);
const two=circuitReview({...v,oneWayLengthM:80});
approx(two.voltageDropV,2*r.voltageDropV,1e-9);
approx(two.minimumCircularMilsForDrop,2*r.minimumCircularMilsForDrop,1e-9);
for(const broken of [
 {...v,loadA:0},{...v,voltageV:NaN},{...v,phases:2},
 {...v,terminalC:90,insulationC:60},{...v,ambientFactor:1.2},
 {...v,groupingFactor:0},{...v,designMultiplier:1.1},
 {...v,oneWayLengthM:-1},{...v,maxVoltageDropPercent:100},
 {...v,circularMils:Infinity}
])assert.throws(()=>circuitReview(broken));
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const ui=read('portal/src/main/resources/static/circuit-sizing.html');
const android=read('android-client/app/src/main/java/com/gfh/electricalworkbench/CircuitSizingActivity.kt');
const math=read('android-client/app/src/main/java/com/gfh/electricalworkbench/CircuitReviewMath.kt');
const tests=read('android-client/app/src/test/java/com/gfh/electricalworkbench/CircuitReviewMathTest.kt');
for(const name of Object.keys(v)){
 assert.ok(ui.includes('id="'+name+'"'),'Web UI missing input '+name);
 assert.ok(math.includes(name+':'),'Android input struct missing '+name);
}
assert.ok(ui.includes('CircuitReviewMath.circuitReview(inputs)'));
assert.ok(android.includes('CircuitReviewMath.calculate(inputs)'));
assert.ok(tests.includes('1.76746')&&tests.includes('25.52'));
assert.ok(read('android-client/app/src/main/AndroidManifest.xml').includes('CircuitSizingActivity'));
assert.ok(read('android-client/app/src/main/java/com/gfh/electricalworkbench/MobileToolFinder.kt').includes('CircuitSizingActivity::class.java'));
assert.ok(read('portal/src/main/resources/static/calculators.html').includes('/circuit-sizing.html'));
console.log('Unified circuit sizing regression passed: web arithmetic, invalid inputs, Android wiring and navigation.');
