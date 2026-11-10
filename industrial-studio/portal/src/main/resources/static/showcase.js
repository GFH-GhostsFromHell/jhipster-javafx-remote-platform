(() => {
'use strict';
const $ = id => document.getElementById(id);
const history = new Map();
const stations = {
  dewatering: {pc:'PC 01',name:'Underground dewatering',desc:'Sump pumps, discharge pressure and wet-well monitoring'},
  conveyor: {pc:'PC 02',name:'Ore conveyor',desc:'Ore transport, motor loading and mechanical condition'},
  substation: {pc:'PC 03',name:'600 V substation',desc:'Feeder measurements and transformer condition'}
};
let active='dewatering';
let lastSuccess=0;
let latest=null;
const svgNS='http://www.w3.org/2000/svg';
const make=(tag,className,content)=>{
    const e=document.createElement(tag);
    if(className)e.className=className;
    if(content!==undefined)e.textContent=content;
    return e;
};
const setSync=(text,offline)=>{
    const e=$('sync');e.textContent=text;e.classList.toggle('offline',offline);
};
function sparkLine(point){
    const svg=document.createElementNS(svgNS,'svg');
    svg.setAttribute('viewBox','0 0 200 38');
    svg.setAttribute('preserveAspectRatio','none');
    svg.setAttribute('class','spark');
    svg.setAttribute('role','img');
    svg.setAttribute('aria-label','Recent simulated '+point.label+' readings');
    const list=history.get(point.id)||[];
    if(list.length<2)return svg;
    const low=Math.min(...list),high=Math.max(...list),delta=Math.max(0.5,high-low);
    const poly=document.createElementNS(svgNS,'polyline');
    const points=list.map((value,i)=>{
       const x=i*200/Math.max(1,list.length-1);
       const y=34-(value-low)*29/delta;
       return x.toFixed(1)+','+y.toFixed(1);
    }).join(' ');
    poly.setAttribute('points',points);
    poly.setAttribute('fill','none');
    poly.setAttribute('stroke',point.state==='SIMULATED_LIMIT'?'#edb56a':'#7bdce8');
    poly.setAttribute('stroke-width','2.7');
    poly.setAttribute('stroke-linecap','round');
    poly.setAttribute('stroke-linejoin','round');
    svg.append(poly);
    return svg;
}
function metricCard(point){
    const card=make('article','metric');
    const heading=make('div','name',point.label);
    const value=make('div','number',Number(point.value).toFixed(1)+' ');
    value.append(make('span','unit',point.unit||''));
    const gauge=make('div','gauge');
    const fill=make('div','fill');
    const frac=(Number(point.value)-Number(point.min))/(Number(point.max)-Number(point.min));
    fill.style.width=(Math.max(0,Math.min(1,Number.isFinite(frac)?frac:0))*100).toFixed(1)+'%';
    const warning=point.state==='SIMULATED_LIMIT';
    fill.classList.toggle('warn',warning);
    gauge.append(fill);
    const foot=make('div','meta'+(warning?' warn':''),
        warning?'SIMULATED LIMIT EXCEEDED':'SIMULATED · within demo limits');
    card.append(heading,value,gauge,sparkLine(point),foot);
    return card;
}
function render(){
    if(!latest)return;
    const panel=(latest.panels||[]).find(p=>p.id===active);
    const config=stations[active];
    if(!panel||!config)return;
    $('pcLabel').textContent=config.pc;
    $('panelTitle').textContent=panel.name;
    $('panelDesc').textContent=panel.description;
    const grid=$('metricGrid');
    const fragment=document.createDocumentFragment();
    for(const point of panel.points||[]){
      fragment.append(metricCard(point));
    }
    grid.replaceChildren(fragment);
    const dt=new Date(latest.timestamp);
    $('lastUpdate').textContent='Sample received '+(Number.isNaN(dt.getTime())?'just now':dt.toLocaleTimeString());
    setSync('● Cloud sample received',false);
}
async function refresh(){
    try{
        const controller=new AbortController();
        // Abort manually via AbortSignal.timeout where supported, avoiding piling up requests.
        const request={method:'GET',headers:{Accept:'application/json'},cache:'no-store',
            signal:typeof AbortSignal.timeout==='function'?AbortSignal.timeout(7000):controller.signal};
        const r=await fetch('/api/studio/showcase',request);
        if(!r.ok)throw Error('HTTP '+r.status);
        const packet=await r.json();
        if(packet.mode!=='SIMULATION'||packet.readOnly!==true||packet.realPlcConnected!==false||
           !Array.isArray(packet.panels))throw Error('Unexpected data mode');
        for(const panel of packet.panels){
            for(const point of panel.points||[]){
                if(point.quality!=='SIMULATED')throw Error('Unexpected data quality');
                if(!Number.isFinite(point.value))throw Error('Invalid demo reading');
                let list=history.get(point.id);
                if(!list){list=[];history.set(point.id,list)}
                list.push(point.value);
                if(list.length>36)list.shift();
            }
        }
        latest=packet;lastSuccess=Date.now();render();
    }catch(e){
        const age=lastSuccess?Date.now()-lastSuccess:Number.POSITIVE_INFINITY;
        if(age>6500){
            $('metricGrid').replaceChildren(make('p','', 'CLOUD DATA UNAVAILABLE — demonstration values are not current.'));
            setSync('● Cloud connection unavailable',true);
            $('lastUpdate').textContent='No valid recent sample';
        }else{
            setSync('● Refresh delayed',true);
        }
    }
}
for(const b of document.querySelectorAll('#areas button')){
    b.addEventListener('click',()=>{
        active=b.dataset.id;
        for(const item of document.querySelectorAll('#areas button')){
            item.setAttribute('aria-pressed',String(item===b));
        }
        render();
    });
}
refresh();
setInterval(refresh,2000);
})();