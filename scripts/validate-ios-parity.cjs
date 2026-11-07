/* Consistency checks for the iOS offline 309A bank and native/tool navigation. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const ios = path.join(root, 'ios-client', 'ElectricalWorkbench');
const bank = JSON.parse(fs.readFileSync(path.join(ios, 'Resources', 'question-bank.json'), 'utf8'));
const expected = [
  'A — Common Occupational Skills',
  'B — Generating, Distribution and Service Systems',
  'C — Wiring Systems',
  'D — Motors and Control Systems',
  'E — Signalling and Communication Systems'
];
if (!Array.isArray(bank) || bank.length !== 25) throw new Error('iOS daily quiz must contain 25 questions');
for (const category of expected) {
  if (bank.filter(q => q.category === category).length !== 5) {
    throw new Error('Each of the five task categories must have five questions: ' + category);
  }
}
for (const [i, q] of bank.entries()) {
  if (!q.question || !q.explanation || !q.reference ||
      !Array.isArray(q.options) || q.options.length !== 4 ||
      !Number.isInteger(q.correctIndex) || q.correctIndex < 0 || q.correctIndex > 3) {
    throw new Error('Invalid question record ' + i);
  }
}
const reference = fs.readFileSync(
  path.join(ios, '..', '..', 'android-client', 'app', 'src', 'main', 'java',
    'com', 'gfh', 'electricalworkbench', 'MobileQuestionBank.kt'), 'utf8');
for (const q of bank) {
  if (!reference.includes(q.question)) {
    throw new Error('Android study bank divergence: ' + q.question.slice(0, 70));
  }
}
const rootUI = fs.readFileSync(path.join(ios, 'WorkbenchRoot.swift'), 'utf8');
const navigation = fs.readFileSync(path.join(ios, 'WorkbenchTools.swift'), 'utf8');
for (const screen of ['QuickBenderView','PullBoxSizingView','BenderCalibrationView','PracticeQuizView','FabricationView','TroubleshootingOfflineView']) {
  if (!rootUI.includes(screen) && !navigation.includes(screen)) {
    throw new Error('Missing native iOS screen link: ' + screen);
  }
}
const requiredNative = [
  ['CloudAccountSession.swift', ['api/client/bootstrap', 'ELECTRICAL_CALCULATORS', 'CloudSignInView']],
  ['OfflinePowerCalculatorsView.swift', ['case transformer', 'case motor', 'case voltageDrop', 'case capacitor', 'case powerFactor', 'case fault']],
  ['NativeSafetyWorkspaceView.swift', ['Thunderstorm', 'PPEComparisonView', 'ApproachComparisonView', 'GloveInspectionView', 'SafetyMaintenanceView']],
  ['NativeWorkspacesView.swift', ['NativeLearnView', 'NativeFieldToolsView', 'NativeProjectView', 'NativeCareerView']],
  ['NativeChatGPTWorkspaceView.swift', ['/api/chatgpt/status', '/api/chatgpt/models', '/api/chatgpt/respond']],
  ['EnterpriseTeamView.swift', ['/api/enterprise/membership', 'ENTERPRISE_TEAM_MANAGEMENT']],
];
for (const [file, required] of requiredNative) {
  const code = fs.readFileSync(path.join(ios, file), 'utf8');
  for (const symbol of required) {
    if (!code.includes(symbol)) throw new Error(file + ' missing ' + symbol);
  }
}
const androidSafety = fs.readFileSync(
  path.join(root, 'android-client', 'app', 'src', 'main', 'java',
            'com', 'gfh', 'electricalworkbench', 'SafetyWorkspace.kt'), 'utf8');
for (const name of ['Thunderstorm', 'PPE', 'Approach', 'Unsafe', 'Gloves', 'Maintenance']) {
  if (!androidSafety.includes(name)) throw new Error('Missing Android safety source ' + name);
}
const portal = JSON.parse(fs.readFileSync(
  path.join(root, 'portal', 'src', 'main', 'resources', 'static', 'ios-release.json'), 'utf8'));
if (portal.platform !== 'ios' || !/^\d+\.\d+\.\d+$/.test(portal.latestVersion) ||
    (portal.appStoreUrl && !/^https:\/\/apps\.apple\.com\//.test(portal.appStoreUrl))) {
  throw new Error('Invalid iOS release manifest');
}
console.log('iOS parity validation passed: 25 questions across five categories, native safety/field/account/ChatGPT/enterprise links, release manifest valid.');
