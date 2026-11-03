(function(){
  function tierLabel(t){return (t||'TIER_1').replace('_',' ')}
  function button(text,cls){const b=document.createElement('button');b.type='button';b.className=cls||'btn';b.textContent=text;return b}
  async function renderUsers(){
    const list=document.getElementById('userList');
    if(!list)return;
    const users=await api('/api/admin/users');
    list.innerHTML='<h3>Users</h3>';
    users.forEach(u=>{
      const card=document.createElement('div');card.className='admin-user';
      const title=document.createElement('b');title.textContent=u.displayName||u.username;card.appendChild(title);
      card.appendChild(document.createTextNode(' · '+u.username));
      card.appendChild(document.createElement('br'));
      const meta=document.createElement('span');meta.className='muted';meta.textContent=(u.email||'No email')+' · '+tierLabel(u.subscriptionTier)+' · '+(u.enabled?'Enabled':'Disabled');card.appendChild(meta);
      const controls=document.createElement('div');controls.style.cssText='display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:10px';
      const select=document.createElement('select');select.setAttribute('aria-label','Subscription tier for '+u.username);select.style.cssText='padding:9px 11px;border:1px solid #c7d3e0;border-radius:10px;background:white';
      [['TIER_1','Tier 1'],['TIER_2','Tier 2'],['TIER_3','Tier 3']].forEach(([v,l])=>{const o=document.createElement('option');o.value=v;o.textContent=l;o.selected=v===(u.subscriptionTier||'TIER_1');select.appendChild(o)});
      const save=button('Save tier','btn');
      const edit=button('Edit','btn secondary');
      const status=document.createElement('span');status.className='muted';status.style.fontSize='12px';
      save.addEventListener('click',async()=>{const old=save.textContent;try{save.disabled=true;save.textContent='Saving…';const r=await api('/api/admin/users/'+encodeURIComponent(u.username)+'/tier',jsonOpt('PUT',{subscriptionTier:select.value}));u.subscriptionTier=r.subscriptionTier;meta.textContent=(u.email||'No email')+' · '+tierLabel(u.subscriptionTier)+' · '+(u.enabled?'Enabled':'Disabled');status.textContent='Saved';status.className='good';}catch(e){status.textContent=e.message;status.className='bad';}finally{save.disabled=false;save.textContent=old;}});
      edit.addEventListener('click',()=>editUser(JSON.stringify(u)));
      controls.append(select,save,edit,status);card.appendChild(controls);list.appendChild(card);
    });
  }
  window.loadUsers=renderUsers;
  async function refreshWhenReady(){try{await api('/api/admin/users');await renderUsers();}catch(e){}}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>setTimeout(refreshWhenReady,0));else setTimeout(refreshWhenReady,0);
})();
