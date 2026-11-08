/* Regulatory provenance and report safety acceptance checks.
 * Does not assert that any licensed CEC table row is verified. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const root=path.resolve(__dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const manifest=JSON.parse(read('docs/CEC2024_TABLE_VERIFICATION_REGISTER.json'));
assert.equal(manifest.referenceProfileId,'OESC_ON_2024');
assert.equal(manifest.effectiveDate,'2025-05-01');
assert.equal(manifest.numericDatasetReleaseStatus,'AUTHORIZED_SOURCE_NOT_AVAILABLE');
assert.equal(manifest.reviewRows.length,8);
for(const row of manifest.reviewRows){
 assert.equal(row.status,'PENDING_AUTHORIZED_COPY');
 assert.equal(row.sourceDocumentId,null);
 assert.equal(row.verifiedAt,undefined);
 assert.equal(row.reviewedAt,null);
 assert.equal(row.reviewer,null);
 assert.equal(row.acceptedNumericRowCount,0);
 assert.equal(row.approvedForDistribution,false);
}
const math=require('../portal/src/main/resources/static/circuit-review-math.js');
const ar=math.circuitReview({
 loadA:40,designMultiplier:1.25,voltageV:600,phases:3,
 oneWayLengthM:40,circularMils:66360,kOhmCircularMilPerFoot:12.9,
 terminalColumnAmpacityA:55,terminalC:60,insulationC:90,
 ambientFactor:0.58,groupingFactor:0.8,selectedOcpdA:50,maxVoltageDropPercent:3
});
assert.ok(Math.abs(ar.adjustedAmpacityA-25.52)<1e-10);
assert.equal(ar.codeComplianceVerified,false);
assert.equal(ar.ampacityArithmeticFits,false);
const Print=require('../portal/src/main/resources/static/circuit-project-print.js');
const report={
 kind:'circuitWorksheet',schemaVersion:1,_syncId:'unique-1',
 project:'Substation <img src=x onerror=alert(1)>',
 title:'600V <script>alert(1)</script>',revision:2,
 saved:'2026-11-08T13:00:00Z',codeEdition:'CEC_2024',
 jurisdiction:'Ontario',referenceTables:'Table 2; Table 5A/5C',
 inputs:{
  loadA:40,designMultiplier:1.25,voltageV:600,phases:3,oneWayLengthM:40,
  conductorDescription:'Copper <test>',tableReference:'Table 2',terminalC:60,insulationC:90,
  terminalColumnAmpacityA:55,ambientFactor:.58,groupingFactor:.8,
  circularMils:66360,kOhmCircularMilPerFoot:12.9,maxVoltageDropPercent:3,selectedOcpdA:50
 },
 results:ar,
 assumptions:{notes:'Confirm <terminal> before use'},
 result:'Original calculation\nNOT CODE VERIFIED'
};
const html=Print.html(report,{client:'<client>',site:'Distribution <bay>',reviewer:'<reviewer>'});
for(const phrase of ['PRELIMINARY','NOT CODE VERIFIED','OESC_ON_2024',
 'AUTHORIZED_TABLE_VERIFICATION_REQUIRED',
 'Substation &lt;img','&lt;script&gt;','600V','Revision 2',
 '2026-11-08','Actual load','Terminal temperature','Corrected conductor ampacity',
 'Minimum circular mils','Field notes','Signature']){
 assert.ok(html.includes(phrase),'Missing report field '+phrase);
}
assert.ok(!html.includes('<script>')&&!html.includes('<img src=x'),'Unsafe HTML in PDF preview');
assert.throws(()=>Print.html({...report,results:{...ar,codeComplianceVerified:true}}),/saved/);
assert.throws(()=>Print.html({...report,inputs:null}),/saved/);
const web=read('portal/src/main/resources/static/circuit-sizing.html');
for(const s of ['printSavedPdf','reportClient','reportSite','reportReviewer',
 'CircuitProjectPrint.html(selected','window.print()','@media print','size:letter']){
 assert.ok(web.includes(s),'Browser print-to-PDF UI missing '+s);
}
const windows=read('desktop-client/src/main/java/com/example/desktop/CircuitReviewPane.java');
assert.ok(windows.includes('CircuitWorksheetPdf.render(selection.record()'));
const d=read('desktop-client/src/main/java/com/example/desktop/CircuitWorksheetPdf.java');
const a=read('android-client/app/src/main/java/com/gfh/electricalworkbench/CircuitWorksheetPdf.kt');
const i=read('ios-client/ElectricalWorkbench/CircuitProjectReportText.swift');
const renderer=read('ios-client/ElectricalWorkbench/CircuitProjectPDF.swift');
for(const [name,body] of [['Windows',d],['Android',a],['iOS',i]]){
 for(const phrase of ['OESC_ON_2024','AUTHORIZED_TABLE_VERIFICATION_REQUIRED',
   'codeComplianceVerified','terminalColumnAmpacityA','adjustedAmpacityA',
   'minimumCircularMilsForDrop','saved','signature']){
   assert.ok(body.toLowerCase().includes(phrase.toLowerCase()),
      name+' report missing '+phrase);
 }
}
assert.ok(renderer.includes('UIGraphicsPDFRenderer'));
assert.ok(a.includes('PdfDocument'));
assert.ok(read('android-client/app/src/main/java/com/gfh/electricalworkbench/CircuitSizingActivity.kt').includes('savePdf.launch('));
assert.ok(read('ios-client/ElectricalWorkbench/UnifiedCircuitSizingView.swift').includes('ShareLink(item:preparedPDF)'));
console.log('CEC table evidence / PDF reporting PASS: source remains unverified; web output sanitized; complete native report fields and export pathways.');
