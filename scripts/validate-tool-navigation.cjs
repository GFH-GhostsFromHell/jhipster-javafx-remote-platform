'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');
const read = path => fs.readFileSync(path, 'utf8');
const root = 'portal/src/main/resources/static/';
const dashboard = read(root + 'portal.html');
const calc = read(root + 'calculators.html');
const guided = read(root + 'guided-calculators.html');
const field = read(root + 'field-suite.html');
const android = read('android-client/app/src/main/java/com/gfh/electricalworkbench/MobileWorkbenchActivity.kt');
const more = read('android-client/app/src/main/java/com/gfh/electricalworkbench/MoreWorkspaces.kt');
const ios = read('ios-client/ElectricalWorkbench/WorkbenchTools.swift');
const desktop = read('desktop-client/src/main/java/com/example/desktop/ElectricalWorkbenchNativeApp.java');

const includes = (source, token, label) => assert.ok(source.includes(token), label + ': ' + token);
const exactlyOne = (source, token) => assert.equal(source.split(token).length - 1, 1, 'Expected one ' + token);
for (const id of ['toolFinderSearch','toolFinderCount','toolFinderEmpty','toolDirectory']) {
  exactlyOne(dashboard, 'id="' + id + '"');
}
for (const category of ['all','install','calculate','troubleshoot','projects','reference']) {
  includes(dashboard, 'data-tool-category="' + category + '"', 'Tool filter category');
}
for (const link of [
  '/bend-instruction-lab.html', '/guided-calculators.html',
  '/troubleshooting-kb.html', '/account-records.html'
]) includes(dashboard, 'class="workflow-link" href="' + link + '"', 'Quick task route');
includes(dashboard, '.nav .label{display:inline!important', 'Mobile navigation must keep labels');
includes(dashboard, "function update()", 'Task filtering enabled');
assert.ok(!dashboard.includes('<h2>Reports & statistics</h2>'), 'Do not bring back empty dashboard placeholders');
includes(calc, 'id="sectionFinder"', 'Section-based calculator search');
includes(guided, '<h1>Step-by-Step Electrical Calculators</h1>', 'Guided calculator title');
includes(field, '<h1>Jobsite Tools</h1>', 'Field tools title');
for (const title of ['Electrical Calculators','Step-by-Step Calculators','Jobsite Tools','Equipment Troubleshooting']) {
  includes(dashboard, '<span class="label">' + title + '</span>', 'Portal navigation');
}
includes(android, 'initialMoreArea = area', 'Android direct home links');
includes(more, 'remember(initialArea)', 'Android direct target selection');
for (const area of ['LEARN','FIELD','PROJECTS','CAREER']) {
  includes(android, 'onOpenMore(MoreArea.' + area + ')', 'Android section deep link');
}
includes(ios, 'name: "Step-by-Step Calculators"', 'iOS tool naming');
includes(desktop, 'navButton("🧮  Electrical Calculators")', 'Desktop tool naming');
console.log('Task-first navigation labels, routes, search controls, and Android section shortcuts verified.');
