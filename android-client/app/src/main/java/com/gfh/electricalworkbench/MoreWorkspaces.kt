package com.gfh.electricalworkbench

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.sqrt

enum class MoreArea { HUB, LEARN, FIELD, PROJECTS, CAREER }

@Composable
fun MoreWorkspaceScreen(username: String, onOpenWeb: (String) -> Unit, initialArea: MoreArea = MoreArea.HUB) {
    var area by remember(initialArea) { mutableStateOf(initialArea) }
    if (area == MoreArea.HUB) {
        MWPage("More Tools", "Choose a task to open its tools and records.") {
            MWHubCard("🎓 Red Seal Study", "Daily 25 quiz, CEC navigator, formulas, control trainer and province/code profile") { area = MoreArea.LEARN }
            MWHubCard("🛠 Jobsite Tools", "Troubleshooting, nameplates, commissioning, inspection, blueprint scaling, fault-current and grounding/bonding tools") { area = MoreArea.FIELD }
            MWHubCard("📁 Saved Projects", "Project log, material takeoff, saved field records and cable planning") { area = MoreArea.PROJECTS }
            MWHubCard("📈 Trade Hours & Career", "Trade hours, credential wallet, wage calculator and official job resources") { area = MoreArea.CAREER }
            MWHubCard("⚡ Code News", "ESA/OESC bulletins and CEC/OESC notices") { onOpenWeb("/news.html") }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            OutlinedButton(onClick = { area = MoreArea.HUB }, modifier = Modifier.padding(10.dp)) { Text("← All workspaces") }
            Box(Modifier.fillMaxSize()) {
                when (area) {
                    MoreArea.LEARN -> LearnWorkspace(username)
                    MoreArea.FIELD -> FieldWorkspace(username)
                    MoreArea.PROJECTS -> ProjectsWorkspace(username)
                    MoreArea.CAREER -> CareerWorkspace(username)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun LearnWorkspace(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Daily 25", "CEC Navigator", "Formulas", "Controls", "Province")
    Column(Modifier.fillMaxSize()) {
        MWSectionHeader("Learn", "Red Seal / C of Q study and CEC reference workflows.")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MWCard2) {
            names.forEachIndexed { index, name -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) }) }
        }
        when (tab) {
            0 -> DailyQuiz(username, store)
            1 -> CecNavigator()
            2 -> FormulaLibrary()
            3 -> ControlTrainer()
            else -> ProvinceProfile()
        }
    }
}

@Composable
private fun DailyQuiz(username: String, store: MobileWorkspaceStore) {
    val questions = remember(username, LocalDate.now()) { MobileQuestionBank.daily25(LocalDate.now(), username) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var answered by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    MWPage("Red Seal / C of Q Daily 25", "Five questions from each A–E task category. The set is stable for the user and date.") {
        if (index >= questions.size) {
            MWCard("Daily quiz complete") {
                Text("Final score: $score / ${questions.size}", color = MWCyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Review explanations and use the CEC Navigator for weak topics.", color = MWMuted)
                if (!saved) {
                    Button(onClick = {
                        store.append("quizHistory", JSONObject().put("date", LocalDate.now().toString()).put("score", score).put("total", questions.size))
                        saved = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save result") }
                } else Text("Result saved locally.", color = MWGood)
            }
        } else {
            val q = questions[index]
            MWCard("Question ${index + 1} of ${questions.size}") {
                Text(q.category, color = MWCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(q.question, color = MWText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                q.options.forEachIndexed { optionIndex, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == optionIndex, onClick = { if (!answered) selected = optionIndex })
                        Text("${('A'.code + optionIndex).toChar()}. $option", color = MWText, modifier = Modifier.weight(1f))
                    }
                }
                if (answered) {
                    val correct = selected == q.correctIndex
                    Text(
                        if (correct) "Correct." else "Incorrect. Correct answer: ${('A'.code + q.correctIndex).toChar()}.",
                        color = if (correct) MWGood else MWDanger,
                        fontWeight = FontWeight.Bold
                    )
                    Text(q.explanation, color = MWMuted)
                    Text("Reference: ${q.reference}", color = MWCyan, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        if (!answered) {
                            if (selected >= 0) {
                                if (selected == q.correctIndex) score++
                                answered = true
                            }
                        } else {
                            index++
                            selected = -1
                            answered = false
                        }
                    },
                    enabled = answered || selected >= 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (answered) if (index == questions.lastIndex) "Finish quiz" else "Next question" else "Check answer") }
                Text("Score: $score / ${if (answered) index + 1 else index}", color = MWMuted)
            }
        }
    }
}

private data class MobileRule(val topic: String, val reference: String, val summary: String)

@Composable
private fun CecNavigator() {
    var query by remember { mutableStateOf("") }
    val entries = remember {
        listOf(
            MobileRule("Voltage drop / conductor length", "Appendix D / Table D3 study workflow", "Voltage drop, maximum run length and conductor-selection checks."),
            MobileRule("Residential service", "Rule 8-200 study workflow", "Dwelling demand including basic load, range, heating/A-C and special loads."),
            MobileRule("Apartment service", "Rule 8-202 study workflow", "Multi-suite demand and common/house load calculation."),
            MobileRule("School / hospital / other occupancy", "Section 8 study workflow", "Occupancy demand by area, connected load and applicable demand factors."),
            MobileRule("Grounding and bonding", "Section 10", "Service grounding, bonding jumpers, electrodes and equipment bonding."),
            MobileRule("Raceway fill", "Section 12 / Table 9 series", "Raceway dimensions, conductor fill and wiring-method installation."),
            MobileRule("Tap conductors", "Section 14", "Tap ampacity, length and downstream overcurrent-protection conditions."),
            MobileRule("Class 1 / Class 2", "Section 16", "Low-voltage source, OCPD and conductor requirements."),
            MobileRule("Patient care", "Section 24", "Patient-care environments, bonding, receptacles and isolated systems."),
            MobileRule("Transformers", "Section 26", "Transformer currents, conductors, OCPD, grounding and capacitor equipment."),
            MobileRule("Motors / feeders", "Section 28", "Motor conductors, overloads, branch OCPD, disconnects and multi-motor feeders."),
            MobileRule("Fire alarm / fire pump", "Section 32", "Fire-alarm wiring/power and special fire-pump requirements."),
            MobileRule("Welders", "Section 42", "Duty-cycle and group-demand treatment for welder conductors/feeders."),
            MobileRule("Emergency systems", "Section 46", "Emergency power, transfer and emergency-lighting supply requirements."),
            MobileRule("Electric heating", "Section 62", "Space heating, trace heating and heating load treatment."),
            MobileRule("Communications / fibre", "Section 60/86 study topics", "Separation, cable suitability and communications wiring considerations.")
        )
    }
    val filtered = entries.filter { query.isBlank() || (it.topic + it.reference + it.summary).contains(query, true) }
    MWPage("CEC Rule Navigator", "Search topic summaries and rule/table references without reproducing Code text.") {
        MWField("Search", query) { query = it }
        filtered.forEach { entry ->
            MWCard(entry.topic) {
                Text(entry.reference, color = MWCyan, fontWeight = FontWeight.Bold)
                Text(entry.summary, color = MWMuted)
            }
        }
    }
}

@Composable
private fun FormulaLibrary() {
    val formulas = """
OHM'S LAW
V = I × R     I = V / R     R = V / I

POWER
DC / 1φ resistive: P = V × I
1φ AC: P = V × I × PF
3φ AC: P = √3 × V(L-L) × I × PF
I(3φ) = P / (√3 × V × PF)

TRANSFORMERS
1φ: I = VA / V
3φ: I = VA / (√3 × V)
Approx terminal fault current: I_fault ≈ I_FLA / Z(pu)

VOLTAGE DROP — K FACTOR
1φ: VD = 2 × K × I × L / CM
3φ: VD = √3 × K × I × L / CM
L = VD × CM / (factor × K × I)
K = VD × CM / (factor × I × L)

POWER FACTOR CORRECTION
kVAR = kW × [tan(cos⁻¹ PF_old) − tan(cos⁻¹ PF_new)]

CT RATIO
Primary = Secondary × (CT primary / CT secondary)

4–20 mA
Signal = 4 + (process fraction × 16)

HYDRAULICS
Force = Pressure × Area

PVC MOVEMENT
Movement = run × ΔT × expansion coefficient
""".trimIndent()
    MWPage("Formula Library", "Fast trade formulas and rearranged forms.") {
        MWCard("Electrical formulas") { Text(formulas, color = MWText, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
    }
}

@Composable
private fun ControlTrainer() {
    val scenarios = listOf("3-wire starter", "Reversing starter", "Wye-delta starter", "VFD 4–20 mA", "CT measurement", "Fire alarm Class A/B")
    var scenario by remember { mutableStateOf(scenarios.first()) }
    val lesson = when (scenario) {
        "3-wire starter" -> "STOP(NC) and overload contacts are in series with the starter coil. START(NO) energizes the coil and the auxiliary contact seals around START. Opening STOP or overload de-energizes the coil."
        "Reversing starter" -> "Forward and reverse contactors swap two motor phases. Electrical and mechanical interlocking prevent both contactors from closing together."
        "Wye-delta starter" -> "Start in WYE so each winding sees about 57.7% of line voltage, then transition to DELTA for normal running."
        "VFD 4–20 mA" -> "For 0–1800 rpm mapped to 4–20 mA: 1200 rpm = 4 + (1200/1800 × 16) ≈ 14.67 mA."
        "CT measurement" -> "For a 400:5 CT, ratio = 80:1. A measured 2.5 A secondary corresponds to 200 A primary. Never open-circuit an energized CT secondary."
        else -> "Class A wiring returns to the source/control equipment and can maintain operation beyond a single open depending on topology. Class B terminates in the field with supervision."
    }
    MWPage("Control Trainer", "Motor controls, VFDs, CTs and signalling scenarios.") {
        MWCard("Choose scenario") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scenarios.forEach { item -> OutlinedButton(onClick = { scenario = item }) { Text(item) } }
            }
            Text(scenario, color = MWCyan, fontWeight = FontWeight.Bold)
            Text(lesson, color = MWText)
        }
    }
}

@Composable
private fun ProvinceProfile() {
    MWPage("Province / Code Profile", "CEC-based tools remain common while provincial amendments and inspection guidance are layered on top.") {
        MWCard("Ontario") {
            Text("Ontario profile: CEC/OESC-focused workspace with ESA news integration. Verify current Ontario amendments, bulletins and inspection requirements for final decisions.", color = MWText)
        }
        MWCard("Other provinces / territories") {
            Text("Core CEC tools can be reused, but local amendments and inspection bulletins need their own verified dataset.", color = MWMuted)
        }
    }
}

@Composable
private fun FieldWorkspace(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Troubleshoot", "Nameplate", "Commission", "Inspect", "Blueprint", "Fault", "Ground/Bond")
    Column(Modifier.fillMaxSize()) {
        MWSectionHeader("Field", "Native field tools and offline records.")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MWCard2) {
            names.forEachIndexed { index, name -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) }) }
        }
        when (tab) {
            0 -> TroubleshootingTool()
            1 -> NameplateTool(store)
            2 -> CommissioningTool(store, username)
            3 -> InspectionTool(store)
            4 -> BlueprintTool()
            5 -> FaultCurrentTool()
            else -> GroundBondTool()
        }
    }
}

@Composable
private fun TroubleshootingTool() {
    val symptoms = listOf("Motor will not start", "Breaker trips immediately", "Contactor chatters", "GFCI nuisance trips", "VFD fault / won't run", "Transformer secondary low", "4–20 mA reading wrong", "Fire alarm open trouble")
    var symptom by remember { mutableStateOf(symptoms.first()) }
    val steps = when (symptom) {
        "Motor will not start" -> "1. Establish safe work condition / required energized-work procedure.\n2. Confirm source voltage and disconnect/fuses/breaker.\n3. Check overload and stop/E-stop/interlock chain.\n4. Verify control voltage.\n5. Check starter coil command and auxiliary contacts.\n6. Check motor leads/windings and mechanical load.\n7. For VFD-fed motors, check drive enable, reference and faults."
        "Breaker trips immediately" -> "1. Do not repeatedly reclose into a fault.\n2. Isolate load and inspect for phase-phase/phase-ground faults.\n3. Verify breaker rating and fault duty.\n4. Check insulation/equipment faults.\n5. Distinguish inrush from a true fault using the applicable protection rules."
        "Contactor chatters" -> "Check control voltage under load, loose wiring, failing coil, dirty armature/shading ring, unstable PLC/VFD output, seal-in contact and stop/overload chain."
        "GFCI nuisance trips" -> "Check real leakage: moisture, damaged cords, downstream neutral-ground connections, shared/miswired neutrals, filters/VFDs and aggregate leakage. Never bypass GFCI protection."
        "VFD fault / won't run" -> "Record the exact fault code. Check line voltage, DC bus, motor cable/motor insulation, acceleration/deceleration, overload/current limit, enable/interlocks, local/remote mode and speed reference."
        "Transformer secondary low" -> "Check primary voltage and tap position, phase/connection, unloaded and loaded secondary voltage, loading/voltage drop and terminations."
        "4–20 mA reading wrong" -> "Confirm loop power, polarity, series wiring, input configuration, common/reference, scaling endpoints and sourcing/sinking. Measure loop current in series."
        else -> "Identify the zone/circuit and exact trouble indication. Check listed equipment instructions, field wiring for opens/shorts/grounds, EOL device/location, modules and power supplies."
    }
    MWPage("Troubleshooting Assistant", "Organizes a diagnostic process; it does not replace safe isolation, testing procedures or equipment instructions.") {
        MWCard("Symptom") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                symptoms.forEach { item -> OutlinedButton(onClick = { symptom = item }) { Text(item) } }
            }
            Text(symptom, color = MWCyan, fontWeight = FontWeight.Bold)
            Text(steps, color = MWText)
        }
    }
}

@Composable
private fun NameplateTool(store: MobileWorkspaceStore) {
    var equipment by remember { mutableStateOf("Motor") }
    var maker by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("575") }
    var phases by remember { mutableStateOf("3") }
    var rating by remember { mutableStateOf("60 hp") }
    var current by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> photo = uri?.toString().orEmpty() }
    MWPage("Equipment Nameplate Capture", "Attach a nameplate image and store equipment values locally.") {
        MWCard("Nameplate / equipment record") {
            MWField("Equipment type", equipment) { equipment = it }
            MWField("Manufacturer", maker) { maker = it }
            MWField("Model", model) { model = it }
            MWField("Voltage", voltage) { voltage = it }
            MWField("Phases", phases) { phases = it }
            MWField("Rating", rating) { rating = it }
            MWField("FLA / RLA / current", current) { current = it }
            MWField("Notes", notes) { notes = it }
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (photo.isBlank()) "Choose nameplate photo" else "Photo attached — change") }
            Button(onClick = {
                store.append("nameplates", JSONObject().put("date", LocalDate.now().toString()).put("equipment", equipment).put("manufacturer", maker).put("model", model).put("voltage", voltage).put("phases", phases).put("rating", rating).put("current", current).put("photo", photo).put("notes", notes))
                status = "Saved locally."
            }, modifier = Modifier.fillMaxWidth()) { Text("Save equipment record") }
            if (status.isNotBlank()) Text(status, color = MWGood)
        }
    }
}

@Composable
private fun CommissioningTool(store: MobileWorkspaceStore, username: String) {
    var project by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }
    var voltage by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var insulation by remember { mutableStateOf("") }
    var continuity by remember { mutableStateOf("PASS") }
    var rotation by remember { mutableStateOf("ABC") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    MWPage("Commissioning & Test Sheet", "Record test results offline with the project workspace.") {
        MWCard("Electrical test record") {
            MWField("Project", project) { project = it }
            MWField("Equipment / circuit", equipment) { equipment = it }
            MWField("Measured voltage", voltage) { voltage = it }
            MWField("Measured current", current) { current = it }
            MWField("Insulation resistance", insulation) { insulation = it }
            MWField("Continuity / bonding", continuity) { continuity = it }
            MWField("Phase rotation", rotation) { rotation = it }
            MWField("Notes", notes) { notes = it }
            Button(onClick = {
                store.append("commissioning", JSONObject().put("date", LocalDate.now().toString()).put("project", project).put("equipment", equipment).put("voltage", voltage).put("current", current).put("insulationResistance", insulation).put("continuity", continuity).put("phaseRotation", rotation).put("tester", username).put("notes", notes))
                status = "Commissioning record saved."
            }, modifier = Modifier.fillMaxWidth()) { Text("Save test sheet") }
            if (status.isNotBlank()) Text(status, color = MWGood)
        }
    }
}

@Composable
private fun InspectionTool(store: MobileWorkspaceStore) {
    var project by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var deficiencies by remember { mutableStateOf("") }
    var labels by remember { mutableStateOf(false) }
    var bonding by remember { mutableStateOf(false) }
    var torque by remember { mutableStateOf(false) }
    var covers by remember { mutableStateOf(false) }
    var clearance by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    MWPage("Inspection / Deficiency Checklist", "General field checklist. The inspection authority, Code and project specifications remain authoritative.") {
        MWCard("Field inspection") {
            MWField("Project", project) { project = it }
            MWField("Location", location) { location = it }
            MWCheck("Equipment/circuits labelled", labels) { labels = it }
            MWCheck("Bonding/grounding visually checked", bonding) { bonding = it }
            MWCheck("Terminations/torque documentation checked", torque) { torque = it }
            MWCheck("Covers/barriers installed", covers) { covers = it }
            MWCheck("Working space/access checked", clearance) { clearance = it }
            MWCheck("Required testing completed", testing) { testing = it }
            MWField("Deficiencies / notes", deficiencies) { deficiencies = it }
            Button(onClick = {
                store.append("inspections", JSONObject().put("date", LocalDate.now().toString()).put("project", project).put("location", location).put("labels", labels).put("bonding", bonding).put("torque", torque).put("covers", covers).put("clearance", clearance).put("testing", testing).put("deficiencies", deficiencies))
                status = "Inspection saved."
            }, modifier = Modifier.fillMaxWidth()) { Text("Save inspection") }
            if (status.isNotBlank()) Text(status, color = MWGood)
        }
    }
}

@Composable
private fun BlueprintTool() {
    var drawing by remember { mutableStateOf("125") }
    var scaleDrawing by remember { mutableStateOf("10") }
    var scaleActual by remember { mutableStateOf("1000") }
    var allowance by remember { mutableStateOf("10") }
    var result by remember { mutableStateOf("") }
    MWPage("Drawing / Blueprint Helper", "Scale drawing measurements and estimate actual cable/raceway lengths.") {
        MWCard("Scale calculator") {
            MWField("Measured drawing length (mm)", drawing) { drawing = it }
            MWField("Scale drawing units", scaleDrawing) { scaleDrawing = it }
            MWField("Scale actual units", scaleActual) { scaleActual = it }
            MWField("Routing/waste allowance (%)", allowance) { allowance = it }
            Button(onClick = {
                result = runCatching {
                    val actual = drawing.toDouble() * scaleActual.toDouble() / scaleDrawing.toDouble()
                    val withAllowance = actual * (1.0 + allowance.toDouble() / 100.0)
                    "Scaled actual length: %.2f mm\nWith %.1f%% allowance: %.2f mm\n= %.2f m".format(actual, allowance.toDouble(), withAllowance, withAllowance / 1000.0)
                }.getOrElse { "Check inputs." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Calculate actual length") }
            if (result.isNotBlank()) Text(result, color = MWText, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun FaultCurrentTool() {
    var kva by remember { mutableStateOf("75") }
    var voltage by remember { mutableStateOf("208") }
    var phases by remember { mutableStateOf("3") }
    var impedance by remember { mutableStateOf("3") }
    var upstream by remember { mutableStateOf("0") }
    var result by remember { mutableStateOf("") }
    MWPage("Transformer Fault-Current Estimate", "Approximate transformer-terminal current for study/planning; not a coordination or arc-flash study.") {
        MWCard("Available fault-current worksheet") {
            MWField("Transformer kVA", kva) { kva = it }
            MWField("Secondary voltage", voltage) { voltage = it }
            MWField("Phases (1 or 3)", phases) { phases = it }
            MWField("Transformer impedance (%)", impedance) { impedance = it }
            MWField("Upstream available fault current A (0 = ignore)", upstream) { upstream = it }
            Button(onClick = {
                result = runCatching {
                    val k = kva.toDouble(); val v = voltage.toDouble(); val z = impedance.toDouble() / 100.0
                    require(k > 0 && v > 0 && z > 0)
                    val fla = if (phases.trim() == "3") k * 1000.0 / (sqrt(3.0) * v) else k * 1000.0 / v
                    val transformerLimited = fla / z
                    val source = upstream.toDouble()
                    val combined = if (source > 0) 1.0 / (1.0 / transformerLimited + 1.0 / source) else transformerLimited
                    "Transformer FLA: %.1f A\nTransformer-limited terminal fault current: %.0f A\nCombined estimate: %.0f A".format(fla, transformerLimited, combined)
                }.getOrElse { "Check inputs." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Estimate fault current") }
            if (result.isNotBlank()) Text(result, color = MWText, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun GroundBondTool() {
    val items = listOf("Service grounding conductor", "Equipment bonding conductor", "Transformer secondary bonding jumper", "Grounding electrode conductor", "Ground rod / plate", "Grounding electrode ring", "HV station grounding grid")
    var item by remember { mutableStateOf(items.first()) }
    val answer = when (item) {
        "Equipment bonding conductor" -> "Start with Section 10 equipment bonding requirements and the applicable bonding-conductor sizing table. Identify circuit/feeder OCPD and conductor material before sizing."
        "Transformer secondary bonding jumper" -> "Use the separately derived system / transformer secondary bonding provisions in Section 10. Determine derived conductor size, transformer arrangement and connection point."
        "Ground rod / plate" -> "Use the electrode installation requirements for the selected electrode type, including dimensions, burial/location and connection method."
        "Grounding electrode ring" -> "Confirm ring-electrode construction, conductor material/size and burial conditions under Section 10."
        "HV station grounding grid" -> "High-voltage station grounding is specialized design work. Applicable high-voltage grounding provisions and engineering criteria, including touch/step potential, may apply."
        else -> "Identify service/derived-system rating, electrode type, conductor material and applicable Section 10 sizing rule/table. Verify the final size against CEC 2024."
    }
    MWPage("Grounding & Bonding Wizard", "Choose an installation element to identify the calculation/reference path.") {
        MWCard("Sizing path") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { option -> OutlinedButton(onClick = { item = option }) { Text(option) } }
            }
            Text(item, color = MWCyan, fontWeight = FontWeight.Bold)
            Text(answer, color = MWText)
        }
    }
}

@Composable
private fun ProjectsWorkspace(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Project Log", "Takeoff", "Records", "Cable Plan")
    Column(Modifier.fillMaxSize()) {
        MWSectionHeader("Projects", "Offline project, material and cable-planning records.")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MWCard2) {
            names.forEachIndexed { index, name -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) }) }
        }
        when (tab) {
            0 -> ProjectLog(store)
            1 -> MaterialTakeoff(store)
            2 -> SavedRecordSummary(store)
            else -> CablePlanner()
        }
    }
}

@Composable
private fun ProjectLog(store: MobileWorkspaceStore) {
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var system by remember { mutableStateOf("347/600 V 3φ 4W") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    MWPage("Project Workspace", "Keep project and field notes together locally.") {
        MWCard("New project") {
            MWField("Project name", name) { name = it }
            MWField("Client / employer", client) { client = it }
            MWField("Location", location) { location = it }
            MWField("Electrical system", system) { system = it }
            MWField("Notes", notes) { notes = it }
            Button(onClick = {
                store.append("projects", JSONObject().put("created", LocalDate.now().toString()).put("name", name).put("client", client).put("location", location).put("system", system).put("notes", notes))
                status = "Project saved."
            }, modifier = Modifier.fillMaxWidth()) { Text("Save project") }
            if (status.isNotBlank()) Text(status, color = MWGood)
        }
    }
}

@Composable
private fun MaterialTakeoff(store: MobileWorkspaceStore) {
    var lines by remember { mutableStateOf("12/2 NMD90,100,1.25\n4 in square boxes,12,4.50\nDuplex receptacles,10,3.25") }
    var result by remember { mutableStateOf("") }
    var total by remember { mutableStateOf(0.0) }

    fun calculate(): String {
        var sum = 0.0
        var count = 0
        val out = StringBuilder()
        lines.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(',')
            val desc = parts[0].trim()
            val qty = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 1.0
            val cost = parts.getOrNull(2)?.trim()?.toDoubleOrNull() ?: 0.0
            val extended = qty * cost
            sum += extended
            count++
            out.append("$desc : %.2f × %.2f = %.2f\n".format(qty, cost, extended))
        }
        total = sum
        out.append("\nItems: $count\nEstimated material total: $%.2f".format(sum))
        return out.toString()
    }

    MWPage("Material Takeoff / Job Planner", "Enter one line per item as description, quantity, unit cost.") {
        MWCard("Takeoff") {
            OutlinedTextField(value = lines, onValueChange = { lines = it }, label = { Text("Material lines") }, minLines = 8, modifier = Modifier.fillMaxWidth())
            Button(onClick = { result = calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Calculate takeoff") }
            OutlinedButton(onClick = {
                result = calculate()
                store.append("materials", JSONObject().put("date", LocalDate.now().toString()).put("lines", lines).put("estimatedTotal", total))
            }, modifier = Modifier.fillMaxWidth()) { Text("Save takeoff") }
            if (result.isNotBlank()) Text(result, color = MWText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SavedRecordSummary(store: MobileWorkspaceStore) {
    val collections = listOf("projects", "nameplates", "commissioning", "inspections", "hours", "credentials", "quizHistory", "materials", "unsafeConditions", "rubberGloves", "safetyMaintenance")
    MWPage("Saved Local Records", "Record counts from Projects, Field, Safety and Career.") {
        collections.forEach { collection ->
            MWCard(collection.replaceFirstChar { it.uppercase() }) {
                Text("${store.records(collection).size} saved record(s)", color = MWCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CablePlanner() {
    var current by remember { mutableStateOf("100") }
    var length by remember { mutableStateOf("50") }
    var kFactor by remember { mutableStateOf("12.9") }
    var circularMils by remember { mutableStateOf("133100") }
    var phases by remember { mutableStateOf("3") }
    var voltage by remember { mutableStateOf("600") }
    var result by remember { mutableStateOf("") }
    MWPage("Cable & Raceway Planner", "Voltage-drop worksheet; final ampacity/raceway selection must be verified in the Workbench.") {
        MWCard("Voltage drop") {
            MWField("Load current (A)", current) { current = it }
            MWField("One-way length (m)", length) { length = it }
            MWField("K factor", kFactor) { kFactor = it }
            MWField("Circular mil area", circularMils) { circularMils = it }
            MWField("Phases (1 or 3)", phases) { phases = it }
            MWField("System voltage", voltage) { voltage = it }
            Button(onClick = {
                result = runCatching {
                    val lengthFt = length.toDouble() * 3.28084
                    val factor = if (phases.trim() == "3") sqrt(3.0) else 2.0
                    val vd = factor * kFactor.toDouble() * current.toDouble() * lengthFt / circularMils.toDouble()
                    "Voltage drop: %.2f V\nPercent: %.2f%%\n\nVerify final conductor selection using the applicable CEC 2024 method and Table D3 notes where relevant.".format(vd, vd / voltage.toDouble() * 100.0)
                }.getOrElse { "Check inputs." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Calculate voltage drop") }
            if (result.isNotBlank()) Text(result, color = MWText, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CareerWorkspace(username: String) {
    val context = LocalContext.current
    val store = remember(username) { MobileWorkspaceStore(context, username) }
    var tab by remember { mutableIntStateOf(0) }
    val names = listOf("Hours", "Credentials", "Wage", "Jobs")
    Column(Modifier.fillMaxSize()) {
        MWSectionHeader("Career & Apprenticeship", "Track hours, credentials, wages and official trade resources.")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MWCard2) {
            names.forEachIndexed { index, name -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) }) }
        }
        when (tab) {
            0 -> HoursTracker(store)
            1 -> CredentialWallet(store)
            2 -> WageCalculator()
            else -> JobResources()
        }
    }
}

@Composable
private fun HoursTracker(store: MobileWorkspaceStore) {
    var employer by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Construction / maintenance") }
    var hours by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val total = remember(revision) { store.records("hours").sumOf { it.optDouble("hours", 0.0) } }
    MWPage("Apprenticeship / Trade Hours", "Track work hours locally by employer and category.") {
        MWCard("Current total") { Text("%.1f h".format(total), color = MWCyan, fontSize = 26.sp, fontWeight = FontWeight.Black) }
        MWCard("Add hours") {
            MWField("Employer", employer) { employer = it }
            MWField("Work category", category) { category = it }
            MWField("Hours", hours) { hours = it }
            MWField("Notes", notes) { notes = it }
            Button(onClick = {
                val value = hours.toDoubleOrNull() ?: 0.0
                if (value > 0.0) {
                    store.append("hours", JSONObject().put("date", LocalDate.now().toString()).put("employer", employer).put("category", category).put("hours", value).put("notes", notes))
                    revision++
                    hours = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save hours") }
        }
    }
}

@Composable
private fun CredentialWallet(store: MobileWorkspaceStore) {
    var name by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    val records = remember(revision) { store.records("credentials").takeLast(20).reversed() }
    MWPage("Credential Wallet", "Keep certification and training reminders locally.") {
        MWCard("Add credential") {
            MWField("Credential / training", name) { name = it }
            MWField("Issuer", issuer) { issuer = it }
            MWField("Expiry / renewal date", expiry) { expiry = it }
            MWField("Notes", notes) { notes = it }
            Button(onClick = {
                store.append("credentials", JSONObject().put("name", name).put("issuer", issuer).put("expiry", expiry).put("notes", notes))
                revision++
            }, modifier = Modifier.fillMaxWidth()) { Text("Save credential") }
        }
        MWCard("Saved credentials") {
            if (records.isEmpty()) Text("No credentials saved yet.", color = MWMuted)
            records.forEach { item -> Text("${item.optString("name")} · ${item.optString("issuer")} · ${item.optString("expiry")}", color = MWText, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun WageCalculator() {
    var rate by remember { mutableStateOf("45") }
    var regularHours by remember { mutableStateOf("40") }
    var overtimeHours by remember { mutableStateOf("0") }
    var overtimeMultiplier by remember { mutableStateOf("1.5") }
    var weeks by remember { mutableStateOf("52") }
    var result by remember { mutableStateOf("") }
    MWPage("Wage Calculator", "Simple gross-pay estimate; payroll deductions, collective agreements and employment standards may change actual pay.") {
        MWCard("Gross pay worksheet") {
            MWField("Hourly rate", rate) { rate = it }
            MWField("Regular hours / week", regularHours) { regularHours = it }
            MWField("Overtime hours / week", overtimeHours) { overtimeHours = it }
            MWField("OT multiplier", overtimeMultiplier) { overtimeMultiplier = it }
            MWField("Weeks / year", weeks) { weeks = it }
            Button(onClick = {
                result = runCatching {
                    val weekly = rate.toDouble() * regularHours.toDouble() + rate.toDouble() * overtimeMultiplier.toDouble() * overtimeHours.toDouble()
                    "Weekly gross: $%.2f\nAnnualized gross: $%.2f".format(weekly, weekly * weeks.toDouble())
                }.getOrElse { "Check inputs." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
            if (result.isNotBlank()) Text(result, color = MWText, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun JobResources() {
    val context = LocalContext.current
    MWPage("Official Job & Trade Resources", "External public sources open explicitly in your browser.") {
        MWHubCard("Government of Canada Job Bank", "Search electrician and technologist postings across Canada.") { openExternal(context, "https://www.jobbank.gc.ca/") }
        MWHubCard("Skilled Trades Ontario", "Ontario trade registration, certification and apprenticeship information.") { openExternal(context, "https://www.skilledtradesontario.ca/") }
        MWHubCard("Red Seal Program", "Interprovincial Red Seal trade information.") { openExternal(context, "https://www.red-seal.ca/") }
    }
}
