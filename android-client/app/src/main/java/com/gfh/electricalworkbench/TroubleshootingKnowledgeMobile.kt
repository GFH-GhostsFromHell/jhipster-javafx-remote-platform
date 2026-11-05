package com.gfh.electricalworkbench

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Independent offline knowledge-base summaries; site and OEM instructions remain authoritative. */
private data class DiagnosticGuide(val category: String, val title: String, val faultChecks: List<String>, val decision: String)
private val universalDiagnosis = listOf(
    "SAFETY: Verify work authorization, hazard assessment, isolation of every energy source, lockout/tagout and absence of voltage using an approved tester.",
    "Interview the operator: exactly what failed, when, which mode, and any preceding alarms or recent repairs.",
    "Review the OEM drawing, sequence of operation, fault logs, interlocks and expected device states.",
    "Divide source, protection, control power, commands, I/O, output and mechanical load into testable sections.",
    "Select a correctly rated instrument, establish the expected result, and test systematically under approved procedures.",
    "Correct the verified root cause; do not defeat safety or overload protections.",
    "Follow approved return-to-service checks and record readings, repairs, resets and remaining deficiencies."
)
private val diagnosticGuides = listOf(
    DiagnosticGuide("Motors", "Three-phase motor does not start", listOf("Check supply/phase-loss and starter trip status.", "Trace STOP, E-stop, overload NC, permissives and coil command.", "With isolation confirmed, check winding/insulation and mechanical jam by approved methods."), "Coil command absent → controls; contactor operates but no torque → power path, motor or load."),
    DiagnosticGuide("Motors", "Contactor chatters", listOf("Check control supply versus coil-rated voltage.", "Inspect loose terminals, worn auxiliary seal-in contacts and contactor wear.", "Review PLC commands and interlocks for cycling."), "Unstable coil voltage → wiring/control source; healthy voltage with chatter → contactor mechanics."),
    DiagnosticGuide("Motors", "Single-phasing / imbalance", listOf("Review protection and overload events.", "Confirm phase-to-phase supply/load measurements only by authorized methods.", "Inspect fuses, contacts, leads and motor after isolation."), "Supply missing a phase → upstream; supply normal but load missing → starter/cable."),
    DiagnosticGuide("Controls", "START/STOP seal-in will not latch", listOf("Trace series NC stop, overload and process permissives.", "Verify NO auxiliary seal-in path and coil voltage.", "Inspect reset logic and 2-wire versus 3-wire design."), "Runs only while START held → inspect seal-in contact or PLC latch rung."),
    DiagnosticGuide("Controls", "Forward/reverse motor issue", listOf("Inspect mutual mechanical and electrical interlocks under isolation.", "Trace selected coil and opposite NC auxiliary contact.", "Confirm safe direction, feedback and stopping sequence."), "Both coils requested → take out of service; never bypass interlocks."),
    DiagnosticGuide("Controls", "ON-delay / OFF-delay replaced incorrectly", listOf("Write a timing chart from input transition through output state.", "Confirm TON versus TOF, timebase and NO/NC terminal meanings.", "Test timing and fail-safe operation using approved procedure."), "TON delays energization; TOF delays de-energization."),
    DiagnosticGuide("PLC", "PLC input ON, output OFF", listOf("Observe rung from input through every permissive and interlock.", "Find the first false instruction and its physical source.", "If output rung true, inspect output module power and field wiring."), "Rung false → logic/conditions; rung true, no terminal voltage → output stage. Do not force safety I/O."),
    DiagnosticGuide("PLC", "4–20 mA instrumentation fault", listOf("Review loop supply, AI card mode and configured sensor range.", "Compare transmitter local indication to loop current and raw PLC value.", "Check open loop, polarity, shields and scaling: (mA − 4)/16 × span + minimum."), "Correct current, wrong display → scaling; near 0 mA → open loop/supply."),
    DiagnosticGuide("VFD", "VFD overcurrent", listOf("Record fault stage: start/accel/steady load/decel.", "Inspect ramps, nameplate settings and mechanical binding.", "OEM-isolate drive before any cable or motor insulation test."), "Start trip → output/cable/motor; loaded trip → torque, process, acceleration."),
    DiagnosticGuide("VFD", "VFD DC-bus overvoltage", listOf("Record whether alarm occurs during deceleration.", "Review input voltage, ramp, regenerative load and braking design.", "OEM-qualified inspection of resistor/chopper and DC bus only."), "Deceleration trip → regenerative energy; never access charged DC bus."),
    DiagnosticGuide("VFD", "VFD reversed motor direction", listOf("Check commanded direction and drive input assignment.", "Confirm safe equipment conditions for a test jog.", "Correct motor phasing only with isolation and OEM authorization."), "Wrong command → control logic; correct command/wrong rotation → approved motor lead phasing."),
    DiagnosticGuide("Mining", "Battery-electric mining vehicle HV enable", listOf("Stop, secure machine, and log BMS/traction/thermal alarms.", "Review LV auxiliary supply and safe external permissive diagnostics.", "Refer HV isolation, precharge, contactor and battery checks to OEM/site HV-qualified personnel."), "Isolation/precharge fault → no bypass; escalate to authorized HV team."),
    DiagnosticGuide("Mining", "Mine sump pump trips", listOf("Check level switches, overload/VFD status and discharge obstruction.", "Inspect electrical cable/motor after site isolation and moisture precautions.", "Evaluate mechanical blockage, dry-run and controller sequence."), "No command → level/PLC; high current → hydraulic load or motor."),
    DiagnosticGuide("Mining", "Conveyor E-stop or pull-cord trip", listOf("Review exact safety trip and startup sequencing alarms.", "Inspect pull-cords, belt drift, zero-speed and chute switches.", "Check motor/drive and mechanics only after isolation."), "Never bridge safety chain. Correct and verify complete stop/restart sequence."),
    DiagnosticGuide("Mining", "Ventilation fan interlock", listOf("Coordinate with mine control; establish approved ventilation state.", "Trace airflow/gas/damper/door safety permissives.", "Investigate starter, VFD and motor after ruling out safety inhibit."), "Life-safety permissive missing → do not bypass; escalate."),
    DiagnosticGuide("Protection", "Feeder relay trip", listOf("Record relay element and sequence of events.", "Read one-line, coordination settings and affected equipment.", "Use switching authorization for downstream fault assessment."), "No repeated reclose of unknown fault; investigate phase/ground/overload target."),
    DiagnosticGuide("Protection", "SEL/GE relay or RTU loses SCADA", listOf("Separate local protection health from remote telemetry.", "Review power, network switch, link, permitted connectivity and alarms.", "Check DNP/RTU point mapping, communications and timestamp under change control."), "Local data correct/network down → link; link good/points bad → mapping/protocol."),
    DiagnosticGuide("HVAC", "Furnace or makeup-air unit lockout", listOf("Map call, fan, airflow proof, purge, ignition and flame proving sequence.", "Record stage and lockout reason; inspect BAS enable and limits.", "Refer combustion/gas train work to properly qualified service personnel."), "Never jump flame or pressure safeties; use OEM sequence."),
    DiagnosticGuide("Distribution", "Transformer overheating", listOf("Compare actual load and temperatures with nameplate and trend.", "Review cooling, ventilation, harmonics and phase imbalance.", "Qualified inspection of winding/insulation/connection concerns under isolation."), "Protection trip or dielectric issue → do not re-energize without investigation."),
    DiagnosticGuide("Distribution", "Voltage low only under load", listOf("Compare supply and load-end condition at matched load under authorized method.", "Review feeder conductor/length and load current.", "Inspect high-resistance connections and phase imbalance after isolation."), "Normal no-load voltage, low loaded voltage → resistance, undersized run or source capacity."),
    DiagnosticGuide("Life safety", "Fire alarm circuit trouble", listOf("Identify panel trouble and impacted circuits.", "Use authorized impairment and fire-watch controls.", "Inspect field wiring supervision, EOL location and device configuration using listed procedures."), "Never leave protections disabled; restore and acceptance-test.")
)

@Composable
internal fun TroubleshootingKnowledgeMobile() {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(diagnosticGuides.first()) }
    var expanded by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(setOf<Int>()) }
    var asset by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Select a diagnostic problem, then work through the safety-first procedure.") }
    val steps = universalDiagnosis.take(4) + selected.faultChecks + universalDiagnosis.drop(4)
    val options = diagnosticGuides.filter { it.title.contains(search, ignoreCase = true) || it.category.contains(search, ignoreCase = true) }
    Text("Industrial Troubleshooting · offline KB", style = MaterialTheme.typography.titleLarge)
    Text("Epiroc-style interview and field troubleshooting methods. General guidance only; OEM/site safety procedures are authoritative.", style = MaterialTheme.typography.bodySmall)
    Text("SAFETY: Lockout/tagout, zero-energy verification and site authorizations apply. High-voltage and battery-electric repairs require qualified OEM/site personnel.", color = MaterialTheme.colorScheme.error)
    OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search faults or equipment") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.title + " ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.take(30).forEach { guide ->
                DropdownMenuItem(text = { Text(guide.category + " · " + guide.title) }, onClick = {
                    selected = guide; completed = emptySet(); expanded = false; message = "Review safety and begin diagnostic steps."
                })
            }
        }
    }
    Text(selected.decision, style = MaterialTheme.typography.bodyMedium)
    steps.forEachIndexed { index, step ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Checkbox(checked = index in completed, onCheckedChange = { checked ->
                completed = if (checked) completed + index else completed - index
            })
            Text((index + 1).toString() + ". " + step, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(completed.size.toString() + " of " + steps.size + " steps checked")
    OutlinedTextField(value = asset, onValueChange = { asset = it }, label = { Text("Machine / location / work order") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Symptoms, measured values and resolution") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    Button(onClick = {
        runCatching {
            val prefs = context.getSharedPreferences("electrical_workbench_diagnostic_kb", Context.MODE_PRIVATE)
            val old = JSONArray(prefs.getString("cases", "[]"))
            val item = JSONObject().put("date", java.time.Instant.now().toString())
                .put("title", selected.title).put("asset", asset).put("notes", notes)
                .put("completed", completed.size).put("total", steps.size)
            val replacement = JSONArray().put(item)
            for (i in 0 until minOf(old.length(), 49)) replacement.put(old.get(i))
            prefs.edit().putString("cases", replacement.toString()).apply()
        }.onSuccess { message = "Troubleshooting case saved locally on this device." }
            .onFailure { message = "Could not save case: " + it.message }
    }, modifier = Modifier.fillMaxWidth()) { Text("Save troubleshooting case") }
    OutlinedButton(onClick = {
        val content = selected.title + "\n" + steps.mapIndexed { i, s -> (i + 1).toString() + ". " + s }.joinToString("\n")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Troubleshooting steps", content))
        message = "Procedure copied."
    }, modifier = Modifier.fillMaxWidth()) { Text("Copy steps") }
    Text(message)
    val history = remember(message) {
        runCatching {
            val raw = context.getSharedPreferences("electrical_workbench_diagnostic_kb", Context.MODE_PRIVATE).getString("cases", "[]")
            val saved = JSONArray(raw)
            (0 until minOf(saved.length(), 8)).map { saved.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }
    if (history.isNotEmpty()) {
        HorizontalDivider()
        Text("Recent saved cases", style = MaterialTheme.typography.titleMedium)
        history.forEach { record ->
            Text((record.optString("date").take(10)) + " · " + record.optString("title") + " · " + record.optString("asset"))
        }
    }
}
