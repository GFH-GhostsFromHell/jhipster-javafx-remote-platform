(function(){
'use strict';
const $=id=>document.getElementById(id), RAD=Math.PI/180;
const v=(id)=>{let raw=$(id).value.trim();return raw===''?null:Number(raw)};
const fmt=(n,p=2)=>Number.isFinite(n)?Number(n.toFixed(p)).toString():'—';
const unit=()=>$('unit').value, mul=()=>unit()==='mm'?1:25.4;
const length=n=>Number.isFinite(n)?fmt(n,unit()==='mm'?1:3)+' '+unit():'Measurement required';
const toMm=n=>n*mul();
const html=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let layout=null,step=0,ops=[],model=null,mode='finished',yaw=-.7,pitch=.38,zoom=1,drag=null;
const F={};
function marker(tag,pos,tool,note){return {tag,pos,tool,note}}
function instruction(label,where,reference,hook,flip,body,caution){
 return {label,where,reference,hook,flip,body,caution};
}
function hookSide(kind,stepIndex,isKlein){
 if(!isKlein)return 'unknown';
 const verified={
  stub:['left'],offset:['left','left'],roll:['left','left'],
  back:['left','right'],kick90:['left','unknown'],kickExtra:['left','unknown','unknown'],
  kickBefore:['unknown','left'],saddle3:['unknown','right','left']
 };
 return (verified[kind]||[])[stepIndex]||'unknown';
}
function hookCaption(side){
 return side==='left'?'← HOOK FACES LEFT · TOWARD DATUM':
        side==='right'?'HOOK FACES RIGHT → · TOWARD FAR END':
        '? VERIFY HOOK FACING WITH YOUR SHOE';
}
function markerHookSide(kind,tag,isKlein){
 if(!isKlein)return 'unknown';
 if(['stub','offset','roll','back','kick90','kickExtra','kickBefore'].includes(kind)){
  if(tag==='A')return 'left';
  if(tag==='B'&&['offset','roll'].includes(kind))return 'left';
 }
 if(kind==='saddle3')return tag==='A'?'right':tag==='B'?'left':'unknown';
 return 'unknown';
}
function valid(){
 const required={
  stub:['height'], offset:['height','distance'],roll:['height','distance','width'],
  back:['height','width'],kick90:['height'],kickBefore:['height','distance'],
  kickExtra:['height','distance','width'],saddle3:['height','distance'],
  saddle4:['height','distance','width'],segment:[],custom:[]
 }[$('kind').value]||[];
 const names={height:'rise / stub height',distance:'distance from tape zero',width:'width / sideways roll'};
 for(const id of required){
  const number=v(id);
  if(number===null)throw Error('Enter '+names[id]+' to calculate this bend.');
  if(!Number.isFinite(number)||number<0)throw Error('Enter a valid '+names[id]+'.');
 }
 const kind=$('kind').value;
 // Zero rise and coincident 90s cannot form independent bends.
 if(required.includes('height')&&v('height')===0)
  throw Error('BENDS TOO CLOSE: enter a positive rise or stub height.');
 if(['back','saddle4'].includes(kind)&&v('width')===0)
  throw Error('BENDS TOO CLOSE: enter a positive spacing or obstacle width.');
 for(const id of ['radius','stock']){
  const number=v(id);
  if(number===null||!Number.isFinite(number)||number<=0)throw Error('Enter a valid '+id+'.');
 }
 for(const id of ['deduct','extraMark','minMarkGap','minStraight']){
  const number=v(id);
  if(number!==null&&(!Number.isFinite(number)||number<0))throw Error('Enter a valid '+id+' or leave it blank.');
 }
}
function getLayout(){
 valid();const type=$('kind').value,height=v('height'),deduct=v('deduct'),angle=Number($('angle').value),distance=v('distance'),width=v('width'),extra=v('extraMark'),isKlein=$('method').value==='klein';
 let name='',warning='',datum='Tape ZERO is the LEFT end shown in the straight conduit diagram.',marks=[],steps=[];
 const mark90=['stub','back','kick90','kickExtra','kickBefore'].includes(type)&&deduct!==null?height-deduct:null;
 if(mark90!==null&&mark90<0)throw Error('Stub height must be greater than the measured deduct.');
 const put90=()=>{
  if(mark90!==null)marks.push(marker('A',mark90,'ARROW','Measure from the short stub end.'));
  steps.push(instruction('90° stub-up',mark90===null?'Enter measured deduct first':length(mark90),'ARROW', 'Hook toward SHORT stub end', 'NO pipe roll',
   'Measure the required stub height from the end that becomes the stub. Subtract the actual shoe deduct. Draw a full-width pencil line at A and align the bender ARROW with the line. Apply correct foot pressure and bend to a resting 90°.',
   'Use the exact take-up stamped on or calibrated for the shoe. This is a Klein-style method; verify manufacturer instructions.'));
 };
 const putOffset=(rolled)=>{
  const trueRise=rolled?Math.hypot(height,width):height;
  if(trueRise===0)throw Error('The offset rise must be positive.');
  const sep=trueRise/Math.sin(angle*RAD),shrink=trueRise*Math.tan(angle*RAD/2);
  const A=$('critical').value==='near'?distance+shrink:distance+shrink-sep, B=A+sep;
  if(A<0)throw Error('Offset mark A falls before tape zero. Increase reference distance.');
  const tilt=rolled?Math.atan2(width,height)/RAD:0;
  marks=[marker('A',A,'ARROW','First offset mark.'),marker('B',B,'ARROW','Second offset mark.')];
  steps.push(instruction('First '+(rolled?'rolled ':'')+'offset',length(A),'ARROW','HOOK AWAY FROM MARK B',
   rolled?'Set plane '+fmt(tilt,1)+'°':'Initial bend; SAME plane',
   'Measure A from the tape end, align the ARROW precisely on A. Orient the bending plane '+(rolled?'at '+fmt(tilt,1)+'° from vertical before beginning':'upright')+'. Make the first '+angle+'° bend. For the shown Klein-style method the hook faces away from the second mark.',
   'Measure and verify actual field shrink, shoe radius and springback.'));
  steps.push(instruction('Opposite return bend',length(B),'ARROW','Follow reverse-bend shoe setup',
   'FLIP tool assembly; maintain original bend plane',
   'Align the ARROW on B, then make an equal and OPPOSITE '+angle+'° bend. The documented Klein floor-to-air method flips the assembly and rotates the conduit in the cradle. The final two runs must be parallel and in the SAME '+(rolled?'inclined':'vertical')+' bending plane.',
   'Flipping the assembly to use the other side is not the same as adding an independent roll. Do not change the roll angle between the two bends.'));
  warning=(rolled?'Rolled offset: rotate the BENDING PLANE once before the first bend ('+fmt(tilt,1)+'° from vertical); maintain that plane at the return. ':'')+'This method is based on a Klein-style hand bender; other models may require different handling. Mark spacing '+length(sep)+'; ideal geometry shrink '+length(shrink)+'.';
 };
 switch(type){
 case 'stub':
  name='90° stub-up';put90();
  warning='The arrow mark is valid only with the measured/stamped deduct and the exact tool. The short END is the measurement datum.';break;
 case 'offset':
  name='Two-bend offset';putOffset(false);break;
 case 'roll':
  name='Rolling offset';putOffset(true);break;
 case 'back':
  name='Back-to-back 90s';put90();
  datum='First mark: tape zero at the original stub end. Second STAR mark: measure '+length(width)+' FROM THE BACK OF THE FORMED FIRST 90°—not from the unbent conduit end.';
  steps.push(instruction('Second 90° back-bend','Measure '+length(width)+' from first bend BACK','STAR','Hook toward the free end opposite original bend','Reposition for second 90; keep both legs COPLANAR',
   'First complete the stub 90. Measure the required separation from the BACK of that finished bend to the second location and mark it. Align the STAR point on this second mark. On the documented Klein method the hook faces the free end opposite the original-bend side. Bend 90° in the same plane.',
   'The second STAR position is measured after forming the first 90 and is deliberately not plotted at a guessed straight-stock coordinate.'));
  warning='The straight-stock strip shows only the first ARROW mark. The STAR mark is determined from the BACK OF THE ALREADY FORMED first bend; tight U bends can require a different method.';break;
 case 'kickExtra':
 case 'kick90':
 case 'kickBefore':
  name=type==='kickExtra'?'Kicked 90 + another 90':type==='kick90'?'Kicked 90':'Kick followed by 90';
  if(type!=='kickBefore')put90();
  if(extra!==null)marks.push(marker('K',extra,'USER-SUPPLIED','Manual kick tape mark; verify its datum.'));
  const kick=instruction('Kick '+angle+'°',extra===null?'Manual layout needed':length(extra),'ARROW only if verified for kick','Face tool for required kick direction','Reposition; verify pipe plane / no guessed flip',
   'Determine the desired kick location from a known physical datum and test head clearance. Align the designated kick reference for your actual shoe and make a '+angle+'° kick. The 3D model shows one possible orientation; it cannot determine this physical mark from stub height alone.',
   'Do not use mathematical bend-entry points or 3D geometry as a physical ARROW mark.');
  if(type==='kickBefore'){steps.push(kick);put90();}else steps.push(kick);
  if(type==='kickExtra')steps.push(instruction('Additional 90°','Requires a full second 90° layout','ARROW / STAR as indicated by your tool','Depends on remaining leg and reference','Choose bend plane before tool placement',
   'Measure the actual remaining leg after the previous bends and determine the second 90 reference using shoe deduct, gain and the job layout. This prototype displays a possible 3D outcome but does not invent a third production mark.',
   'A kinked 90 and a second 90 can create shoe clearance and cumulative shrink issues. Verify all three bends on scrap.'));
  datum='Tape zero = short stub end for first 90. Any later K/90 physical mark must use a clearly defined, measured datum.';
  warning='Multi-bend 3D is for VISUAL PLANNING. Only the first 90 arrow position is computed when deduct is known; later kick/90 marks require a field layout and verified shoe references.';
  break;
 case 'saddle3':{
  name='Three-point saddle';const centre=angle===60?60:45;
  const spacing=height*(centre===45?2.5:2),shrink=height*(centre===45?3/16:1/4);
  const c=distance+shrink,a=c-spacing,b=c+spacing;
  if(a<0)throw Error('Saddle outer mark would be before tape zero.');
  marks=[marker('A',a,'ARROW','First return'),marker('C',c,'CENTRE RIM NOTCH','Central bend'),marker('B',b,'ARROW','Second return')];
  steps.push(instruction('Centre saddle',length(c),'CENTRE RIM NOTCH','Per model-specific centre method','Keep one saddle plane',
   'Use centre mark C at '+length(c)+'. Place the designated '+centre+'° CENTRE rim notch there and bend to '+centre+'°. Outside marks A and B are '+length(spacing)+' from the centre.',
   'Do not substitute the STAR for the centre rim notch; verify your actual bender supports this mark.'));
  steps.push(instruction('First return',length(a),'OUTER ARROW','Verify opposite return bend','FLIP assembly; stay COPLANAR',
   'Align outer mark A with the shoe ARROW and bend '+centre/2+'° in the opposite direction to the centre bend. Maintain the exact original saddle plane.','Use the shoe-specific air-bend setup.'));
  steps.push(instruction('Second return',length(b),'OUTER ARROW','Toward centre as shown by shoe guide','Reposition; do not twist conduit out of plane',
   'Place the ARROW on outside mark B and form the final '+centre/2+'° return. Verify both legs align and the obstruction clears.','Check springback and the actual clearance over the obstacle.'));
  warning='Klein-style 3-point saddle field factors for a '+centre+'° centre bend. Select 45° or 60° for a matching field example; other centre angles require independent calibration.';
  break;
 }
 case 'saddle4':{
  name='Four-point saddle (geometry only)';
  const sep=height/Math.sin(angle*RAD),shrink=height*Math.tan(angle*RAD/2);
  const a=distance+shrink-sep,b=a+sep,c=b+width,d=c+sep;
  if(a<0)throw Error('The first planning point lies before tape zero.');
  [a,b,c,d].forEach((point,i)=>marks.push(marker(String.fromCharCode(65+i),point,'GEOMETRY ONLY','NOT an approved shoe mark')));
  [a,b,c,d].forEach((point,i)=>steps.push(instruction('Four-point bend '+(i+1),length(point),'UNVERIFIED','Check manufacturer shoe','Keep all four in the same plane',
   'This is an ideal bend-centre planning coordinate, NOT a physical ARROW/STUB placement. Confirm a manufacturer-supported four-point saddle layout and check the actual top-flat width and shoe clearance before marking real conduit.',
   'Do not use these positions as manufacturing marks.')));
  warning='Four-point saddle coordinates are geometric placeholders, NOT physical bender marks. This experimental view is not a field-layout calculator.';break;
 }
 case 'segment':
  name='Segmented 90° (geometry only)';
  steps.push(instruction('Plan segmented 90°','Calibrate separate bends','SHOE-SPECIFIC','Per manufacturer','NO unintended pipe rotation',
   'Divide 90° into six segments of 15°. Determine actual centreline spacing and the bender-specific tangent/ARROW method first. Keep every segment in the same plane.',
   'Ideal arc centres are not field shoe alignment marks.'));
  steps.push(instruction('Make six bends','Verify each mark','SHOE-SPECIFIC','Per manufacturer','Keep same bend plane',
   'Bend each segment to its calibrated angle, then verify the finished total 90°. Check accumulated springback and minimum shoe spacing.',
   'Not all hand benders can form a repeatable segmented 90.'));
  warning='Segmented 90 is a conceptual shape preview; there are no verified physical field marks.';break;
 default:
  name='Custom multi-bend (geometry only)';
  steps.push(instruction('Design bend sequence','GEOMETRY ONLY','UNVERIFIED','Confirm actual bender','Choose local bend plane in editor',
    'Edit the 3D operations and inspect the final result by rotating it. Before fabrication, derive physical shop marks using the actual bender and complete field dimensions.',
    'No physical ARROW/STAR/NOTCH position is calculated for custom operations.'));
  warning='Custom bend model is a visualization only, not a fabrication or cut list.';
 }
 if(!isKlein){
  marks=marks.map(m=>Object.assign({},m,{tool:m.tool==='GEOMETRY ONLY'?m.tool:'VERIFY THIS SHOE'}));
  steps=steps.map(s=>Object.assign({},s,{reference:s.reference==='GEOMETRY ONLY'?s.reference:'VERIFY SHOE',hook:'CHECK YOUR BENDER',flip:'VERIFY OEM METHOD'}));
  warning+=' Your tool is set to OTHER: manufacturer-specific orientation cues have been suppressed.';
 }
 steps=steps.map((item,index)=>Object.assign({},item,{hookSide:hookSide(type,index,isKlein)}));
 marks=marks.map(item=>Object.assign({},item,{hookSide:markerHookSide(type,item.tag,isKlein)}));
 return {name,datum,warning,steps,marks};
}

function syncQuickFields(){
 const shown={
  stub:['height','deduct'], offset:['height','angle','distance'],
  roll:['height','angle','distance','width'],back:['height','deduct','width'],
  kick90:['height','deduct','angle','width'],
  kickExtra:['height','deduct','angle','distance','width'],
  kickBefore:['height','deduct','angle','distance'],
  saddle3:['height','angle','distance'],saddle4:['height','angle','distance','width'],
  segment:['height'],custom:[]
 };
 const kind=$('kind').value, active=shown[kind]||[];
 // Keep optional calibration out of the way unless a 90 requires a shoe deduct.
 const needsDeduct=active.includes('deduct');
 $('loadProfile').hidden=!needsDeduct;
 $('profileStatus').hidden=!needsDeduct;
 ['height','deduct','angle','distance','width'].forEach(id=>{
  $(id).closest('.field').style.display=active.includes(id)?'':'none';
 });
 const offset=['offset','roll'].includes(kind),back=kind==='back';
 document.querySelector('label[for="height"]').textContent=offset?'Required offset rise':kind==='stub'||back?'Required 90° stub height':'Height / kick rise';
 document.querySelector('label[for="width"]').textContent=kind==='roll'?'Horizontal roll':back?'Back-to-back distance':'Obstacle / spacing width';
 document.querySelector('label[for="distance"]').textContent=offset?'Near obstacle reference from tape end':'Reference distance from tape end';
}

const TEN_FOOT_EMT_MM=3048;
// Stock checks use the SAME straight-stock datum. A back-of-bend STAR mark
// is relative to a formed bend and must not be compared with first-end marks.
function checkStockAndSpacing(){
 const stock=v('stock'), minMarkGap=v('minMarkGap');
 const stockMm=toMm(stock);
 if(stock===null||!Number.isFinite(stock)||stock<=0)
  throw Error('Enter a positive actual stock length.');
 if(stockMm> TEN_FOOT_EMT_MM+1e-6)
  throw Error('CALCULATION NOT POSSIBLE: stock is over one standard 10 ft EMT stick (3,048 mm). Select 10 ft or a shorter piece.');
 if(minMarkGap!==null&&(!Number.isFinite(minMarkGap)||minMarkGap<0))
  throw Error('Minimum shoe mark spacing must be zero or greater.');
 const marks=layout.marks.filter(m=>Number.isFinite(m.pos));
 const overshot=marks.find(m=>m.pos<0||toMm(m.pos)>stockMm+1e-6);
 if(overshot)
  throw Error('CALCULATION NOT POSSIBLE on one 10 ft EMT: mark '+overshot.tag+' is at '+length(overshot.pos)+', outside '+length(stock)+' of actual stock.');
 // Use only known same-datum physical marks for shoe-spacing checks.
 // K is manually supplied and may have another tape reference.
 const ordered=marks.filter(m=>m.tool!=='GEOMETRY ONLY'&&m.tag!=='K').slice().sort((a,b)=>a.pos-b.pos);
 for(let i=1;i<ordered.length;i++){
  const gap=ordered[i].pos-ordered[i-1].pos;
  if(gap<=1e-6)
   throw Error('BENDS TOO CLOSE: physical marks '+ordered[i-1].tag+' and '+ordered[i].tag+
    ' overlap. Increase the required rise or spacing.');
  if(minMarkGap!==null&&minMarkGap>0&&gap+1e-6<minMarkGap)
   throw Error('BENDS TOO CLOSE: '+ordered[i-1].tag+' and '+ordered[i].tag+
    ' are only '+length(gap)+' apart, below the verified shoe minimum of '+length(minMarkGap)+'.');
 }
 const furthest=marks.length?Math.max(...marks.map(m=>m.pos)):0;
 $('stockStatus').className='stock-status pending';
 $('stockStatus').textContent='Single-piece EMT · shop marks within stock (furthest '+length(furthest)+' of '+length(stock)+'; max 10 ft). '+
  (minMarkGap===null?'Shoe clearance NOT VERIFIED — set your minimum in More options.':
  'Same-datum marks checked against your '+length(minMarkGap)+' minimum.')+
  ' Verify actual shoe access, radius, springback and developed length.';
}

function showQuickSummary(error){
 const box=$('quickSummary');
 if(error){const msg=error.message||String(error);box.innerHTML='<strong>'+(/^Enter /.test(msg)?'Enter your job measurements':'Check these measurements')+'</strong><span>'+html(msg)+'</span>';return}
 if(!layout)return;
 const marks=layout.marks.filter(m=>m.tool!=='GEOMETRY ONLY');
 const first=layout.steps[0];
 const markRows=marks.length?marks.map(m=>'<div class="quick-mark">'+html(m.tag)+' · '+html(length(m.pos))+' · '+html(m.tool)+'</div>').join(''):
  '<div class="quick-alert">No verified physical mark is available here. Confirm the shoe and required dimensions before marking conduit.</div>';
 box.innerHTML='<strong>'+html(layout.name)+'</strong>'+markRows+
 '<span><b>Next:</b> '+html(first.where)+' · '+html(first.reference)+'</span>'+
 '<span><b>Hook:</b> '+html(hookCaption(first.hookSide))+'</span>'+
 '<span><b>Action:</b> '+html(first.label)+' · '+html(first.flip)+'</span>'+
 '<span class="quick-alert">Verify shoe marks, springback and physical dimensions before bending.</span>';
}
function renderMarks(){
 const $svg=$('markDrawing'),max=Math.max(v('stock')||1,...layout.marks.map(m=>m.pos+(unit()==='mm'?100:4)));
 const xx=n=>65+Math.min(1,n/max)*890;
 let code='<svg viewBox="0 0 1030 225" role="img" aria-label="Conduit with tape datum and exact supported field marks"><defs><linearGradient id="m" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#e8f9ff"/><stop offset=".4" stop-color="#a0b5c6"/><stop offset="1" stop-color="#506c7e"/></linearGradient></defs>';
 code+='<rect x="60" y="89" width="915" height="27" rx="13" fill="url(#m)" stroke="#35566c"/><path d="M65 68V133" stroke="#164e70" stroke-width="4"/><text x="57" y="52" fill="#164e70" font-size="18" font-weight="900">0 · MEASURE FROM HERE</text>';
 layout.marks.forEach((m,i)=>{const x=xx(m.pos),color=['#eb2d51','#008fbe','#a4591b','#774aba'][i%4],facing=m.hookSide==='left'?'← HOOK':m.hookSide==='right'?'HOOK →':'? CHECK SHOE';code+='<path d="M'+x+' 79V127" stroke="'+color+'" stroke-width="6"/><text x="'+x+'" y="'+(i%2?168:60)+'" font-size="20" font-weight="900" text-anchor="middle" fill="'+color+'">'+html(m.tag)+'</text><text x="'+x+'" y="'+(i%2?189:28)+'" font-size="14" font-weight="900" text-anchor="middle" fill="'+(m.hookSide==='unknown'?'#a4591b':'#085e82')+'">'+html(facing)+'</text>'});
 code+='<text x="63" y="209" fill="#244d67" font-size="16">DRAW A FULL LINE AT EACH NUMBERED PHYSICAL MARK; read exact dimensions below</text></svg>';
 $svg.innerHTML=code;$('datum').textContent=layout.datum;
 $('markCards').innerHTML=layout.marks.map(m=>'<div class="mark"><b>'+html(m.tag)+' = '+html(length(m.pos))+'</b><div class="face-direction '+(m.hookSide==='unknown'?'unverified':'')+'">'+html(hookCaption(m.hookSide))+'</div><small>'+html(m.tool)+'</small><small>'+html(m.note)+'</small></div>').join('')||'<p>No verified physical marks are calculated for this arrangement.</p>';
 if($('kind').value==='back'&&$('method').value==='klein')
  $('markCards').innerHTML+='<div class="mark"><b>STAR · second 90°</b><div class="face-direction">'+html(hookCaption('right'))+'</div><small>Measure from BACK of completed first bend, not straight-stock zero. Align STAR, not ARROW.</small></div>';
 $('markAlert').textContent=layout.warning;
}
function renderStep(){
 const item=layout.steps[step];
 $('stepCounter').textContent='Step '+(step+1)+' / '+layout.steps.length;$('stepTitle').textContent=item.label;
 $('cueMark').textContent=item.where;$('cueReference').textContent=item.reference;$('cueHook').textContent=hookCaption(item.hookSide)+' · '+item.hook;$('cueFlip').textContent=item.flip;
 $('hookVisual').textContent=hookCaption(item.hookSide);
 $('hookVisual').className='hookvisual '+(item.hookSide==='unknown'?'unverified':'');
 $('instruction').textContent=item.body;$('stepWarning').textContent=item.caution;
 $('prev').disabled=step===0;$('next').disabled=step===layout.steps.length-1;
 if(mode==='partial')renderModel();
}
function defaults(){
 const a=Number($('angle').value),kind=$('kind').value,angleR=a*RAD;
 const H=toMm(v('height')||0),W=toMm(v('width')||0),D=toMm(v('distance')||0),R=toMm(v('radius')||120);
 const stubLead=Math.max(150,H-R*.8),lead=Math.max(150,Math.min(900,D*.45));
 const offsetLength=rise=>Math.max(40,(rise-2*R*(1-Math.cos(angleR)))/Math.sin(angleR));
 const offsetBetween=offsetLength(H),rollRise=Math.hypot(H,W),rollBetween=offsetLength(rollRise),rollPlane=Math.atan2(W,H)*180/Math.PI;
 const centre=a===60?60:45,side=centre/2;
 const config={
  kickExtra:[[stubLead,90,0],[Math.max(160,W*.7),a,90],[Math.max(190,D*.4),90,-90]],
  kick90:[[stubLead,90,0],[Math.max(160,W*.7),a,90]],
  kickBefore:[[Math.max(180,D*.35),a,90],[stubLead,90,0]],
  stub:[[stubLead,90,0]],
  offset:[[lead,a,0],[offsetBetween,-a,0]],
  back:[[Math.max(140,H-R*.8),90,0],[Math.max(180,W),90,0]],
  saddle3:[[Math.max(160,lead*.65),side,0],[Math.max(80,H/Math.sin(side*RAD)*.65),-centre,0],[Math.max(80,H/Math.sin(side*RAD)*.65),side,0]],
  saddle4:[[Math.max(160,lead*.65),a,0],[offsetBetween,-a,0],[Math.max(170,W),-a,0],[offsetBetween,a,0]],
  roll:[[lead,a,rollPlane],[rollBetween,-a,0]],
  segment:[[160,15,0],[140,15,0],[140,15,0],[140,15,0],[140,15,0],[140,15,0]],
  custom:[[stubLead,90,0],[Math.max(200,W*.6),30,90],[Math.max(200,D*.4),90,-90]]
 };
 ops=config[kind].map(row=>({before:Math.round(row[0]),angle:row[1],plane:Math.round(row[2]*10)/10}));
 renderOperations();
}
function renderOperations(){
 $('operationRows').innerHTML='';
 ops.forEach((op,i)=>{
  const row=document.createElement('div');row.className='ops';
  const n=document.createElement('div');n.innerHTML='<label>Operation</label><span class="n">B'+(i+1)+'</span>';row.appendChild(n);
  [['Straight before bend (mm)','before'],['Bend angle (°)','angle'],['Local bend plane (°)','plane']].forEach(([label,key])=>{
   const wrap=document.createElement('div'),l=document.createElement('label'),inp=document.createElement('input');
   l.textContent=label;inp.type='number';inp.step='any';inp.value=op[key];inp.setAttribute('aria-label','B'+(i+1)+' '+label);
   inp.oninput=()=>{if(inp.value.trim()!=='')op[key]=Number(inp.value);renderModel()};
   wrap.append(l,inp);row.appendChild(wrap);
  });
  const remove=document.createElement('button');remove.textContent='Remove';remove.onclick=()=>{if(ops.length<=1)return;ops.splice(i,1);renderOperations();renderModel()};row.appendChild(remove);$('operationRows').appendChild(row);
 });
}
const add=(a,b)=>a.map((n,i)=>n+b[i]),times=(a,n)=>a.map(x=>x*n),cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],norm=a=>times(a,1/Math.max(1e-8,Math.hypot(...a)));
function calculatePath(){
 const radius=toMm(v('radius')),tail=v('tail'),minStraight=v('minStraight');
 const stock=toMm(v('stock'));
 if(!Number.isFinite(radius)||radius<=0||tail===null||!Number.isFinite(tail)||tail<0)throw Error('Preview radius and tail must be valid.');
 if(!Number.isFinite(stock)||stock<=0||stock>TEN_FOOT_EMT_MM+1e-6)
  throw Error('CALCULATION NOT POSSIBLE: enter stock from 0 to 10 ft (3,048 mm).');
 if(minStraight!==null&&(!Number.isFinite(minStraight)||minStraight<0))
  throw Error('Enter a valid minimum straight between arcs (mm).');
 // Reject invalid operations BEFORE summing developed material, so negative
 // leads cannot mask a stock overrun.
 if(!ops.length)throw Error('Enter at least one 3D bend operation.');
 ops.forEach((o,i)=>{
  if(!Number.isFinite(o.before)||o.before<0||!Number.isFinite(o.angle)||Math.abs(o.angle)>180||Math.abs(o.angle)<1e-8||!Number.isFinite(o.plane))
   throw Error('Invalid bend operation '+(i+1)+': check straight lead, angle and plane.');
  if(i>0&&o.before<=1e-6)
   throw Error('BENDS TOO CLOSE: B'+(i+1)+' has no straight conduit between bend arcs. Confirm shoe clearance.');
 });
 const developed=ops.reduce((sum,o)=>sum+o.before+Math.abs(o.angle)*RAD*radius,toMm(tail));
 if(!Number.isFinite(developed)||developed>stock+1e-6)
  throw Error('CALCULATION NOT POSSIBLE on one 10 ft EMT: 3D developed centreline needs '+fmt(developed,1)+' mm, available '+fmt(stock,1)+' mm. Shorten the leads or bends.');
 if(minStraight!==null&&minStraight>0)
  for(let i=1;i<ops.length;i++)
   if(ops[i].before+1e-6<minStraight)
    throw Error('BENDS TOO CLOSE: B'+(i+1)+' has '+fmt(ops[i].before,1)+' mm straight before it; your shoe needs '+fmt(minStraight,1)+' mm.');
 let pos=[0,0,0],t=[1,0,0],n=[0,0,1];
 const pts=[{p:pos.slice(),bend:0}],ends=[];
 function straight(distance,bend){
  const count=Math.max(1,Math.ceil(distance/24)),delta=times(t,distance/count);
  for(let k=0;k<count;k++){pos=add(pos,delta);pts.push({p:pos.slice(),bend})}
 }
 ops.forEach((o,i)=>{
  straight(o.before,i);
  const bin=norm(cross(t,n)),phi=o.plane*RAD,normal=norm(add(times(n,Math.cos(phi)),times(bin,Math.sin(phi))));
  const arc=o.angle*RAD,count=Math.max(2,Math.ceil(Math.abs(o.angle)/2.5)),p0=pos.slice();
  for(let j=1;j<=count;j++){
   const theta=arc*j/count,part=add(times(t,radius*Math.sin(theta)),times(normal,radius*(1-Math.cos(theta))));
   pts.push({p:add(p0,part),bend:i+1});
  }
  pos=pts[pts.length-1].p.slice();
  const nextT=norm(add(times(t,Math.cos(arc)),times(normal,Math.sin(arc))));
  const nextN=norm(add(times(normal,Math.cos(arc)),times(t,-Math.sin(arc))));
  t=nextT;n=nextN;ends.push(pts.length-1);
 });
 straight(toMm(tail),ops.length+1);
 // Prevent invalid projected positions from silently drawing an empty canvas.
 if(pts.some(pt=>pt.p.length!==3||pt.p.some(n=>!Number.isFinite(n))))
  throw Error('3D coordinates are invalid. Reset the bend sequence or check its dimensions.');
 return {pts,ends,radius,developed};
}
const canvas=$('tubeCanvas'),ctx=canvas.getContext('2d');
function rotated(v){
 const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);
 const x=v[0]*cy-v[1]*sy,y=v[0]*sy+v[1]*cy;
 return [x,v[2]*cp-y*sp,v[2]*sp+y*cp];
}
function renderModel(){
 if(!ctx){$('geometryNote').textContent='3D preview unavailable: drawing canvas is not supported.';return}
 try{model=calculatePath()}catch(e){
  model=null;$('geometryNote').textContent='3D input error: '+e.message;
  ctx.clearRect(0,0,canvas.width,canvas.height);
  ctx.fillStyle='#e8f7ff';ctx.font='bold 22px system-ui';
  ctx.fillText('3D preview unavailable',24,60);
  ctx.font='18px system-ui';ctx.fillText('Check dimensions or reset the bend sequence.',24,100);
  return;
 }
 const W=canvas.width,H=canvas.height,end=mode==='partial'?model.ends[Math.min(step,model.ends.length-1)]:model.pts.length-1;
 const visible=model.pts.slice(0,end+1),camera=visible.map(o=>rotated(o.p));
 const xs=camera.map(p=>p[0]),ys=camera.map(p=>p[1]),minx=Math.min(...xs),maxx=Math.max(...xs),miny=Math.min(...ys),maxy=Math.max(...ys);
 if(![minx,maxx,miny,maxy].every(Number.isFinite)){
  $('geometryNote').textContent='3D projection error: invalid coordinates. Reset the view or bend sequence.';
  ctx.clearRect(0,0,W,H);
  return;
 }
 const midx=(minx+maxx)/2,midy=(miny+maxy)/2,size=Math.min((W-135)/Math.max(1,maxx-minx),(H-115)/Math.max(1,maxy-miny))*zoom;
 const screen=camera.map(p=>[W/2+(p[0]-midx)*size,H/2-(p[1]-midy)*size]);
 const bg=ctx.createLinearGradient(0,0,W,H);bg.addColorStop(0,'#071e30');bg.addColorStop(1,'#1b4966');ctx.fillStyle=bg;ctx.fillRect(0,0,W,H);
 const draw=(a,b,width,color)=>{
  if(b<=a)return;ctx.beginPath();for(let i=a;i<=b;i++)i===a?ctx.moveTo(...screen[i]):ctx.lineTo(...screen[i]);
  ctx.strokeStyle=color;ctx.lineWidth=width;ctx.lineCap='round';ctx.lineJoin='round';ctx.stroke();
 };
 if(screen.length>1){draw(0,screen.length-1,22,'#041622');draw(0,screen.length-1,17,'#64899f');draw(0,screen.length-1,12,'#b0d9e6');draw(0,screen.length-1,3,'#e4fcff')}
 model.ends.forEach((e,i)=>{
  if(e>=screen.length)return;
  let j=e;while(j>0&&model.pts[j].bend===i+1)j--;draw(j,e,7,['#f47e90','#64edf0','#ffd485','#b69eff','#91e595'][i%5]);
  let p=screen[e];ctx.fillStyle='#ffeab5';ctx.beginPath();ctx.arc(p[0],p[1],6,0,Math.PI*2);ctx.fill();ctx.fillStyle='#f7ffff';ctx.font='bold 18px system-ui';ctx.fillText('B'+(i+1),p[0]+10,p[1]-12);
 });
 if(screen.length){ctx.fillStyle='#9cf5ff';ctx.font='bold 17px system-ui';let a=screen[0],b=screen[screen.length-1];ctx.fillText('START',a[0]+8,a[1]-15);ctx.fillText(mode==='partial'?'IN PROGRESS':'FINISH',b[0]+8,b[1]+23)}
 ctx.fillStyle='#d1e7f3';ctx.font='15px system-ui';ctx.fillText('3D CENTRELINE · GEOMETRY ONLY · NOT AN ARROW / STAR SHOP MARK',16,25);
 $('geometryNote').textContent='Bends: '+ops.map((o,i)=>'B'+(i+1)+' '+fmt(o.angle)+'° / plane '+fmt(o.plane)+'° / lead '+fmt(o.before)+' mm').join(' · ')+'. Developed material '+fmt(model.developed,1)+' / '+fmt(toMm(v('stock')),1)+' mm; preview radius '+fmt(model.radius)+' mm. Actual radius and physical shoe clearance must be verified.';
}
function view(name){
 const options={iso:[-.7,.38],front:[0,0],side:[-Math.PI/2,0],top:[0,1.56]};
 [yaw,pitch]=options[name]||options.iso;renderModel();
}
document.querySelectorAll('[data-camera]').forEach(b=>b.onclick=()=>view(b.dataset.camera));
$('zoomPlus').onclick=()=>{zoom=Math.min(5,zoom*1.2);renderModel()};
$('zoomMinus').onclick=()=>{zoom=Math.max(.2,zoom/1.2);renderModel()};
$('resetView').onclick=()=>{zoom=1;view('iso')};
canvas.addEventListener('pointerdown',e=>{drag={x:e.clientX,y:e.clientY};canvas.setPointerCapture(e.pointerId)});
canvas.addEventListener('pointermove',e=>{if(!drag)return;let dx=e.clientX-drag.x,dy=e.clientY-drag.y;drag={x:e.clientX,y:e.clientY};yaw+=dx*.007;pitch=Math.max(-1.54,Math.min(1.54,pitch+dy*.007));renderModel()});
canvas.addEventListener('pointerup',()=>drag=null);canvas.addEventListener('pointercancel',()=>drag=null);
canvas.addEventListener('wheel',e=>{e.preventDefault();zoom=Math.min(5,Math.max(.2,zoom*(e.deltaY>0?.9:1.1)));renderModel()},{passive:false});
canvas.addEventListener('keydown',e=>{if(e.key==='ArrowLeft')yaw-=.1;else if(e.key==='ArrowRight')yaw+=.1;else if(e.key==='ArrowUp')pitch+=.1;else if(e.key==='ArrowDown')pitch-=.1;else return;e.preventDefault();renderModel()});
$('finished').onclick=()=>{mode='finished';$('previewStatus').textContent='Final shape';renderModel()};
$('throughStep').onclick=()=>{mode='partial';$('previewStatus').textContent='Up to selected bend';renderModel()};
$('prev').onclick=()=>{step=Math.max(0,step-1);renderStep()};
$('next').onclick=()=>{step=Math.min(layout.steps.length-1,step+1);renderStep()};
$('addBend').onclick=()=>{if(ops.length>=12)return alert('Maximum 12 bends in the 3D preview.');ops.push({before:300,angle:30,plane:0});renderOperations();renderModel()};
$('resetBends').onclick=()=>{defaults();renderModel()};
function refresh(reset){
 try{
  layout=getLayout();
  checkStockAndSpacing();
  if(reset)defaults();
  syncQuickFields();step=0;renderMarks();renderStep();showQuickSummary();renderModel();
 }catch(e){
  layout=null;
  $('stockStatus').className=/^Enter /.test(e.message)?'stock-status pending':'stock-status bad';
  $('stockStatus').textContent=e.message;
  syncQuickFields();showQuickSummary(e);
  $('markDrawing').innerHTML='';$('markCards').innerHTML='';
  $('markAlert').textContent=e.message;
  $('instruction').textContent='Correct the measurements. No physical bend plan is approved.';
  $('geometryNote').textContent='Preview withheld while field stock or mark check fails.';
 }
}

let quickInputTimer;
$('quickFields').addEventListener('input',e=>{
 if(!e.target.matches('input[type="number"]'))return;
 clearTimeout(quickInputTimer);
 quickInputTimer=setTimeout(()=>refresh(true),180);
});
$('threeDDetails').addEventListener('toggle',()=>{if($('threeDDetails').open)renderModel()});
$('editorDetails').addEventListener('toggle',()=>{if($('editorDetails').open)renderModel()});
$('kind').onchange=()=>refresh(true);$('angle').onchange=()=>refresh(true);$('method').onchange=()=>refresh(false);
$('critical').onchange=()=>refresh(false);
$('recalc').onclick=()=>{refresh(true);$('quickSummary').scrollIntoView({behavior:'smooth',block:'center'});};
['stock','minMarkGap','minStraight'].forEach(id=>$(id).addEventListener('input',()=>refresh(false)));
$('unit').addEventListener('change',function(){
 const old=this.dataset.previous||'mm',now=this.value,change=old===now?1:now==='mm'?25.4:1/25.4;
 ['height','deduct','distance','extraMark','width','radius','stock','minMarkGap'].forEach(id=>{let value=$(id).value.trim();if(value!==''&&Number.isFinite(Number(value)))$(id).value=fmt(Number(value)*change,now==='mm'?2:4)});
 this.dataset.previous=now;refresh(false);
});
$('loadProfile').onclick=()=>{
 let p;try{p=JSON.parse(localStorage.getItem('ew.benderCalibration.active.v1')||'null')}catch(e){}
 if(!p||!['mm','in'].includes(p.units)){
  $('profileStatus').textContent='No valid saved bender profile. Record one in Bender Calibration first.';return;
 }
 const hasDeduct=typeof p.deduct==='number'&&Number.isFinite(p.deduct)&&p.deduct>=0;
 const profileNumber=key=>{
  const raw=p[key];
  if(raw===null||raw===undefined||String(raw).trim()==='')return null;
  const n=Number(raw);
  return Number.isFinite(n)&&n>0&&n<=(p.units==='mm'?3048:120)?n:null;
 };
 const minGap=profileNumber('minMarkGap'),minStraight=profileNumber('minStraight');
 if(!hasDeduct&&minGap===null&&minStraight===null){
  $('profileStatus').textContent='No measured deduct or verified gap saved for this shoe.';return;
 }
 const convert=n=>p.units===unit()?n:unit()==='mm'?n*25.4:n/25.4;
 // Clear values absent from this profile, avoiding stale measurements from
 // another shoe. Arc clearance is always expressed in mm in the editor.
 $('deduct').value=hasDeduct?fmt(convert(p.deduct),4):'';
 $('minMarkGap').value=minGap!==null?fmt(convert(minGap),4):'';
 $('minStraight').value=minStraight!==null?fmt(p.units==='mm'?minStraight:minStraight*25.4,2):'';
 $('profileStatus').textContent='Loaded '+p.name+' ('+p.size+' '+p.material+'). Confirm the exact shoe. Blank clearance fields mean NOT VERIFIED.';
 refresh(false);
};
$('copyPlan').onclick=()=>{
 if(!layout)return;
 const data=[layout.name,layout.datum,'PHYSICAL MARKS:'].concat(layout.marks.map(m=>m.tag+': '+length(m.pos)+' — '+m.tool+' — '+m.note))
 .concat(['INSTRUCTIONS:']).concat(layout.steps.map((s,i)=>(i+1)+'. '+s.label+'\nMARK: '+s.where+'\nTOOL: '+s.reference+'\nHOOK FACING: '+hookCaption(s.hookSide)+'\nHOOK SETUP: '+s.hook+'\nFLIP / ROLL: '+s.flip+'\n'+s.body+'\nCAUTION: '+s.caution)).concat(['SAFETY: '+layout.warning]).join('\n');
 navigator.clipboard.writeText(data).then(()=>alert('Instruction plan copied.'),()=>alert('Clipboard unavailable here.'));
};
$('print').onclick=()=>window.print();
$('quickCopy').onclick=()=>$('copyPlan').click();
const items=['Tape zero and reference end are confirmed.','I know which ARROW/STAR/NOTCH belongs on EACH physical mark.','Hook direction matches this exact bender and floor/air method.','I checked whether to FLIP the bender assembly for the next bend.','I checked pipe ROTATION and preserved the intended bend plane.','The finished shape, shoe clearance, radius, springback and bend sum are verified.'];
$('checks').innerHTML=items.map((t,i)=>'<label class="check"><input type="checkbox" aria-label="Field check '+(i+1)+'"><span>'+html(t)+'</span></label>').join('');
$('unit').dataset.previous='mm';defaults();refresh(false);
})();