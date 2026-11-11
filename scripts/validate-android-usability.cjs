'use strict';
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const a='android-client/app/src/main/java/com/gfh/electricalworkbench/';
const read=(s)=>fs.readFileSync(path.join(root,s),'utf8');
const palette=read(a+'ElectricalUiTheme.kt');
const common=read(a+'MobileWorkspaceCommon.kt');
const finder=read(a+'MobileToolFinder.kt');
const shell=read(a+'MobileWorkbenchActivity.kt');
const more=read(a+'MoreWorkspaces.kt');
const safety=read(a+'SafetyWorkspace.kt');
const circuit=read(a+'CircuitSizingActivity.kt');
const transformer=read(a+'TransformerSizingActivity.kt');
const field=read(a+'FieldSizingActivity.kt');
const web=read(a+'MobileWebStyle.kt');
const must=(src,s,label)=>assert.ok(src.includes(s),label+': '+s);
function rgb(hex){return[0,2,4].map(n=>parseInt(hex.slice(n,n+2),16)/255);}
function luminance(hex){
  const linear=rgb(hex).map(x=>x<=.04045?x/12.92:((x+.055)/1.055)**2.4);
  return linear[0]*.2126+linear[1]*.7152+linear[2]*.0722;
}
function contrast(a,b){const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
const colors={};
for(const match of palette.matchAll(/val (\w+)=Color\(0xFF([0-9A-Fa-f]{6})\)/g)) colors[match[1]]=match[2];
for(const [front,back,min] of [
  ['text','background',7],['text','raised',7],['text','card',7],
  ['muted','card',4.5],['muted','raised',4.5],['primary','raised',4.5],
  ['onPrimary','primary',4.5],['warning','card',4.5],['danger','card',4.5]
]){
  assert.ok(colors[front]&&colors[back],'Missing color pair '+front+'/'+back);
  assert.ok(contrast(colors[front],colors[back])>=min,
    front+' on '+back+' fails contrast: '+contrast(colors[front],colors[back]).toFixed(2));
}
must(shell,'ElectricalUiTheme(content)','One shared Android theme');
must(common,'Card(onClick=onClick','Compact one-tap cards, no redundant Open button');
must(common,'MWSectionPicker(','One bounded section picker');
for(const area of ['Study','Field','Projects','Career'])must(more,'MWSectionPicker("'+area+'"','No scrolling tab bars for '+area);
must(safety,'MWSectionPicker("Safety"','No scrolling safety tabs');
assert.ok(!more.includes('ScrollableTabRow(')&&!safety.includes('ScrollableTabRow('),'Remove horizontal section tab scrolling');
must(finder,'Search tools by job or calculation','Task query');
must(finder,'val featured=setOf(','Common tasks visible first');
must(finder,'Show all ','Explicit expansion of rest of catalog');
must(finder,'FilterChip(selected=offlineOnly','Offline filter');
must(finder,'getSharedPreferences("ewb.tool.shortcuts.v1."','Account-scoped favourites');
assert.ok(!finder.includes('.horizontalScroll('),'The tool finder must not require sideways scrolling');
for(const task of ['conduit-marks','conductor-drop','pull-box','motor','fault-offline','jobsite']){
  must(finder,'"'+task+'"','Common field task '+task);
}
for(const area of ['LEARN','FIELD','PROJECTS','CAREER'])must(finder,'MoreArea.'+area,'Direct section links');
must(shell,'MWHubCard("Bend conduit"','Home opens with a real job task');
must(shell,'MWHubCard("Size a circuit · Offline"','Native circuit quick start');
must(circuit,'if (showArchive) CircuitInputCard','Hide long history until requested');
must(transformer,'var editing by remember(load.id)','Editable but collapsed transformer loads');
must(transformer,'if (editing) {','Long load fields must be deliberate');
must(field,'var showMeasureSteps by remember(type)','Conduit guide can expand when needed');
must(field,'Text(datum,color=EWColors.text','Never hide measurement reference datum');
must(web,'flex: 1 1 155px','Reduce mobile web action stacking');
for(const screen of ['CircuitSizingActivity.kt','TransformerSizingActivity.kt','IndustrialFieldActivity.kt','FieldSizingActivity.kt','AccountEntryActivity.kt','UpdateGateActivity.kt']){
  must(read(a+screen),'ElectricalUiTheme','Unify native screen palette in '+screen);
}
console.log('Android visual navigation contract passed: high-contrast palette, task-first access, bounded section selectors and safe expandable long forms.');
