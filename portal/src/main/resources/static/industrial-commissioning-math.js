/* Pure, offline industrial electrical arithmetic; never outputs approved relay
 * settings, available fault current, Code compliance, or actual equipment safety.
 */
(function(root){
'use strict';
const MODES={
 ct:{label:'CT secondary / primary current',fields:[
    ['primaryRatedA','CT primary nameplate (A)',400],
    ['secondaryRatedA','CT secondary nameplate (A)',5],
    ['actualPrimaryA','Measured / expected primary (A)',200]]},
 vt:{label:'PT / VT secondary voltage',fields:[
    ['primaryRatedV','VT rated primary (V)',4160],
    ['secondaryRatedV','VT rated secondary (V)',120],
    ['actualPrimaryV','Measured primary (V)',4000]]},
 power:{label:'Balanced three-phase power',fields:[
    ['lineVoltageV','Line-to-line voltage (V)',600],
    ['lineCurrentA','Line current (A)',100],
    ['powerFactor','Power factor (0–1)',0.85]]},
 pickup:{label:'CT-based relay-current translation',fields:[
    ['primaryRatedA','CT rated primary (A)',400],
    ['secondaryRatedA','CT rated secondary (A)',5],
    ['primaryPickupA','Specified primary pickup current (A)',240]]},
 fault:{label:'Transformer ideal secondary fault estimate',fields:[
    ['transformerKva','Transformer apparent rating (kVA)',75],
    ['secondaryVoltageV','Secondary line voltage (V)',600],
    ['impedancePercent','Nameplate impedance (%)',5]]},
 analog:{label:'4–20 mA instrumentation scaling',fields:[
    ['engineeringMin','4 mA engineering value',0],
    ['engineeringMax','20 mA engineering value',1800],
    ['engineeringValue','Requested engineering value',1200]]}
};
function positive(v,name){
 const x=Number(v);
 if(v===null||v===undefined||String(v).trim()===''||!Number.isFinite(x)||x<=0)
  throw Error(name+' must be a positive, finite value.');
 return x;
}
function finite(v,name){
 const x=Number(v);
 if(v===null||v===undefined||String(v).trim()===''||!Number.isFinite(x))
  throw Error(name+' must be a finite value.');
 return x;
}
function good(values){
 if(Object.values(values).some(v=>typeof v==='number'&&!Number.isFinite(v)))
  throw Error('Calculated output is outside the supported range.');
 return {...values,codeComplianceVerified:false,
  referenceProfileId:'OESC_ON_2024',
  numericDatasetVerificationStatus:'AUTHORIZED_TABLE_VERIFICATION_REQUIRED'};
}
function calculate(mode,input){
 if(!Object.hasOwn(MODES,mode))throw Error('Choose a supported industrial worksheet.');
 const v=input||{};
 if(mode==='ct'){
  const p=positive(v.primaryRatedA,'CT primary rating'),s=positive(v.secondaryRatedA,'CT secondary rating');
  const actual=positive(v.actualPrimaryA,'Primary current');
  return good({ratio:p/s,secondaryCurrentA:actual*s/p,
   reconstructedPrimaryA:(actual*s/p)*p/s,
   note:'Ideal CT ratio only. Actual polarity, burden, saturation, class and wiring must be field verified.'});
 }
 if(mode==='vt'){
  const p=positive(v.primaryRatedV,'VT primary rating'),s=positive(v.secondaryRatedV,'VT secondary rating');
  const actual=positive(v.actualPrimaryV,'Primary voltage');
  return good({ratio:p/s,secondaryVoltageV:actual*s/p,
   note:'Ideal ratio only. Check VT configuration, burden, grounding, fuse protection and equipment labels.'});
 }
 if(mode==='power'){
  const volts=positive(v.lineVoltageV,'Line voltage'),amp=positive(v.lineCurrentA,'Line current');
  const pf=positive(v.powerFactor,'Power factor');
  if(pf>1)throw Error('Power factor must be between zero and one.');
  const kva=Math.sqrt(3)*volts*amp/1000;
  return good({apparentPowerKva:kva,realPowerKw:kva*pf,
   note:'Balanced, sinusoidal 3-phase power only. Does not analyze harmonics or unbalanced currents.'});
 }
 if(mode==='pickup'){
  const p=positive(v.primaryRatedA,'CT primary rating'),s=positive(v.secondaryRatedA,'CT secondary rating');
  const pickup=positive(v.primaryPickupA,'Primary pickup');
  return good({ratio:p/s,secondaryPickupA:pickup*s/p,
   note:'Translation of an ALREADY SPECIFIED primary pickup only. Not an approved relay setting; verify protection study, CT saturation, burden, wiring and relay manufacturer.'});
 }
 if(mode==='fault'){
  const kva=positive(v.transformerKva,'Transformer kVA');
  const volts=positive(v.secondaryVoltageV,'Secondary voltage');
  const impedance=positive(v.impedancePercent,'Nameplate impedance');
  if(impedance>=100)throw Error('Impedance percent must be less than 100 for this simple approximation.');
  const fla=kva*1000/(Math.sqrt(3)*volts);
  return good({transformerFullLoadCurrentA:fla,
    idealInfiniteSourceFaultA:fla*100/impedance,
    note:'IDEAL infinite-source, symmetrical transformer-only estimate. NOT available fault current or an arc-flash assessment; real sources, impedance, X/R and all contributions must be evaluated.'});
 }
 const min=finite(v.engineeringMin,'4 mA engineering value');
 const max=finite(v.engineeringMax,'20 mA engineering value');
 const value=finite(v.engineeringValue,'Engineering value');
 if(max<=min)throw Error('20 mA engineering value must exceed 4 mA value.');
 if(value<min||value>max)throw Error('Requested engineering value is outside the configured span.');
 const fraction=(value-min)/(max-min);
 return good({analogMilliamp:4+16*fraction,spanPercent:100*fraction,
  note:'Linear 4–20 mA translation only. Verify input range, wiring, fail-safe behavior and device calibration.'});
}
root.IndustrialCommissioningMath={MODES,calculate};
if(typeof module!=='undefined'&&module.exports)module.exports={MODES,calculate};
})(typeof window!=='undefined'?window:this);
