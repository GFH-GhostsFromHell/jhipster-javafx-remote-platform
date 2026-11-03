(function(){
  const MEMBER_TIERS=[['TIER_1','Tier 1'],['TIER_2','Tier 2'],['TIER_3','Tier 3']];
  function make(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e}
  function option(value,label,current){const o=document.createElement('option');o.value=value;o.textContent=label;o.selected=value===current;return o}
  function tierLabel(t){return t==='TIER_4'?'Business / Enterprise':(t||'TIER_1').replace('_',' ')}
  function status(el,msg,ok=true){if(!el)return;el.textContent=msg;el.className='status '+(ok?'good':'bad')}

  function addTierFourCard(profile){
    const tiers=document.getElementById('tiers');
    if(!tiers||tiers.querySelector('[data-tier="TIER_4"]'))return;
    const card=make('div','tier');card.dataset.tier='TIER_4';
    if(profile?.subscriptionTier==='TIER_4')card.classList.add('active');
    const h=make('h3','', 'Tier 4 · Business / Enterprise');
    const d=make('div','', 'Tier 3 tools plus managed Tier 1–3 team seats and business oversight.');
    const note=make('div','photo-help','Enterprise accounts are provisioned by the platform administrator.');
    card.append(h,d,note);tiers.appendChild(card);
  }

  function applyManagedNotice(membership){
    if(!membership?.managed)return;
    const tiers=document.getElementById('tiers');
    const card=tiers?.closest('.card');
    if(card&&!document.getElementById('enterpriseManagedNotice')){
      const n=make('div','placeholder');n.id='enterpriseManagedNotice';n.style.marginTop='14px';
      const b=make('b','', 'Business-managed account');
      const detail=make('div','muted',`${membership.businessName||membership.ownerUsername} manages this seat as ${tierLabel(membership.assignedTier)}${membership.roleLabel?' · '+membership.roleLabel:''}.`);
      n.append(b,detail);card.appendChild(n);
    }
    ['buyTier2','buyTier3'].forEach(id=>{const b=document.getElementById(id);if(b){b.disabled=true;b.textContent='Managed by business account'}});
  }

  function applyTierFourOwnerBillingState(profile){
    if(profile?.subscriptionTier!=='TIER_4')return;
    ['buyTier2','buyTier3'].forEach(id=>{const b=document.getElementById(id);if(b){b.disabled=true;b.textContent='Enterprise account'}});
  }

  function installEnterpriseView(){
    if(document.getElementById('view-enterprise'))return;
    const nav=document.querySelector('.nav');
    const adminNav=document.getElementById('adminNav');
    const button=make('button');button.id='enterpriseNav';button.dataset.view='enterprise';
    button.innerHTML='<span class="icon">♟</span><span class="label">Business Team</span>';
    button.addEventListener('click',()=>{showView('enterprise',button);const t=document.getElementById('pageTitle');if(t)t.textContent='Business Team';loadEnterprise();});
    if(adminNav)nav.insertBefore(button,adminNav);else nav.appendChild(button);

    const wrap=document.querySelector('main.wrap');
    const section=make('section','view');section.id='view-enterprise';
    section.innerHTML=`
      <div class="section-title" style="margin-top:0"><div><h2>Business / Enterprise</h2><div class="muted">Manage the electricians, apprentices and other users assigned to this business account.</div></div><span class="pill">Tier 4</span></div>
      <div class="stats">
        <div class="stat"><div class="label">Managed seats</div><div id="entSeatCount" class="value">—</div><div class="sub">Users attached to this business</div></div>
        <div class="stat"><div class="label">Tier 1 seats</div><div id="entTier1" class="value">—</div><div class="sub">Core field tools</div></div>
        <div class="stat"><div class="label">Tier 2 seats</div><div id="entTier2" class="value">—</div><div class="sub">Expanded tools</div></div>
        <div class="stat"><div class="label">Tier 3 seats</div><div id="entTier3" class="value">—</div><div class="sub">Advanced / engineering tools</div></div>
      </div>
      <div class="grid">
        <section class="card"><h2>Business profile</h2><p class="muted">This name appears to users managed by the enterprise account.</p><div class="field"><label>Business / organization name</label><input id="entBusinessName" maxlength="255"></div><button class="btn" id="entSaveBusiness">Save business name</button><div id="entBusinessStatus" class="status"></div></section>
        <section class="card"><h2>Add an existing user</h2><p class="muted">The user must already have an Electrical Workbench account. Personal active paid subscriptions must be cancelled or transferred before the seat can be business-managed.</p><div class="field"><label>Username</label><input id="entAddUsername" placeholder="employee username"></div><div class="field"><label>Role / trade label</label><input id="entAddRole" maxlength="120" placeholder="Apprentice, Journeyperson, Foreperson..."></div><div class="field"><label>Assigned subscription tier</label><select id="entAddTier"><option value="TIER_1">Tier 1</option><option value="TIER_2">Tier 2</option><option value="TIER_3">Tier 3</option></select></div><button class="btn" id="entAddButton">Add to business</button><div id="entAddStatus" class="status"></div></section>
      </div>
      <section class="card" style="margin-top:18px"><div class="section-title" style="margin-top:0"><div><h2>Team roster</h2><div class="muted">Assign each managed user Tier 1, 2 or 3 and optionally label their field role.</div></div></div><div id="entTeamList"></div></section>`;
    wrap.appendChild(section);
    document.getElementById('entSaveBusiness').addEventListener('click',saveBusinessName);
    document.getElementById('entAddButton').addEventListener('click',addEnterpriseMember);
  }

  async function loadEnterprise(){
    const list=document.getElementById('entTeamList');
    if(!list)return;
    try{
      const data=await api('/api/enterprise');
      document.getElementById('entBusinessName').value=data.businessName||'';
      document.getElementById('entSeatCount').textContent=String(data.seatCount??0);
      document.getElementById('entTier1').textContent=String(data.tier1Count??0);
      document.getElementById('entTier2').textContent=String(data.tier2Count??0);
      document.getElementById('entTier3').textContent=String(data.tier3Count??0);
      renderMembers(data.members||[]);
    }catch(e){list.textContent=e.message||'Enterprise team could not be loaded.'}
  }

  function renderMembers(members){
    const list=document.getElementById('entTeamList');list.innerHTML='';
    if(!members.length){const p=make('div','placeholder');p.append(make('b','', 'No managed users yet'),make('div','muted','Add an existing user above to create the first business-managed seat.'));list.appendChild(p);return}
    members.forEach(u=>{
      const card=make('div','admin-user');
      const title=make('b','',u.displayName||u.username);card.append(title,document.createTextNode(' · '+u.username),document.createElement('br'));
      const meta=make('span','muted',(u.email||'No email')+' · '+(u.enabled?'Enabled':'Disabled')+(u.authProvider?' · '+u.authProvider:''));card.appendChild(meta);
      const controls=make('div');controls.style.cssText='display:grid;grid-template-columns:minmax(140px,.7fr) minmax(180px,1fr) auto auto auto;gap:8px;align-items:end;margin-top:12px';
      const tierWrap=make('div','field');tierWrap.style.margin='0';tierWrap.appendChild(make('label','', 'Seat tier'));const tier=make('select');MEMBER_TIERS.forEach(([v,l])=>tier.appendChild(option(v,l,u.subscriptionTier)));tierWrap.appendChild(tier);
      const roleWrap=make('div','field');roleWrap.style.margin='0';roleWrap.appendChild(make('label','', 'Role / trade'));const role=make('input');role.maxLength=120;role.value=u.roleLabel||'';role.placeholder='Apprentice / Journeyperson';roleWrap.appendChild(role);
      const saveTier=make('button','btn ghost','Save tier');const saveRole=make('button','btn ghost','Save role');const remove=make('button','btn secondary','Remove');
      const rowStatus=make('div','status');rowStatus.style.gridColumn='1 / -1';
      saveTier.addEventListener('click',async()=>{try{await api('/api/enterprise/members/'+encodeURIComponent(u.username)+'/tier',jsonOpt('PUT',{subscriptionTier:tier.value}));status(rowStatus,'Tier updated.');await loadEnterprise()}catch(e){status(rowStatus,e.message,false)}});
      saveRole.addEventListener('click',async()=>{try{await api('/api/enterprise/members/'+encodeURIComponent(u.username)+'/role',jsonOpt('PUT',{roleLabel:role.value}));status(rowStatus,'Role updated.');await loadEnterprise()}catch(e){status(rowStatus,e.message,false)}});
      remove.addEventListener('click',async()=>{if(!confirm('Remove '+u.username+' from this business account? Their prior personal tier will be restored.'))return;try{await api('/api/enterprise/members/'+encodeURIComponent(u.username),{method:'DELETE'});await loadEnterprise()}catch(e){status(rowStatus,e.message,false)}});
      controls.append(tierWrap,roleWrap,saveTier,saveRole,remove,rowStatus);card.appendChild(controls);list.appendChild(card);
    });
  }

  async function saveBusinessName(){const s=document.getElementById('entBusinessStatus');try{await api('/api/enterprise',jsonOpt('PUT',{businessName:document.getElementById('entBusinessName').value}));status(s,'Business name saved.');await loadEnterprise()}catch(e){status(s,e.message,false)}}
  async function addEnterpriseMember(){const s=document.getElementById('entAddStatus');try{await api('/api/enterprise/members',jsonOpt('POST',{username:document.getElementById('entAddUsername').value,subscriptionTier:document.getElementById('entAddTier').value,roleLabel:document.getElementById('entAddRole').value}));document.getElementById('entAddUsername').value='';document.getElementById('entAddRole').value='';status(s,'User added to the business account.');await loadEnterprise()}catch(e){status(s,e.message,false)}}

  async function init(){
    try{
      const p=await api('/api/profile');
      addTierFourCard(p);
      applyTierFourOwnerBillingState(p);
      let membership={managed:false};
      try{membership=await api('/api/enterprise/membership')}catch(e){}
      applyManagedNotice(membership);
      if(p.subscriptionTier==='TIER_4'){
        installEnterpriseView();
        const badge=document.getElementById('tierBadge');if(badge)badge.textContent='Business / Enterprise';
        const dash=document.getElementById('dashTier');if(dash)dash.textContent='Business / Enterprise';
      }
    }catch(e){}
  }
  if(document.readyState==='complete')init();else window.addEventListener('load',init);
})();
