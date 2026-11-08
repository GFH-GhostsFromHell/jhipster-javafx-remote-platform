'use strict';
const assert=require('node:assert/strict');
const http=require('node:http');
const {validateBuild,smoke}=require('./verify-live-portal.cjs');
const sha='f2a8b786b1f1539c447e44a7b98137eac6a13633';
const known={
 status:'UP',commit:sha,commitAvailable:true,
 numericDatasetVerified:false,codeComplianceVerified:false,
 referenceProfileId:'OESC_ON_2024'
};
assert.equal(validateBuild(known,sha),sha);
assert.equal(validateBuild(known,sha.substring(0,10)),sha);
assert.throws(()=>validateBuild({...known,commit:'unknown'},sha),/missing/);
assert.throws(()=>validateBuild({...known,commit:'0'.repeat(40)},sha),/does not match/);
assert.throws(()=>validateBuild({...known,numericDatasetVerified:true},sha),/unexpectedly/);
assert.throws(()=>validateBuild({...known,codeComplianceVerified:true},sha),/unexpectedly/);
let badHealth=false,badHome=false;
const server=http.createServer((req,res)=>{
 const endpoint=req.url||'';
 if(endpoint==='/api/public/build-info'){
   res.setHeader('Content-Type','application/json');
   res.end(JSON.stringify(known));
 }else if(endpoint==='/api/public/client-health'){
   res.setHeader('Content-Type','application/json');
   res.end(JSON.stringify({status:badHealth?'DOWN':'UP'}));
 }else if(endpoint==='/api/public/auth-options'){
   res.setHeader('Content-Type','application/json');
   res.end(JSON.stringify({password:true,google:false}));
 }else if(endpoint==='/index.html'){
   res.setHeader('Content-Type',badHome?'text/plain':'text/html');
   res.end('<title>Electrical Workbench</title>');
 }else{res.writeHead(404);res.end();}
});
server.listen(0,'127.0.0.1',async()=>{
 try {
   const url='http://127.0.0.1:'+server.address().port;
   const ok=await smoke(url,sha);
   assert.equal(ok.commit,sha);
   assert.equal(ok.staticLanding,'OK');
   badHealth=true;
   await assert.rejects(()=>smoke(url,sha),/not UP/);
   badHealth=false;badHome=true;
   await assert.rejects(()=>smoke(url,sha),/did not return HTML/);
   console.log('Production smoke verifier tests passed: exact commit, healthy endpoints, no false approval, login page and fail-closed mismatches.');
 }catch(e){console.error(e);process.exitCode=1;}
 finally{server.close();}
});
