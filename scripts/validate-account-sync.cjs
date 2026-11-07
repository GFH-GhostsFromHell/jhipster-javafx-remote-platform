/* Local-first browser account sync regression tests, no network or external dependencies. */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const root=path.resolve(__dirname,'..');
const source=fs.readFileSync(path.join(root,'portal/src/main/resources/static/account-sync.js'),'utf8');
new vm.Script(source,{filename:'account-sync.js'});
const server=new Map();
let uuid=0;
function clone(x){return JSON.parse(JSON.stringify(x))}
function bucket(name){if(!server.has(name))server.set(name,new Map());return server.get(name)}
async function fetchMock(url,options={}){
  const parts=String(url).split('/');
  assert.equal(parts[1],'api');
  assert.equal(parts[2],'sync');
  const map=bucket(parts[3]);
  if(options.method==='POST'){
    assert.equal(parts[4],'batch');
    const pending=JSON.parse(options.body).records,accepted=[],conflicts=[];
    for(const item of pending){
      const old=map.get(item.id);
      if((old&&old.revision!==item.revision)||(!old&&item.revision!==0)){
        conflicts.push(clone(old||{id:item.id,revision:0,data:{},deleted:true}));
        continue;
      }
      const value={id:item.id,revision:(old?.revision||0)+1,
        data:clone(item.data),deleted:item.deleted,updatedAt:new Date().toISOString()};
      map.set(item.id,value);accepted.push(clone(value));
    }
    return {ok:true,json:async()=>({accepted,conflicts})};
  }
  return {ok:true,json:async()=>clone([...map.values()])};
}
function client(){
  const entries=new Map(),key='sample-projects';
  const environment={
    fetch:fetchMock,console,
    window:{addEventListener(){}},
    document:{addEventListener(){},hidden:false},
    localStorage:{
      getItem(id){return entries.has(id)?entries.get(id):null},
      setItem(id,value){entries.set(id,String(value))}
    },
    crypto:{randomUUID(){return 'test-'+(++uuid)}},
    setInterval(){},setTimeout,clearTimeout,Date,JSON,Math,Promise
  };
  vm.runInNewContext(source,environment,{filename:'account-sync.js'});
  const read=()=>JSON.parse(entries.get(key)||'[]');
  const write=x=>entries.set(key,JSON.stringify(x));
  const run=()=>environment.EWAccountSync.run();
  const boot=()=>environment.EWAccountSync.register('projects',key,()=>{});
  return {entries,read,write,run,boot};
}
const delay=()=>new Promise(done=>setTimeout(done,120));
async function run(){
  const a=client(),b=client();
  a.write([{id:'p1',name:'One',records:[]}]);a.boot();await delay();
  assert.equal(bucket('projects').get('p1').revision,1);
  b.write([]);b.boot();await delay();
  assert.equal(b.read().length,1,'another browser imports the account project');
  b.write([...b.read(),{id:'p2',name:'Two',records:[]}]);b.run();await delay();
  assert.equal(bucket('projects').get('p2').revision,1);
  a.run();await delay();
  assert.equal(a.read().length,2,'independent new project merges');
  b.write(b.read().filter(x=>x.id!=='p1'));b.run();await delay();
  assert.equal(bucket('projects').get('p1').deleted,true,'remove writes a tombstone');
  a.run();await delay();
  assert.deepEqual(a.read().map(x=>x.id),['p2'],'other browser applies tombstone');
  a.write(a.read().map(x=>x.id==='p2'?{...x,name:'Edit A'}:x));
  a.run();await delay();
  assert.equal(bucket('projects').get('p2').data.name,'Edit A');
  b.write(b.read().map(x=>x.id==='p2'?{...x,name:'Edit B'}:x));
  b.run();await delay();
  assert.equal(bucket('projects').get('p2').data.name,'Edit A',
    'stale local edit must not overwrite newer server revision');
  assert.equal(b.read().find(x=>x.id==='p2').name,'Edit B','local conflict retained');
  const backups=JSON.parse(b.entries.get('ewb.sync.conflicts.projects')||'[]');
  assert.ok(backups.some(x=>x.id==='p2'),'conflict backup retained');
  const sw=fs.readFileSync(path.join(root,'portal/src/main/resources/static/field-tools-sw.js'),'utf8');
  assert.ok(sw.includes('account-sync.js'));
  assert.ok(sw.includes('Never cache authenticated API responses'));
  const java=fs.readFileSync(path.join(root,
    'portal/src/main/java/com/example/remoteportal/web/AccountSyncResource.java'),'utf8');
  assert.ok(java.includes('principal.getName()'));
  assert.ok(java.includes('revision=?'));
  assert.ok(java.includes('deleted boolean'));
  console.log('Account sync test passed: imports, independent adds, delete tombstones, conflict backups.');
}
run().catch(error=>{console.error(error);process.exit(1)});
