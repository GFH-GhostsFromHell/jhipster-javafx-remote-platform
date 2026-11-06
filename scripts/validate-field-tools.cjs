/* Syntax, required control IDs and key content regression checks for static field tools. */
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const root=path.resolve(__dirname,'../portal/src/main/resources/static');
const files=['field-suite.html','troubleshooting-kb.html','bend-calcs.html','bender-calibration.html'];
let failed=false;
for(const name of files){
 const html=fs.readFileSync(path.join(root,name),'utf8');
 const scripts=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(match=>match[1]);
 if(!scripts.length)throw new Error('No inline script in '+name);
 for(let i=0;i<scripts.length;i++)new vm.Script(scripts[i],{filename:name+'#inline'+i});
 const ids=new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]));
 const literals=[...html.matchAll(/\$\('([^']+)'\)/g)].map(m=>m[1]);
 const missing=[...new Set(literals.filter(id=>!ids.has(id)))];
 if(missing.length){console.error(name+': missing DOM controls '+missing.join(', '));failed=true}
 console.log(name+': '+scripts.length+' JavaScript scripts parse; '+ids.size+' element IDs; '+missing.length+' missing control IDs');
}

const labHtml=fs.readFileSync(path.join(root,'bend-instruction-lab.html'),'utf8');
const labJs=fs.readFileSync(path.join(root,'bend-instruction-lab.js'),'utf8');
new vm.Script(labJs,{filename:'bend-instruction-lab.js'});
const labIds=new Set([...labHtml.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]));
const labRefs=[...labJs.matchAll(/\$\('([^']+)'\)/g)].map(m=>m[1]);
const labMissing=[...new Set(labRefs.filter(id=>!labIds.has(id)))];
if(labMissing.length){console.error('Bend lab is missing DOM IDs: '+labMissing.join(','));failed=true}

// Regression check: all points must contain finite X/Y/Z coordinates. An
// incomplete cross product made Android show only the background canvas.
const geometryStart=labJs.indexOf('const add=(a,b)');
const geometryEnd=labJs.indexOf("const canvas=$('tubeCanvas')");
if(geometryStart<0||geometryEnd<=geometryStart){
 console.error('Bend lab geometry test block could not be found');failed=true;
}else{
 const geometry=labJs.slice(geometryStart,geometryEnd);
 const shapes=[
  [{before:404,angle:90,plane:0},{before:350,angle:30,plane:90},{before:360,angle:90,plane:-90}],
  [{before:400,angle:30,plane:0},{before:380,angle:-30,plane:0}],
  [{before:400,angle:90,plane:0}],
  [{before:250,angle:30,plane:0},{before:300,angle:-30,plane:0},{before:300,angle:-30,plane:0},{before:300,angle:30,plane:0}]
 ];
 for(const [index,ops] of shapes.entries()){
  try{
   const model=vm.runInNewContext(geometry+'\ncalculatePath()',{
    ops,TEN_FOOT_EMT_MM:3048,
    v:name=>name==='radius'?120:name==='stock'?3048:name==='minStraight'?null:400,
    toMm:n=>n,RAD:Math.PI/180
   });
   if(model.pts.length<2||model.pts.some(point=>point.p.length!==3||point.p.some(n=>!Number.isFinite(n))))
    throw Error('Non-finite 3D coordinate');
   console.log('Bend 3D test '+(index+1)+': '+model.pts.length+' finite centerline points');
  }catch(error){console.error('Bend 3D geometry regression '+(index+1)+': '+error.message);failed=true}
 }
 // Regression: one 10 ft EMT cannot fit a developed centreline beyond 3,048 mm.
 // Confirm measured shoe clearance is enforced when supplied.
 const scenarios=[
  {name:'10 foot limit',ops:[{before:3300,angle:90,plane:0}],minStraight:null, expected:'CALCULATION NOT POSSIBLE'},
  {name:'tool bend spacing',ops:[{before:300,angle:30,plane:0},{before:25,angle:-30,plane:0}],minStraight:150,expected:'BENDS TOO CLOSE'}
 ];
 for(const check of scenarios){
  try{
   vm.runInNewContext(geometry+'\\ncalculatePath()',{
    ops:check.ops,TEN_FOOT_EMT_MM:3048,
    v:name=>name==='radius'?120:name==='stock'?3048:name==='minStraight'?check.minStraight:400,
    toMm:n=>n,RAD:Math.PI/180
   });
   console.error('Expected rejection not raised: '+check.name);failed=true;
  }catch(error){
   if(!String(error.message).includes(check.expected)){
    console.error('Unexpected stock-check failure '+check.name+': '+error.message);failed=true;
   }else console.log('Bend stock regression OK: '+check.name);
  }
 }
}
if(!/src="\\/bend-instruction-lab\\.js(?:\?[^"]*)?"/.test(labHtml)){console.error('Bend lab JS is not linked');failed=true}
for(const key of ['kickExtra','kick90','back','roll','saddle3','saddle4','offset','segment','custom']){
 if(!labHtml.includes('value="'+key+'"')){console.error('Missing bend preset '+key);failed=true}
}
for(const method of ['pointerdown','pointermove','wheel','calculatePath','getLayout','renderMarks','renderStep']){
 if(!labJs.includes(method)){console.error('Missing 3D/instruction feature '+method);failed=true}
}
console.log('Experimental Bend Lab: JS syntax valid, '+labIds.size+' controls, '+labMissing.length+' missing IDs');
const kb=fs.readFileSync(path.join(root,'troubleshooting-kb.html'),'utf8');
const procedures=[...kb.matchAll(/\btopic\('[a-z0-9-]+'/g)].length;
if(procedures<20){console.error('Expected >=20 troubleshooting procedures, found '+procedures);failed=true}
for(const term of ['battery-electric','PLC input ON','VFD DC-bus overvoltage','Furnace','lockout/tagout']){
 if(!kb.includes(term)){console.error('Missing diagnostic content: '+term);failed=true}
}
const suite=fs.readFileSync(path.join(root,'field-suite.html'),'utf8');
const views=['home','trouble','bend','blueprint','controls','sld','code','quote','projects','commission','capture'];
for(const view of views){if(!suite.includes('id="view-'+view+'"')){console.error('Missing suite view '+view);failed=true}}
const sw=fs.readFileSync(path.join(root,'field-tools-sw.js'),'utf8');
new vm.Script(sw,{filename:'field-tools-sw.js'});
if(sw.includes('/api/')){console.error('Offline cache must not cache API calls');failed=true}
console.log('KB procedures: '+procedures+'; Suite views: '+views.length+'; Offline worker syntax validated');
if(failed)process.exit(1);
