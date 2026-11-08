/* Printable A4/Letter-ready snapshot from a SAVED project circuit revision.
 * All user values are HTML-escaped. This is NOT an engineering certificate. */
(function(root){
'use strict';
const safe=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({
  '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
}[c]));
const format=(n,unit='')=>{
  if(n===undefined||n===null||n==='')return 'Not supplied';
  return safe(n)+(unit?' '+unit:'');
};
function html(r,meta={}){
  if(!r||r.kind!=='circuitWorksheet'||!r.inputs||!r.results||
     r.results.codeComplianceVerified!==false)
    throw Error('Select a saved, unverified circuit worksheet revision for reporting.');
  const i=r.inputs,o=r.results,assumptions=r.assumptions||{};
  const field=(label,value,unit='')=>'<tr><th>'+safe(label)+'</th><td>'+
     format(value,unit)+'</td></tr>';
  const yesNo=value=>value===true?'WITHIN INPUT BOUNDS':'REVIEW / FAIL';
  const id=r._syncId||r.id||'Not available';
  const edited=String(r.saved||'Unknown UTC timestamp');
  return `
    <div class="pdf-head">
      <div class="pdf-kicker">ELECTRICAL WORKBENCH / FIELD CALCULATION RECORD</div>
      <h1>Electrical circuit calculation report</h1>
      <p class="pdf-warning">PRELIMINARY — NOT CODE VERIFIED — NOT FOR INSTALLATION APPROVAL</p>
    </div>
    <div class="pdf-report-meta">
      <strong>Project:</strong> ${safe(r.project)}<br>
      <strong>Worksheet:</strong> ${safe(r.title)} · Revision ${safe(r.revision)}<br>
      <strong>Saved UTC:</strong> ${safe(edited)}<br>
      <strong>Record ID:</strong> ${safe(id)}<br>
      <strong>Client / employer:</strong> ${safe(meta.client||'Not supplied')}<br>
      <strong>Site:</strong> ${safe(meta.site||'Not supplied')}
    </div>
    <h2>Code reference and source status</h2>
    <table>${field('Regulatory profile','OESC_ON_2024 / CSA C22.1:24')}
      ${field('Jurisdiction',r.jurisdiction||'Ontario, Canada')}
      ${field('Edition',r.codeEdition||'CEC_2024')}
      ${field('Entered table references',r.referenceTables||'User supplied')}
      ${field('Numeric table verification','AUTHORIZED_TABLE_VERIFICATION_REQUIRED')}
      ${field('Equipment termination markings verified','NO')}
      ${field('Code compliance verified','NO')}</table>
    <h2>Recorded circuit and conductor inputs</h2>
    <table>${field('Actual load',i.loadA,'A')}
      ${field('Selected design multiplier',i.designMultiplier)}
      ${field('Supply voltage',i.voltageV,'V')}
      ${field('Phases',i.phases)}
      ${field('One-way length',i.oneWayLengthM,'m')}
      ${field('Selected conductor',i.conductorDescription)}
      ${field('Ampacity table',i.tableReference)}
      ${field('Equipment termination temperature',i.terminalC,'°C')}
      ${field('Insulation temperature',i.insulationC,'°C')}
      ${field('Entered termination-column ampacity',i.terminalColumnAmpacityA,'A')}
      ${field('Entered ambient factor',i.ambientFactor)}
      ${field('Entered grouping factor',i.groupingFactor)}
      ${field('Entered conductor area',i.circularMils,'circular mils')}
      ${field('Entered conductor K factor',i.kOhmCircularMilPerFoot,'Ω·cmil/ft')}
      ${field('Voltage drop target',i.maxVoltageDropPercent,'%')}
      ${field('Entered OCPD',i.selectedOcpdA,'A')}</table>
    <h2>Arithmetic results</h2>
    <table>${field('Required design current',o.designCurrentA,'A')}
      ${field('Corrected conductor ampacity',o.adjustedAmpacityA,'A')}
      ${field('Conductor margin',o.conductorMarginA,'A')}
      ${field('Calculated voltage drop',o.voltageDropV,'V')}
      ${field('Calculated voltage drop percentage',o.voltageDropPercent,'%')}
      ${field('Allowable entered drop',o.allowableDropV,'V')}
      ${field('Required area for entered VD target',o.minimumCircularMilsForDrop,'circular mils')}
      ${field('Max one-way run at entered area',o.maximumOneWayLengthM,'m')}
      ${field('Ampacity arithmetic screen',yesNo(o.ampacityArithmeticFits))}
      ${field('Voltage drop screen',yesNo(o.voltageDropArithmeticFits))}
      ${field('Conservative OCPD arithmetic screen',yesNo(o.protectionScreenFits))}</table>
    <h2>Field notes and saved calculation</h2>
    <p><strong>Recorded notes:</strong> ${safe(assumptions.notes||'No notes recorded.')}</p>
    <p>K-factor voltage drop is a resistive approximation. It does not evaluate AC impedance, all Table D3 conditions or upstream feeder drop. Specialized equipment rules are not verified.</p>
    <pre class="pdf-calculation">${safe(r.result||'No saved worked calculation text.')}</pre>
    <h2>Review and sign-off</h2>
    <p><strong>Reviewer:</strong> ${safe(meta.reviewer||'___________________________')}</p>
    <p><strong>Review date:</strong> _____________________ &nbsp; <strong>Signature:</strong> _____________________</p>
    <p class="pdf-warning">No conductor, fuse, breaker or electrical installation has been approved by this report. Verify the authorized CEC/OESC reference, actual equipment markings, material, installation, neutral counting, correction factors, protection and bonding independently.</p>
  `;
}
const API={html,escapeHtml:safe};
root.CircuitProjectPrint=API;
if(typeof module!=='undefined'&&module.exports)module.exports=API;
})(typeof window!=='undefined'?window:this);
