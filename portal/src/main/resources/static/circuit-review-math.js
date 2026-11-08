/* Arithmetic-only, installation-agnostic single-circuit sizing worksheet.
 * No code table is imported or certified. Users provide actual Code-table data. */
(function(root){
 'use strict';
 const METRES_TO_FEET=3.280839895013123;
 function positive(n,label){
  if(!Number.isFinite(n)||n<=0)throw Error('Enter a positive finite '+label+'.');
  return n;
 }
 function circuitReview(v){
  if(!v||typeof v!=='object')throw Error('Enter circuit details.');
  const load=positive(v.loadA,'load current');
  const basis=positive(v.designMultiplier,'load sizing multiplier');
  if(basis!==1&&basis!==1.25)throw Error('Select 100% or 125% general-circuit sizing treatment.');
  const voltage=positive(v.voltageV,'system voltage');
  const ph=Number(v.phases);
  if(ph!==1&&ph!==3)throw Error('Select 1-phase two-wire or balanced 3-phase.');
  const length=positive(v.oneWayLengthM,'one-way length');
  const cm=positive(v.circularMils,'selected circular-mil area');
  const k=positive(v.kOhmCircularMilPerFoot,'resistance K factor');
  const base=positive(v.terminalColumnAmpacityA,'termination-column ampacity');
  const term=Number(v.terminalC),ins=Number(v.insulationC);
  if(![60,75,90].includes(term)||![60,75,90].includes(ins)||ins<term)
   throw Error('Choose verified 60, 75 or 90°C terminations and compatible insulation.');
  const amb=positive(v.ambientFactor,'ambient correction factor');
  const grp=positive(v.groupingFactor,'grouping correction factor');
  if(amb>1||grp>1)throw Error('Correction factors above 1 require separate review; enter verified factors from 0 to 1.');
  const ocpd=positive(v.selectedOcpdA,'selected OCPD rating');
  const pct=positive(v.maxVoltageDropPercent,'voltage-drop target');
  if(pct>=100)throw Error('Voltage-drop target must be less than 100%.');
  const design=load*basis;
  const corrected=base*amb*grp;
  const phi=ph===3?Math.sqrt(3):2;
  const ft=length*METRES_TO_FEET;
  const drop=phi*k*load*ft/cm;
  const limit=voltage*pct/100;
  const minCM=phi*k*load*ft/limit;
  const maxM=(limit*cm/(phi*k*load))/METRES_TO_FEET;
  const derived=[design,corrected,drop,limit,minCM,maxM];
  if(derived.some(n=>!Number.isFinite(n)||n<=0))
   throw Error('Values exceed supported arithmetic range.');
  return {
   designCurrentA:design,adjustedAmpacityA:corrected,conductorMarginA:corrected-design,
   voltageDropV:drop,voltageDropPercent:drop/voltage*100,allowableDropV:limit,
   minimumCircularMilsForDrop:minCM,maximumOneWayLengthM:maxM,
   ampacityArithmeticFits:design<=corrected+1e-9,
   voltageDropArithmeticFits:drop<=limit+1e-9,
   protectionScreenFits:ocpd+1e-9>=design&&ocpd<=corrected+1e-9,
   codeComplianceVerified:false
  };
 }
 root.CircuitReviewMath={circuitReview};
 if(typeof module!=='undefined'&&module.exports)module.exports={circuitReview};
})(typeof window!=='undefined'?window:this);
