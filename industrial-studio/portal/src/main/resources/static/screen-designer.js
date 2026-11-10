// Declarative HMI designer for the isolated, read-only simulator.
// No dynamic Java, FXML, HTML, CSS, or third-party scripts are run from screen definitions.
(() => {
'use strict';
const el = id => document.getElementById(id);
let screens=[], devices=[], tags=[], current=null, draft=null, dirty=false;
let updateStates=[], selectedPc=null, updatePoller=null;
const api=(path,method,body)=>window.studioAdminApi(path,method,body);
const status=(message,error=false)=>{
    const node=el('designerStatus');node.textContent=message;node.style.color=error?'#ffb1b1':'#9feacb';
};
const safeId=id=>/^[A-Za-z][A-Za-z0-9_-]{1,47}$/.test(id);
const field=(tag,value,placeholder)=>{
    const element=document.createElement(tag);
    if(tag==='input')element.value=value??'';
    if(placeholder)element.placeholder=placeholder;
    return element;
};
const button=(title,callback)=>{
    const b=document.createElement('button');b.textContent=title;b.type='button';b.onclick=callback;return b;
};
function option(value,text){
    const o=document.createElement('option');o.value=value;o.textContent=text;return o;
}
function reset(){
    if(updatePoller){clearInterval(updatePoller);updatePoller=null;}
    screens=[];devices=[];tags=[];current=null;draft=null;dirty=false;
    updateStates=[];selectedPc=null;
    el('selectedPcId').replaceChildren();el('selectedPcStatus').textContent='Sign in to view registered PCs.';
    el('widgetsEditor').replaceChildren();el('screenPreview').replaceChildren();
    el('deviceEditor').replaceChildren();el('newDeviceSecret').replaceChildren();
    el('newDeviceSecret').classList.add('hidden');
}
function selectScreen(id){
    if(dirty && !window.confirm('Discard unsaved HMI screen changes?')){
        el('screenSelect').value=current?.id||'';return;
    }
    current=screens.find(s=>s.id===id)||null;
    draft=current?JSON.parse(JSON.stringify(current.layout)):null;dirty=false;
    renderEditor();
}
function renderScreenList(){
    const screenSelect=el('screenSelect'),newDevice=el('newDeviceScreen');
    const previous=current?.id;
    screenSelect.replaceChildren();newDevice.replaceChildren();
    for(const s of screens){
        screenSelect.append(option(s.id,s.layout.name+' ('+s.id+')'));
        newDevice.append(option(s.id,s.layout.name));
    }
    const next=screens.find(x=>x.id===previous)?.id||screens[0]?.id||null;
    current=null;dirty=false;
    selectScreen(next);
}
function tagSelect(value,callback){
    const select=document.createElement('select');
    select.append(option('','Choose tag'));
    for(const t of tags) select.append(option(t.id,t.label+' / '+t.id));
    select.value=value||'';
    select.onchange=()=>callback(select.value);
    return select;
}
function labelField(text,control){
    const label=document.createElement('label');
    label.append(document.createTextNode(text));label.append(control);return label;
}
function editWidget(w,index){
    const row=document.createElement('div');row.className='widgetEdit';
    const type=document.createElement('select');
    for(const t of ['TEXT','VALUE','GAUGE','STATUS'])type.append(option(t,t));
    type.value=w.type;
    type.onchange=()=>{
        w.type=type.value;
        if(w.type==='TEXT')w.tagId=null;
        if(w.type!=='TEXT' && !w.tagId)w.tagId=tags[0]?.id||'';
        if(w.type==='GAUGE'){w.minimum=0;w.maximum=100}
        dirty=true;renderEditor();
    };
    const title=field('input',w.title);
    title.oninput=()=>{w.title=title.value;dirty=true;renderPreview()};
    row.append(labelField('Type',type),labelField('Title',title));
    if(w.type!=='TEXT'){
        row.append(labelField('PLC tag',tagSelect(w.tagId,value=>{w.tagId=value;dirty=true;renderPreview()})));
    }
    if(w.type==='GAUGE'){
        for(const key of ['minimum','maximum']){
            const control=field('input',w[key]);control.type='number';control.step='any';
            control.oninput=()=>{w[key]=control.value===''?null:Number(control.value);dirty=true;renderPreview()};
            row.append(labelField(key==='minimum'?'Gauge minimum':'Gauge maximum',control));
        }
    }
    const move=document.createElement('div');move.className='row';
    move.append(
        button('↑ Up',()=>{if(index>0){[draft.widgets[index-1],draft.widgets[index]]=[draft.widgets[index],draft.widgets[index-1]];dirty=true;renderEditor()}}),
        button('↓ Down',()=>{if(index<draft.widgets.length-1){[draft.widgets[index],draft.widgets[index+1]]=[draft.widgets[index+1],draft.widgets[index]];dirty=true;renderEditor()}}),
        button('Remove',()=>{if(draft.widgets.length>1){draft.widgets.splice(index,1);dirty=true;renderEditor()}else status('Keep at least one widget',true)})
    );
    row.append(move);return row;
}
function renderPreview(){
    const preview=el('screenPreview');preview.replaceChildren();if(!draft)return;
    preview.style.gridTemplateColumns='repeat('+draft.columns+', minmax(0,1fr))';
    for(const w of draft.widgets){
        const card=document.createElement('div');card.className='card';
        const heading=document.createElement('strong');heading.textContent=w.title;
        const value=document.createElement('div');value.className='value';
        value.textContent=w.type==='TEXT'?'Static label':w.type==='STATUS'?'SIMULATED STATUS':w.type==='GAUGE'?'▰▰▰▱▱':'00.0';
        const sub=document.createElement('div');sub.className='sub';sub.textContent=w.type+' · '+(w.tagId||'No tag');
        card.append(heading,value,sub);preview.append(card);
    }
}
function renderEditor(){
    el('widgetsEditor').replaceChildren();
    if(!current || !draft){
        el('screenName').value='';el('screenRevision').textContent='No screen';return;
    }
    el('screenSelect').value=current.id;
    el('screenName').value=draft.name;el('screenColumns').value=String(draft.columns);
    el('screenRevision').textContent='Revision '+current.revision+(dirty?' · unsaved edits':'');
    draft.widgets.forEach((w,i)=>el('widgetsEditor').append(editWidget(w,i)));
    renderPreview();
}
function newWidgetId(){
    let n=1;while(draft.widgets.some(w=>w.id==='widget'+n))n++;
    return 'widget'+n;
}
async function save(){
    if(!current||!draft)return;
    if(dirty===false){status('No changes to save');return}
    try{
        const result=await api('/api/studio/screens/'+encodeURIComponent(current.id),'PUT',{
            expectedRevision:current.revision,layout:draft
        });
        screens=screens.map(s=>s.id===current.id?result:s);
        current=result;draft=JSON.parse(JSON.stringify(current.layout));dirty=false;
        renderEditor();renderDeviceEditor();status('Saved HMI screen '+current.id+' revision '+current.revision+
            '. Registered PCs using this screen will display Update available until they click Update now.');
        loadUpdateStates().catch(e=>status('Screen saved, but update status could not refresh: '+e.message,true));
    }catch(e){status(e.message+'. Your local edits are still in the editor.',true)}
}
async function create(){
    const id=el('newScreenId').value.trim();
    if(!safeId(id)){status('Screen ID must have 2-48 alphanumeric, hyphen or underscore characters, starting with a letter',true);return}
    if(dirty&&!window.confirm('Discard unsaved screen changes?'))return;
    const layout={name:'New HMI screen',columns:2,widgets:[{
        id:'title',type:'TEXT',title:'New HMI screen',tagId:null,minimum:null,maximum:null
    }]};
    try{
        const result=await api('/api/studio/screens','POST',{id,layout});
        screens.push(result);screens.sort((a,b)=>a.id.localeCompare(b.id));
        current=result;draft=JSON.parse(JSON.stringify(result.layout));dirty=false;
        renderScreenList();selectScreen(id);renderDeviceEditor();
        el('newScreenId').value='';status('Created screen '+id);
    }catch(e){status(e.message,true)}
}
function renderDeviceEditor(){
    const out=el('deviceEditor');out.replaceChildren();
    const heading=document.createElement('h3');heading.textContent='Registered PCs';out.append(heading);
    if(devices.length===0){
        const note=document.createElement('p');note.textContent='No PCs yet. Name your first PC above, choose its screen and select Register & get setup code.';out.append(note);
    }
    renderSelectedPcPicker();
    for(const device of devices){
        const row=document.createElement('div');row.className='deviceRow';
        row.dataset.workstationId=device.id;
        const name=field('input',device.name);
        const assigned=document.createElement('select');
        for(const screen of screens)assigned.append(option(screen.id,screen.layout.name+' ('+screen.id+')'));
        assigned.value=device.screenId;
        const enabled=field('input','');enabled.type='checkbox';enabled.checked=device.enabled;
        row.append(labelField('PC '+device.id,name),labelField('Assigned HMI screen',assigned),
            labelField('Enabled',enabled),
            button('Save changes',async()=>{
                try{
                    const result=await api('/api/studio/workstations/'+encodeURIComponent(device.id),'PUT',{
                        name:name.value.trim(),screenId:assigned.value,enabled:enabled.checked
                    });
                    devices=devices.map(d=>d.id===result.id?result:d);
                    renderDeviceEditor();
                    status('Saved '+result.name+'. The selected PC will check for a new configuration and show Update now.');
                    loadUpdateStates().catch(e=>status('Saved, but update status unavailable: '+e.message,true));
                }catch(e){status(e.message,true)}
            }));
        const updateLabel=document.createElement('small');
        updateLabel.className='pcUpdateFlag';
        updateLabel.dataset.deviceId=device.id;
        const update=updateStates.find(u=>u.workstationId===device.id);
        updateLabel.textContent=!device.enabled?'Disabled':!update?'Checking update status...':
            update.updatePending?'Update available — waiting for PC':'Up to date';
        row.append(updateLabel);
        row.append(button('Pair / reconnect PC',()=>showPairing(device)));
        row.append(button('Advanced: download setup ZIP',async()=>{
            try{
                await window.studioAdminDownload(
                    '/api/studio/workstations/'+encodeURIComponent(device.id)+'/setup-package',
                    'IndustrialHMI-'+device.id+'-Setup.zip');
                status('Downloaded optional setup ZIP for '+device.id+'. The easier setup uses Pair / reconnect PC.');
            }catch(e){status('Setup ZIP failed: '+e.message,true)}
        }));
        out.append(row);
    }
}
function renderSelectedPcPicker(){
    const picker=el('selectedPcId');
    const previous=selectedPc||picker.value;
    picker.replaceChildren();
    for(const pc of devices)picker.append(option(pc.id,pc.name+' ('+pc.id+')'));
    selectedPc=devices.some(pc=>pc.id===previous)?previous:(devices[0]?.id||null);
    if(selectedPc)picker.value=selectedPc;
    renderSelectedPcStatus();
}
function renderSelectedPcStatus(){
    const node=el('selectedPcStatus');
    const pc=devices.find(d=>d.id===selectedPc);
    if(!pc){node.textContent='No PC selected. Register a PC above first.';return;}
    const rev=updateStates.find(u=>u.workstationId===pc.id);
    if(!pc.enabled){node.textContent='This PC is disabled. Enable it to distribute updates.';return;}
    if(!rev){node.textContent='Checking for updates on '+pc.name+'...';return;}
    const last=rev.appliedAt?new Date(rev.appliedAt).toLocaleString():'never';
    node.textContent=rev.updatePending?
        'Update available for '+pc.name+' · Assigned screen: '+rev.screenId+
        ' rev '+rev.screenRevision+' · Tag configuration: rev '+rev.configurationRevision+
        ' · Last applied: '+last+
        '. The PC will show Update now; changes are not silently applied.':
        pc.name+' is up to date · Screen '+rev.screenId+' rev '+rev.screenRevision+
        ' · Config rev '+rev.configurationRevision+' · Applied: '+last+'.';
}
async function loadUpdateStates(){
    updateStates=await api('/api/studio/workstations/update-status');
    renderSelectedPcStatus();
    for(const label of document.querySelectorAll('.pcUpdateFlag')){
        const pc=devices.find(d=>d.id===label.dataset.deviceId);
        const state=updateStates.find(u=>u.workstationId===label.dataset.deviceId);
        label.textContent=!pc?.enabled?'Disabled':!state?'Status unavailable':
            state.updatePending?'Update available — waiting for PC':'Up to date';
    }
}
async function queueSelectedPcUpdate(){
    if(!selectedPc){status('Select a registered PC first',true);return}
    const pc=devices.find(d=>d.id===selectedPc);
    if(!pc?.enabled){status('The selected PC is disabled. Enable it before requesting an update.',true);return}
    try{
        const result=await api('/api/studio/workstations/'+encodeURIComponent(selectedPc)+'/request-update','POST');
        updateStates=updateStates.filter(u=>u.workstationId!==result.workstationId).concat(result);
        renderSelectedPcStatus();
        await loadUpdateStates();
        status('Update queued for '+pc.name+'. Its JavaFX app will show Update available. The operator clicks Update now to apply.');
    }catch(e){status('Could not queue update: '+e.message,true)}
}
async function showPairing(device){
    const panel=el('newDeviceSecret');panel.classList.remove('hidden');
    panel.replaceChildren();
    const inProgress=document.createElement('p');
    inProgress.textContent='Creating a secure setup code for '+device.name+'…';panel.append(inProgress);
    try{
        const pairing=await api('/api/studio/workstations/'+encodeURIComponent(device.id)+'/pairing','POST');
        const intro=document.createElement('strong');
        intro.textContent='Desktop setup code for '+device.name+' ('+device.id+')';
        const instructions=document.createElement('p');
        instructions.textContent='On the JavaFX PC, select Set up this PC, paste the full code below, then click Connect this PC. No separate viewer password is needed. This code expires in 15 minutes and can only be used once.';
        const code=document.createElement('textarea');
        code.value=pairing.setupCode;code.readOnly=true;code.rows=3;
        code.style.height='auto';code.style.width='100%';
        code.setAttribute('aria-label','Single-use workstation setup code');
        const copy=button('Copy setup code',async()=>{
            try{await navigator.clipboard.writeText(pairing.setupCode);
                status('Setup code copied. Paste it into the desktop app on '+device.name+'.');
            }catch(e){
                code.focus();code.select();
                status('Clipboard permission unavailable. The code is selected; press Ctrl+C to copy.',true);
            }
        });
        const expiry=document.createElement('small');
        expiry.textContent='Expires: '+new Date(pairing.expiresAt).toLocaleString()+
            ' · Generating a newer code invalidates older unredeemed codes.';
        panel.replaceChildren(intro,instructions,code,copy,expiry);
        panel.scrollIntoView({behavior:'smooth',block:'nearest'});
        status('Setup code ready for '+device.name+'. Copy it into the JavaFX desktop client.');
    }catch(e){
        panel.replaceChildren(document.createTextNode('Unable to create setup code: '+e.message));
        status('Pairing failed: '+e.message,true);
    }
}
function uniqueIdFromName(name){
    let base=name.toLowerCase().normalize('NFKD').replace(/[^a-z0-9 _-]/g,'')
        .trim().replace(/[ _]+/g,'-').replace(/^-+/,'').slice(0,42);
    if(!/^[a-z]/.test(base))base='pc-'+base;
    if(base.length<2)base='workstation';
    let id=base,n=2;
    while(devices.some(d=>d.id===id)){
        id=(base.slice(0,43)+'-'+n).slice(0,48);n++;
    }
    return id;
}
async function provision(){
    const name=el('newDeviceName').value.trim();
    const id=el('newDeviceId').value.trim() || uniqueIdFromName(name);
    const screenId=el('newDeviceScreen').value;
    if(!safeId(id)||!name){status('Enter a PC name and a valid PC ID (or leave ID blank to generate one)',true);return}
    try{
        const x=await api('/api/studio/workstations','POST',{id,name,screenId});
        devices.push(x.workstation);devices.sort((a,b)=>a.id.localeCompare(b.id));
        renderDeviceEditor();
        el('newDeviceId').value='';el('newDeviceName').value='';
        await showPairing(x.workstation);
        selectedPc=x.workstation.id;
        loadUpdateStates().catch(e=>status('PC registered; update status unavailable: '+e.message,true));
    }catch(e){status('Unable to register PC: '+e.message,true)}
}
async function loadAll(){
    try{
        const [s,d,c,u]=await Promise.all([
            api('/api/studio/screens'),api('/api/studio/workstations'),api('/api/studio/config'),
            api('/api/studio/workstations/update-status')
        ]);
        screens=s;devices=d;tags=c.configuration.tags;updateStates=u;
        renderScreenList();renderDeviceEditor();
        if(updatePoller)clearInterval(updatePoller);
        updatePoller=setInterval(()=>loadUpdateStates().catch(()=>{}),10000);
        status('Screen designer ready. Select a registered PC to see its pending updates.');
    }catch(e){status('Unable to load HMI designer: '+e.message,true)}
}
el('screenSelect').onchange=e=>selectScreen(e.target.value);
el('screenName').oninput=e=>{if(!draft)return;draft.name=e.target.value;dirty=true;renderPreview()};
el('screenColumns').onchange=e=>{if(!draft)return;draft.columns=Number(e.target.value);dirty=true;renderPreview()};
el('addWidget').onclick=()=>{
    if(!draft)return;
    if(draft.widgets.length>=40){status('Maximum 40 widgets per screen',true);return}
    draft.widgets.push({id:newWidgetId(),type:'VALUE',title:'New value',tagId:tags[0]?.id||'',
        minimum:null,maximum:null});
    dirty=true;renderEditor();
};
el('saveScreen').onclick=save;
el('newScreen').onclick=create;
el('provisionDevice').onclick=provision;
el('selectedPcId').onchange=e=>{selectedPc=e.target.value;renderSelectedPcStatus()};
el('refreshPcStatus').onclick=()=>loadUpdateStates().then(()=>status('PC update status refreshed')).catch(e=>status(e.message,true));
el('queuePcUpdate').onclick=queueSelectedPcUpdate;
window.addEventListener('studio:configuration-saved',()=>{
    loadUpdateStates().catch(e=>status('Could not load PC update status: '+e.message,true));
});
window.addEventListener('studio:connected',loadAll);
window.addEventListener('studio:disconnected',reset);
})();