/**
 * Read-only production smoke check. Never registers an account, charges
 * a payment method, writes records or accesses authenticated user data.
 * Node 18+ built-ins only. Retries to allow Render's rolling deploy.
 */
'use strict';
const assert=require('node:assert/strict');
const DEFAULT_BASE='https://remote-javafx-portal.onrender.com';
function validateBuild(payload,expected) {
  if(!payload||payload.status!=='UP')throw Error('Build info does not report UP');
  if(!/^[a-f0-9]{40}$/.test(String(payload.commit)))
    throw Error('Build commit is missing or not a full SHA; verify RENDER_GIT_COMMIT injection.');
  if(expected && !payload.commit.startsWith(expected.toLowerCase()))
    throw Error('Production commit '+payload.commit.substring(0,12)+
      ' does not match expected '+expected.substring(0,12));
  if(payload.codeComplianceVerified!==false || payload.numericDatasetVerified!==false)
    throw Error('Production unexpectedly claims verified numerical Code data.');
  return payload.commit;
}
async function get(url,timeoutMs=14000){
  const response=await fetch(url,{
    method:'GET',redirect:'manual',cache:'no-store',
    signal:AbortSignal.timeout(timeoutMs),
    headers:{'Accept':'application/json','Cache-Control':'no-cache'}
  });
  if(!response.ok)throw Error('HTTP '+response.status+' at '+new URL(url).pathname);
  return response;
}
async function smoke(base,expected){
  const build=await get(base+'/api/public/build-info');
  const payload=await build.json();
  const sha=validateBuild(payload,expected);
  const health=await (await get(base+'/api/public/client-health')).json();
  if(health.status!=='UP')throw Error('Client health is not UP');
  const auth=await (await get(base+'/api/public/auth-options')).json();
  if(auth.password!==true)throw Error('Public password login status missing');
  const home=await get(base+'/index.html');
  if(!/text\/html/i.test(home.headers.get('content-type')||''))
    throw Error('Public sign-in landing page did not return HTML');
  const html=await home.text();
  if(!/Electrical Workbench|Remote JavaFX|electrical/i.test(html))
    throw Error('Public sign-in landing page is not the expected application');
  // Pages requiring login are intentionally not probed with anonymous secrets.
  return {commit:sha,health:health.status,authStatus:'public options readable',
    staticLanding:'OK',referenceProfileId:payload.referenceProfileId};
}
async function waitFor(base,expected,attempts=9,delayMs=20000){
  if(!Number.isInteger(attempts)||attempts<1||attempts>50)throw Error('Invalid max attempts.');
  for(let i=1;i<=attempts;i++){
    try{
      const result=await smoke(base,expected);
      process.stdout.write('PRODUCTION VERIFIED '+JSON.stringify(result)+'\n');
      return result;
    }catch(e){
      process.stderr.write('Production check '+i+'/'+attempts+': '+e.message+'\n');
      if(i===attempts)throw e;
      await new Promise(resolve=>setTimeout(resolve,delayMs));
    }
  }
}
if(require.main===module){
  const args=Object.fromEntries(process.argv.slice(2).map(a=>{
    const idx=a.indexOf('=');return idx<0?[a,'']:[a.slice(0,idx),a.slice(idx+1)];
  }));
  const base=String(args['--base']||DEFAULT_BASE).replace(/\/$/,'');
  if(!/^https:\/\/[a-z0-9.-]+(?::443)?$/i.test(base)){
    process.stderr.write('Only HTTPS production base URLs are supported.\n');
    process.exit(2);
  }
  const expected=String(args['--expected']||'').toLowerCase();
  if(expected && !/^[0-9a-f]{7,40}$/.test(expected)){
    process.stderr.write('Expected commit must be a valid Git SHA prefix.\n');
    process.exit(2);
  }
  const attempts=Number(args['--attempts']||9);
  waitFor(base,expected,attempts,Number(args['--delay-ms']||20000))
    .catch(e=>{process.stderr.write('PRODUCTION NOT VERIFIED: '+e.message+'\n');process.exitCode=1;});
}
module.exports={validateBuild,smoke,waitFor};
