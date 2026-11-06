/* Compact measurement-first guide. Job dimensions are not shoe alignment marks. */
(function () {
  'use strict';
  var kind = document.getElementById('kind');
  var sketch = document.getElementById('measureSketch');
  if (!kind || !sketch) return;

  var guides = {
    offset: {
      lead: 'Put tape zero at a square end of the straight conduit.',
      steps: ['Measure along the run to your chosen critical-bend/reference line.', 'Measure the required perpendicular centreline rise, including clearance.', 'Enter the angle; both ARROW marks will be measured from tape zero.'],
      hints: {height:'Required change in conduit centreline height; not just obstacle height.', distance:'Tape zero to the job reference line used by your chosen near/far method.'}, drawing:'offset'
    },
    roll: {
      lead: 'Measure the vertical rise AND sideways roll independently.',
      steps: ['Choose tape zero and measure to the critical/reference line.', 'Measure vertical rise and horizontal sideways roll centreline to centreline.', 'The app calculates the diagonal true offset. Do not enter a diagonal as rise.'],
      hints: {height:'Vertical rise only.', width:'Sideways roll only (plan view); not diagonal.', distance:'Tape zero to the chosen reference line.'}, drawing:'roll'
    },
    stub: {
      lead: 'Tape zero is the cut end that will become the short stub.',
      steps: ['Measure the finished stub height required at the job.', 'Read take-up/deduct stamped on your exact bender or load your test-bend profile.', 'The ARROW pencil mark is finished stub height MINUS that deduct.'],
      hints: {height:'Finished 90° stub height using the measurement convention of your shoe.', deduct:'Actual shoe take-up/deduct, not a general guess.'}, drawing:'stub'
    },
    back: {
      lead: 'Measure the second 90 AFTER the first one is bent.',
      steps: ['From the short end, measure finished first stub height.', 'Enter the actual deduct and make the first 90 with the ARROW.', 'Then measure from the BACK of the completed first bend to the second STAR reference.'],
      hints: {height:'Finished first stub from its short end.', deduct:'Actual take-up for the first 90.', width:'Back-to-back distance, measured after the first bend from its back; NOT tape zero.'}, drawing:'back'
    },
    saddle3: {
      lead: 'Locate the CENTRE of the obstacle, not its near face.',
      steps: ['Measure from tape zero along the straight run to obstacle centre.', 'Measure how high the conduit centreline must rise to clear the obstruction.', 'Use centre NOTCH and outside ARROW only if your shoe matches the selected method.'],
      hints: {height:'Rise necessary to clear obstacle, including proper clearance.', distance:'Tape zero to centre of the obstruction.'}, drawing:'saddle'
    },
    saddle4: {
      lead: 'Measure the near face, width, and clearance height.',
      steps: ['Measure from tape zero to the NEAR face of the obstruction.', 'Measure obstacle width along the run, then lift required to clear it.', 'Displayed positions are geometry only — confirm physical shoe marks separately.'],
      hints: {height:'Required lift with clearance.', distance:'From tape zero to near face.', width:'Obstacle width in the direction of the conduit.'}, drawing:'saddle4'
    },
    kick90: {
      lead: 'A kick RISE alone cannot tell you where to put a kick mark.',
      steps: ['Measure the 90 stub from its short end and enter actual deduct.', 'Measure required kick rise and choose its angle.', 'Locate the kick from a separate known job datum; use a shoe-specific layout for its mark.'],
      hints: {height:'Finished 90° stub / kick rise per selected layout.', deduct:'Exact bender take-up.', width:'Planning dimension only; not an automatic kick mark.'}, drawing:'kick'
    },
    kickBefore: {
      lead: 'The kick and 90 require two separate physical reference points.',
      steps: ['Establish which cut end becomes the finished short 90 leg.', 'Measure required rise and locate the kick from a known reference.', 'Verify the shoe layout; the 3D sketch is NOT a bend-mark template.'],
      hints: {height:'Job rise/finished leg; confirm how both are dimensioned.', distance:'Planning reference; does not establish a kick shoe mark.', deduct:'Verified 90° deduct.'}, drawing:'kick'
    },
    kickExtra: {
      lead: 'Three bends require the kick position and second 90 location.',
      steps: ['Measure first 90 stub from the short end and use actual deduct.', 'Measure kick rise and location independently.', 'Measure remaining leg for the second 90; verify tool access before marking.'],
      hints: {height:'Required first stub and rise per layout.', distance:'Job planning reference, not an automatic shoe mark.', width:'Remaining leg or spacing for planning only.'}, drawing:'kick'
    },
    segment: {
      lead: 'A segmented 90 requires a verified shoe-specific spacing method.',
      steps: ['Measure the required finished radius and available space.', 'Check the bend shoe permits repeatable small bends.', 'Only the finished shape is previewed; no physical marks are issued.'], hints:{}, drawing:'preview'
    },
    custom: {
      lead: 'Custom 3D layouts are for planning before a shop layout.',
      steps: ['Measure each finished leg, obstacle, and bend plane.', 'Preview the geometry in the advanced editor.', 'Derive physical pencil marks independently for your exact bender.'], hints:{}, drawing:'preview'
    }
  };
  function el(tag, text) {
    var node = document.createElement(tag);
    node.textContent = text;
    return node;
  }
  function arrow(x1,y1,x2,y2) {
    return '<line x1="'+x1+'" y1="'+y1+'" x2="'+x2+'" y2="'+y2+'" stroke="#095f98" stroke-width="2" marker-start="url(#mArr)" marker-end="url(#mArr)"/>';
  }
  function t(x,y,txt) {
    return '<text x="'+x+'" y="'+y+'" fill="#103f5d" font-size="12.5" font-weight="700">'+txt+'</text>';
  }
  function draw(type) {
    var base = '<svg viewBox="0 0 460 145" role="img" aria-label="How to measure job dimensions, schematic not to scale"><defs><marker id="mArr" viewBox="0 0 10 10" refX="5" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse"><path d="M0 0L10 5L0 10Z" fill="#095f98"/></marker></defs><rect width="460" height="145" rx="9" fill="#f5fafc"/>';
    var ending = '</svg>';
    if (type === 'stub')
      return base+'<path d="M145 19V94Q145 104 159 104H426" fill="none" stroke="#798f9e" stroke-width="13" stroke-linecap="round"/>'+arrow(96,21,96,102)+t(12,65,'Finished stub')+t(12,80,'height')+t(148,15,'TAPE 0: short end')+ending;
    if (type === 'back')
      return base+'<path d="M88 22V92H366V22" fill="none" stroke="#798f9e" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"/>'+arrow(92,114,365,114)+t(100,136,'BACK of first 90 → second STAR')+ending;
    if (type === 'kick')
      return base+'<path d="M82 22V100H238L337 69H423" fill="none" stroke="#798f9e" stroke-width="12" stroke-linejoin="round"/>'+arrow(61,24,61,99)+arrow(382,70,382,101)+t(10,60,'Stub')+t(345,60,'Rise')+t(160,20,'Kick mark needs job layout')+ending;
    if (type === 'preview')
      return base+'<path d="M25 100H432" fill="none" stroke="#798f9e" stroke-width="12" stroke-linecap="round"/>'+t(60,56,'Measure finished legs and radius')+t(60,75,'Preview only — no physical shoe marks')+ending;
    var saddle=type==='saddle'||type==='saddle4',center=type==='saddle',x=center?316:287;
    var obstacle='<rect x="'+(center?285:288)+'" y="64" width="'+(center?63:82)+'" height="37" fill="#e3ac79" stroke="#9d6633"/>';
    var drawing=base+'<path d="M22 101H436" stroke="#798f9e" stroke-width="12" stroke-linecap="round"/>'+obstacle+
      '<path d="M22 82V122" stroke="#b83c52" stroke-width="3"/>'+t(25,76,'TAPE 0')+
      arrow(28,124,x,124)+t(115,141,center?'To obstruction CENTRE':'To reference line')+
      arrow(406,63,406,101)+t(367,53,'Rise')+
      t(270,44,type==='saddle4'?'Measure width':center?'Obstruction centre':'Reference');
    if(type==='roll')drawing+=t(160,23,'Sideways roll: measure separately');
    return drawing+ending;
  }
  function update(){
    var help=guides[kind.value]||guides.custom;
    document.getElementById('measureIntro').textContent=help.lead;
    var list=document.getElementById('measureSteps');
    list.replaceChildren();
    help.steps.forEach(function(step){list.appendChild(el('li',step));});
    sketch.innerHTML=draw(help.drawing);
    document.getElementById('measureCaution').textContent=
      'Measurement sketch only — NOT to scale, and NOT physical bender ARROW / STAR / NOTCH marks.';
    document.querySelectorAll('#quickFields .field').forEach(function(field){
      var input=field.querySelector('input,select');
      if(!input)return;
      var note=field.querySelector('.field-help');
      if(!note){note=el('small','');note.className='field-help';field.appendChild(note);}
      note.textContent=help.hints[input.id]||(
        input.id==='angle'?'Angle of EACH bend; not the sum of both bends.':''
      );
      note.hidden=!note.textContent;
    });
  }
  kind.addEventListener('change',update);
  update();
})();
