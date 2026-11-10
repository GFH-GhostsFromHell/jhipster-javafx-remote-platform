// Export/restore portable HMI designs; never include credential hashes, secrets or pairing codes.
(() => {
'use strict';
const $=id=>document.getElementById(id);
const api=(path,method,body)=>window.studioAdminApi(path,method,body);
const say=(msg,error=false)=>{const out=$('designBackupStatus');out.textContent=msg;out.style.color=error?'#ffb0b0':'#9ce9c7'};
const validId=v=>typeof v==='string'&&/^[A-Za-z][A-Za-z0-9_-]{1,47}$/.test(v);
function checkImport(pack){
 if(!pack||pack.schemaVersion!==1||!pack.configuration||!Array.isArray(pack.screens)||pack.screens.length>200)
   throw Error('Not an Industrial Control Studio design backup v1');
 if(!pack.configuration.tags||!Array.isArray(pack.configuration.tags)||pack.configuration.tags.length>200)
   throw Error('Invalid tag configuration');
 const known=new Set(pack.configuration.tags.map(t=>t.id)),seen=new Set();
 for(const s of pack.screens){
   if(!validId(s.id)||seen.has(s.id)||!s.layout||!Array.isArray(s.layout.widgets))
      throw Error('Duplicate, invalid or unsupported HMI screen ID');
   seen.add(s.id);
   if(s.layout.widgets.length<1||s.layout.widgets.length>40)
      throw Error('Invalid number of widgets');
   for(const w of s.layout.widgets) {
      if(!['TEXT','VALUE','GAUGE','STATUS'].includes(w.type)) throw Error('Unsupported widget type');
      if(w.type!=='TEXT'&&!known.has(w.tagId)) throw Error('Missing tag for screen '+s.id);
   }
 }
 return pack;
}
async function download(){
 try{
  say('Retrieving saved designs from cloud…');
  const [cfg,screens,pcs]=await Promise.all([
   api('/api/studio/config'),api('/api/studio/screens'),api('/api/studio/workstations')
  ]);
  const bundle={
   format:'industrial-control-studio-designs',
   schemaVersion:1,
   exportedAt:new Date().toISOString(),
   warning:'DESIGNS ONLY; excludes device credentials, pairing codes and password hashes. Restore requires workstation re-enrollment.',
   configuration:cfg.configuration,
   screens:screens.map(s=>({id:s.id,layout:s.layout})),
   workstationAssignments:pcs.map(d=>({id:d.id,name:d.name,screenId:d.screenId,enabled:d.enabled}))
  };
  const blob=new Blob([JSON.stringify(bundle,null,2)+'\n'],{type:'application/json'});
  const url=URL.createObjectURL(blob);
  const link=document.createElement('a');
  link.href=url;link.download='industrial-studio-designs-'+new Date().toISOString().slice(0,10)+'.json';
  document.body.append(link);link.click();link.remove();
  setTimeout(()=>URL.revokeObjectURL(url),60000);
  say('Design backup downloaded: '+bundle.screens.length+' screens, '+bundle.workstationAssignments.length+
      ' PC assignments. Store it securely: tag mappings and Java source drafts may be sensitive.');
 }catch(e){say('Export failed: '+e.message,true)}
}
async function restore(){
 const file=$('designBackupFile').files?.[0];
 if(!file){say('Select a saved design JSON file first.',true);return}
 if(file.size>1_000_000){say('Backup exceeds 1 MB, refusing import.',true);return}
 let pack;
 try{
    pack=checkImport(JSON.parse(await file.text()));
 }catch(e){say('Import validation failed: '+e.message,true);return}
 const msg='Import '+pack.screens.length+' screens and overwrite the current tag mapping? '+
   'This can replace existing HMI configurations, cannot be undone automatically, and may leave devices assigned to changed screens. '+
   'PC enrollment secrets are NOT imported. Take a backup of the current configuration first.';
 if(!window.confirm(msg))return;
 $('restoreDesigns').disabled=true;
 try{
   const latest=await api('/api/studio/config');
   await api('/api/studio/config','PUT',{expectedRevision:latest.revision,configuration:pack.configuration});
   let count=0;
   for(const s of pack.screens){
     const existing=await api('/api/studio/screens');
     const current=existing.find(x=>x.id===s.id);
     if(current){
       await api('/api/studio/screens/'+encodeURIComponent(s.id),'PUT',
         {expectedRevision:current.revision,layout:s.layout});
     }else{
       await api('/api/studio/screens','POST',{id:s.id,layout:s.layout});
     }
     count++;
   }
   say('Restored '+count+' screen definitions and tag mappings. Refresh the designer. '+
       'Register PCs again on a new database; secrets were deliberately excluded.');
   window.dispatchEvent(new Event('studio:connected'));
 }catch(e){
   say('Restore stopped after a partial change: '+e.message+
       '. Review the current configuration before retrying; this process is not atomic.',true);
 }finally{$('restoreDesigns').disabled=false}
}
$('exportDesigns').onclick=download;
$('restoreDesigns').onclick=restore;
})();