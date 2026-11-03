from pathlib import Path

# ----- JavaFX Daily 25: make answer options explicit, full-width and high-contrast -----
p=Path('desktop-client/src/main/java/com/example/desktop/ElectricianPlatformPane.java')
s=p.read_text()
old='''        VBox answers = new VBox(8); ToggleGroup group = new ToggleGroup();\n'''
new='''        VBox answers = new VBox(10); ToggleGroup group = new ToggleGroup();\n        answers.setFillWidth(true);\n        answers.setMinHeight(190);\n        answers.setStyle("-fx-background-color:#f8fafc;-fx-border-color:#e2e8f0;-fx-border-radius:10;-fx-background-radius:10;-fx-padding:10;");\n'''
if old not in s: raise SystemExit('quiz answers anchor not found')
s=s.replace(old,new,1)
old='''            for (int i=0;i<q.options().size();i++) { RadioButton rb = new RadioButton((char)('A'+i) + ". " + q.options().get(i)); rb.setToggleGroup(group); rb.setUserData(i); rb.setWrapText(true); answers.getChildren().add(rb); }\n'''
new='''            for (int i=0;i<q.options().size();i++) {\n                RadioButton rb = new RadioButton((char)('A'+i) + ". " + q.options().get(i));\n                rb.setToggleGroup(group);\n                rb.setUserData(i);\n                rb.setWrapText(true);\n                rb.setMaxWidth(Double.MAX_VALUE);\n                rb.setPrefWidth(900);\n                rb.setMinHeight(38);\n                rb.setVisible(true);\n                rb.setManaged(true);\n                rb.setStyle("-fx-text-fill:#0f172a;-fx-font-size:14px;-fx-font-weight:600;-fx-background-color:white;-fx-border-color:#cbd5e1;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:9 12;");\n                answers.getChildren().add(rb);\n            }\n            if (q.options().isEmpty()) {\n                Label missing = new Label("No answer choices were loaded for this question. The question bank entry needs repair.");\n                missing.setStyle("-fx-text-fill:#b91c1c;-fx-font-weight:bold;");\n                answers.getChildren().add(missing);\n            }\n'''
if old not in s: raise SystemExit('quiz option loop anchor not found')
s=s.replace(old,new,1)
p.write_text(s)

# ----- Local store: normalize new guided collections -----
p=Path('desktop-client/src/main/java/com/example/desktop/LocalWorkspaceStore.java')
s=p.read_text()
old='''        "safetyAssessments","unsafeConditions","rubberGloves","safetyMaintenance","stormStandDowns"\n'''
new='''        "safetyAssessments","unsafeConditions","rubberGloves","safetyMaintenance","stormStandDowns",\n        "guidedSolutions","practiceDrafts"\n'''
if old not in s: raise SystemExit('local store collections anchor not found')
s=s.replace(old,new,1)
p.write_text(s)

# ----- Android: add Guided Worksheets as the first Workbench card -----
p=Path('android-client/app/src/main/java/com/gfh/electricalworkbench/MobileWorkbenchActivity.kt')
s=p.read_text()
old='''        MWHubCard("Engineering Power Systems", "Per-unit, base values, transformer impedance, transmission lines, symmetrical components, sequence faults and short-circuit tools") {\n            context.startActivity(Intent(context, EngineeringPowerActivity::class.java))\n        }\n        MWHubCard("Motor & protection wizard", "Motor type, starting method, time-delay/non-time-delay fuse, inverse-time breaker, overload and disconnect selections") { onOpenWeb("/calculators.html?section=s28") }\n'''
new='''        MWHubCard("Guided C of Q Worksheets", "Default guided workflow with worked steps, Why-this-answer explanations, conductor + voltage drop, D3/K, grounding/bonding, transformer, motor taps, refrigeration, fire pump, neutral, fire alarm, service demand, presets and saved solutions") { onOpenWeb("/guided-calculators.html") }\n        MWHubCard("Engineering Power Systems", "Per-unit, base values, transformer impedance, transmission lines, symmetrical components, sequence faults and short-circuit tools") {\n            context.startActivity(Intent(context, EngineeringPowerActivity::class.java))\n        }\n        MWHubCard("Motor & protection wizard", "Motor type, starting method, time-delay/non-time-delay fuse, inverse-time breaker, overload and disconnect selections") { onOpenWeb("/calculators.html?section=s28") }\n'''
if old not in s: raise SystemExit('android workbench anchor not found')
s=s.replace(old,new,1)
s=s.replace('''    MWPage("CEC 2024 Workbench", "The shared calculator workspace now uses the same selection-driven motor, transformer, refrigeration, welder and protection choices as desktop.") {\n''','''    MWPage("CEC 2024 Workbench", "Guided Worksheets is the default exam-style workflow; the full section calculators and advanced pack remain available for detailed work.") {\n''',1)
p.write_text(s)

# ----- Main web calculator: expose Guided Worksheets prominently -----
p=Path('portal/src/main/resources/static/calculators.html')
s=p.read_text()
old='''<header class="top"><div><div class="brand">CEC 2024 Calculator Workspace</div><div style="font-size:12px;color:#cbd5e1">Web companion to the desktop electrician calculator</div></div><div><a href="/advanced-calculations.html">Advanced calculations</a> <a href="/portal.html">Account portal</a> <button onclick="logout()">Sign out</button></div></header>\n'''
new='''<header class="top"><div><div class="brand">CEC 2024 Calculator Workspace</div><div style="font-size:12px;color:#cbd5e1">Web companion to the desktop electrician calculator</div></div><div><a href="/guided-calculators.html">Guided Worksheets</a> <a href="/advanced-calculations.html">Advanced calculations</a> <a href="/portal.html">Account portal</a> <button onclick="logout()">Sign out</button></div></header>\n'''
if old not in s: raise SystemExit('calculator header anchor not found')
s=s.replace(old,new,1)
p.write_text(s)

# ----- Android release notes -----
p=Path('android-client/app/src/main/assets/release-notes.txt')
s=p.read_text()
addition='''\nGuided calculator usability upgrade:\n- Guided C of Q Worksheets is now the first calculator shortcut on Android.\n- Guided/Advanced input modes, numbered worked solutions, rule-path explanations and rounding-direction notes are included.\n- Combined conductor/voltage-drop, D3/K, grounding/bonding, transformer, motor tap, refrigeration, fire-pump, neutral, fire-alarm and service-demand worksheets are available.\n- Common worksheet presets are included.\n- Worked solutions can be copied, saved locally, or turned into practice-question drafts.\n'''
if 'Guided calculator usability upgrade:' not in s:s=s.rstrip()+addition+'\n\nCEC/OESC values must be verified against the applicable current code edition, Ontario amendments, equipment instructions, and site requirements.\n'
p.write_text(s)
print('Guided workspace integration patch applied')
