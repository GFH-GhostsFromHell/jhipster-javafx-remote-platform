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
if(!labHtml.includes('src="/bend-instruction-lab.js"')){console.error('Bend lab JS is not linked');failed=true}
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
