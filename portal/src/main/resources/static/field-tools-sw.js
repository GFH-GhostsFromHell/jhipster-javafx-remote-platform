'use strict';
/* Offline cache for public static Field Suite worksheets only.
   Never cache authenticated API responses, user photos, account pages, or credentials. */
const NAME='ew-field-suite-static-v2';
const ASSETS=['/field-suite.html','/troubleshooting-kb.html','/bend-calcs.html','/bender-calibration.html','/pull-box-calcs.html','/bend-instruction-lab.html','/bend-instruction-lab.js'];
self.addEventListener('install',event=>{
 event.waitUntil(caches.open(NAME).then(cache=>Promise.all(ASSETS.map(path=>cache.add(path).catch(()=>null)))).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
 event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('ew-field-suite-static-')&&key!==NAME).map(key=>caches.delete(key)))).then(()=>self.clients.claim()));
});
self.addEventListener('fetch',event=>{
 const req=event.request;
 if(req.method!=='GET'||req.mode==='navigate'&&!ASSETS.includes(new URL(req.url).pathname))return;
 const url=new URL(req.url);
 if(url.origin!==self.location.origin||!ASSETS.includes(url.pathname))return;
 event.respondWith(fetch(req).then(response=>{
  if(response.ok&&response.type==='basic'){
   const clone=response.clone();
   caches.open(NAME).then(cache=>cache.put(req,clone)).catch(()=>{});
  }
  return response;
 }).catch(()=>caches.match(req).then(cached=>cached||Response.error())));
});
